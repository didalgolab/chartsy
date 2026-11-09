/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.ml.online.neural;

import one.chartsy.core.ml.PredictorFunction;
import one.chartsy.core.ml.online.VectorModelTrainer;

import java.util.Arrays;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.DoubleSupplier;

/**
 * Fresh, fixed-cycle regression fits using the iRprop+ mathematical update.
 *
 * <p>The step conventions follow Shark revision 1f3733898a1dac4a23439c4063cecf17537d936d
 * (Rprop.cpp), including initial step .01, bounds 0..1e100 and final-iterate return.
 * This independently written Java implementation is not the original C++ program.
 * See Igel and Huesken, Empirical Evaluation of the Improved Rprop Learning Algorithm.</p>
 *
 * <p>One isolated advancing random stream supplies fresh uniform weights per fit.
 * Equal new trainer instances reproduce a sequence; repeatedly fitting one instance
 * intentionally does not reset its seed. The trainer is not thread-safe. Neither
 * the Java generator nor the caller's sample order recovers an unknown author stream.</p>
 */
public final class IrpropPlusTrainer implements VectorModelTrainer {
    public record Options(int hidden, NeuralActivation hiddenActivation, NeuralActivation outputActivation,
                          int cycles, long seed, boolean shortcuts, boolean biases) {
        public Options {
            if (hidden < 1 || cycles < 1)
                throw new IllegalArgumentException("Positive hidden count and training cycles required");
            if (hiddenActivation == null || outputActivation == null)
                throw new IllegalArgumentException("Both activation functions required");
        }
        public static Options historicalShark(int hidden, NeuralActivation hiddenActivation, int cycles, long seed) {
            return new Options(hidden, hiddenActivation, NeuralActivation.LINEAR, cycles, seed, true, true);
        }
    }

    public record Diagnostics(long fitOrdinal, String initialization, int rows, int cycles,
                              int evaluations, double initialLoss, double finalLoss,
                              double maximumGradient, long increases, long signReversals,
                              long backtracks) { }

    private final Options options;
    private final DoubleSupplier initialValue;
    private final String initialization;
    private FeedForwardNetwork.Shape resumedShape;
    private long fitOrdinal;

    public IrpropPlusTrainer(Options options) {
        this(options, uniformValues(options.seed()), "ADVANCING_SPLITTABLE_RANDOM");
    }

    /**
     * Continues the original seeded stream after completed fresh fits of one fixed shape.
     * Only initialization is advanced: no training rows or optimizer state are replayed.
     * The caller must independently verify the completed models and their causal prefix.
     * Every later fit must retain this shape. Supplied initializer streams cannot use this path.
     */
    public static IrpropPlusTrainer resume(Options options, FeedForwardNetwork.Shape shape, long completedFits) {
        if (completedFits < 0 || shape.hidden() != options.hidden() || shape.shortcuts() != options.shortcuts()
                || shape.biases() != options.biases())
            throw new IllegalArgumentException("Nonnegative fit count and unchanged network shape required");
        long values = Math.multiplyExact(completedFits, shape.parameterCount());
        var trainer = new IrpropPlusTrainer(options);
        for (long i = 0; i < values; i++) trainer.initialValue.getAsDouble();
        trainer.fitOrdinal = completedFits;
        trainer.resumedShape = shape;
        return trainer;
    }

    /**
     * Continues a fresh, exclusively owned supplied stream and its global fit ordinal.
     * The supplier must start at the first fit's first parameter, not at the seam.
     * It must contain exactly this fixed shape's values per fit; an interleaved
     * multi-model initializer stream cannot be resumed with this fit count.
     * Only the accepted initializer values are consumed; no old optimization runs.
     * The caller must verify every old context and initializer value separately.
     */
    public static IrpropPlusTrainer resume(Options options, FeedForwardNetwork.Shape shape, long completedFits,
                                          DoubleSupplier initialValue) {
        if (completedFits < 0 || shape.hidden() != options.hidden() || shape.shortcuts() != options.shortcuts()
                || shape.biases() != options.biases())
            throw new IllegalArgumentException("Nonnegative fit count and unchanged network shape required");
        long values = Math.multiplyExact(completedFits, shape.parameterCount());
        var trainer = new IrpropPlusTrainer(options, initialValue);
        for (long i = 0; i < values; i++) FeedForwardNetwork.requireFinite(trainer.initialValue.getAsDouble());
        trainer.fitOrdinal = completedFits;
        trainer.resumedShape = shape;
        return trainer;
    }

    /**
     * Retains an exclusively owned initializer stream; this trainer is not thread-safe.
     * The options seed is unused on this path. Each valid fit consumes one finite value
     * per parameter, in parameter order; finite values need not lie in [-.1,.1).
     * A failed fit does not rewind consumed values and must not be retried as the same fit.
     */
    public IrpropPlusTrainer(Options options, DoubleSupplier initialValue) {
        this(options, initialValue, "ADVANCING_SUPPLIED_INITIALIZER");
    }

    private IrpropPlusTrainer(Options options, DoubleSupplier initialValue, String initialization) {
        if (initialValue == null) throw new IllegalArgumentException("Initializer required");
        this.options = options;
        this.initialValue = initialValue;
        this.initialization = initialization;
    }

    private static DoubleSupplier uniformValues(long seed) {
        var random = new SplittableRandom(seed);
        return () -> random.nextDouble(-.1, .1);
    }
    public Options options() { return options; }

    @Override public FittedModel fit(double[][] features, double[][] targets) {
        var work = workspace(features, targets);
        double[] initial = new double[work.shape.parameterCount()];
        for (int i = 0; i < initial.length; i++) {
            initial[i] = initialValue.getAsDouble();
            FeedForwardNetwork.requireFinite(initial[i]);
        }
        return train(work, initial, ++fitOrdinal, initialization);
    }

    /** Explicit initial parameters allow deterministic mathematical comparisons without consuming RNG. */
    public FittedModel fitFromParameters(double[][] features, double[][] targets, double[] initialParameters) {
        var work = workspace(features, targets);
        FeedForwardNetwork.validateVector(initialParameters, work.shape.parameterCount(), "Initial parameters");
        return train(work, initialParameters.clone(), 0, "EXPLICIT_PARAMETERS");
    }

    private FeedForwardNetwork.Workspace workspace(double[][] features, double[][] targets) {
        int inputs = columns(features, "Features"), outputs = columns(targets, "Targets");
        var shape = new FeedForwardNetwork.Shape(inputs, options.hidden(), outputs, options.shortcuts(), options.biases());
        if (resumedShape != null && !resumedShape.equals(shape))
            throw new IllegalArgumentException("Resumed fits must retain the original network shape");
        return new FeedForwardNetwork.Workspace(shape, options.hiddenActivation(), options.outputActivation(),
                FeedForwardNetwork.copyMatrix(features, inputs, "Features"),
                FeedForwardNetwork.copyMatrix(targets, outputs, "Targets"));
    }

    private static int columns(double[][] values, String name) {
        if (values == null || values.length == 0 || values[0] == null || values[0].length == 0)
            throw new IllegalArgumentException(name + " needs positive dimensions");
        return values[0].length;
    }

    private FittedModel train(FeedForwardNetwork.Workspace work, double[] initial, long ordinal, String initialization) {
        var state = new UpdateState(initial);
        double loss = work.evaluate(state.point), initialLoss = loss;
        for (int cycle = 0; cycle < options.cycles(); cycle++) {
            state.step(loss, work.gradient);
            loss = work.evaluate(state.point);
        }
        double maxGradient = 0;
        for (double value : work.gradient) maxGradient = Math.max(maxGradient, Math.abs(value));
        var network = new FeedForwardNetwork(work.shape, options.hiddenActivation(), options.outputActivation(), state.point);
        var diagnostics = new Diagnostics(ordinal, initialization, work.features.length, options.cycles(),
                options.cycles() + 1, initialLoss, loss, maxGradient, state.increases, state.signReversals, state.backtracks);
        return new FittedModel(network, initial, diagnostics);
    }

    /** Coordinate update only; all coordinates use the same two objective values. */
    static final class UpdateState {
        final double[] point, delta, previousGradient, previousStep;
        double previousLoss = Double.MAX_VALUE;
        long increases, signReversals, backtracks;

        UpdateState(double[] initial) {
            point = initial.clone();
            delta = new double[initial.length];
            previousGradient = new double[initial.length];
            previousStep = new double[initial.length];
            Arrays.fill(delta, .01);
        }

        void step(double loss, double[] gradient) {
            FeedForwardNetwork.requireFinite(loss);
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
                    if (loss > previousLoss) {
                        point[i] -= previousStep[i];
                        backtracks++;
                    }
                    previousGradient[i] = 0;
                    FeedForwardNetwork.requireFinite(point[i]);
                    continue;
                }
                double sign = current > 0 ? 1 : current < 0 ? -1 : 0;
                previousStep[i] = -sign * delta[i];
                point[i] += previousStep[i];
                previousGradient[i] = current;
                FeedForwardNetwork.requireFinite(point[i]);
            }
            previousLoss = loss;
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
