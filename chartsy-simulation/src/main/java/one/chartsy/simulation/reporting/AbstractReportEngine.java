package one.chartsy.simulation.reporting;

import one.chartsy.trade.strategy.ReportOptions;

public abstract class AbstractReportEngine implements ReportEngine {
    private final ReportOptions options;
    private volatile boolean finished;

    protected AbstractReportEngine(ReportOptions options) {
        this.options = options;
    }

    @Override
    public ReportOptions getOptions() {
        return options;
    }

    @Override
    public final boolean isFinished() {
        return finished;
    }

    @Override
    public void finish() {
        finished = true;
    }
}
