package one.chartsy.simulation.platform;

import one.chartsy.simulation.incubator.ReportOptions;
import one.chartsy.simulation.reporting.ReportEngine;
import one.chartsy.trade.Account;

public interface BacktestPlatformServices {

    ReportEngine createReportEngine(ReportOptions options, Account account);
}
