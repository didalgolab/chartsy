/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.strategy;

import one.chartsy.SymbolIdentity;
import one.chartsy.core.LaunchableTarget;
import one.chartsy.base.ThreadContext;
import one.chartsy.data.Series;
import one.chartsy.financial.SymbolIdentifier;
import one.chartsy.time.Chronological;
import one.chartsy.trade.*;
import one.chartsy.trade.data.Position;
import org.openide.util.Lookup;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public abstract class Strategy<E extends Chronological> extends HierarchicalTradingAlgorithm implements TradingAlgorithm, LaunchableTarget<Object> {
    /** The external lookup associated with the strategy. */
    private final Lookup lookup;
    /** The variables shared between all strategies created under the same meta-strategy. */
    private final ConcurrentMap<String, Object> globalVariables;
    /** The symbol assigned to the current Strategy. */
    protected final SymbolIdentifier symbol;
    /** The account associated with the Strategy. */
    protected final Account account;
    /** The data series associated with the current strategy. */
    protected final Series<E> series;
    /** The list of all series associated with the strategy. */
    protected final List<? extends Series<?>> alternateSeries;
    /** The type of primary data accepted by this strategy. */
    protected final Class<E> primaryDataType;


    protected Strategy() {
        this(findConfig(), null);
    }

    protected Strategy(Class<E> primaryDataType) {
        this(findConfig(), primaryDataType);
    }

    protected Strategy(TradingAlgorithmContext context) {
        this(context, null);
    }

    private static TradingAlgorithmContext findConfig() {
        try {
            ThreadContext config = ThreadContext.current();
            if (config.getVars().get("context") instanceof TradingAlgorithmContext context)
                return context;
            else
                throw new TradingException("Current context not available, found " + config.getVars().keySet() + " instead");

        } catch (ThreadContext.NotFoundException e) {
            return new HostTradingAlgorithmContext();
        }
    }

    @SuppressWarnings("unchecked")
    protected Strategy(TradingAlgorithmContext context, Class<E> primaryDataType) {
        super(context);
        if (primaryDataType == null)
            primaryDataType = (Class<E>) StrategyUtils.probeDataType((Class<? extends Strategy<?>>) getClass());
        this.primaryDataType = primaryDataType;

        this.lookup = context.getLookup();
        this.globalVariables = context.sharedVariables();
        var partitionKey = context.partitionKey().orElse(null);
        this.symbol = (partitionKey instanceof SymbolIdentity symb)? new SymbolIdentifier(symb): null;
        this.series = getPrimaryDataSeries(primaryDataType, context.partitionSeries().values());
        this.alternateSeries = List.copyOf(context.partitionSeries().values());
        var mainAccount = lookup.lookup(Account.class);
        if (mainAccount != null)
            this.account = mainAccount;
        else {
            List<Account> accounts = context.tradingService().getAccounts();
            this.account = accounts.isEmpty()? null : accounts.get(0);
        }
    }

    @SuppressWarnings("unchecked")
    protected Series<E> getPrimaryDataSeries(Class<E> primaryDataType, Collection<? extends Series<?>> dataSource) {
        for (Series<?> dataSeries : dataSource)
            if (primaryDataType.equals(dataSeries.getResource().dataType()))
                return (Series<E>) dataSeries;

        for (Series<?> dataSeries : dataSource)
            if (primaryDataType.isAssignableFrom(dataSeries.getResource().dataType()))
                return (Series<E>) dataSeries;

        return null;
    }

    public Class<E> getPrimaryDataType() {
        return primaryDataType;
    }

    public ConcurrentMap<String, Object> globalVariables() {
        return globalVariables;
    }

    public <T> T globalVariable(String name, Supplier<T> initialValue) {
        @SuppressWarnings("unchecked")
        T value = (T)globalVariables().computeIfAbsent(name, __ -> Objects.requireNonNull(initialValue.get()));
        return value;
    }

    public Lookup lookup() {
        return lookup;
    }

    public <T> T lookup(Class<T> clazz) {
        return lookup().lookup(clazz);
    }

    @Override
    public void onExecution(Execution execution) {
        if (execution.getOrder().isEntry())
            entryOrderFilled(execution.getOrder());
        else
            exitOrderFilled(execution.getOrder());
    }

    public void entryOrderFilled(Order order) { }

    public void exitOrderFilled(Order order) { }

    public boolean isLongOnMarket() {
        return account.isLongOnMarket(symbol);
    }

    public boolean isShortOnMarket() {
        return account.isShortOnMarket(symbol);
    }

    public boolean isOnMarket() {
        return account.isOnMarket(symbol);
    }

    public SymbolIdentifier getSymbol() {
        return symbol;
    }

    public double orderUnitSize() {
        return 1.0;
    }

    public interface BrokerContext {
        Account getAccount();
        TradingService getTradingService();
    }

    public Order submitOrder(Order order) {
        return context.tradingService().getOrderBroker().submitOrder(context, order);
    }

    public Order buy() {
        return buy(orderUnitSize());
    }

    public Order buy(double quantity) {
        return submitOrder(new Order(symbol, OrderType.MARKET, Order.Side.BUY, quantity));
    }

    /**
     * Submits a new order; or resets existing position exit stop and clears exit
     * limit.
     *
     * @param quantity
     *            the order quantity
     * @param exitStop
     *            the order exit stop (stop loss) price
     * @return the submitted order (optionally)
     * @see #buyOrResetExits(double, double, double)
     */
    public Optional<Order> buyOrResetExits(double quantity, double exitStop) {
        return buyOrResetExits(quantity, exitStop, Double.NaN);
    }

    /**
     * Submits a new order or resets existing position exit stop and limit.
     * <p>
     * The method is equivalent of {@link #submitOrder(Order)} if there is currently
     * no long position open on the market. Otherwise an existing position's exit
     * stop and limit are updated to the given prices.
     *
     * @param quantity
     *            the order quantity
     * @param exitStop
     *            the order exit stop (stop loss) price
     * @param exitLimit
     *            the order exit limit (take profit) price
     * @return the submitted order (optionally)
     */
    public Optional<Order> buyOrResetExits(double quantity, double exitStop, double exitLimit) {
        Position position = account.getPosition(symbol);
        if (!isLongOnMarket()) {
            Order order = new Order(symbol, OrderType.MARKET, Order.Side.BUY, TimeInForce.OPEN);
            order.setExitStop(exitStop);
            order.setExitLimit(exitLimit);
            order.setQuantity(quantity);

            return Optional.of(submitOrder(order));
        } else {
            position.getEntryOrder().setExitStop(exitStop);
            position.getEntryOrder().setExitLimit(exitLimit);

            return Optional.empty();
        }
    }

    public Order buyToCover() {
        return buyToCover(orderUnitSize());
    }

    public Order buyToCover(double volume) {
        return submitOrder(new Order(symbol, OrderType.MARKET, Order.Side.BUY_TO_COVER, volume));
    }

    public Order sell() {
        return sell(orderUnitSize());
    }

    public Order sell(double volume) {
        return submitOrder(new Order(symbol, OrderType.MARKET, Order.Side.SELL, volume));
    }

    public Order sellShort() {
        return sellShort(orderUnitSize());
    }

    public Order sellShort(double volume) {
        return submitOrder(new Order(symbol, OrderType.MARKET, Order.Side.SELL_SHORT, volume));
    }

    /**
     * Submits a new order; or resets existing position exit stop and clears exit
     * limit.
     *
     * @param quantity
     *            the order quantity
     * @param exitStop
     *            the order exit stop (stop loss) price
     * @return the submitted order (optionally)
     * @see #sellShortOrResetExits(double, double, double)
     */
    public Optional<Order> sellShortOrResetExits(double quantity, double exitStop) {
        return sellShortOrResetExits(quantity, exitStop, Double.NaN);
    }

    /**
     * Submits a new order or resets existing position exit stop and limit.
     * <p>
     * The method is equivalent of {@link #submitOrder(Order)} if there is currently
     * no short position open on the market. Otherwise an existing position's exit
     * stop and limit are updated to the given prices.
     *
     * @param quantity
     *            the order quantity
     * @param exitStop
     *            the order exit stop (stop loss) price
     * @param exitLimit
     *            the order exit limit (take profit) price
     * @return the submitted order (optionally)
     */
    public Optional<Order> sellShortOrResetExits(double quantity, double exitStop, double exitLimit) {
        Position position = account.getPosition(symbol);
        if (!isShortOnMarket()) {
            Order order = new Order(symbol, OrderType.MARKET, Order.Side.SELL_SHORT, TimeInForce.OPEN);
            order.setExitStop(exitStop);
            order.setExitLimit(exitLimit);
            order.setQuantity(quantity);

            return Optional.of(submitOrder(order));
        } else {
            position.getEntryOrder().setExitStop(exitStop);
            position.getEntryOrder().setExitLimit(exitLimit);

            return Optional.empty();
        }
    }
}
