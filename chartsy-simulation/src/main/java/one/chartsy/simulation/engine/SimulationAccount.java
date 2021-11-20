package one.chartsy.simulation.engine;

import one.chartsy.*;
import one.chartsy.commons.event.ListenerList;
import one.chartsy.naming.SymbolIdentifier;
import one.chartsy.simulation.SimulationProperties;
import one.chartsy.trade.Account;
import one.chartsy.trade.Execution;
import one.chartsy.trade.Order;
import one.chartsy.trade.data.Position;
import one.chartsy.trade.data.TransactionData;
import one.chartsy.trade.event.PositionChangeListener;

import java.util.*;

public class SimulationAccount implements Account {
    /** The strategy properties currently in use. */
    private final SimulationProperties properties;
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

    public SimulationAccount(SimulationProperties properties) {
        this.properties = properties;
        this.balance = properties.getInitialBalance();
    }

    @Override
    public String getId() {
        return "";
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
        long currTime = execution.getTime();
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
        if (position != null)
            profit += position.updateProfit(ohlc.close(), ohlc.getTime());
        // TODO
        //if (!equityChangeListeners.isEmpty())
        //    equityChangeListeners.fire().equityChanged(this, bar.time);
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

    @Override
    public void addPositionChangeListener(PositionChangeListener listener) {
        positionChangeListeners.addListener(listener);
    }

    @Override
    public void removePositionChangeListener(PositionChangeListener listener) {
        positionChangeListeners.removeListener(listener);
    }
}
