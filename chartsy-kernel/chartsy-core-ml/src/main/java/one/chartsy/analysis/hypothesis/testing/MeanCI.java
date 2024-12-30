/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.analysis.hypothesis.testing;

import one.chartsy.data.RealVector;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

public class MeanCI {

    public static final double DEFAULT_CONFIDENCE_LEVEL = 0.95;

    /**
     * Calculates a 95% confidence interval (CI) for the population mean estimated
     * from the given values.
     *
     * @param values the vector of values
     * @return the {@code RealVector} containing the {@code {min, max}} values of
     *         the estimated confidence interval
     */
    public static RealVector meanCI(RealVector values) {
        return meanCI(values, DEFAULT_CONFIDENCE_LEVEL);
    }

    /**
     * Calculates a confidence interval (CI) for the population mean estimated
     * from the given values.
     *
     * @param values the vector of values
     * @param confidenceLevel the requested confidence level, between 0 and 1 (exclusive)
     * @return the {@code RealVector} containing the {@code {min, max}} values of
     *         the estimated confidence interval
     */
    public static RealVector meanCI(RealVector values, double confidenceLevel) {
        if (confidenceLevel <= 0.0 || confidenceLevel >= 1.0)
            throw new IllegalArgumentException("confidenceLevel must in range (0, 1)");

        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (int i = 0; i < values.size(); i++) {
            stats.addValue(values.get(i));
        }

        double mean = stats.getMean();
        double stddev = stats.getStandardDeviation();
        int n = values.size();

        // Standard Error of the Mean
        double sem = stddev / Math.sqrt(n);

        // Requested % Confidence Interval for the Mean
        double alpha = 1.0 - confidenceLevel;
        TDistribution tDist = new TDistribution(n - 1);
        double tStat = tDist.inverseCumulativeProbability(1.0 - alpha / 2); // % CI
        double ciLowerMean = mean - tStat * sem;
        double ciUpperMean = mean + tStat * sem;

        return RealVector.fromValues(ciLowerMean, ciUpperMean);
    }
}
