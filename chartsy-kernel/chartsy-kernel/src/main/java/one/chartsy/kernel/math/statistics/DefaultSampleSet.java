/*
 * Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.kernel.math.statistics;

public class DefaultSampleSet implements SampleSet {

    /** The number of samples. */
    private long count;
    /** The running mean. */
    private double mean;
    /** The sum of squares of differences from the current mean. */
    private double m2;
    /** The running maximum. */
    private double min = Double.POSITIVE_INFINITY;
    /** The running minimum. */
    private double max = Double.NEGATIVE_INFINITY;

    @Override
    public void reset() {
        this.count = 0L;
        this.mean = 0.0;
        this.m2 = 0.0;
        this.min = Double.POSITIVE_INFINITY;
        this.max = Double.NEGATIVE_INFINITY;
    }

    @Override
    public void add(double value) {
        long nPrev = this.count;
        this.count = nPrev + 1L;
        double delta = value - this.mean;
        this.mean += delta / this.count;
        double delta2 = value - this.mean;
        this.m2 += delta * delta2;
        if (value < min)
            min = value;
        if (value > max)
            max = value;
    }

    @Override
    public void addAll(double... values) {
        if (values == null)
            return;
        for (double x : values)
            add(x);
    }

    @Override
    public void addAll(double[] values, int offset, int length) {
        if (values == null)
            return;
        if (offset < 0 || length < 0 || offset + length > values.length)
            throw new IndexOutOfBoundsException("Invalid slice: offset=" + offset + ", length=" + length
                    + ", array.length=" + values.length);

        for (int i = 0; i < length; i++)
            add(values[offset + i]);
    }

    @Override
    public final long count() {
        return count;
    }

    @Override
    public final boolean isReady() {
        return count >= 2 && sampleStandardDeviation() > 0.0;
    }

    @Override
    public final double mean() {
        return mean;
    }

    @Override
    public final double sampleVariance() {
        long count = this.count;
        return (count < 2) ? Double.NaN : m2 / (count - 1.0);
    }

    @Override
    public final double sampleStandardDeviation() {
        return Math.sqrt(sampleVariance());
    }

    @Override
    public final double min() {
        return min;
    }

    @Override
    public final double max() {
        return max;
    }

    /** z_hat in sample-sigma units: (value - mean_hat) / s. NaN if not ready. */
    public final double getZHat(double value) {
        if (!isReady())
            return Double.NaN;
        return (value - mean) / sampleStandardDeviation();
    }

    /** Observed noncentral-t statistic: sqrt(n)*(value - mean)/s. NaN if not ready. */
    public final double getTObs(double value) {
        if (!isReady())
            return Double.NaN;
        return Math.sqrt((double) count) * (value - mean) / sampleStandardDeviation();
    }

    @Override
    public final StandardScore standardize(double value, double confidenceLevel) {
        return StandardScoreEstimator.getInstance().estimate(value, snapshot(), confidenceLevel);
    }

    @Override
    public SampleSetSnapshot snapshot() {
        return new SampleSetSnapshot.Of(count, mean, sampleVariance());
    }
}
