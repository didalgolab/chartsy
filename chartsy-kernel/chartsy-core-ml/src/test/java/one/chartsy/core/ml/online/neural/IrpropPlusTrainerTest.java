/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.ml.online.neural;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class IrpropPlusTrainerTest {
    private static final double[][] X={{.3,-.8,1.2},{-.6,.4,.2},{.5,.9,-.7}};
    private static final double[][] Y={{.2},{-.1},{.5}};

    @Test void coordinate_updates_cover_growth_reversal_with_and_without_rollback() {
        var s=new IrpropPlusTrainer.UpdateState(new double[]{0});
        s.step(10,new double[]{2});assertEquals(-.01,s.point[0]);
        s.step(9,new double[]{3});assertEquals(-.022,s.point[0],1e-16);assertEquals(.012,s.delta[0]);
        s.step(8,new double[]{-1});assertEquals(-.022,s.point[0],1e-16);assertEquals(.006,s.delta[0]);assertEquals(0,s.previousGradient[0]);
        s.step(9,new double[]{-2});assertEquals(-.016,s.point[0],1e-16);
        s.step(10,new double[]{1});assertEquals(-.022,s.point[0],1e-16);assertEquals(.003,s.delta[0]);
        assertEquals(1,s.increases);assertEquals(2,s.signReversals);assertEquals(1,s.backtracks);
    }

    @Test void equal_loss_does_not_backtrack_and_zero_derivative_does_not_move() {
        var s=new IrpropPlusTrainer.UpdateState(new double[]{0});
        s.step(1,new double[]{1});s.step(1,new double[]{-1});assertEquals(-.01,s.point[0]);assertEquals(0,s.backtracks);
        s.step(2,new double[]{0});assertEquals(-.01,s.point[0]);assertEquals(0,s.previousStep[0],0);
        s.step(1,new double[]{-1});assertEquals(-.005,s.point[0]);
    }

    @Test void pinned_step_bounds_and_underflow_sign_product_are_preserved() {
        var s=new IrpropPlusTrainer.UpdateState(new double[]{0,0,0});
        s.delta[0]=1e100;s.previousGradient[0]=1;
        s.delta[1]=Double.MIN_VALUE;s.previousGradient[1]=1;
        s.step(1,new double[]{1,-1,1e-300});
        assertEquals(1e100,s.delta[0]);assertEquals(0,s.delta[1]);assertEquals(-.01,s.point[2]);
        s.step(.9,new double[]{0,0,1e-300});assertEquals(.01,s.delta[2]);assertEquals(-.02,s.point[2]);
    }

    @Test void fixed_cycles_return_final_iterate_even_when_an_earlier_loss_is_lower() {
        var shape=new FeedForwardNetwork.Shape(1,1,1,true,true);
        double[] initial=new double[shape.parameterCount()];initial[4]=.001;
        var one=new IrpropPlusTrainer(IrpropPlusTrainer.Options.historicalShark(1,NeuralActivation.LINEAR,1,1));
        var model=one.fitFromParameters(new double[][]{{0}},new double[][]{{0}},initial);
        assertEquals(.000001,model.diagnostics().initialLoss(),1e-18);
        assertEquals(.000081,model.diagnostics().finalLoss(),1e-18);
        assertEquals(-.009,model.network().parameters()[4],1e-16);
        assertEquals(2,model.diagnostics().evaluations());
        var two=new IrpropPlusTrainer(IrpropPlusTrainer.Options.historicalShark(1,NeuralActivation.LINEAR,2,1));
        var rolled=two.fitFromParameters(new double[][]{{0}},new double[][]{{0}},initial);
        assertEquals(.001,rolled.network().parameters()[4],1e-16);assertEquals(1,rolled.diagnostics().backtracks());
    }

    @Test void advancing_seeded_fits_repeat_sequences_but_do_not_reset_each_fit() {
        var options=IrpropPlusTrainer.Options.historicalShark(3,NeuralActivation.FAST_SIGMOID,10,253);
        var a=new IrpropPlusTrainer(options);var b=new IrpropPlusTrainer(options);
        var a1=a.fit(X,Y);var a2=a.fit(X,Y);var b1=b.fit(X,Y);var b2=b.fit(X,Y);
        assertArrayEquals(a1.initialParameters(),b1.initialParameters());assertArrayEquals(a2.initialParameters(),b2.initialParameters());
        assertArrayEquals(a1.network().parameters(),b1.network().parameters());assertArrayEquals(a2.network().parameters(),b2.network().parameters());
        assertFalse(Arrays.equals(a1.initialParameters(),a2.initialParameters()));assertEquals(2,a2.diagnostics().fitOrdinal());
        var logistic=new IrpropPlusTrainer(IrpropPlusTrainer.Options.historicalShark(3,NeuralActivation.LOGISTIC,10,253)).fit(X,Y);
        assertArrayEquals(a1.initialParameters(),logistic.initialParameters());
    }

    @Test void explicit_parameters_do_not_consume_stream_and_all_snapshots_are_owned() {
        var options=IrpropPlusTrainer.Options.historicalShark(3,NeuralActivation.FAST_SIGMOID,5,253);
        var trainer=new IrpropPlusTrainer(options);double[] initial=new double[19];
        double[][] x=Arrays.stream(X).map(double[]::clone).toArray(double[][]::new);
        double[][] y=Arrays.stream(Y).map(double[]::clone).toArray(double[][]::new);
        var explicit=trainer.fitFromParameters(x,y,initial);double[] expected=explicit.predict(X[0]);
        initial[0]=999;x[0][0]=999;y[0][0]=999;explicit.initialParameters()[0]=999;
        assertArrayEquals(expected,explicit.predict(X[0]));
        assertArrayEquals(new IrpropPlusTrainer(options).fit(X,Y).initialParameters(),trainer.fit(X,Y).initialParameters());
        assertEquals(0,explicit.diagnostics().fitOrdinal());assertSame(explicit.network(),explicit.unwrap(FeedForwardNetwork.class).orElseThrow());
    }

    @Test void constant_targets_still_train_exactly_the_configured_cycles() {
        var trainer=new IrpropPlusTrainer(IrpropPlusTrainer.Options.historicalShark(3,NeuralActivation.FAST_SIGMOID,5000,253));
        var model=trainer.fit(X,new double[][]{{.2},{.2},{.2}});
        assertEquals(5000,model.diagnostics().cycles());assertEquals(5001,model.diagnostics().evaluations());
        assertTrue(Double.isFinite(model.diagnostics().finalLoss()));assertTrue(Double.isFinite(model.predict(X[0])[0]));
    }

    @Test void invalid_rows_do_not_advance_rng_and_scalar_adapter_uses_the_same_fitted_network() {
        var options=IrpropPlusTrainer.Options.historicalShark(3,NeuralActivation.FAST_SIGMOID,10,253);
        var trainer=new IrpropPlusTrainer(options);
        assertThrows(IllegalArgumentException.class,()->trainer.fit(new double[][]{{1,Double.NaN,2}},new double[][]{{1}}));
        assertThrows(IllegalArgumentException.class,()->trainer.fit(X,new double[][]{{1},{2}}));
        var model=trainer.fit(X,Y);assertEquals(1,model.diagnostics().fitOrdinal());
        var scalar=new ScalarNeuralTrainer(options).fit(X,new double[]{.2,-.1,.5});
        assertArrayEquals(model.network().parameters(),scalar.unwrap(FeedForwardNetwork.class).orElseThrow().parameters());
        assertEquals(model.predict(X[0])[0],scalar.predictAsDouble(X[0]));
        assertThrows(IllegalArgumentException.class,()->new IrpropPlusTrainer.Options(0,NeuralActivation.LINEAR,NeuralActivation.LINEAR,10,1,true,true));
        assertThrows(IllegalArgumentException.class,()->new IrpropPlusTrainer.Options(1,NeuralActivation.LINEAR,NeuralActivation.LINEAR,0,1,true,true));
    }
}
