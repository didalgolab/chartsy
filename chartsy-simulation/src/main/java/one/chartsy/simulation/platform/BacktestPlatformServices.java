package one.chartsy.simulation.platform;

import one.chartsy.trade.strategy.ReportOptions;
import one.chartsy.simulation.reporting.ReportEngine;
import one.chartsy.trade.Account;

public interface BacktestPlatformServices {

    ReportEngine createReportEngine(ReportOptions options, Account account);
}
