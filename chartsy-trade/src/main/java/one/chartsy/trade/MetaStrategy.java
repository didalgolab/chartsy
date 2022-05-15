/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade;

import one.chartsy.SymbolIdentity;
import one.chartsy.When;
import one.chartsy.data.Series;
import one.chartsy.naming.SymbolIdentifier;
import one.chartsy.time.Chronological;
import one.chartsy.trade.data.Position;
import one.chartsy.trade.strategy.*;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ProxyLookup;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

@SuppressWarnings("ForLoopReplaceableByForEach")
public class MetaStrategy implements TradingAlgorithm {

    public static final Predicate<?> ACCEPT_ALL = __ -> true;

    @SuppressWarnings("unchecked")
    private Predicate<Series<?>> dataSeriesFilter = (Predicate<Series<?>>) ACCEPT_ALL;

    private Account account;

    private final ConcurrentMap<String, Object> sharedVariables = new ConcurrentHashMap<>();
    private final TradingAlgorithmFactory childStrategiesProviders;

    private final List<TradingAlgorithm> subStrategies = new ArrayList<>();
    private final Map<SymbolIdentity, Slot> symbols = new LinkedHashMap<>();
    private Slot[] instruments;

    private Lookup lookup = Lookup.EMPTY;

    protected Lookup createLookup(TradingAlgorithmContext runtime) {
        return new ProxyLookup(
                Lookups.fixed(account, sharedVariables, this),
                runtime.getLookup()
        );
    }

    protected void initSimulation(TradingAlgorithmContext context, List<? extends Series<?>> dataSeries, Predicate<Series<?>> dataFilter, TradingAlgorithmFactory isp) {
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
                        symb -> new Slot(new SymbolIdentifier(symb), account.getInstrument(symb)));
                slot.addDataSeries(series);
            }
        }

        // init instrument strategies
        StrategyInstantiator initializer = new StrategyInstantiator(context);
        symbols.forEach((symbol, slot) -> {
            var config = new StrategyConfigData(lookup, slot.getSymbol(), slot.dataSeries, sharedVariables, Map.of(), account);
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

    public int activeSymbolCount() {
        int count = 0;
        for (InstrumentState instrument : symbols.values())
            if (instrument.isActive())
                count++;

        return count;
    }

    public int activeSymbolCountSince(long lastTradingTime) {
        int count = 0;
        for (Slot instrument : symbols.values())
            if (instrument.isActiveSince(lastTradingTime))
                count++;

        return count;
    }

    public ConcurrentMap<String, Object> sharedVariables() {
        return sharedVariables;
    }


    private static final class Slot extends InstrumentState {
        private final List<Series<?>> dataSeries = new LinkedList<>();
        private final Instrument instrument;
        private TradingAlgorithm strategy;

        public Slot(SymbolIdentifier symbol, Instrument instrument) {
            super(symbol);
            this.instrument = instrument;
        }

        public Instrument getInstrument() {
            return instrument;
        }

        @Override
        public boolean isActive() {
            return super.isActive() && getInstrument().isActive();
        }

        public boolean isActiveSince(long lastTradeTime) {
            return getInstrument().isActiveSince(lastTradeTime);
        }

        void addDataSeries(Series<?> s) {
            dataSeries.add(s);
        }

        public List<Series<?>> getDataSeries() {
            return dataSeries;
        }

        public TradingAlgorithm getStrategy() {
            return strategy;
        }

        void setStrategy(TradingAlgorithm strategy) {
            if (this.strategy != null)
                throw new IllegalStateException("InstrumentSlots' strategy already initialized");
            this.strategy = strategy;
        }
    }

    public MetaStrategy(Supplier<? extends Strategy> childStrategies) {
        this(AbstractTradingAlgorithmFactory.from(childStrategies));
    }

    public MetaStrategy(TradingAlgorithmFactory childStrategies) {
        this.childStrategiesProviders = Objects.requireNonNull(childStrategies);
    }

    @Override
    public void onInit(TradingAlgorithmContext runtime) {
        this.account = runtime.tradingService().getAccounts().get(0);
        this.lookup = createLookup(runtime);
        initSimulation(runtime, runtime.dataSeries(), dataSeriesFilter, childStrategiesProviders);
        subStrategies.forEach(strategy -> strategy.onInit(runtime));
        subStrategies.forEach(TradingAlgorithm::onAfterInit);
    }

    @Override
    public void onAfterInit() { }

    @Override
    public void onExit(ExitState state) {
        subStrategies.forEach(strategy -> strategy.onExit(state));
    }

    public void forEachStrategy(Consumer<TradingAlgorithm> action) {
        symbols.values().forEach(slot -> action.accept(slot.getStrategy()));
    }

    protected TradingAlgorithm getTargetStrategy(When when) {
        int index = when.getId();
        return (index >= 0 && index < instruments.length)? instruments[index].getStrategy() : null;
    }

    protected TradingAlgorithm getTargetStrategy(SymbolIdentity symbol) {
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
        List<TradingAlgorithm> strategies = this.subStrategies;
        for (int i = 0, count = strategies.size(); i < count; i++)
            strategies.get(i).onData(when, next, timeTick);
    }

    @Override
    public void onExecution(Execution execution) {
        getTargetStrategy(execution.getSymbol()).onExecution(execution);
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
    public void doFirst(When when) {
        getTargetStrategy(when).doFirst(when);
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
    public void doLast(When when) {
        getTargetStrategy(when).doLast(when);
    }

}
