/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.simulation.platform;

import one.chartsy.HLC;
import one.chartsy.base.Dataset;
import one.chartsy.data.packed.ByteBufferMutableHLCDataset;
import one.chartsy.simulation.reporting.AbstractReportEngine;
import one.chartsy.simulation.reporting.EquityInformation;
import one.chartsy.simulation.reporting.Report;
import one.chartsy.trade.BalanceState;
import one.chartsy.trade.strategy.ReportOptions;
import one.chartsy.trade.Account;
import one.chartsy.trade.data.Position;
import one.chartsy.trade.event.PositionValueChangeListener;

import java.util.Optional;

public class StandardReportEngine extends AbstractReportEngine {
    private final Account account;
    private final Handler handler;
    protected volatile Report lastReport;
    protected EquityInformation.Builder equity;
    protected ByteBufferMutableHLCDataset equityEvolution;


    public StandardReportEngine(ReportOptions options, Account account) {
        super(options);
        this.account = account;
        this.handler = new Handler(options, account);
        installListeners(options);
    }

    @Override
    public Optional<EquityInformation.Builder> getEquity() {
        return Optional.ofNullable(equity);
    }

    @Override
    public Optional<Dataset<HLC>> getEquityEvolution() {
        return Optional.ofNullable(equityEvolution);
    }

    protected void installListeners(ReportOptions options) {
        var enabledOpts = options.getEnabled();
        if (enabledOpts.contains(ReportOptions.EQUITY))
            account.addPositionValueChangeListener(equity = EquityInformation.builder(account));

        if (enabledOpts.contains(ReportOptions.EQUITY_CHART)) {
            equityEvolution = new ByteBufferMutableHLCDataset();
            account.addPositionValueChangeListener((account, position) -> {
                equityEvolution.add(position.getMarketTime(), account.getEquity());
            });
        }
    }

    protected void uninstallListeners() {
        account.removePositionValueChangeListener(handler);
    }

    @Override
    public void finish() {
        super.finish();
        uninstallListeners();
    }

    @Override
    public Report createReport() {
        var finished = isFinished();
        var report = lastReport;
        if (report == null || !finished) {
            report = generateReport(getOptions());
            if (finished)
                this.lastReport = report;
        }
        return report;
    }

    protected Report generateReport(ReportOptions options) {
        return new StandardReport(this);
    }

    private class Handler implements PositionValueChangeListener {
        private EquityInformation.Builder equitySummary;
        private ByteBufferMutableHLCDataset equityEvolution;

        private Handler(ReportOptions options, BalanceState initial) {
            var enabledOpts = options.getEnabled();
            if (enabledOpts.contains(ReportOptions.EQUITY))
                equitySummary = EquityInformation.builder(initial);
            if (enabledOpts.contains(ReportOptions.EQUITY_CHART))
                equityEvolution = new ByteBufferMutableHLCDataset();
        }

        @Override
        public void positionValueChanged(Account account, Position position) {
            var equity = account.getEquity();
            var marketTime = position.getMarketTime();
            if (equitySummary != null)
                equitySummary.add(marketTime, equity);
            if (equityEvolution != null)
                equityEvolution.add(marketTime, equity);

        }
    }
}
