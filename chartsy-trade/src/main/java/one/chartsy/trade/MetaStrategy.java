package one.chartsy.trade;

import one.chartsy.SymbolIdentity;
import one.chartsy.When;
import one.chartsy.data.Series;
import one.chartsy.naming.SymbolIdentifier;
import one.chartsy.time.Chronological;
import one.chartsy.trade.data.Position;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

@SuppressWarnings("ForLoopReplaceableByForEach")
public class MetaStrategy implements TradingStrategy {

    public static final Predicate<?> ACCEPT_ALL = __ -> true;

    @SuppressWarnings("unchecked")
    private Predicate<Series<?>> dataSeriesFilter = (Predicate<Series<?>>) ACCEPT_ALL;

    private Account account;

    private final ConcurrentMap<String, Object> sharedVariables = new ConcurrentHashMap<>();
    private final TradingStrategyProvider childStrategiesProviders;

    private final List<TradingStrategy> subStrategies = new ArrayList<>();
    private final Map<SymbolIdentity, Slot> symbols = new HashMap<>();
    private Slot[] instruments;

    private Lookup lookup = Lookup.EMPTY;

    protected Lookup createLookup(TradingStrategyContext context) {
        return new ProxyLookup(
                Lookups.fixed(account, sharedVariables, this),
                context.getLookup()
        );
    }

    protected void initSimulation(List<? extends Series<?>> dataSeries, Predicate<Series<?>> dataFilter, TradingStrategyProvider isp) {
        // clear object state
        this.symbols.clear();
        this.subStrategies.clear();
        this.sharedVariables.clear();
        this.instruments = new Slot[dataSeries.size()];

        // init instrument slots
        for (int i = 0; i < dataSeries.size(); i++) {
            Series<?> series = dataSeries.get(i);
            if (dataFilter.test(series)) {
                Slot slot = instruments[i] = symbols.computeIfAbsent(series.getResource().symbol(),
                        symb -> new Slot(new SymbolIdentifier(symb)));
                slot.addDataSeries(series);
            }
        }

        // init instrument strategies
        StrategyInitializer initializer = new StrategyInitializer();
        symbols.forEach((symbol, slot) -> {
            var config = new StrategyConfigData(slot.getSymbol(), slot.dataSeries, sharedVariables, Map.of(), account);
            var childStrategy = initializer.newInstance(isp, config);

            slot.setStrategy(childStrategy);
            subStrategies.add(childStrategy);
        });
    }

    public InstrumentState getInstrument(When when) {
        return getInstrument(when.getId());
    }

    public InstrumentState getInstrument(int datasetId) {
        return instruments[datasetId];
    }

    public int totalSymbolCount() {
        return symbols.size();
    }

    public int activeSymbolCountSince(long lastTradeTime) {
        int count = 0;
        for (InstrumentState instrument : symbols.values())
            if (instrument.isActiveSince(lastTradeTime))
                count++;

        return count;
    }


    private static final class Slot extends InstrumentState {
        private final List<Series<?>> dataSeries = new LinkedList<>();
        private TradingStrategy strategy;

        public Slot(SymbolIdentifier symbol) {
            super(symbol);
        }

        public void addDataSeries(Series<?> s) {
            dataSeries.add(s);
        }

        public List<Series<?>> getDataSeries() {
            return dataSeries;
        }

        public TradingStrategy getStrategy() {
            return strategy;
        }

        void setStrategy(TradingStrategy strategy) {
            if (this.strategy != null)
                throw new IllegalStateException("InstrumentSlots' strategy already initialized");
            this.strategy = strategy;
        }
    }

    public MetaStrategy(TradingStrategyProvider childStrategies) {
        this.childStrategiesProviders = Objects.requireNonNull(childStrategies);
    }

    @Override
    public void initTradingStrategy(TradingStrategyContext context) {
        this.account = context.tradingService().getAccounts().get(0);
        initSimulation(context.dataSeries(), dataSeriesFilter, childStrategiesProviders);
        forEachStrategy(strategy -> strategy.initTradingStrategy(context));
    }

    public void forEachStrategy(Consumer<TradingStrategy> action) {
        symbols.values().forEach(slot -> action.accept(slot.getStrategy()));
    }

    protected TradingStrategy getTargetStrategy(When when) {
        int index = when.getId();
        return (index >= 0 && index < instruments.length)? instruments[index].getStrategy() : null;
    }

    protected TradingStrategy getTargetStrategy(SymbolIdentity symbol) {
        Slot slot = symbols.get(new SymbolIdentifier(symbol));
        return (slot == null)? null : slot.getStrategy();
    }

    public Predicate<Series<?>> getDataSeriesFilter() {
        return dataSeriesFilter;
    }

    public void setDataSeriesFilter(Predicate<Series<?>> dataSeriesFilter) {
        this.dataSeriesFilter = dataSeriesFilter;
    }

    @Override
    public void onData(When when, Chronological next, boolean timeTick) {
        List<TradingStrategy> strategies = this.subStrategies;
        for (int i = 0, count = strategies.size(); i < count; i++)
            strategies.get(i).onData(when, next, timeTick);
    }

    @Override
    public void onExecution(Execution execution, Order order) {
        TradingStrategy.super.onExecution(execution, order);
    }

    @Override
    public void onTradingDayStart(LocalDate date) {
        subStrategies.forEach(strategy -> strategy.onTradingDayStart(date));
    }

    @Override
    public void onTradingDayEnd(LocalDate date) {
        subStrategies.forEach(strategy -> strategy.onTradingDayEnd(date));
    }

    @Override
    public void onExitManagement(When when) {
        getTargetStrategy(when).onExitManagement(when);
    }

    @Override
    public void exitOrders(When when, Position position) {
        getTargetStrategy(when).exitOrders(when, position);
    }

    @Override
    public void entryOrders(When when, Chronological data) {
        getTargetStrategy(when).entryOrders(when, data);
    }

    @Override
    public void adjustRisk(When when) {
        getTargetStrategy(when).adjustRisk(when);
    }

    @Override
    public void entryOrderFilled(Order order, Execution execution) {
        getTargetStrategy(execution.getSymbol()).entryOrderFilled(order, execution);
    }

    @Override
    public void exitOrderFilled(Order order, Execution execution) {
        getTargetStrategy(execution.getSymbol()).exitOrderFilled(order, execution);
    }
}
