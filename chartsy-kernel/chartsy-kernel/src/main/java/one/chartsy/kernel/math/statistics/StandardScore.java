/*
 * Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.kernel.math.statistics;

import one.chartsy.core.Range;

/**
 * Result of standardizing a value, including the z-score and its CI,
 * and the samples' moment snapshot used to compute them.
 */
public interface StandardScore {

    /**
     * @return the original target value that was standardized
     */
    double value();

    /**
     * @return the standardized score point estimate z_hat
     */
    double score();

    /**
     * @return the confidence interval for the true standardized score parameter
     */
    Range confidenceInterval();

    /**
     * @return the statistics snapshot used for the calculation (at call time)
     */
    SampleSetSnapshot statisticsUsed();

    record Of(double value, double score, Range confidenceInterval, SampleSetSnapshot statisticsUsed)
            implements StandardScore { }
}
