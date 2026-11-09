package one.chartsy.core.ml.online;

import one.chartsy.core.ml.PredictorFunction;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RollingVectorModelTest {
    @Test void chronological_window_owns_both_coordinate_arrays_across_observers_and_mutating_trainers() {
        var snapshots = new ArrayList<double[][]>();
        var model = new RollingVectorModel(2, (x, y) -> {
            snapshots.add(new double[][]{x[0].clone(), x[1].clone(), y[0].clone(), y[1].clone()});
            x[0][0] = -99; y[0][0] = -99;
            return constant(2, 3);
        });
        double[] input = {1}, target = {2, 3};
        model.observe(input, target); input[0] = 99; target[0] = 99;
        model.observe(new double[]{4}, new double[]{5, 6});
        model.fit(); model.fit();
        assertArrayEquals(new double[]{1}, snapshots.getFirst()[0]);
        assertArrayEquals(new double[]{2, 3}, snapshots.getFirst()[2]);
        assertArrayEquals(snapshots.getFirst()[0], snapshots.getLast()[0]);
        assertArrayEquals(snapshots.getFirst()[2], snapshots.getLast()[2]);
        model.observe(new double[]{7}, new double[]{8, 9}); model.fit();
        assertArrayEquals(new double[]{4}, snapshots.getLast()[0]);
        assertArrayEquals(new double[]{7}, snapshots.getLast()[1]);
        assertEquals(3, model.version());
    }

    @Test void malformed_or_nonfinite_observations_leave_completed_history_unchanged() {
        var retained = new ArrayList<double[][]>();
        var model = new RollingVectorModel(1, (x, y) -> { retained.add(y); return constant(1, 2); });
        model.observe(new double[]{1}, new double[]{2, 3});
        assertThrows(IllegalArgumentException.class, () -> model.observe(new double[]{1, 2}, new double[]{3, 4}));
        assertThrows(IllegalArgumentException.class, () -> model.observe(new double[]{1}, new double[]{3}));
        assertThrows(IllegalArgumentException.class, () -> model.observe(new double[]{Double.NaN}, new double[]{3, 4}));
        assertThrows(IllegalArgumentException.class, () -> model.observe(new double[]{1}, new double[]{3, Double.POSITIVE_INFINITY}));
        model.fit(); assertArrayEquals(new double[]{2, 3}, retained.getFirst()[0]);
        assertEquals(1, model.size()); assertEquals(2, model.outputDimension());
    }

    @Test void failed_fit_or_wrong_output_shape_does_not_advance_version_or_reuse_old_model() {
        for (double[] prediction : List.of(new double[]{1}, new double[]{1, Double.NaN})) {
            var model = new RollingVectorModel(1, (x, y) -> constant(prediction));
            model.observe(new double[]{1}, new double[]{2, 3});
            assertThrows(IllegalArgumentException.class, model::fit); assertEquals(0, model.version());
        }
        var model = new RollingVectorModel(2, (x, y) -> { throw new IllegalStateException("fit failed"); });
        assertThrows(IllegalStateException.class, model::fit);
        model.observe(new double[]{1}, new double[]{2, 3});
        assertFalse(model.isReady()); assertThrows(IllegalStateException.class, model::fit);
        model.observe(new double[]{4}, new double[]{5, 6});
        assertThrows(IllegalStateException.class, model::fit); assertEquals(0, model.version());
    }

    private static PredictorFunction<double[], double[]> constant(double... output) {
        return new PredictorFunction<>() {
            @Override public double[] predict(double[] input) { return output.clone(); }
            @Override public <T> Optional<T> unwrap(Class<T> type) { return Optional.empty(); }
        };
    }
}
