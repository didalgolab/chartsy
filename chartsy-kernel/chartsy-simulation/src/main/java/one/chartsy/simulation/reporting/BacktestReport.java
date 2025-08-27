/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.reporting;

import one.chartsy.data.stream.Message;

public interface BacktestReport extends Message {

    EquitySummaryStatistics equitySummary();

    double elapsedSeconds();

    int jvmRunNumber();

    record Of(
            EquitySummaryStatistics equitySummary,
            double elapsedSeconds,
            int jvmRunNumber,
            String sourceId,
            String destinationId,
            long time
    ) implements BacktestReport { }
}
