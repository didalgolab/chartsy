/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.ml.online.neural;

import one.chartsy.core.ml.PredictorFunction;
import one.chartsy.core.ml.online.VectorModelTrainer;

import java.util.Arrays;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.DoubleSupplier;

/// Fresh fixed-cycle network fits using unconstrained Rprop-minus.
///
/// Every coordinate starts with step .01. Consecutive gradient products increase
/// the step by 1.2 or decrease it by .5, within 0..1e100. Each update moves against
/// the current gradient, including on reversal, and remembers that gradient.
/// There is no rollback, reversal clearing, best-iterate selection or early stop.
/// The initial evaluation plus each update gives `cycles + 1` evaluations.
///
/// The update follows the public November 2015 Shark RpropMinus reference; this is
/// an independent Java implementation, not an identified private author build.
/// Network arithmetic and parameter order are those of {@link FeedForwardNetwork}.
/// Its adjacent-layer order agrees with that reference; shortcut ordering does not
/// carry the same compatibility claim.
///
/// One trainer owns one advancing initialization stream and is not thread-safe.
/// Java SplittableRandom is not claimed to reproduce Shark's random stream.
public final class RpropMinusTrainer implements VectorModelTrainer {
    public record Options(int hidden, NeuralActivation hiddenActivation, NeuralActivation outputActivation,
                          int cycles, long seed, boolean shortcuts, boolean biases,
                          double initializationRadius) {
        public Options {
            if (hidden < 1 || cycles < 1 || cycles == Integer.MAX_VALUE)
                throw new IllegalArgumentException("Positive hidden count and cycles with representable cycles + 1 required");
            if (hiddenActivation == null || outputActivation == null)
                throw new IllegalArgumentException("Both activation functions required");
            if (!Double.isFinite(initializationRadius) || initializationRadius <= 0)
                throw new IllegalArgumentException("Positive finite initialization radius required");
        }
    }

    /// Maximum absolute gradient includes the initial and every final evaluation.
    public record Diagnostics(long fitOrdinal, String initialization, int rows, int cycles, int evaluations,
                              double initialLoss, double finalLoss, double maxAbsGradient, int lossIncreases,
                              long stepIncreases, long signReversals) { }

    private final Options options;
    private final DoubleSupplier initializer;
    private final String initialization;
    private long fitOrdinal;

    public RpropMinusTrainer(Options options) {
        this(options, uniformValues(options.seed(), options.initializationRadius()), "ADVANCING_SPLITTABLE_RANDOM");
    }

    /// Retains the caller's exclusively owned stream; the options seed and radius
    /// do not constrain supplied values. Each valid fit consumes one finite value
    /// per parameter. A failed draw or fit never rewinds or retries that stream.
    public RpropMinusTrainer(Options options, DoubleSupplier initializer) {
        this(options, initializer, "ADVANCING_SUPPLIED_INITIALIZER");
    }

    private RpropMinusTrainer(Options options, DoubleSupplier initializer, String initialization) {
        if (options == null || initializer == null)
            throw new IllegalArgumentException("Options and initializer required");
        this.options = options;
        this.initializer = initializer;
        this.initialization = initialization;
    }

    private static DoubleSupplier uniformValues(long seed, double radius) {
        var random = new SplittableRandom(seed);
        // Scale a finite [-1,1) draw without forming the potentially infinite 2 * radius.
        return () -> (2 * random.nextDouble() - 1) * radius;
    }

    public Options options() { return options; }

    @Override public FittedModel fit(double[][] features, double[][] targets) {
        var work = workspace(features, targets);
        double[] initial = new double[work.shape.parameterCount()];
        for (int i = 0; i < initial.length; i++) {
            initial[i] = initializer.getAsDouble();
            FeedForwardNetwork.requireFinite(initial[i]);
        }
        return train(work, initial, ++fitOrdinal, initialization);
    }

    /// Uses an owned parameter copy without consuming the initializer or advancing
    /// the fit ordinal. Diagnostics identify this path with ordinal zero.
    public FittedModel fitFromParameters(double[][] features, double[][] targets, double[] initialParameters) {
        var work = workspace(features, targets);
        FeedForwardNetwork.validateVector(initialParameters, work.shape.parameterCount(), "Initial parameters");
        return train(work, initialParameters.clone(), 0, "EXPLICIT_PARAMETERS");
    }

    private FeedForwardNetwork.Workspace workspace(double[][] features, double[][] targets) {
        if (features == null || targets == null || features.length == 0 || features.length != targets.length
                || features[0] == null || targets[0] == null)
            throw new IllegalArgumentException("Nonempty aligned feature and target rows required");
        var shape = new FeedForwardNetwork.Shape(features[0].length, options.hidden(), targets[0].length,
                options.shortcuts(), options.biases());
        return new FeedForwardNetwork.Workspace(shape, options.hiddenActivation(), options.outputActivation(),
                FeedForwardNetwork.copyMatrix(features, shape.inputs(), "Features"),
                FeedForwardNetwork.copyMatrix(targets, shape.outputs(), "Targets"));
    }

    private FittedModel train(FeedForwardNetwork.Workspace work, double[] initial,
                              long ordinal, String initialization) {
        var state = new UpdateState(initial);
        double initialLoss = work.evaluate(state.point);
        double loss = initialLoss;
        double maxAbsGradient = maxAbs(work.gradient);
        int lossIncreases = 0;
        for (int cycle = 0; cycle < options.cycles(); cycle++) {
            state.step(work.gradient);
            double nextLoss = work.evaluate(state.point);
            if (nextLoss > loss) lossIncreases++;
            loss = nextLoss;
            maxAbsGradient = Math.max(maxAbsGradient, maxAbs(work.gradient));
        }
        var network = new FeedForwardNetwork(work.shape, options.hiddenActivation(), options.outputActivation(), state.point);
        var diagnostics = new Diagnostics(ordinal, initialization, work.features.length, options.cycles(),
                options.cycles() + 1, initialLoss, loss, maxAbsGradient, lossIncreases,
                state.increases, state.signReversals);
        return new FittedModel(network, initial, diagnostics);
    }

    private static double maxAbs(double[] values) {
        double maximum = 0;
        for (double value : values) maximum = Math.max(maximum, Math.abs(value));
        return maximum;
    }

    /// All coordinates consume the same pre-update full gradient. Multiplication
    /// deliberately preserves the reference's binary64 underflow/overflow signs.
    static final class UpdateState {
        final double[] point, delta, previousGradient;
        long increases, signReversals;

        UpdateState(double[] initial) {
            point = initial.clone();
            delta = new double[initial.length];
            previousGradient = new double[initial.length];
            Arrays.fill(delta, .01);
        }

        void step(double[] gradient) {
            if (gradient.length != point.length)
                throw new IllegalArgumentException("Gradient dimension differs from parameters");
            for (int i = 0; i < point.length; i++) {
                double current = gradient[i];
                FeedForwardNetwork.requireFinite(current);
                double product = current * previousGradient[i];
                if (product > 0) {
                    delta[i] = Math.min(1e100, 1.2 * delta[i]);
                    increases++;
                } else if (product < 0) {
                    delta[i] = Math.max(0, .5 * delta[i]);
                    signReversals++;
                }
                double sign = current > 0 ? 1 : current < 0 ? -1 : 0;
                point[i] -= delta[i] * sign;
                previousGradient[i] = current;
                FeedForwardNetwork.requireFinite(point[i]);
            }
        }
    }

    public static final class FittedModel implements PredictorFunction<double[], double[]> {
        private final FeedForwardNetwork network;
        private final double[] initialParameters;
        private final Diagnostics diagnostics;

        private FittedModel(FeedForwardNetwork network, double[] initialParameters, Diagnostics diagnostics) {
            this.network = network;
            this.initialParameters = initialParameters.clone();
            this.diagnostics = diagnostics;
        }

        public FeedForwardNetwork network() { return network; }
        public double[] initialParameters() { return initialParameters.clone(); }
        public Diagnostics diagnostics() { return diagnostics; }

        @Override public double[] predict(double[] input) { return network.predict(input); }

        @Override public <T> Optional<T> unwrap(Class<T> type) {
            if (type.isInstance(this)) return Optional.of(type.cast(this));
            if (type.isInstance(diagnostics)) return Optional.of(type.cast(diagnostics));
            return network.unwrap(type);
        }
    }
}
