package one.chartsy.core.ml.online;

import one.chartsy.core.ml.SmilePredictors;
import one.chartsy.core.ml.ToDoublePredictorFunction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RollingModelTest {
    @Test
    void fit_keeps_exactly_the_latest_examples_in_chronological_order() {
        var learner = new RollingModel(3, (x, y) -> {
            assertArrayEquals(new double[]{2, 3, 4}, y);
            assertArrayEquals(new double[]{20, 21}, x[0]);
            assertArrayEquals(new double[]{40, 41}, x[2]);
            return constant(0);
        });
        for (int i = 1; i <= 4; i++) learner.observe(new double[]{i * 10, i * 10 + 1}, i);
        assertEquals(3, learner.size());
        learner.fit();
        assertEquals(1, learner.version());
    }

    @Test
    void observe_copies_input_and_fit_does_not_expose_stored_arrays() {
        List<Double> firstValues = new ArrayList<>();
        var learner = new RollingModel(1, (x, y) -> {
            firstValues.add(x[0][0]);
            x[0][0] = -123;
            return constant(0);
        });
        double[] input = {7};
        learner.observe(input, 1);
        input[0] = 999;
        learner.fit();
        learner.fit();
        assertEquals(List.of(7.0, 7.0), firstValues);
    }

    @Test
    void observe_rejects_invalid_examples_without_changing_window() {
        var learner = new RollingModel(2, (x, y) -> constant(0));
        assertThrows(IllegalArgumentException.class, () -> learner.observe(new double[0], 1));
        learner.observe(new double[]{1, 2}, 0);
        assertThrows(IllegalArgumentException.class, () -> learner.observe(new double[]{1}, 1));
        assertThrows(IllegalArgumentException.class, () -> learner.observe(new double[]{1, Double.NaN}, 1));
        assertThrows(IllegalArgumentException.class, () -> learner.observe(new double[]{1, 2}, Double.POSITIVE_INFINITY));
        assertEquals(1, learner.size());
        assertFalse(learner.isReady());
        assertThrows(IllegalStateException.class, learner::fit);
    }

    @Test
    void fit_propagates_failure_without_reusing_a_previous_model() {
        AtomicInteger fits = new AtomicInteger();
        var learner = new RollingModel(1, (x, y) -> {
            if (fits.incrementAndGet() > 1) throw new IllegalArgumentException("fit failed");
            return constant(0.25);
        });
        learner.observe(new double[]{1}, 0);
        assertEquals(0.25, learner.fit().predictAsDouble(new double[]{1}));
        assertThrows(IllegalArgumentException.class, learner::fit);
        assertEquals(1, learner.version());
    }

    @Test
    void fit_rejects_a_nonfinite_model() {
        var learner = new RollingModel(1, (x, y) -> constant(Double.NaN));
        learner.observe(new double[]{1}, 0);
        assertThrows(IllegalStateException.class, learner::fit);
        assertEquals(0, learner.version());
    }

    @Test
    void linearRegression_handles_constant_labels_and_rank_deficient_windows() {
        for (double label : new double[]{0, 1}) {
            var learner = new RollingModel(200, SmilePredictors::linearRegression);
            for (int i = 0; i < 200; i++) learner.observe(new double[]{1, 1}, label);
            assertEquals(label, learner.fit().predictAsDouble(new double[]{1, 1}), 1e-10);
        }
        var learner = new RollingModel(200, SmilePredictors::linearRegression);
        for (int i = 0; i < 200; i++) learner.observe(new double[]{i % 2, i % 2}, i % 2);
        var predictor = learner.fit();
        assertEquals(0, predictor.predictAsDouble(new double[]{0, 0}), 1e-10);
        assertEquals(1, predictor.predictAsDouble(new double[]{1, 1}), 1e-10);
    }

    private static ToDoublePredictorFunction<double[]> constant(double value) {
        return new ToDoublePredictorFunction<>() {
            public double predictAsDouble(double[] input) { return value; }
            public <T> Optional<T> unwrap(Class<T> type) { return Optional.empty(); }
        };
    }
}
