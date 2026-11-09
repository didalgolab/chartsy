/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.ml.online.neural;

import one.chartsy.core.ml.PredictorFunction;
import one.chartsy.core.ml.online.VectorModelTrainer;

import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.DoubleSupplier;

/// Fixed-rate, full-batch backpropagation for a fresh two-layer network.
///
/// Each cycle applies all coordinates of the gradient from the same parameter vector,
/// then evaluates the updated network. The loss averages rows and sums squared errors
/// across output coordinates. Training returns the final iterate, without early stopping,
/// momentum, shuffling, clipping or best-iterate selection.
///
/// A trainer owns one advancing initialization stream. Fits in separate data runs are
/// paired by fit ordinal only; differing warmup counts do not imply origin-level pairing.
/// Instances are not thread-safe.
public final class BatchBackpropTrainer implements VectorModelTrainer {
    public record Options(int hidden, NeuralActivation hiddenActivation, NeuralActivation outputActivation,
                          int cycles, double learningRate, long seed, boolean shortcuts, boolean biases) {
        public Options {
            if (hidden < 1 || cycles < 1 || hiddenActivation == null || outputActivation == null
                    || !Double.isFinite(learningRate) || learningRate <= 0)
                throw new IllegalArgumentException("Positive hidden size, cycles and finite learning rate; activations required");
        }
    }

    /// Maximum absolute gradient covers the initial and every post-update evaluation.
    public record Diagnostics(long fitOrdinal, String initialization, int rows, int cycles, int evaluations,
                              double initialLoss, double finalLoss, double maxAbsGradient, int lossIncreases) { }

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

        @Override public double[] predict(double[] features) { return network.predict(features); }

        @Override public <T> Optional<T> unwrap(Class<T> type) {
            if (type.isInstance(this)) return Optional.of(type.cast(this));
            if (type.isInstance(diagnostics)) return Optional.of(type.cast(diagnostics));
            return network.unwrap(type);
        }
    }

    private final Options options;
    private final DoubleSupplier initializer;
    private final String initialization;
    private long fitOrdinal;

    public BatchBackpropTrainer(Options options) {
        this(options, initializer(options.seed()), "ADVANCING_SPLITTABLE_RANDOM");
    }

    /// Retains an exclusively owned initializer; the options seed is unused on this path.
    /// Its actual identity must be recorded separately; finite supplied values may exceed [-.1,.1).
    /// A failed draw is propagated without resetting or retrying the stream.
    public BatchBackpropTrainer(Options options, DoubleSupplier initializer) {
        this(options, initializer, "ADVANCING_SUPPLIED_INITIALIZER");
    }

    private BatchBackpropTrainer(Options options, DoubleSupplier initializer, String initialization) {
        if (options == null || initializer == null)
            throw new IllegalArgumentException("Options and initializer required");
        this.options = options;
        this.initializer = initializer;
        this.initialization = initialization;
    }

    private static DoubleSupplier initializer(long seed) {
        var random = new SplittableRandom(seed);
        return () -> random.nextDouble(-.1, .1);
    }

    public Options options() { return options; }

    @Override public FittedModel fit(double[][] features, double[][] targets) {
        var workspace = workspace(features, targets);
        double[] parameters = new double[workspace.shape.parameterCount()];
        for (int i = 0; i < parameters.length; i++) {
            parameters[i] = initializer.getAsDouble();
            FeedForwardNetwork.requireFinite(parameters[i]);
        }
        return train(workspace, parameters, ++fitOrdinal, initialization);
    }

    /// Trains from an owned copy without consuming the initializer or advancing fit ordinal.
    /// The diagnostics use ordinal zero to identify this explicit-parameter path.
    public FittedModel fitFromParameters(double[][] features, double[][] targets, double[] initialParameters) {
        var workspace = workspace(features, targets);
        FeedForwardNetwork.validateVector(initialParameters, workspace.shape.parameterCount(), "Initial parameters");
        return train(workspace, initialParameters.clone(), 0, "EXPLICIT_PARAMETERS");
    }

    private FeedForwardNetwork.Workspace workspace(double[][] features, double[][] targets) {
        if (features == null || targets == null || features.length == 0 || features.length != targets.length
                || features[0] == null || targets[0] == null)
            throw new IllegalArgumentException("Nonempty aligned feature and target rows required");
        int inputs = features[0].length, outputs = targets[0].length;
        var shape = new FeedForwardNetwork.Shape(inputs, options.hidden(), outputs, options.shortcuts(), options.biases());
        return new FeedForwardNetwork.Workspace(shape, options.hiddenActivation(), options.outputActivation(),
                FeedForwardNetwork.copyMatrix(features, inputs, "Features"),
                FeedForwardNetwork.copyMatrix(targets, outputs, "Targets"));
    }

    private FittedModel train(FeedForwardNetwork.Workspace workspace, double[] parameters,
                              long ordinal, String initialization) {
        double[] initial = parameters.clone();
        double initialLoss = workspace.evaluate(parameters);
        double loss = initialLoss, maxAbsGradient = maxAbs(workspace.gradient);
        int lossIncreases = 0;
        for (int cycle = 0; cycle < options.cycles(); cycle++) {
            // evaluate() has already computed the entire gradient at the old vector.
            for (int i = 0; i < parameters.length; i++) {
                parameters[i] -= options.learningRate() * workspace.gradient[i];
                FeedForwardNetwork.requireFinite(parameters[i]);
            }
            double nextLoss = workspace.evaluate(parameters);
            if (nextLoss > loss) lossIncreases++;
            loss = nextLoss;
            maxAbsGradient = Math.max(maxAbsGradient, maxAbs(workspace.gradient));
        }
        var network = new FeedForwardNetwork(workspace.shape, options.hiddenActivation(),
                options.outputActivation(), parameters);
        var diagnostics = new Diagnostics(ordinal, initialization, workspace.features.length, options.cycles(),
                options.cycles() + 1, initialLoss, loss, maxAbsGradient, lossIncreases);
        return new FittedModel(network, initial, diagnostics);
    }

    private static double maxAbs(double[] values) {
        double maximum = 0;
        for (double value : values) maximum = Math.max(maximum, Math.abs(value));
        return maximum;
    }
}
