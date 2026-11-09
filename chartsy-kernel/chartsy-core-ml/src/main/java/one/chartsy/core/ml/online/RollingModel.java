/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.ml.online;

import one.chartsy.core.ml.ToDoublePredictorFunction;

/**
 * A bounded window of completed supervised examples. Features are copied both
 * on observation and when passed to the trainer, so neither caller can rewrite
 * historical observations. This class is intended for a single event-loop thread.
 *
 * A fit always trains a fresh model from exactly the latest {@link #capacity()}
 * examples. Failed fits propagate to the caller; there is no stale-model fallback.
 */
public final class RollingModel {
    private final ModelTrainer trainer;
    private final double[][] features;
    private final double[] targets;
    private int nextIndex;
    private int size;
    private int inputDimension;
    private long version;

    public RollingModel(int capacity, ModelTrainer trainer) {
        if (capacity < 1)
            throw new IllegalArgumentException("capacity must be positive");
        if (trainer == null)
            throw new NullPointerException("trainer");
        this.trainer = trainer;
        this.features = new double[capacity][];
        this.targets = new double[capacity];
    }

    /** Adds an example only after its target is known. Invalid inputs do not change the window. */
    public void observe(double[] input, double target) {
        if (input.length == 0 || (inputDimension != 0 && input.length != inputDimension))
            throw new IllegalArgumentException("features must have a consistent, positive dimension");
        for (double value : input)
            if (!Double.isFinite(value))
                throw new IllegalArgumentException("features must be finite");
        if (!Double.isFinite(target))
            throw new IllegalArgumentException("target must be finite");

        features[nextIndex] = input.clone();
        targets[nextIndex] = target;
        inputDimension = input.length;
        nextIndex = (nextIndex + 1) % capacity();
        size = Math.min(size + 1, capacity());
    }

    public int size() { return size; }
    public int capacity() { return features.length; }
    public boolean isReady() { return size == capacity(); }

    /** Number of successfully fitted and validated models. */
    public long version() { return version; }

    /**
     * Refits from a chronological copy and validates predictions on the known
     * examples before returning the model. Callers must also validate any new
     * prediction, since a model can be finite on training inputs but not elsewhere.
     */
    public ToDoublePredictorFunction<double[]> fit() {
        if (!isReady())
            throw new IllegalStateException("Need " + capacity() + " examples; have " + size);
        double[][] trainingFeatures = new double[size][];
        double[] trainingTargets = new double[size];
        for (int i = 0; i < size; i++) {
            int index = (nextIndex + i) % capacity();
            trainingFeatures[i] = features[index].clone();
            trainingTargets[i] = targets[index];
        }
        var candidate = trainer.fit(trainingFeatures, trainingTargets);
        for (int i = 0; i < size; i++) {
            // Validate against our observations even if the trainer transforms its input arrays.
            double value = candidate.predictAsDouble(features[(nextIndex + i) % capacity()].clone());
            if (!Double.isFinite(value))
                throw new IllegalStateException("Fitted model produced a non-finite training prediction");
        }
        version++;
        return candidate;
    }
}
