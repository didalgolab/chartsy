package one.chartsy.simulation.platform;

import one.chartsy.simulation.incubator.ReportOptions;
import one.chartsy.simulation.reporting.ReportEngine;
import one.chartsy.trade.Account;

public class BacktestPlatformServicesAdapter implements BacktestPlatformServices {
    protected final BacktestPlatformServices target;

    public BacktestPlatformServicesAdapter(BacktestPlatformServices target) {
        this.target = target;
    }

    @Override
    public ReportEngine createReportEngine(ReportOptions options, Account account) {
        return target.createReportEngine(options, account);
    }
}
