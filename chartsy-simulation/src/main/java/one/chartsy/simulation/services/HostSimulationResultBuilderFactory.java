/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.simulation.services;

import one.chartsy.simulation.SimulationResult;
import one.chartsy.simulation.platform.StandardReport;
import one.chartsy.trade.strategy.ReportOptions;
import org.openide.util.lookup.ServiceProvider;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@ServiceProvider(service = SimulationResultBuilderFactory.class)
public class HostSimulationResultBuilderFactory implements SimulationResultBuilderFactory {

    @Override
    public SimulationResult.Builder create(Map<String, ?> props) {
        return new SimulationResult.Builder()
                .startTime(LocalDateTime.MAX)
                .endTime(LocalDateTime.MAX)
                .testDays(0)
                .testDuration(Duration.ZERO)
                .estimatedDataPointCount(0)
                .totalProfit(0.0)
                .report(new StandardReport(
                        new ReportOptions.Builder().build(),
                        Optional.empty(),
                        Optional.empty()
                ))
                .remainingOrderCount(0);
    }
}
