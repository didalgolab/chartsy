/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.analysis.hypothesis.testing;

import one.chartsy.data.RealVector;

public class StandardDeviationCI {

    /**
     * Calculates a 95% confidence interval (CI) for the population standard deviation estimated
     * from the given values.
     *
     * @param values the vector of values
     * @return the {@code RealVector} containing the {@code {min, max}} values of
     *         the estimated confidence interval for the standard deviation
     */
    public static RealVector standardDeviationCI(RealVector values) {
        return standardDeviationCI(values, VarianceCI.DEFAULT_CONFIDENCE_LEVEL);
    }

    /**
     * Calculates a confidence interval (CI) for the population standard deviation estimated
     * from the given values.
     *
     * @param values the vector of values
     * @param confidenceLevel the requested confidence level, between 0 and 1 (exclusive)
     * @return the {@code RealVector} containing the {@code {min, max}} values of
     *         the estimated confidence interval for the standard deviation
     */
    public static RealVector standardDeviationCI(RealVector values, double confidenceLevel) {
        RealVector varianceCI = VarianceCI.varianceCI(values, confidenceLevel);
        double ciLowerStdDev = Math.sqrt(varianceCI.get(0));
        double ciUpperStdDev = Math.sqrt(varianceCI.get(1));

        return RealVector.fromValues(ciLowerStdDev, ciUpperStdDev);
    }
}