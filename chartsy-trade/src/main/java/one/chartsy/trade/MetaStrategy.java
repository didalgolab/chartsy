package one.chartsy.trade;

import one.chartsy.SymbolIdentity;
import one.chartsy.When;
import one.chartsy.data.Series;
import one.chartsy.naming.SymbolIdentifier;
import one.chartsy.time.Chronological;
import one.chartsy.trade.data.Position;

import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@SuppressWarnings("ForLoopReplaceableByForEach")
public class MetaStrategy implements TradingStrategy {

    private Account account;

    private final ConcurrentMap<String, ?> sharedVariables = new ConcurrentHashMap<>();
    private final List<TradingStrategyProvider> childStrategiesProviders;
    private final List<TradingStrategy> subStrategies = new ArrayList<>();
    private final Map<SymbolIdentifier, List<TradingStrategy>> symbolStrategies = new LinkedHashMap<>();
    private final Map<SymbolIdentifier, List<Series<?>>> symbolDatasets = new LinkedHashMap<>();
    private List<TradingStrategy>[] dispatchTable;


    public MetaStrategy(TradingStrategyProvider... childStrategies) {
        this.childStrategiesProviders = List.of(childStrategies);
    }

    @Override
    public void initTradingStrategy(TradingStrategyContext context) {
        this.account = context.tradingService().getAccounts().get(0);
        initSimulation(context.dataSeries());
        subStrategies.forEach(strategy -> strategy.initTradingStrategy(context));
    }

    public void initSimulation(Collection<? extends Series<?>> datasets) {
        clear();
        dispatchTable = new List[datasets.size()];

        int index = 0;
        for (Series<?> dataset : datasets) {
            SymbolIdentifier symbol = new SymbolIdentifier(dataset.getResource().symbol());
            symbolDatasets.computeIfAbsent(symbol, __ -> new ArrayList<>(2)).add(dataset);
            dispatchTable[index++] = symbolStrategies.computeIfAbsent(symbol, __ -> new ArrayList<>(2));
        }

        for (TradingStrategyProvider provider : childStrategiesProviders)
            initChildStrategy(provider);
    }

    protected void initChildStrategy(TradingStrategyProvider provider) {
        StrategyInitializer initializer = new StrategyInitializer();
        symbolDatasets.forEach((symbol, datasets) -> {
            var config = new StrategyConfigData(symbol, datasets, sharedVariables, Map.of(), account);
            var childStrategy = initializer.newInstance(provider, config);

            subStrategies.add(childStrategy);
            symbolStrategies.get(symbol).add(childStrategy);
        });
    }

    protected void clear() {
        subStrategies.clear();
        symbolStrategies.clear();
        symbolDatasets.clear();
        sharedVariables.clear();
    }

    protected List<TradingStrategy> getTargetStrategies(When when) {
        int index = when.getId();
        return (index >= 0 && index < dispatchTable.length)? dispatchTable[index] : List.of();
    }

    protected List<TradingStrategy> getTargetStrategies(SymbolIdentity symbol) {
        List<TradingStrategy> targetStrategies = symbolStrategies.get(new SymbolIdentifier(symbol));
        return (targetStrategies != null)? targetStrategies : List.of();
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
        getTargetStrategies(when).forEach(strategy -> strategy.onExitManagement(when));
    }

    @Override
    public void exitOrders(When when, Position position) {
        getTargetStrategies(when).forEach(strategy -> strategy.exitOrders(when, position));
    }

    @Override
    public void entryOrders(When when, Chronological data) {
        getTargetStrategies(when).forEach(strategy -> strategy.entryOrders(when, data));
    }

    @Override
    public void adjustRisk(When when) {
        getTargetStrategies(when).forEach(strategy -> strategy.adjustRisk(when));
    }

    @Override
    public void entryOrderFilled(Order order, Execution execution) {
        getTargetStrategies(execution.getSymbol()).forEach(strategy -> strategy.entryOrderFilled(order, execution));
    }

    @Override
    public void exitOrderFilled(Order order, Execution execution) {
        getTargetStrategies(execution.getSymbol()).forEach(strategy -> strategy.exitOrderFilled(order, execution));
    }
}
