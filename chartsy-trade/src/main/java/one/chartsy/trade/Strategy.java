package one.chartsy.trade;

import one.chartsy.When;
import one.chartsy.core.ThreadContext;
import one.chartsy.data.Series;
import one.chartsy.naming.SymbolIdentifier;
import one.chartsy.time.Chronological;
import one.chartsy.trade.data.Position;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public abstract class Strategy<E extends Chronological> implements TradingStrategy {
    /** The unique identifier of the strategy. */
    private final UUID uuid = UUID.randomUUID();
    /** The instance logger currently in use. */
    private final Logger log = LogManager.getLogger(getClass());
    /** The current strategy configuration. */
    protected final StrategyConfig config;
    /** The symbol assigned to the current Strategy. */
    protected final SymbolIdentifier symbol;
    /** The data series associated with the current strategy. */
    protected final Series<E> series;
    /** The type of primary data accepted by this strategy. */
    protected final Class<E> primaryDataType;


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
            this.symbol = config.symbol();
            this.series = selectPrimaryDataSeries(primaryDataType, config.dataSources());
        } else {
            this.symbol = null;
            this.series = null;
        }
    }

    @SuppressWarnings("unchecked")
    protected Series<E> selectPrimaryDataSeries(Class<E> primaryDataType, List<? extends Series<?>> dataSource) {
        for (Series<?> dataSeries : dataSource)
            if (primaryDataType.equals(dataSeries.getResource().dataType()))
                return (Series<E>) dataSeries;

        for (Series<?> dataSeries : dataSource)
            if (primaryDataType.isAssignableFrom(dataSeries.getResource().dataType()))
                return (Series<E>) dataSeries;

        return null;
    }

    @Override
    public void initTradingStrategy(TradingStrategyContext context) {
        log().info("Strategy {} configured", symbol.name());
    }

    @Override
    public void onTradingDayStart(LocalDate date) {
    }

    @Override
    public void onTradingDayEnd(LocalDate date) {
    }

    @Override
    public void exitOrders(When when) {

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

    public final Logger log() {
        return log;
    }

    public final UUID getUID() {
        return uuid;
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
}
