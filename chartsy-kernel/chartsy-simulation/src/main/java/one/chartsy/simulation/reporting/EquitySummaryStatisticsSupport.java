/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.reporting;

import one.chartsy.trade.account.AccountBalanceEntry;

/**
 * A helper class for {@link EquitySummaryStatistics}.
 */
public final class EquitySummaryStatisticsSupport {
    // Utility class; prevent instantiation
    private EquitySummaryStatisticsSupport() { }

    /**
     * Creates a new {@link EquitySummaryStatistics} object wired to the given {@link AccountBalanceEntry}.
     *
     * @param balanceEntry the account balance entry to track
     * @return a newly created and wired {@link EquitySummaryStatistics} instance
     */
    public static EquitySummaryStatistics createTracker(AccountBalanceEntry balanceEntry) {
        return createTracker(balanceEntry, 0.0);
    }

    /**
     * Creates a new {@link EquitySummaryStatistics} object using the specified annual risk-free rate
     * wired to the given {@link AccountBalanceEntry} so that it monitors the evolution of equity
     * over time and gathers statistics useful for performance evaluation (for instance, computing
     * the Sharpe ratio).
     *
     * @param balanceEntry the account balance entry to track
     * @param annualRiskFreeRate the annual risk-free rate used for Sharpe ratio calculation
     * @return a newly created and wired {@link EquitySummaryStatistics} instance
     */
    public static EquitySummaryStatistics createTracker(AccountBalanceEntry balanceEntry, double annualRiskFreeRate) {
        var currentEquity = balanceEntry.getEquity();
        var stats = new EquitySummaryStatistics(currentEquity, annualRiskFreeRate);

        balanceEntry.addPositionValueChangeListener(position -> {
            stats.add(position.getBalanceEntry().getEquity(), position.getMarketTime());
        });
        return stats;
    }
}
