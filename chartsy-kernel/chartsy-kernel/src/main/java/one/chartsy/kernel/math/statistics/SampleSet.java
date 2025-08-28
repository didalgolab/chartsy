/*
 * Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.kernel.math.statistics;

/**
 * Streaming accumulator for univariate statistics (mean and variance),
 * suitable for online ingestion using a numerically stable algorithm
 * (e.g., Welford/Kahan). Implementations MUST be thread-safe only if
 * explicitly stated; otherwise assume not thread-safe.
 *
 * @author Mariusz Bernacki
 */
public interface SampleSet {

    /**
     * Adds a single observation to the accumulator.
     *
     * @param value the observation; implementations may ignore {@code NaN} by policy,
     *              or throw {@code IllegalArgumentException} if {@code NaN} is not allowed.
     */
    void add(double value);

    /**
     * Adds an array of observations to the accumulator.
     *
     * @param values array of values
     */
    void addAll(double... values);

    /**
     * Adds a slice of an array of observations to the accumulator.
     *
     * @param values array of values
     * @param offset start index (inclusive)
     * @param length number of elements to add
     * @throws IndexOutOfBoundsException if the slice is invalid
     */
    void addAll(double[] values, int offset, int length);

    /**
     * Resets the accumulator to the empty state.
     */
    void reset();

    /**
     * @return the number of observations ingested (n)
     */
    long count();

    /**
     * @return the arithmetic mean; NaN if empty
     */
    double mean();

    /**
     * @return unbiased sample variance (denominator n-1); NaN if n < 2
     */
    double sampleVariance();

    /**
     * @return unbiased sample standard deviation; NaN if n < 2
     */
    double sampleStandardDeviation();

    /**
     * @return the running minimum; NaN if empty
     */
    double min();

    /**
     * @return the running maximum; NaN if empty
     */
    double max();

    /**
     * @return true if at least two observations have been ingested (n >= 2)
     *         and a finite standard deviation can be estimated.
     */
    boolean isReady();

    /**
     * Computes the z-score for a value and a confidence interval for the true standardized
     * distance {@code z = (x - mu) / sigma} using the current samples moments.
     *
     * @param value the value to standardize
     * @param confidenceLevel e.g., 0.95 for a 95 percent interval
     * @return the computed standardized score and CI
     * @throws IllegalStateException if this.count() < 1 or standard deviation is not finite
     */
    StandardScore standardize(double value, double confidenceLevel);

    /**
     * Computes the z-score for a value and a 95% confidence interval for the true standardized
     * distance {@code z = (x - mu) / sigma} using the current samples moments.
     *
     * @param value the value to standardize
     * @return the computed standardized score and CI
     * @throws IllegalStateException if this.count() < 1 or standard deviation is not finite
     */
    default StandardScore standardize(double value) {
        return standardize(value, 0.95);
    }

    /**
     * Returns an immutable snapshot of the current summary statistics.
     *
     * @return a snapshot representing the current state
     */
    SampleSetSnapshot snapshot();
}
