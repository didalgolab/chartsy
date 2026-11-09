/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.ml.online.neural;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.SplittableRandom;
import java.util.function.DoubleSupplier;

import static org.junit.jupiter.api.Assertions.*;

class IrpropPlusInitializerTest {
    private static final double[][] X = {{.3,-.8,1.2},{-.6,.4,.2},{.5,.9,-.7}};
    private static final double[][] Y = {{.2},{-.1},{.5}};
    private static IrpropPlusTrainer.Options options(long seed) {
        return IrpropPlusTrainer.Options.historicalShark(3, NeuralActivation.FAST_SIGMOID, 3, seed);
    }
    private static final class Values implements DoubleSupplier {
        int calls;
        @Override public double getAsDouble() { return ++calls * .001; }
    }

    @Test void fit_supplied_values_advance_once_per_coordinate_and_preserve_owned_scalar_model() {
        var values = new Values();
        var trainer = new ScalarNeuralTrainer(options(253), values);
        assertEquals(0, values.calls);
        var scalar = trainer.fit(X, new double[]{.2,-.1,.5});
        var first = scalar.unwrap(IrpropPlusTrainer.FittedModel.class).orElseThrow();
        assertEquals(19, values.calls);
        assertEquals(1, first.diagnostics().fitOrdinal());
        assertEquals("ADVANCING_SUPPLIED_INITIALIZER", first.diagnostics().initialization());
        for (int i=0; i<19; i++) assertEquals((i+1)*.001, first.initialParameters()[i]);
        double predicted = scalar.predictAsDouble(X[0]);
        first.initialParameters()[0] = 999;
        first.network().parameters()[0] = 999;
        assertEquals(predicted, scalar.predictAsDouble(X[0]));
        assertEquals(19, values.calls);
        var second = trainer.fit(X, new double[]{.2,-.1,.5}).unwrap(IrpropPlusTrainer.FittedModel.class).orElseThrow();
        assertEquals(38, values.calls);
        assertEquals(2, second.diagnostics().fitOrdinal());
        assertEquals(.020, second.initialParameters()[0]);
        assertFalse(Arrays.equals(first.initialParameters(), second.initialParameters()));
    }

    @Test void fit_invalid_matrices_consume_nothing_and_leave_first_ordinal_available() {
        double[][][] invalid = {null, new double[0][], {null}, {{1,2,3},{1}}, {{1,Double.NaN,3}}, {{1,2,Double.POSITIVE_INFINITY}}};
        for (double[][] bad : invalid) {
            var values = new Values();
            var trainer = new IrpropPlusTrainer(options(253), values);
            assertThrows(IllegalArgumentException.class, () -> trainer.fit(bad, Y));
            assertThrows(IllegalArgumentException.class, () -> trainer.fit(X, bad));
            assertEquals(0, values.calls);
            assertEquals(1, trainer.fit(X, Y).diagnostics().fitOrdinal());
        }
        var values = new Values();
        var trainer = new IrpropPlusTrainer(options(253), values);
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(X, new double[][]{{1},{2}}));
        assertEquals(0, values.calls);
        assertEquals(1, trainer.fit(X, Y).diagnostics().fitOrdinal());
    }

    @Test void constructor_rejects_missing_supplier_and_injected_seed_is_not_used() {
        assertThrows(IllegalArgumentException.class, () -> new IrpropPlusTrainer(options(253), null));
        assertThrows(IllegalArgumentException.class, () -> new ScalarNeuralTrainer(options(253), null));
        var a = new IrpropPlusTrainer(options(1), new Values()).fit(X,Y);
        var b = new IrpropPlusTrainer(options(Long.MIN_VALUE), new Values()).fit(X,Y);
        assertArrayEquals(a.initialParameters(), b.initialParameters());
        assertArrayEquals(a.network().parameters(), b.network().parameters());
        var outside = new IrpropPlusTrainer(options(1), () -> .25).fit(X,Y);
        for (double v : outside.initialParameters()) assertEquals(.25,v);
    }

    @Test void fit_supplier_failures_consume_only_the_attempted_prefix_without_retry() {
        for (double invalid : new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            int[] calls = {0};
            var trainer = new IrpropPlusTrainer(options(1), () -> ++calls[0] == 4 ? invalid : .01);
            assertThrows(ArithmeticException.class, () -> trainer.fit(X,Y));
            assertEquals(4,calls[0]);
        }
        int[] calls = {0};
        var expected = new IllegalStateException("supplier failed");
        var trainer = new IrpropPlusTrainer(options(1), () -> {
            if (++calls[0] == 4) throw expected;
            return .01;
        });
        assertSame(expected, assertThrows(IllegalStateException.class, () -> trainer.fit(X,Y)));
        assertEquals(4,calls[0]);
    }

    @Test void fitFromParameters_is_isolated_from_the_supplied_stream_and_ordinal() {
        var values = new Values();
        var trainer = new IrpropPlusTrainer(options(1),values);
        double[] parameters = new double[19];
        var explicit = trainer.fitFromParameters(X,Y,parameters);
        assertEquals(0,values.calls);
        assertEquals(0,explicit.diagnostics().fitOrdinal());
        assertEquals("EXPLICIT_PARAMETERS",explicit.diagnostics().initialization());
        parameters[0]=999;
        assertEquals(0,explicit.initialParameters()[0]);
        assertEquals(1,trainer.fit(X,Y).diagnostics().fitOrdinal());
        assertEquals(19,values.calls);
        trainer.fitFromParameters(X,Y,new double[19]);
        assertEquals(2,trainer.fit(X,Y).diagnostics().fitOrdinal());
        assertEquals(38,values.calls);
    }

    @Test void fit_default_retains_exact_SplittableRandom_bounds_and_advancing_seed_sequence() {
        var trainer = new IrpropPlusTrainer(options(253));
        var random = new SplittableRandom(253);
        for (int ordinal=1; ordinal<=2; ordinal++) {
            double[] expected = new double[19];
            for (int i=0;i<expected.length;i++) expected[i]=random.nextDouble(-.1,.1);
            var model = trainer.fit(X,Y);
            assertArrayEquals(expected,model.initialParameters());
            assertEquals(ordinal,model.diagnostics().fitOrdinal());
            assertEquals("ADVANCING_SPLITTABLE_RANDOM",model.diagnostics().initialization());
        }
    }
}
