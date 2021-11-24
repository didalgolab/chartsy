package one.chartsy.trade;

import one.chartsy.When;
import one.chartsy.collections.ImmutableCollections;
import one.chartsy.core.ThreadContext;
import one.chartsy.data.Series;
import one.chartsy.naming.SymbolIdentifier;
import one.chartsy.time.Chronological;
import one.chartsy.trade.data.Position;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openide.util.Lookup;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public abstract class Strategy<E extends Chronological> implements TradingStrategy {
    /** The unique identifier of the strategy. */
    private final UUID strategyUUID = UUID.randomUUID();
    /** The external lookup associated with the strategy. */
    private final Lookup lookup;
    /** The instance logger currently in use. */
    private final Logger log = LogManager.getLogger(getClass());
    /** The current strategy configuration. */
    protected final StrategyConfig config;
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

    protected TradingStrategyContext tradingStrategyContext;


    protected Strategy() {
        this(findConfig(), null);
    }

    protected Strategy(Class<E> primaryDataType) {
        this(findConfig(), primaryDataType);
    }

    protected Strategy(StrategyConfig config) {
        this(config, null);
    }

    @SuppressWarnings("unchecked")
    protected Strategy(StrategyConfig config, Class<E> primaryDataType) {
        if (primaryDataType == null)
            primaryDataType = (Class<E>) StrategyInitializer.probeDataType((Class<? extends Strategy<?>>) getClass());
        this.primaryDataType = primaryDataType;

        this.config = config;
        if (config != null) {
            this.lookup = config.lookup();
            this.globalVariables = config.sharedVariables();
            this.symbol = config.symbol();
            this.series = getPrimaryDataSeries(primaryDataType, config.dataSources());
            this.alternateSeries = config.dataSources();
            this.account = config.account();
        } else {
            this.lookup = Lookup.EMPTY;
            this.globalVariables = ImmutableCollections.emptyConcurrentMap();
            this.symbol = null;
            this.series = null;
            this.alternateSeries = List.of();
            this.account = null;
        }
    }

    @SuppressWarnings("unchecked")
    protected Series<E> getPrimaryDataSeries(Class<E> primaryDataType, List<? extends Series<?>> dataSource) {
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

    @Override
    public void initTradingStrategy(TradingStrategyContext context) {
        tradingStrategyContext = context;
        log().info("Strategy {} configured", symbol.name());
    }

    @Override
    public void onAfterInit() { }

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
    public void onTradingDayStart(LocalDate date) {
    }

    @Override
    public void onTradingDayEnd(LocalDate date) {
    }

    @Override
    public void onExitManagement(When when) {

    }

    @Override
    public void exitOrders(When when, Position position) {

    }

    @Override
    public void entryOrders(When when, Chronological data) {

    }

    @Override
    public void adjustRisk(When when) {

    }

    @Override
    public void entryOrderFilled(Order order, Execution execution) {

    }

    @Override
    public void exitOrderFilled(Order order, Execution execution) {

    }

    public boolean isLongOnMarket() {
        return account.isLongOnMarket(symbol);
    }

    public boolean isShortOnMarket() {
        return account.isShortOnMarket(symbol);
    }

    public boolean isOnMarket() {
        return account.isOnMarket(symbol);
    }

    public final Logger log() {
        return log;
    }

    public final UUID getStrategyUUID() {
        return strategyUUID;
    }

    private static StrategyConfig findConfig() {
        try {
            var config = ThreadContext.current().getVars().get("config");
            if (config == null || config instanceof StrategyConfig)
                return (StrategyConfig) config;
            else
                throw new TradingException("Valid StrategyConfig not found, found instead: " + config.getClass().getSimpleName());
        } catch (ThreadContext.NotFoundException e) {
            return null;
        }
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
        return tradingStrategyContext.tradingService().getOrderBroker().submitOrder(order);
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
