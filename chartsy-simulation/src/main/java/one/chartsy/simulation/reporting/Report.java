package one.chartsy.simulation.reporting;

import one.chartsy.HLC;
import one.chartsy.data.Dataset;
import one.chartsy.trade.strategy.ReportOptions;

import java.util.Optional;

public interface Report {

    ReportOptions options();

    Optional<EquityInformation> equity();

    Optional<Dataset<HLC>> equityEvolution();
}
