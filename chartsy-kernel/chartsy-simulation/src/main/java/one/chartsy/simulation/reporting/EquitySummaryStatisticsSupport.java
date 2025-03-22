/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.reporting;

import one.chartsy.time.DateChangeSignal;
import one.chartsy.trade.account.AccountBalanceEntry;

import java.util.Collection;

/**
 * A helper class for {@link EquitySummaryStatistics}.
 */
public final class EquitySummaryStatisticsSupport {

    private EquitySummaryStatisticsSupport() { }

    public static EquitySummaryStatistics createTracker(AccountBalanceEntry balanceEntry) {
        return createTracker(balanceEntry, 0.0);
    }

    public static EquitySummaryStatistics createTracker(AccountBalanceEntry balanceEntry, double annualRiskFreeRate) {
        var currentEquity = balanceEntry.getEquity();
        var stats = new EquitySummaryStatistics(currentEquity, annualRiskFreeRate);

        balanceEntry.addPositionValueChangeListener(position -> {
            stats.add(position.getBalanceEntry().getEquity(), position.getMarketTime());
        });
        return stats;
    }

    public static EquitySummaryStatistics createCombinedTracker(Collection<AccountBalanceEntry> accounts) {
        return createCombinedTracker(accounts, 0.0);
    }

    public static EquitySummaryStatistics createCombinedTracker(Collection<AccountBalanceEntry> accounts, double annualRiskFreeRate) {
        var accountArray = accounts.toArray(AccountBalanceEntry[]::new);
        var stats = new EquitySummaryStatistics(getTotalEquity(accountArray), annualRiskFreeRate);
        var dateChange = DateChangeSignal.create();
        for (AccountBalanceEntry account : accountArray) {
            account.addPositionValueChangeListener(position -> {
                if (dateChange.poll(position::getMarketTime))
                    stats.add(getTotalEquity(accountArray), position.getMarketTime());
            });
        }
        return stats;
    }

    private static double getTotalEquity(AccountBalanceEntry[] accountBalances) {
        double totalEquity = 0.0;
        for (AccountBalanceEntry account : accountBalances) {
            totalEquity += account.getEquity();
        }
        return totalEquity;
    }
}
