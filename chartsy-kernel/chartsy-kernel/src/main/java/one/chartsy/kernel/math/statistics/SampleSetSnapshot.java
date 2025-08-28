/*
 * Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.kernel.math.statistics;

/**
 * Immutable, read-only view of summary statistics captured at a point in time.
 * All quantities refer to the underlying sample that has been observed so far.
 *
 * @author Mariusz Bernacki
 */
public interface SampleSetSnapshot {

    /**
     * @return the number of observations n (n >= 0)
     */
    long count();

    /**
     * @return the arithmetic mean of the sample, or NaN if count == 0
     */
    double mean();

    /**
     * Returns the unbiased sample variance (with Bessel's correction, denominator n-1).
     *
     * @return sample variance, or NaN if count < 2
     */
    double sampleVariance();

    /**
     * @return sqrt of getSampleVariance(), or NaN if count < 2
     */
    default double sampleStandardDeviation() {
        return Math.sqrt(sampleVariance());
    }

    record Of(long count, double mean, double sampleVariance) implements SampleSetSnapshot { }
}