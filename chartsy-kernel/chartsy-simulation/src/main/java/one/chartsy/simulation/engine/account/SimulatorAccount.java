/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.engine.account;

import one.chartsy.messaging.data.TradeBar;
import one.chartsy.simulation.reporting.EquitySummaryStatistics;
import one.chartsy.trade.account.AccountBalanceEntry;

import java.util.HashMap;
import java.util.Map;

public class SimulatorAccount implements Account {

    public static final double DEFAULT_ACCOUNT_BALANCE = 10_000.0;
    public static final double DEFAULT_ANNUAL_RISK_FREE_RATE = 0.0;

    private final String id;
    private final AccountBalanceEntry mainBalance;
    private final Map<String, AccountBalanceEntry> secondaryBalances = new HashMap<>();
    private final EquitySummaryStatistics equityStatistics;

    public SimulatorAccount(String id) {
        this.id = id;
        this.mainBalance = createBalance(null, DEFAULT_ACCOUNT_BALANCE);
        this.equityStatistics = createEquityTracker(mainBalance, DEFAULT_ANNUAL_RISK_FREE_RATE);
    }

    protected AccountBalanceEntry createBalance(String currency, double initial) {
        return new AccountBalanceEntry(initial);
    }

    protected EquitySummaryStatistics createEquityTracker(AccountBalanceEntry startingBalance, double annualRiskFreeRate) {
        var currentEquity = startingBalance.getEquity();
        return new EquitySummaryStatistics(currentEquity, annualRiskFreeRate);
    }

    public AccountBalanceEntry getOrCreateNewBalance(String currency) {
        if (currency == null || currency.isEmpty()) {
            return mainBalance;
        }
        return secondaryBalances.computeIfAbsent(currency, cur -> createBalance(cur, 0.0));
    }

    public double getTotalEquity() {
        double totalEquity = mainBalance.getEquity();
        for (AccountBalanceEntry balance : secondaryBalances.values()) {
            totalEquity += balance.getEquity();
        }
        return totalEquity;
    }

    public void onTradeBar(TradeBar bar) {
        mainBalance.onBarEvent(bar);
        if (!secondaryBalances.isEmpty())
            secondaryBalances.values().forEach(balance -> balance.onBarEvent(bar));

        equityStatistics.add(getTotalEquity(), bar.getTime());
    }

    public final EquitySummaryStatistics getEquityStatistics() {
        return equityStatistics;
    }

    public final String getId() {
        return id;
    }
}
