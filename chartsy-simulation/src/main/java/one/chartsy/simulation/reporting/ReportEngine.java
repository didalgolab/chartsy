package one.chartsy.simulation.reporting;

import one.chartsy.HLC;
import one.chartsy.data.Dataset;
import one.chartsy.simulation.incubator.ReportOptions;

import java.util.Optional;

public interface ReportEngine {

    ReportOptions getOptions();

    Optional<EquityInformation.Builder> getEquity();

    Optional<Dataset<HLC>> getEquityEvolution();

    boolean isFinished();

    void finish();

    Report createReport();
}
