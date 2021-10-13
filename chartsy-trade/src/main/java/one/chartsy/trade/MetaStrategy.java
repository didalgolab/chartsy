package one.chartsy.trade;

import one.chartsy.SymbolIdentity;
import one.chartsy.When;
import one.chartsy.data.Series;
import one.chartsy.naming.SymbolIdentifier;
import one.chartsy.time.Chronological;
import one.chartsy.trade.data.Position;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@SuppressWarnings("ForLoopReplaceableByForEach")
public class MetaStrategy implements TradingStrategy {

    private Account account;

    private final List<TradingStrategy> childStrategies = new ArrayList<>();
    private final Map<SymbolIdentifier, List<TradingStrategy>> symbolStrategies = new HashMap<>();
    private final Map<SymbolIdentifier, List<Series<?>>> symbolDatasets = new HashMap<>();
    private List<TradingStrategy>[] dispatchTable;


    @Override
    public void initTradingStrategy(TradingStrategyContext context) {
        this.account = context.tradingService().getAccounts().get(0);
        childStrategies.forEach(strategy -> strategy.initTradingStrategy(context));
    }

    public void initSimulation(List<Series<?>> datasets) {
        dispatchTable = new List[datasets.size()];

        for (int index = 0; index < datasets.size(); index++) {
            Series<?> dataset = datasets.get(index);
            SymbolIdentifier symbol = new SymbolIdentifier(dataset.getResource().symbol());
            dispatchTable[index] = symbolStrategies.computeIfAbsent(symbol, __ -> new ArrayList<>());
        }

    }

    public void addChildStrategy(Supplier<TradingStrategy> supplier) {
        StrategyInitializer initializer = new StrategyInitializer(supplier);
        symbolDatasets.forEach((symbol, datasets) -> {
            TradingStrategy childStrategy = initializer.newInstance(symbol, datasets);

            childStrategies.add(childStrategy);
            symbolStrategies.get(symbol).add(childStrategy);
        });
    }

    public static class StrategyInitializer {
        private final Supplier<TradingStrategy> supplier;

        public StrategyInitializer(Supplier<TradingStrategy> supplier) {
            this.supplier = supplier;
        }

        public TradingStrategy newInstance(SymbolIdentity symbol, List<Series<?>> datasets) {
            // TODO with context
            return supplier.get();
        }

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
        List<TradingStrategy> strategies = this.childStrategies;
        for (int i = 0, count = strategies.size(); i < count; i++)
            strategies.get(i).onData(when, next, timeTick);
    }

    @Override
    public void onExecution(Execution execution, Order order) {
        TradingStrategy.super.onExecution(execution, order);
    }

    @Override
    public void onTradingDayStart(LocalDate date) {
        childStrategies.forEach(strategy -> strategy.onTradingDayStart(date));
    }

    @Override
    public void onTradingDayEnd(LocalDate date) {
        childStrategies.forEach(strategy -> strategy.onTradingDayEnd(date));
    }

    @Override
    public void exitOrders(When when) {
        getTargetStrategies(when).forEach(strategy -> strategy.exitOrders(when));
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
