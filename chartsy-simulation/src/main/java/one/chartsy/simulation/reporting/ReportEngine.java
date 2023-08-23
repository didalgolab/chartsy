/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.simulation.reporting;

import one.chartsy.HLC;
import one.chartsy.data.Dataset;
import one.chartsy.trade.strategy.ReportOptions;

import java.util.Optional;

public interface ReportEngine {

    ReportOptions getOptions();

    Optional<EquityInformation.Builder> getEquity();

    Optional<Dataset<HLC>> getEquityEvolution();

    boolean isFinished();

    void finish();

    Report createReport();
}
