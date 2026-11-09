/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.ml.online.neural;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BatchBackpropTrainerTest {
    private static final double[][] X = {{1}, {2}};
    private static final double[][] Y = {{.7, -.2}, {.5, .3}};
    private static final double[] INITIAL = {.5, .4, -.3, .1, .2, -.1};

    private static BatchBackpropTrainer.Options options(int cycles, double rate) {
        return new BatchBackpropTrainer.Options(1, NeuralActivation.LINEAR, NeuralActivation.LINEAR,
                cycles, rate, 473, false, true);
    }

    @Test void fitFromParameters_independently_calculated_vector_update_is_simultaneous_and_averages_rows_only() {
        var model = new BatchBackpropTrainer(options(1, .1)).fitFromParameters(X, Y, INITIAL);
        // h=(.6,1.1); heads=(.44,-.28),(.64,-.43); row-mean, summed-head SSE=.31325.
        // The six derivatives are (.47,-.002,-.851,.195,-.12,-.81).
        assertArrayEquals(new double[]{.453, .4002, -.2149, .0805, .212, -.019},
                model.network().parameters(), 2e-15);
        assertEquals(.31325, model.diagnostics().initialLoss(), 2e-15);
        assertEquals(1, model.diagnostics().cycles());
        assertEquals(2, model.diagnostics().evaluations());
        assertEquals(2, model.diagnostics().rows());
        assertEquals(0, model.diagnostics().fitOrdinal());
        assertArrayEquals(new double[]{.4255067, -.13364915}, model.predict(new double[]{1}), 2e-15);
        assertArrayEquals(INITIAL, model.initialParameters());
    }

    @Test void fitFromParameters_exact_cycle_count_returns_final_iterate_even_when_loss_increases() {
        var model = new BatchBackpropTrainer(options(1, 10)).fitFromParameters(X, Y, INITIAL);
        assertArrayEquals(new double[]{-4.2, .42, 8.21, -1.85, 1.4, 8.0},
                model.network().parameters(), 2e-14);
        assertTrue(model.diagnostics().finalLoss() > model.diagnostics().initialLoss());
        assertEquals(1, model.diagnostics().lossIncreases());
        assertEquals(2, model.diagnostics().evaluations());
        var two = new BatchBackpropTrainer(options(2, .1)).fitFromParameters(X, Y, INITIAL);
        var second = new BatchBackpropTrainer(options(1, .1))
                .fitFromParameters(X, Y, new BatchBackpropTrainer(options(1, .1))
                        .fitFromParameters(X, Y, INITIAL).network().parameters());
        assertArrayEquals(second.network().parameters(), two.network().parameters());
        assertEquals(3, two.diagnostics().evaluations());
    }

    @Test void fit_advancing_stream_repeats_between_instances_but_does_not_reset_between_fits() {
        var a = new BatchBackpropTrainer(options(2, .01));
        var b = new BatchBackpropTrainer(options(2, .01));
        var first = a.fit(X, Y);
        var second = a.fit(X, Y);
        var resetFirst = b.fit(X, Y);
        var resetSecond = b.fit(X, Y);
        assertArrayEquals(first.initialParameters(), resetFirst.initialParameters());
        assertArrayEquals(second.initialParameters(), resetSecond.initialParameters());
        assertArrayEquals(second.network().parameters(), resetSecond.network().parameters());
        assertFalse(Arrays.equals(first.initialParameters(), second.initialParameters()));
        assertEquals(1, first.diagnostics().fitOrdinal());
        assertEquals(2, second.diagnostics().fitOrdinal());
        for (double value : first.initialParameters()) assertTrue(value >= -.1 && value < .1);
    }

    @Test void fitFromParameters_explicit_weights_leave_rng_untouched_and_all_snapshots_are_owned() {
        var trainer = new BatchBackpropTrainer(options(2, .01));
        double[][] x = {{1}, {2}}, y = {{.7, -.2}, {.5, .3}};
        double[] initial = INITIAL.clone();
        var model = trainer.fitFromParameters(x, y, initial);
        double[] before = model.predict(new double[]{1});
        assertArrayEquals(INITIAL, initial);
        assertArrayEquals(X[0], x[0]); assertArrayEquals(X[1], x[1]);
        assertArrayEquals(Y[0], y[0]); assertArrayEquals(Y[1], y[1]);
        x[0][0] = 999; y[0][0] = 999; initial[0] = 999;
        model.initialParameters()[0] = 999;
        model.network().parameters()[0] = 999;
        assertArrayEquals(before, model.predict(new double[]{1}));
        assertArrayEquals(new BatchBackpropTrainer(options(2, .01)).fit(X, Y).initialParameters(),
                trainer.fit(X, Y).initialParameters());
        assertSame(model, model.unwrap(BatchBackpropTrainer.FittedModel.class).orElseThrow());
        assertSame(model.network(), model.unwrap(FeedForwardNetwork.class).orElseThrow());
        assertSame(model.diagnostics(), model.unwrap(BatchBackpropTrainer.Diagnostics.class).orElseThrow());
    }

    @Test void fit_supplied_initializer_consumes_one_value_per_parameter_and_invalid_banks_consume_none() {
        var draws = new AtomicInteger();
        var trainer = new BatchBackpropTrainer(options(2, .01), () -> draws.incrementAndGet() * .001);
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(new double[0][], new double[0][]));
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(new double[][]{{Double.NaN}}, new double[][]{{0, 0}}));
        assertEquals(0, draws.get());
        var first = trainer.fit(X, Y);
        assertArrayEquals(new double[]{.001, .002, .003, .004, .005, .006}, first.initialParameters());
        assertEquals(6, draws.get());
        assertEquals(1, first.diagnostics().fitOrdinal());
        trainer.fitFromParameters(X, Y, INITIAL);
        assertEquals(6, draws.get());
        trainer.fit(X, Y);
        assertEquals(12, draws.get());
        var failure = new IllegalStateException("invented initializer failure");
        var attempted = new AtomicInteger();
        var failing = new BatchBackpropTrainer(options(2, .01), () -> {
            if (attempted.incrementAndGet() == 3) throw failure;
            return .01;
        });
        assertSame(failure, assertThrows(IllegalStateException.class, () -> failing.fit(X, Y)));
        assertEquals(3, attempted.get());
    }

    @Test void fit_rejects_invalid_options_dimensions_and_nonfinite_values() {
        for (double rate : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> options(1, rate));
        assertThrows(IllegalArgumentException.class, () -> options(0, .1));
        assertThrows(IllegalArgumentException.class, () -> new BatchBackpropTrainer.Options(
                0, NeuralActivation.LINEAR, NeuralActivation.LINEAR, 1, .1, 473, false, true));
        assertThrows(IllegalArgumentException.class, () -> new BatchBackpropTrainer(options(1, .1), null));
        var trainer = new BatchBackpropTrainer(options(1, .1));
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(new double[][]{{1}, {1, 2}}, Y));
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(X, new double[][]{{1, 2}}));
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(X, new double[][]{{1, 2}, {Double.POSITIVE_INFINITY, 0}}));
        assertThrows(IllegalArgumentException.class, () -> trainer.fitFromParameters(X, Y, new double[1]));
        var bad = INITIAL.clone(); bad[0] = Double.NaN;
        assertThrows(IllegalArgumentException.class, () -> trainer.fitFromParameters(X, Y, bad));
        assertThrows(ArithmeticException.class, () -> new BatchBackpropTrainer(options(1, Double.MAX_VALUE))
                .fitFromParameters(X, Y, INITIAL));
    }

    @Test void fitFromParameters_nonlinear_two_head_training_reduces_loss_without_changing_the_bank() {
        var options = new BatchBackpropTrainer.Options(1, NeuralActivation.FAST_SIGMOID,
                NeuralActivation.FAST_SIGMOID, 200, .05, 473, false, true);
        double[][] x = {{0}, {0}}, y = {{.2, -.3}, {.2, -.3}};
        var model = new BatchBackpropTrainer(options).fitFromParameters(x, y, new double[6]);
        assertEquals(.13, model.diagnostics().initialLoss(), 2e-15);
        assertTrue(model.diagnostics().finalLoss() < .001, "both constant heads should learn");
        assertEquals(201, model.diagnostics().evaluations());
        assertArrayEquals(new double[]{.2, -.3}, model.predict(new double[]{0}), .03);
        assertArrayEquals(new double[]{0}, x[0]);
        assertArrayEquals(new double[]{.2, -.3}, y[0]);
    }
}
