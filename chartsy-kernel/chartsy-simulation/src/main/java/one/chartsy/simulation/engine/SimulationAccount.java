/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.simulation.engine;

import one.chartsy.*;
import one.chartsy.core.event.ListenerList;
import one.chartsy.financial.SymbolIdentifier;
import one.chartsy.trade.event.LegacyPositionValueChangeListener;
import one.chartsy.trade.strategy.SimulatorOptions;
import one.chartsy.trade.Account;
import one.chartsy.trade.Execution;
import one.chartsy.trade.Order;
import one.chartsy.trade.data.Position;
import one.chartsy.trade.data.TransactionData;
import one.chartsy.trade.event.PositionChangeListener;

import java.util.*;

public class SimulationAccount implements Account {
    /** The strategy properties currently in use. */
    private final SimulatorOptions properties;
    /** The initial account balance. */
    private double initialBalance;
    /** The current account balance. */
    private double balance;
    /** The current profit of the account. */
    private double profit;
    /** The account credit. */
    private double credit;
    /** The collection of traded instruments. */
    private final Map<SymbolIdentifier, SimulationInstrument> instruments = new HashMap<>();
    /** The collection of traded instruments hashed by the symbol identity. */
    private final Map<SymbolIdentity, SimulationInstrument> lookupCache = new IdentityHashMap<>() {

        private final LinkedList<SymbolIdentity> currentKeys = new LinkedList<>();

        @Override
        public SimulationInstrument put(SymbolIdentity key, SimulationInstrument value) {
            SimulationInstrument previousValue = super.put(key, value);
            if (previousValue == null) {
                currentKeys.add(key);
                if (currentKeys.size() >= instruments.size()*4 + 4) {
                    SymbolIdentity firstKey = currentKeys.removeFirst();
                    if (firstKey != key)
                        remove(firstKey);
                }
            }
            return previousValue;
        }
    };

    public SimulationAccount(SimulatorOptions properties) {
        this.properties = properties;
        this.balance = this.initialBalance = properties.initialBalance();
    }

    @Override
    public String getId() {
        return "";
    }

    @Override
    public double getInitialBalance() {
        return initialBalance;
    }

    public double getBalance() {
        return balance;
    }

    public double getCredit() {
        return credit;
    }

    public double getProfit() {
        return profit;
    }

    @Override
    public double getEquity() {
        return getBalance() + getCredit() + getProfit();
    }

    @Override
    public SimulationInstrument getInstrument(SymbolIdentity symbol) {
        SimulationInstrument instrument = lookupCache.get(symbol);
        if (instrument == null) {
            synchronized (lookupCache) {
                if ((instrument = lookupCache.get(symbol)) == null)
                    lookupCache.put(symbol, instrument = getInstrument(new SymbolIdentifier(symbol)));
            }
        }
        return instrument;
    }

    public SimulationInstrument getInstrument(SymbolIdentifier symbol) {
        SimulationInstrument instrument = instruments.get(symbol);
        if (instrument == null) {
            synchronized (instruments) {
                instrument = instruments.computeIfAbsent(symbol, this::createInstrument);
            }
        }
        return instrument;
    }

    protected SimulationInstrument createInstrument(SymbolIdentifier symbol) {
        return new SimulationInstrument(new Symbol(symbol));
    }

    @Override
    public Map<SymbolIdentity, List<Order>> getPendingOrders() {
        Map<SymbolIdentity, List<Order>> pendingOrders = new HashMap<>();
        instruments.forEach((key, value) -> {
            if (!value.orders().isEmpty())
                pendingOrders.put(key, new ArrayList<>(value.orders()));
        });

        return pendingOrders;
    }

    /**
     * Returns list of pending orders for the specified symbols or empty list if
     * no pending orders exist for the given symbol.
     *
     * @return the list of pending orders for the symbol
     */
    public List<Order> getOrders(SymbolIdentity symbol) {
        return getInstrument(symbol).orders();
    }

    private long transactionId;

    @Override
    public void exitPosition(Position position, Execution execution) {
        profit -= position.getProfit();
        double currPrice = execution.getPrice();
        long currTime = execution.time();
        position.updateProfit(currPrice, currTime);
        //TODO
        //balance = balance.withAmount(balance.getAmount() + position.getProfit() - position.getExtraCommission() - closingCommission);
        balance += position.getProfit() - position.getExtraCommission() - execution.getClosingCommission();

        TransactionData transaction = new TransactionData(++transactionId,
                position,
                currPrice,
                currTime,
                position.getCommission() + position.getExtraCommission() + execution.getClosingCommission()
        );
        getInstrument(position.getSymbol()).setPosition(null);
        firePositionClosed(position, transaction);
    }

    @Override
    public void enterPosition(Position position, double currPrice, long currTime) {
        SymbolIdentity symbol = position.getSymbol();
        getInstrument(symbol).setPosition(position);
        firePositionOpened(position);
        profit += position.updateProfit(currPrice, currTime);
    }

    @Override
    public void updateProfit(SymbolIdentity symbol, Candle ohlc) {
        Position position = getInstrument(symbol).position();
        if (position != null) {
            profit += position.updateProfit(ohlc.close(), ohlc.time());

            if (!positionValueChangeListeners.isEmpty())
                firePositionValueChanged(this, position);
        }
    }

    private void firePositionValueChanged(Account account, Position position) {
        positionValueChangeListeners.fire().positionValueChanged(account, position);
    }

    private void firePositionOpened(Position position) {
        if (!positionChangeListeners.isEmpty())
            positionChangeListeners.fire().positionOpened(position);
    }

    private void firePositionClosed(Position position, TransactionData transaction) {
        if (!positionChangeListeners.isEmpty())
            positionChangeListeners.fire().positionClosed(position, transaction);
    }

    /** The list of registered position change listeners. */
    private final ListenerList<PositionChangeListener> positionChangeListeners = ListenerList.of(PositionChangeListener.class);
    /** The list of registered position value change listeners. */
    private final ListenerList<LegacyPositionValueChangeListener> positionValueChangeListeners = ListenerList.of(LegacyPositionValueChangeListener.class);

    @Override
    public void addPositionChangeListener(PositionChangeListener listener) {
        positionChangeListeners.addListener(listener);
    }

    @Override
    public void removePositionChangeListener(PositionChangeListener listener) {
        positionChangeListeners.removeListener(listener);
    }

    @Override
    public void addPositionValueChangeListener(LegacyPositionValueChangeListener listener) {
        positionValueChangeListeners.addListener(listener);
    }

    @Override
    public void removePositionValueChangeListener(LegacyPositionValueChangeListener listener) {
        positionValueChangeListeners.removeListener(listener);
    }
}
