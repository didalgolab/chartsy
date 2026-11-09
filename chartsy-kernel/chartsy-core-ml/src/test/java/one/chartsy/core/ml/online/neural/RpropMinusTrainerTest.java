/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.ml.online.neural;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RpropMinusTrainerTest {
    private static final double[][] X = {{.3, -.8}, {-.6, .4}, {.5, .9}};
    private static final double[][] Y = {{.2, -.1}, {-.1, .3}, {.5, -.4}};

    private static RpropMinusTrainer.Options options(int hidden, int cycles, long seed, double radius) {
        return new RpropMinusTrainer.Options(hidden, NeuralActivation.FAST_SIGMOID,
                NeuralActivation.FAST_SIGMOID, cycles, seed, false, true, radius);
    }

    @TestFactory List<DynamicTest> fitFromParameters_matches_independent_two_head_oracle_at_every_checkpoint() throws IOException {
        Map<Integer, Fixture> fixtures = readFixtures();
        List<Expected> expectations = readExpected();
        assertEquals(2, fixtures.size());
        assertEquals(16, expectations.size());
        var tests = new ArrayList<DynamicTest>();
        for (int width : new int[]{2, 5}) {
            assertArrayEquals(new int[]{0, 1, 2, 10, 20, 50, 75, 100}, expectations.stream()
                    .filter(value -> value.width() == width).mapToInt(Expected::cycles).toArray());
        }
        for (var expected : expectations) {
            tests.add(DynamicTest.dynamicTest("C" + expected.width() + " cycles=" + expected.cycles(), () -> {
                var fixture = fixtures.get(expected.width());
                var shape = new FeedForwardNetwork.Shape(fixture.width(), fixture.hidden(), 2, false, true);
                assertEquals(fixture.width() == 2 ? 12 : 26, shape.parameterCount());
                FeedForwardNetwork network;
                if (expected.cycles() == 0) {
                    network = new FeedForwardNetwork(shape, NeuralActivation.FAST_SIGMOID,
                            NeuralActivation.FAST_SIGMOID, fixture.initial());
                } else {
                    var trainer = new RpropMinusTrainer(options(fixture.hidden(), expected.cycles(), 771, .5));
                    var model = trainer.fitFromParameters(fixture.x(), fixture.y(), fixture.initial());
                    network = model.network();
                    assertEquals(expected.cycles() + 1, model.diagnostics().evaluations());
                    assertEquals(expected.cycles(), model.diagnostics().cycles());
                    assertEquals(fixture.x().length, model.diagnostics().rows());
                    assertEquals(0, model.diagnostics().fitOrdinal());
                    assertEquals("EXPLICIT_PARAMETERS", model.diagnostics().initialization());
                    assertClose(expected.loss(), model.diagnostics().finalLoss(), "diagnostic final loss");
                    var initial = expectations.stream()
                            .filter(value -> value.width() == expected.width() && value.cycles() == 0)
                            .findFirst().orElseThrow();
                    assertClose(initial.loss(), model.diagnostics().initialLoss(), "diagnostic initial loss");
                }
                assertVector(expected.parameters(), network.parameters(), "parameters", 2e-12, 0);
                var evaluated = network.lossGradient(fixture.x(), fixture.y());
                assertClose(expected.loss(), evaluated.loss(), "loss");
                assertVector(expected.gradient(), evaluated.gradient(), "gradient", 2e-12, 2e-11);
                double[] prediction = network.predict(fixture.query());
                assertVector(expected.prediction(), prediction, "both outputs", 2e-12, 2e-11);
                for (int head = 0; head < 2; head++)
                    assertEquals(Math.signum(expected.prediction()[head]), Math.signum(prediction[head]), "head sign");
            }));
        }
        return tests;
    }

    @Test void step_reversal_moves_with_halved_step_and_remembers_current_gradient() {
        var state = new RpropMinusTrainer.UpdateState(new double[]{0});
        state.step(new double[]{2});
        assertEquals(-.01, state.point[0]);
        state.step(new double[]{3});
        assertEquals(-.022, state.point[0], 1e-16);
        assertEquals(.012, state.delta[0]);
        state.step(new double[]{-1});
        assertEquals(-.016, state.point[0], 1e-16);
        assertEquals(.006, state.delta[0]);
        assertEquals(-1, state.previousGradient[0]);
        state.step(new double[]{-2});
        assertEquals(-.0088, state.point[0], 1e-16);
        assertEquals(.0072, state.delta[0], 1e-16);
        state.step(new double[]{1});
        assertEquals(-.0124, state.point[0], 1e-16);
        assertEquals(.0036, state.delta[0], 1e-16);
        assertEquals(1, state.previousGradient[0]);
        assertEquals(2, state.increases);
        assertEquals(2, state.signReversals);
    }

    @Test void step_zero_gradient_keeps_the_point_and_only_zero_itself_resets_gradient_memory() {
        var state = new RpropMinusTrainer.UpdateState(new double[]{0});
        state.step(new double[]{1});
        state.step(new double[]{-0.0});
        assertEquals(-.01, state.point[0]);
        assertEquals(.01, state.delta[0]);
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(state.previousGradient[0]));
        state.step(new double[]{-1});
        assertEquals(0, state.point[0]);
        assertEquals(.01, state.delta[0]);
        assertEquals(0, state.signReversals);
    }

    @Test void step_bounds_and_literal_binary64_gradient_products_are_preserved() {
        var state = new RpropMinusTrainer.UpdateState(new double[]{0, 0, 0, 0});
        state.delta[0] = 1e100;
        state.previousGradient[0] = Double.MAX_VALUE;
        state.delta[1] = Double.MIN_VALUE;
        state.previousGradient[1] = 1;
        state.previousGradient[2] = 1e-300;
        state.previousGradient[3] = 1e-300;
        state.step(new double[]{Double.MAX_VALUE, -1, 1e-300, -1e-300});
        assertEquals(1e100, state.delta[0]);
        assertEquals(-1e100, state.point[0]);
        assertEquals(0, state.delta[1]);
        assertEquals(0, state.point[1]);
        assertEquals(.01, state.delta[2]);
        assertEquals(.01, state.delta[3]);
        assertEquals(-.01, state.point[2]);
        assertEquals(.01, state.point[3]);
        assertEquals(1, state.increases);
        assertEquals(1, state.signReversals);
        state.step(new double[]{0, -2, 1e-300, -1e-300});
        assertEquals(0, state.delta[1]);
        assertEquals(0, state.point[1]);
        assertEquals(-.02, state.point[2]);
        assertEquals(.02, state.point[3]);
    }

    @Test void fitFromParameters_returns_final_overshoot_instead_of_best_or_rolled_back_point() {
        // Only the final output bias has a nonzero derivative in this linear network.
        double[] initial = {0, 0, 0, .001};
        double[][] zero = {{0}};
        var oneOptions = new RpropMinusTrainer.Options(1, NeuralActivation.LINEAR,
                NeuralActivation.LINEAR, 1, 1, false, true, .5);
        var one = new RpropMinusTrainer(oneOptions).fitFromParameters(zero, zero, initial);
        assertEquals(1e-6, one.diagnostics().initialLoss(), 1e-18);
        assertEquals(81e-6, one.diagnostics().finalLoss(), 1e-18);
        assertEquals(-.009, one.network().parameters()[3], 1e-16);
        assertEquals(1, one.diagnostics().lossIncreases());
        assertEquals(2, one.diagnostics().evaluations());
        var twoOptions = new RpropMinusTrainer.Options(1, NeuralActivation.LINEAR,
                NeuralActivation.LINEAR, 2, 1, false, true, .5);
        var two = new RpropMinusTrainer(twoOptions).fitFromParameters(zero, zero, initial);
        assertEquals(-.004, two.network().parameters()[3], 1e-16);
        assertEquals(16e-6, two.diagnostics().finalLoss(), 1e-18);
        assertEquals(1, two.diagnostics().signReversals());
        assertEquals(3, two.diagnostics().evaluations());
    }

    @Test void fit_seeded_stream_advances_and_radius_scales_the_same_initial_draws() {
        var configured = options(2, 2, 783, .5);
        var first = new RpropMinusTrainer(configured);
        var repeat = new RpropMinusTrainer(configured);
        var a = first.fit(X, Y);
        var b = first.fit(X, Y);
        assertArrayEquals(a.initialParameters(), repeat.fit(X, Y).initialParameters());
        assertArrayEquals(b.initialParameters(), repeat.fit(X, Y).initialParameters());
        assertFalse(Arrays.equals(a.initialParameters(), b.initialParameters()));
        assertEquals(1, a.diagnostics().fitOrdinal());
        assertEquals(2, b.diagnostics().fitOrdinal());
        assertEquals("ADVANCING_SPLITTABLE_RANDOM", b.diagnostics().initialization());
        var random = new SplittableRandom(783);
        for (double actual : a.initialParameters())
            assertEquals((2 * random.nextDouble() - 1) * .5, actual);
        for (double actual : b.initialParameters())
            assertEquals((2 * random.nextDouble() - 1) * .5, actual);
        for (double radius : new double[]{.001, .1, 1, 10}) {
            double[] initial = new RpropMinusTrainer(options(2, 2, 783, radius)).fit(X, Y).initialParameters();
            var expectedStream = new SplittableRandom(783);
            for (double value : initial) {
                assertEquals((2 * expectedStream.nextDouble() - 1) * radius, value);
                assertTrue(value >= -radius && value < radius);
            }
        }
    }

    @Test void fit_supplier_is_owned_unclipped_and_explicit_path_consumes_no_draw_or_ordinal() {
        var draws = new AtomicInteger();
        var trainer = new RpropMinusTrainer(options(2, 1, 42, .01), () -> .5 + draws.getAndIncrement() * .01);
        var explicit = trainer.fitFromParameters(X, Y, new double[12]);
        assertEquals(0, draws.get());
        assertEquals(0, explicit.diagnostics().fitOrdinal());
        var first = trainer.fit(X, Y);
        var second = trainer.fit(X, Y);
        assertEquals(24, draws.get());
        assertEquals(1, first.diagnostics().fitOrdinal());
        assertEquals(2, second.diagnostics().fitOrdinal());
        assertEquals("ADVANCING_SUPPLIED_INITIALIZER", first.diagnostics().initialization());
        for (int i = 0; i < 12; i++) {
            assertEquals(.5 + i * .01, first.initialParameters()[i]);
            assertEquals(.5 + (i + 12) * .01, second.initialParameters()[i]);
        }
    }

    @Test void fit_invalid_input_precedes_initialization_and_failed_draw_is_not_rewound() {
        var draws = new AtomicInteger();
        var trainer = new RpropMinusTrainer(options(2, 1, 1, .5), () -> {
            int index = draws.getAndIncrement();
            return index == 2 ? Double.NaN : index * .01;
        });
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(new double[][]{{1, Double.NaN}}, new double[][]{{0, 0}}));
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(X, new double[][]{{1, 2}}));
        assertEquals(0, draws.get());
        assertThrows(ArithmeticException.class, () -> trainer.fit(X, Y));
        assertEquals(3, draws.get());
        var next = trainer.fit(X, Y);
        assertEquals(15, draws.get());
        assertEquals(.03, next.initialParameters()[0]);
        assertEquals(1, next.diagnostics().fitOrdinal());
    }

    @Test void fitFromParameters_owns_all_arrays_and_exposes_immutable_network_diagnostics() {
        var trainer = new RpropMinusTrainer(options(2, 2, 775, .5));
        double[][] x = Arrays.stream(X).map(double[]::clone).toArray(double[][]::new);
        double[][] y = Arrays.stream(Y).map(double[]::clone).toArray(double[][]::new);
        double[] initial = new double[12];
        initial[0] = .2;
        var model = trainer.fitFromParameters(x, y, initial);
        double[] expected = model.predict(X[0]);
        double[] fitted = model.network().parameters();
        initial[0] = 99;
        x[0][0] = 99;
        y[0][0] = 99;
        model.initialParameters()[0] = 99;
        model.network().parameters()[0] = 99;
        model.predict(X[0])[0] = 99;
        assertArrayEquals(expected, model.predict(X[0]));
        assertArrayEquals(fitted, model.network().parameters());
        var evaluation = model.network().lossGradient(X, Y);
        double[] gradient = evaluation.gradient();
        evaluation.gradient()[0] = 99;
        assertArrayEquals(gradient, evaluation.gradient());
        assertSame(model, model.unwrap(RpropMinusTrainer.FittedModel.class).orElseThrow());
        assertSame(model.network(), model.unwrap(FeedForwardNetwork.class).orElseThrow());
        assertSame(model.diagnostics(), model.unwrap(RpropMinusTrainer.Diagnostics.class).orElseThrow());
        assertTrue(model.unwrap(String.class).isEmpty());
        assertArrayEquals(new RpropMinusTrainer(options(2, 2, 775, .5)).fit(X, Y).initialParameters(),
                trainer.fit(X, Y).initialParameters());
    }

    @Test void fitFromParameters_zero_gradient_still_executes_all_requested_cycles() {
        var trainer = new RpropMinusTrainer(options(2, 75, 1, .5));
        var model = trainer.fitFromParameters(new double[][]{{0, 0}}, new double[][]{{0, 0}}, new double[12]);
        assertArrayEquals(new double[12], model.network().parameters());
        assertEquals(0, model.diagnostics().initialLoss());
        assertEquals(0, model.diagnostics().finalLoss());
        assertEquals(0, model.diagnostics().maxAbsGradient());
        assertEquals(75, model.diagnostics().cycles());
        assertEquals(76, model.diagnostics().evaluations());
    }

    @Test void options_and_fit_refuse_invalid_dimensions_parameters_and_nonfinite_arithmetic() {
        assertThrows(IllegalArgumentException.class, () -> options(0, 1, 1, .5));
        assertThrows(IllegalArgumentException.class, () -> options(1, 0, 1, .5));
        assertThrows(IllegalArgumentException.class, () -> options(1, Integer.MAX_VALUE, 1, .5));
        for (double radius : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> options(1, 1, 1, radius));
        assertEquals(Double.MAX_VALUE, options(1, 1, 1, Double.MAX_VALUE).initializationRadius());
        assertThrows(IllegalArgumentException.class, () -> new RpropMinusTrainer.Options(1, null,
                NeuralActivation.LINEAR, 1, 1, false, true, .5));
        assertThrows(IllegalArgumentException.class, () -> new RpropMinusTrainer(options(1, 1, 1, .5), null));
        var trainer = new RpropMinusTrainer(options(2, 1, 1, .5));
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(new double[0][], Y));
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(new double[][]{{}}, new double[][]{{0}}));
        assertThrows(IllegalArgumentException.class, () -> trainer.fit(new double[][]{{1, 2}, {3}}, new double[][]{{1}, {2}}));
        assertThrows(IllegalArgumentException.class, () -> trainer.fitFromParameters(X, Y, new double[11]));
        double[] invalid = new double[12];
        invalid[3] = Double.POSITIVE_INFINITY;
        assertThrows(IllegalArgumentException.class, () -> trainer.fitFromParameters(X, Y, invalid));
        double[] overflowing = new double[12];
        overflowing[0] = Double.MAX_VALUE;
        assertThrows(ArithmeticException.class, () -> trainer.fitFromParameters(new double[][]{{Double.MAX_VALUE, 0}},
                new double[][]{{0, 0}}, overflowing));
        var state = new RpropMinusTrainer.UpdateState(new double[]{0});
        assertThrows(IllegalArgumentException.class, () -> state.step(new double[]{1, 2}));
        assertThrows(ArithmeticException.class, () -> state.step(new double[]{Double.POSITIVE_INFINITY}));
    }

    @Test void fit_optional_shortcuts_keep_the_existing_local_network_layout_only() {
        var configured = new RpropMinusTrainer.Options(2, NeuralActivation.LINEAR,
                NeuralActivation.LINEAR, 1, 1, true, true, .5);
        // The shared Java order is input-hidden, each output's shortcuts/hidden weights, biases.
        double[] initial = new double[16];
        initial[4] = .1;
        initial[9] = -.2;
        var model = new RpropMinusTrainer(configured).fitFromParameters(X, Y, initial);
        assertEquals(new FeedForwardNetwork.Shape(2, 2, 2, true, true), model.network().shape());
        assertEquals(16, model.network().parameters().length);
        assertEquals(2, model.predict(X[0]).length);
        assertTrue(Double.isFinite(model.diagnostics().finalLoss()));
    }

    private record Fixture(int width, int hidden, double[] initial, double[][] x, double[][] y, double[] query) { }
    private record Expected(int width, int cycles, double[] parameters, double loss, double[] gradient, double[] prediction) { }

    private static Map<Integer, Fixture> readFixtures() throws IOException {
        var result = new HashMap<Integer, Fixture>();
        for (String[] row : rows("inputs.csv", "width,hidden,initial,features,targets,query")) {
            int width = Integer.parseInt(row[0]);
            var fixture = new Fixture(width, Integer.parseInt(row[1]), vector(row[2]), matrix(row[3]), matrix(row[4]), vector(row[5]));
            assertNull(result.put(width, fixture), "duplicate fixture width");
        }
        return result;
    }

    private static List<Expected> readExpected() throws IOException {
        var result = new ArrayList<Expected>();
        for (String[] row : rows("expected.csv", "width,cycles,parameters,loss,gradient,prediction"))
            result.add(new Expected(Integer.parseInt(row[0]), Integer.parseInt(row[1]), vector(row[2]),
                    Double.parseDouble(row[3]), vector(row[4]), vector(row[5])));
        return result;
    }

    private static List<String[]> rows(String name, String header) throws IOException {
        String resource = "/one/chartsy/core/ml/online/neural/rpropminus/" + name;
        try (var stream = RpropMinusTrainerTest.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing independent oracle resource: " + resource);
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                assertEquals(header, reader.readLine());
                var result = new ArrayList<String[]>();
                for (String line : reader.lines().toList()) {
                    String[] fields = line.split(",", -1);
                    assertEquals(6, fields.length);
                    result.add(fields);
                }
                return result;
            }
        }
    }

    private static double[] vector(String text) {
        return Arrays.stream(text.split(";", -1)).mapToDouble(Double::parseDouble).toArray();
    }

    private static double[][] matrix(String text) {
        return Arrays.stream(text.split("\\|", -1)).map(RpropMinusTrainerTest::vector).toArray(double[][]::new);
    }

    private static void assertClose(double expected, double actual, String label) {
        assertTrue(Double.isFinite(actual), label);
        assertEquals(expected, actual, 2e-12 + 2e-11 * Math.abs(expected), label);
    }

    private static void assertVector(double[] expected, double[] actual, String label, double absolute, double relative) {
        assertEquals(expected.length, actual.length, label);
        for (int i = 0; i < expected.length; i++) {
            assertTrue(Double.isFinite(actual[i]), label + "[" + i + "]");
            assertEquals(expected[i], actual[i], absolute + relative * Math.abs(expected[i]), label + "[" + i + "]");
        }
    }
}
