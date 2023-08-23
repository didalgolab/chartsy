/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.simulation.platform;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.Accessors;
import one.chartsy.HLC;
import one.chartsy.data.Dataset;
import one.chartsy.simulation.reporting.EquityInformation;
import one.chartsy.trade.strategy.ReportOptions;
import one.chartsy.simulation.reporting.Report;
import one.chartsy.simulation.reporting.ReportEngine;

import java.util.Optional;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Accessors(fluent = true)
@AllArgsConstructor
@Getter
public class StandardReport implements Report {
    private final ReportOptions options;
    private final Optional<EquityInformation> equity;
    private final Optional<Dataset<HLC>> equityEvolution;

    public StandardReport(ReportEngine engine) {
        this(engine.getOptions(),
                engine.getEquity().map(EquityInformation.Builder::build),
                engine.getEquityEvolution().map(Dataset::toDirect));
    }
}
