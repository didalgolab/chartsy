/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.ml.online;

import one.chartsy.core.ml.PredictorFunction;

/**
 * A bounded chronological window of completed vector-valued examples.
 * Input and target coordinates are copied on observation and again before each fresh fit.
 * All output coordinates belong to the same example and are trained together.
 * Intended for one event-loop thread; failed fits never increment the version.
 */
public final class RollingVectorModel {
    private final VectorModelTrainer trainer;
    private final double[][] features;
    private final double[][] targets;
    private int nextIndex;
    private int size;
    private int inputDimension;
    private int outputDimension;
    private long version;

    public RollingVectorModel(int capacity, VectorModelTrainer trainer) {
        if (capacity < 1)
            throw new IllegalArgumentException("Positive capacity required");
        if (trainer == null)
            throw new IllegalArgumentException("Trainer required");
        this.trainer = trainer;
        features = new double[capacity][];
        targets = new double[capacity][];
    }

    /** Invalid examples leave the retained window unchanged. */
    public void observe(double[] input, double[] target) {
        validate(input, inputDimension, "Input");
        validate(target, outputDimension, "Target");
        features[nextIndex] = input.clone();
        targets[nextIndex] = target.clone();
        inputDimension = input.length;
        outputDimension = target.length;
        nextIndex = (nextIndex + 1) % capacity();
        size = Math.min(size + 1, capacity());
    }

    public int size() { return size; }
    public int capacity() { return features.length; }
    public boolean isReady() { return size == capacity(); }
    public long version() { return version; }
    public int outputDimension() { return outputDimension; }

    /** Trains a fresh model and checks its output shape and finiteness on retained inputs. */
    public PredictorFunction<double[], double[]> fit() {
        if (!isReady())
            throw new IllegalStateException("Need " + capacity() + " examples; have " + size);
        double[][] trainingFeatures = new double[size][];
        double[][] trainingTargets = new double[size][];
        for (int i = 0; i < size; i++) {
            int index = (nextIndex + i) % capacity();
            trainingFeatures[i] = features[index].clone();
            trainingTargets[i] = targets[index].clone();
        }
        var candidate = trainer.fit(trainingFeatures, trainingTargets);
        for (int i = 0; i < size; i++)
            validate(candidate.predict(features[(nextIndex + i) % capacity()].clone()), outputDimension, "Prediction");
        version++;
        return candidate;
    }

    private static void validate(double[] values, int dimension, String name) {
        if (values == null || values.length == 0 || (dimension != 0 && values.length != dimension))
            throw new IllegalArgumentException(name + " must have a consistent positive dimension");
        for (double value : values)
            if (!Double.isFinite(value)) throw new IllegalArgumentException(name + " must be finite");
    }
}
