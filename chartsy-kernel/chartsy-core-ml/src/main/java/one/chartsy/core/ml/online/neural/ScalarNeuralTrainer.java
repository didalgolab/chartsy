/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.ml.online.neural;

import one.chartsy.core.ml.ToDoublePredictorFunction;
import one.chartsy.core.ml.online.ModelTrainer;

import java.util.Optional;
import java.util.function.DoubleSupplier;

/** A scalar view of the same joint-output regression trainer. */
public final class ScalarNeuralTrainer implements ModelTrainer {
    private final IrpropPlusTrainer trainer;

    public ScalarNeuralTrainer(IrpropPlusTrainer.Options options) { trainer = new IrpropPlusTrainer(options); }

    private ScalarNeuralTrainer(IrpropPlusTrainer trainer) { this.trainer = trainer; }

    /** Continues the owned original stream and ordinal for a fixed scalar network. */
    public static ScalarNeuralTrainer resume(IrpropPlusTrainer.Options options, int inputs, long completedFits) {
        var shape = new FeedForwardNetwork.Shape(inputs, options.hidden(), 1, options.shortcuts(), options.biases());
        return new ScalarNeuralTrainer(IrpropPlusTrainer.resume(options, shape, completedFits));
    }

    /** Continues a fresh owned supplied stream, retaining the scalar shape and global ordinal. */
    public static ScalarNeuralTrainer resume(IrpropPlusTrainer.Options options, int inputs, long completedFits,
                                              DoubleSupplier initialValue) {
        var shape = new FeedForwardNetwork.Shape(inputs, options.hidden(), 1, options.shortcuts(), options.biases());
        return new ScalarNeuralTrainer(IrpropPlusTrainer.resume(options, shape, completedFits, initialValue));
    }

    /** Retains the exclusively owned initializer stream, with the vector trainer's failure contract. */
    public ScalarNeuralTrainer(IrpropPlusTrainer.Options options, DoubleSupplier initialValue) {
        trainer = new IrpropPlusTrainer(options, initialValue);
    }

    @Override public ToDoublePredictorFunction<double[]> fit(double[][] features, double[] targets) {
        double[][] vectors = new double[targets.length][1];
        for (int i = 0; i < targets.length; i++) vectors[i][0] = targets[i];
        var model = trainer.fit(features, vectors);
        return new ToDoublePredictorFunction<>() {
            @Override public double predictAsDouble(double[] input) { return model.predict(input)[0]; }
            @Override public <T> Optional<T> unwrap(Class<T> type) { return model.unwrap(type); }
        };
    }
}
