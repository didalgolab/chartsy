/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.ml.online.neural;

import one.chartsy.core.ml.PredictorFunction;

import java.util.Arrays;
import java.util.Optional;

/**
 * An immutable one-hidden-layer vector predictor with optional direct input/output connections.
 *
 * <p>Parameters traverse destination neurons, then source neurons, followed by biases:
 * input/hidden weights, each output's input shortcuts and hidden weights, hidden biases,
 * output biases. With shortcuts and biases, a 3-3-1 network has 19 parameters.
 * This ordering matches historical Shark FFNet, but Java arithmetic is independent.</p>
 */
public final class FeedForwardNetwork implements PredictorFunction<double[], double[]> {
    public record Shape(int inputs, int hidden, int outputs, boolean shortcuts, boolean biases) {
        public Shape {
            if (inputs < 1 || hidden < 1 || outputs < 1)
                throw new IllegalArgumentException("Positive network dimensions required");
            Math.addExact(Math.addExact(Math.multiplyExact(inputs, hidden),
                    Math.multiplyExact(outputs, Math.addExact(hidden, shortcuts ? inputs : 0))),
                    biases ? Math.addExact(hidden, outputs) : 0);
        }
        public int outputStride() { return hidden + (shortcuts ? inputs : 0); }
        public int outputOffset() { return inputs * hidden; }
        public int biasOffset() { return outputOffset() + outputs * outputStride(); }
        public int parameterCount() { return biasOffset() + (biases ? hidden + outputs : 0); }
    }

    /** Mean row loss and all parameter derivatives; callers own returned arrays. */
    public record LossGradient(double loss, double[] gradient) {
        public LossGradient { gradient = gradient.clone(); }
        @Override public double[] gradient() { return gradient.clone(); }
    }

    private final Shape shape;
    private final NeuralActivation hiddenActivation;
    private final NeuralActivation outputActivation;
    private final double[] parameters;

    public FeedForwardNetwork(Shape shape, NeuralActivation hiddenActivation,
                              NeuralActivation outputActivation, double[] parameters) {
        if (hiddenActivation == null || outputActivation == null)
            throw new IllegalArgumentException("Both activation functions required");
        validateVector(parameters, shape.parameterCount(), "Parameters");
        this.shape = shape;
        this.hiddenActivation = hiddenActivation;
        this.outputActivation = outputActivation;
        this.parameters = parameters.clone();
    }

    public Shape shape() { return shape; }
    public NeuralActivation hiddenActivation() { return hiddenActivation; }
    public NeuralActivation outputActivation() { return outputActivation; }
    public double[] parameters() { return parameters.clone(); }

    @Override public double[] predict(double[] input) {
        validateVector(input, shape.inputs(), "Prediction input");
        double[] hidden = new double[shape.hidden()];
        double[] output = new double[shape.outputs()];
        forward(shape, hiddenActivation, outputActivation, parameters, input, hidden, output);
        return output;
    }

    @Override public <T> Optional<T> unwrap(Class<T> type) {
        return type.isInstance(this) ? Optional.of(type.cast(this)) : Optional.empty();
    }

    public LossGradient lossGradient(double[][] features, double[][] targets) {
        var work = new Workspace(shape, hiddenActivation, outputActivation,
                copyMatrix(features, shape.inputs(), "Features"),
                copyMatrix(targets, shape.outputs(), "Targets"));
        return new LossGradient(work.evaluate(parameters), work.gradient);
    }

    static void forward(Shape shape, NeuralActivation hiddenActivation, NeuralActivation outputActivation,
                        double[] weights, double[] input, double[] hidden, double[] output) {
        int inputs = shape.inputs();
        int hiddenCount = shape.hidden();
        int biasOffset = shape.biasOffset();
        for (int j = 0; j < hiddenCount; j++) {
            double response = 0;
            for (int i = 0; i < inputs; i++) response += weights[j * inputs + i] * input[i];
            if (shape.biases()) response += weights[biasOffset + j];
            requireFinite(response);
            hidden[j] = hiddenActivation.value(response);
            requireFinite(hidden[j]);
        }
        for (int k = 0; k < shape.outputs(); k++) {
            int offset = shape.outputOffset() + k * shape.outputStride();
            double response = 0;
            if (shape.shortcuts()) {
                for (int i = 0; i < inputs; i++) response += weights[offset + i] * input[i];
                offset += inputs;
            }
            for (int j = 0; j < hiddenCount; j++) response += weights[offset + j] * hidden[j];
            if (shape.biases()) response += weights[biasOffset + hiddenCount + k];
            requireFinite(response);
            output[k] = outputActivation.value(response);
            requireFinite(output[k]);
        }
    }

    /** A fit owns one workspace; repeated evaluations allocate no per-row arrays. */
    static final class Workspace {
        final Shape shape;
        final NeuralActivation hiddenActivation;
        final NeuralActivation outputActivation;
        final double[][] features;
        final double[][] targets;
        final double[] hidden;
        final double[] output;
        final double[] outputDerivative;
        final double[] gradient;

        Workspace(Shape shape, NeuralActivation hiddenActivation, NeuralActivation outputActivation,
                  double[][] features, double[][] targets) {
            if (features.length != targets.length) throw new IllegalArgumentException("Feature/target row counts differ");
            this.shape = shape;
            this.hiddenActivation = hiddenActivation;
            this.outputActivation = outputActivation;
            this.features = features;
            this.targets = targets;
            hidden = new double[shape.hidden()];
            output = new double[shape.outputs()];
            outputDerivative = new double[shape.outputs()];
            gradient = new double[shape.parameterCount()];
        }

        double evaluate(double[] weights) {
            Arrays.fill(gradient, 0);
            double loss = 0;
            int inputs = shape.inputs();
            int hiddenCount = shape.hidden();
            int biasOffset = shape.biasOffset();
            for (int row = 0; row < features.length; row++) {
                forward(shape, hiddenActivation, outputActivation, weights, features[row], hidden, output);
                for (int k = 0; k < output.length; k++) {
                    double error = output[k] - targets[row][k];
                    loss += error * error;
                    double delta = 2 * error * outputActivation.derivativeFromOutput(output[k]);
                    outputDerivative[k] = delta;
                    int offset = shape.outputOffset() + k * shape.outputStride();
                    if (shape.shortcuts()) {
                        for (int i = 0; i < inputs; i++) gradient[offset + i] += delta * features[row][i];
                        offset += inputs;
                    }
                    for (int j = 0; j < hiddenCount; j++) gradient[offset + j] += delta * hidden[j];
                    if (shape.biases()) gradient[biasOffset + hiddenCount + k] += delta;
                }
                for (int j = 0; j < hiddenCount; j++) {
                    double delta = 0;
                    for (int k = 0; k < output.length; k++) {
                        int offset = shape.outputOffset() + k * shape.outputStride()
                                + (shape.shortcuts() ? inputs : 0);
                        delta += outputDerivative[k] * weights[offset + j];
                    }
                    delta *= hiddenActivation.derivativeFromOutput(hidden[j]);
                    for (int i = 0; i < inputs; i++) gradient[j * inputs + i] += delta * features[row][i];
                    if (shape.biases()) gradient[biasOffset + j] += delta;
                }
            }
            loss /= features.length;
            requireFinite(loss);
            for (int i = 0; i < gradient.length; i++) {
                gradient[i] /= features.length;
                requireFinite(gradient[i]);
            }
            return loss;
        }
    }

    static double[][] copyMatrix(double[][] matrix, int columns, String name) {
        if (matrix == null || matrix.length == 0)
            throw new IllegalArgumentException(name + " needs at least one row");
        double[][] copy = new double[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            validateVector(matrix[i], columns, name);
            copy[i] = matrix[i].clone();
        }
        return copy;
    }

    static void validateVector(double[] values, int dimension, String name) {
        if (values == null || values.length != dimension)
            throw new IllegalArgumentException(name + " has the wrong dimension");
        for (double value : values)
            if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }

    static void requireFinite(double value) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Non-finite neural arithmetic");
    }
}
