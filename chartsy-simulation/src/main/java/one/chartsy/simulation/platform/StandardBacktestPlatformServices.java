package one.chartsy.simulation.platform;

import one.chartsy.simulation.incubator.ReportOptions;
import one.chartsy.simulation.reporting.ReportEngine;
import one.chartsy.trade.Account;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = BacktestPlatformServices.class)
public class StandardBacktestPlatformServices implements BacktestPlatformServices {

    @Override
    public ReportEngine createReportEngine(ReportOptions options, Account account) {
        return new StandardReportEngine(options, account);
    }
}
