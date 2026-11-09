/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.core.ml.online.forests;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static one.chartsy.core.ml.online.forests.RandomForestTrainer.SplitCriterion.*;
import static org.junit.jupiter.api.Assertions.*;

class RandomForestTrainerTest {
    private static final double[][] SIX_INPUTS = {{0}, {1}, {2}, {3}, {4}, {5}};

    private static RandomForestTrainer oneTree(RandomForestTrainer.SplitCriterion criterion) {
        return new RandomForestTrainer(new RandomForestTrainer.Options(1, 1, 5, 1, 42, criterion));
    }

    @Test
    void historical_child_weighting_and_standard_sse_choose_different_hand_calculated_splits() {
        // Exact objective tables are in forests/independent/BACKEND-AUDIT.md.
        double[][] targets = {{0}, {0}, {1}, {0}, {1}, {2}};
        var historical = oneTree(HISTORICAL_SHARK_WEIGHTED_SSE).fit(SIX_INPUTS, targets);
        var standard = oneTree(TOTAL_SSE).fit(SIX_INPUTS, targets);
        assertArrayEquals(new double[]{0.25}, historical.predict(new double[]{3}), 1E-14);
        assertArrayEquals(new double[]{1.5}, historical.predict(new double[]{4}), 1E-14);
        assertArrayEquals(new double[]{0.4}, standard.predict(new double[]{4}), 1E-14);
        assertArrayEquals(new double[]{2}, standard.predict(new double[]{5}), 1E-14);
    }

    @Test
    void prediction_threshold_is_the_observed_left_value_not_a_midpoint() {
        var model = oneTree(HISTORICAL_SHARK_WEIGHTED_SSE)
                .fit(SIX_INPUTS, new double[][]{{0}, {0}, {1}, {0}, {1}, {2}});
        assertArrayEquals(new double[]{0.25}, model.predict(new double[]{3}), 1E-14);
        assertArrayEquals(new double[]{1.5}, model.predict(new double[]{Math.nextUp(3.0)}), 1E-14);
        assertArrayEquals(new double[]{1.5}, model.predict(new double[]{3.25}), 1E-14);
    }

    @Test
    void direct_error_ties_preserve_the_feature_that_routes_unseen_queries() {
        // Different features induce the same training partition. Welford accumulation
        // breaks this exact direct-residual tie and sends this unseen query elsewhere.
        double[][] x = {{5, 1, 0}, {4, 4, 4}, {0, 0, 1}, {1, 2, 5}, {2, 3, 2}, {3, 5, 3}};
        double[][] y = {{.7, -.3}, {.5, 0}, {.8, -.9}, {-.8, 1}, {0, .8}, {.1, -.6}};
        var trainer = new RandomForestTrainer(new RandomForestTrainer.Options(
                1, 3, 5, 1, 77, HISTORICAL_SHARK_WEIGHTED_SSE));
        assertArrayEquals(new double[]{.75, -.6}, trainer.fit(x, y).predict(new double[]{3.5, 1.5, .5}), 1E-14);
    }

    @Test
    void vector_targets_share_a_partition_instead_of_fitting_separate_scalar_trees() {
        double[][] joint = {{0, 2}, {0, 2}, {0, 0}, {0, 0}, {0, 0}, {2, 0}};
        for (var criterion : RandomForestTrainer.SplitCriterion.values()) {
            var model = oneTree(criterion).fit(SIX_INPUTS, joint);
            var firstAlone = oneTree(criterion).fit(SIX_INPUTS,
                    Arrays.stream(joint).map(row -> new double[]{row[0]}).toArray(double[][]::new));
            var secondAlone = oneTree(criterion).fit(SIX_INPUTS,
                    Arrays.stream(joint).map(row -> new double[]{row[1]}).toArray(double[][]::new));
            assertArrayEquals(new double[]{0, 2}, model.predict(new double[]{1}), 1E-14);
            assertArrayEquals(new double[]{0.5, 0}, model.predict(new double[]{3}), 1E-14);
            assertArrayEquals(new double[]{0}, firstAlone.predict(new double[]{3}), 1E-14);
            assertArrayEquals(new double[]{0}, secondAlone.predict(new double[]{3}), 1E-14);
        }
    }

    @Test
    void node_size_stops_parents_but_allows_singletons_and_zero_gain_splits() {
        var trainer = oneTree(HISTORICAL_SHARK_WEIGHTED_SSE);
        var five = trainer.fit(Arrays.copyOf(SIX_INPUTS, 5), new double[][]{{0}, {0}, {0}, {0}, {0}});
        var six = trainer.fit(SIX_INPUTS, new double[][]{{0}, {0}, {0}, {0}, {0}, {0}});
        assertEquals(1, five.diagnostics().leafCount());
        assertEquals(5, five.diagnostics().minimumLeafSize());
        assertEquals(2, six.diagnostics().leafCount());
        assertEquals(1, six.diagnostics().minimumLeafSize());
        assertEquals(5, six.diagnostics().maximumLeafSize());
    }

    @Test
    void equal_feature_values_stay_together_and_constant_features_return_the_whole_mean() {
        var trainer = oneTree(HISTORICAL_SHARK_WEIGHTED_SSE);
        double[][] labels = {{0}, {0}, {0}, {10}, {10}, {10}};
        var repeated = trainer.fit(new double[][]{{0}, {0}, {0}, {2}, {2}, {2}}, labels);
        assertArrayEquals(new double[]{0}, repeated.predict(new double[]{0}), 1E-14);
        assertArrayEquals(new double[]{10}, repeated.predict(new double[]{0.1}), 1E-14);
        assertEquals(3, repeated.diagnostics().minimumLeafSize());
        var constant = trainer.fit(new double[][]{{0}, {0}, {0}, {0}, {0}, {0}}, labels);
        assertEquals(1, constant.diagnostics().leafCount());
        assertArrayEquals(new double[]{5}, constant.predict(new double[]{100}), 1E-14);
    }

    @Test
    void historical_defaults_use_ceil_candidates_and_truncate_sample_count() {
        double[][] inputs = new double[9][4], targets = new double[9][2];
        for (int i = 0; i < inputs.length; i++) { inputs[i][0] = i; targets[i][0] = i; }
        var model = new RandomForestTrainer(RandomForestTrainer.Options.historicalShark(41)).fit(inputs, targets);
        assertEquals(100, model.diagnostics().treeCount());
        assertEquals(2, model.diagnostics().candidateFeatures());
        assertEquals(5, model.diagnostics().sampleSize()); // floor(9 * .66), not round=6
        assertEquals(100, model.diagnostics().leafCount());
    }

    @Test
    void models_own_inputs_and_targets_and_return_new_prediction_arrays() {
        double[][] x = Arrays.stream(SIX_INPUTS).map(double[]::clone).toArray(double[][]::new);
        double[][] y = {{0}, {0}, {1}, {0}, {1}, {2}};
        var model = oneTree(HISTORICAL_SHARK_WEIGHTED_SSE).fit(x, y);
        double[] query = {4};
        double[] result = model.predict(query);
        x[0][0] = 999; y[4][0] = 999; x[1] = null; y[1] = null;
        result[0] = -999;
        assertArrayEquals(new double[]{1.5}, model.predict(query), 1E-14);
        assertArrayEquals(new double[]{4}, query);
        assertSame(model, model.unwrap(RandomForestTrainer.FittedModel.class).orElseThrow());
        assertTrue(model.unwrap(double[][].class).isEmpty());
    }

    @Test
    void repeated_and_concurrent_fits_use_the_same_local_random_stream() throws Exception {
        double[][] x = new double[70][4], y = new double[70][2];
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < 4; j++) x[i][j] = Math.sin((i + 1) * (j + 1));
            y[i][0] = Math.cos(i); y[i][1] = Math.sin(i * 0.7);
        }
        var trainer = new RandomForestTrainer(RandomForestTrainer.Options.historicalShark(7183));
        double[] expected = trainer.fit(x, y).predict(x[10]);
        var pool = Executors.newFixedThreadPool(3);
        try {
            var futures = new ArrayList<Future<double[]>>();
            for (int i = 0; i < 6; i++) futures.add(pool.submit(() -> trainer.fit(x, y).predict(x[10])));
            for (var future : futures) assertArrayEquals(expected, future.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void declared_seed_controls_sampling() {
        double[][] x = new double[20][1], y = new double[20][1];
        for (int i = 0; i < y.length; i++) y[i][0] = i * i;
        var first = new RandomForestTrainer(new RandomForestTrainer.Options(1, 1, 5, .66, 3, TOTAL_SSE));
        var second = new RandomForestTrainer(new RandomForestTrainer.Options(1, 1, 5, .66, 4, TOTAL_SSE));
        assertNotEquals(first.fit(x, y).predict(new double[]{0})[0], second.fit(x, y).predict(new double[]{0})[0]);
        assertArrayEquals(first.fit(x, y).predict(new double[]{0}), first.fit(x, y).predict(new double[]{0}));
    }

    @Test
    void invalid_training_query_and_options_fail_explicitly() {
        var trainer = oneTree(TOTAL_SSE);
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(new double[0][], new double[0][]));
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(new double[][]{{1}, {2}}, new double[][]{{1}}));
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(new double[][]{{1}, {2, 3}}, new double[][]{{1}, {2}}));
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(new double[][]{{1}, {2}}, new double[][]{{1}, {2, 3}}));
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(new double[][]{{1}}, new double[][]{{Double.NaN}}));
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(new double[][]{{Double.POSITIVE_INFINITY}}, new double[][]{{1}}));
        assertThrows(IllegalArgumentException.class, () -> new RandomForestTrainer.Options(0, 1, 5, 1, 0, TOTAL_SSE));
        assertThrows(IllegalArgumentException.class, () -> new RandomForestTrainer.Options(1, -1, 5, 1, 0, TOTAL_SSE));
        assertThrows(IllegalArgumentException.class, () -> new RandomForestTrainer.Options(1, 1, 0, 1, 0, TOTAL_SSE));
        assertThrows(IllegalArgumentException.class, () -> new RandomForestTrainer.Options(1, 1, 5, Double.NaN, 0, TOTAL_SSE));
        assertThrows(IllegalArgumentException.class, () -> new RandomForestTrainer.Options(1, 1, 5, 1, 0, null));
        var tooManyFeatures = new RandomForestTrainer(new RandomForestTrainer.Options(1, 2, 5, 1, 0, TOTAL_SSE));
        assertThrows(IllegalArgumentException.class, () -> tooManyFeatures.fit(new double[][]{{1}}, new double[][]{{1}}));
        assertThrows(IllegalArgumentException.class, () -> new RandomForestTrainer(RandomForestTrainer.Options.historicalShark(42))
                .fit(new double[][]{{1}}, new double[][]{{1}}));
        var model = trainer.fit(new double[][]{{1}}, new double[][]{{1}});
        assertThrows(IllegalArgumentException.class, () -> model.predict(new double[0]));
        assertThrows(IllegalArgumentException.class, () -> model.predict(new double[]{Double.NaN}));
    }

    @Test
    void squared_error_arithmetic_overflow_does_not_return_a_fitted_model() {
        var trainer = oneTree(TOTAL_SSE);
        assertThrows(ArithmeticException.class, () -> trainer.fit(SIX_INPUTS,
                new double[][]{{Double.MAX_VALUE}, {-Double.MAX_VALUE}, {0}, {0}, {0}, {0}}));
    }
}
