/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.engine.account;

import one.chartsy.messaging.data.TradeBar;
import one.chartsy.SymbolIdentity;
import one.chartsy.simulation.reporting.EquitySummaryStatistics;
import one.chartsy.trade.account.AccountBalanceEntry;
import one.chartsy.trade.account.ProfitConversion;

import java.util.HashMap;
import java.util.Map;

public class SimulatorAccount implements Account {

    public static final double DEFAULT_ACCOUNT_BALANCE = 10_000.0;
    public static final double DEFAULT_ANNUAL_RISK_FREE_RATE = 0.0;

    private final String id;
    private final AccountBalanceEntry mainBalance;
    private final ProfitConversion profitConversion;
    private final Map<String, AccountBalanceEntry> secondaryBalances = new HashMap<>();
    private final EquitySummaryStatistics equityStatistics;

    public SimulatorAccount(String id) {
        this(id, DEFAULT_ACCOUNT_BALANCE);
    }

    public SimulatorAccount(String id, double initialBalance) {
        this(id, initialBalance, ProfitConversion.QUOTE_CURRENCY);
    }

    public SimulatorAccount(String id, double initialBalance, ProfitConversion profitConversion) {
        this.id = id;
        this.profitConversion = profitConversion;
        this.mainBalance = createBalance(null, initialBalance);
        this.equityStatistics = createEquityTracker(mainBalance, DEFAULT_ANNUAL_RISK_FREE_RATE);
    }

    protected AccountBalanceEntry createBalance(String currency, double initial) {
        return new AccountBalanceEntry(initial, profitConversion);
    }

    protected EquitySummaryStatistics createEquityTracker(AccountBalanceEntry startingBalance, double annualRiskFreeRate) {
        var currentEquity = startingBalance.getEquity();
        return EquitySummaryStatistics.forSignedEquity(currentEquity, annualRiskFreeRate);
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

    public double getRealizedPnL() {
        double total = mainBalance.getRealizedPnL();
        for (var balance : secondaryBalances.values()) total += balance.getRealizedPnL();
        return total;
    }

    public double getUnrealizedPnL() {
        double total = mainBalance.getUnrealizedPnL();
        for (var balance : secondaryBalances.values()) total += balance.getUnrealizedPnL();
        return total;
    }

    /** Mark every balance at the quote on which a new risk decision will be made. */
    public void onMarketPrice(SymbolIdentity symbol, double bid, double spread, long time) {
        onMarketQuote(symbol, bid, bid + spread, time);
    }

    /** Mark longs at bid and shorts at ask without reconstructing a spread. */
    public void onMarketQuote(SymbolIdentity symbol, double bid, double ask, long time) {
        updateBalance(mainBalance, symbol, bid, ask, time);
        for (var balance : secondaryBalances.values()) updateBalance(balance, symbol, bid, ask, time);
    }

    public void onTradeBar(TradeBar bar) {
        onTradeBar(bar, 0);
    }

    public void onTradeBar(TradeBar bar, double spread) {
        onMarketPrice(bar.symbol(), bar.get().close(), spread, bar.time());

        equityStatistics.add(getTotalEquity(), bar.time());
    }

    private void updateBalance(AccountBalanceEntry balance, SymbolIdentity symbol, double bid, double ask, long time) {
        var position = balance.getPosition(symbol);
        if (position != null)
            balance.updateMarketData(symbol, position.getDirection().isLong() ? bid : ask, time);
    }

    public final EquitySummaryStatistics getEquityStatistics() {
        return equityStatistics;
    }

    public final String getId() {
        return id;
    }
}
