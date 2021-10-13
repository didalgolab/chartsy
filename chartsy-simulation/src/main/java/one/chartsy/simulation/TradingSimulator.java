package one.chartsy.simulation;

import one.chartsy.*;
import one.chartsy.data.Series;
import one.chartsy.simulation.impl.SimpleMatchingEngine;
import one.chartsy.time.Chronological;
import one.chartsy.trade.*;
import one.chartsy.trade.data.Position;
import org.openide.util.Lookup;

import java.time.*;
import java.util.Collection;
import java.util.List;

public class TradingSimulator extends TradingStrategyProxy implements TradingService, SimulationDriver {

    protected SimpleMatchingEngine matchingEngine;

    public TradingSimulator(TradingStrategy strategy) {
        super(strategy);
    }

    protected SimpleMatchingEngine createMatchingEngine(SimulationProperties properties, SimulationResult.Builder result) {
        SimpleMatchingEngine model = new SimpleMatchingEngine(properties, result);
        model.addExecutionListener((getTarget()::onExecution));
        return model;
    }

    @Override
    public List<Account> getAccounts() {
        return List.of(matchingEngine.getAccount());
    }

    @Override
    public OrderBroker getOrderBroker() {
        return matchingEngine;
    }

    @Override
    public Lookup getLookup() {
        return Lookup.EMPTY;
    }

    @Override
    public void initSimulation(SimulationContext context) {
        initDataSource(context.properties(), context.dataSeries());
        super.initTradingStrategy(context.withTradingService(this));
    }

    protected void initDataSource(
            SimulationProperties properties,
            Collection<? extends Series<?>> datasets)
    {
        if (matchingEngine != null)
            throw new SimulationException("Simulation already started");

        SimulationResult.Builder result = new SimulationResult.Builder();
        result.state(SimulationResult.State.RUNNING);
        result.startTime(LocalDateTime.now());
        result.estimatedDataPointCount(countEstimatedDataPoints(datasets));

        this.matchingEngine = createMatchingEngine(properties, result);
        if (properties.isTransactionHistoryEnabled()) {
            result.transactions(new TransactionList());
            //matchingEngine.addPositionChangeListener(new TransactionHistoryCollector(result.getTransactionList()));
        }
        // TODO: EquityTracker
        // TODO: OnHeapEquityCurve
    }

    protected long countEstimatedDataPoints(Collection<? extends Series<?>> datasets) {
        var estimatedDataPointCount = 0;
        for (var dataset : datasets)
            estimatedDataPointCount += dataset.length();

        return estimatedDataPointCount;
    }

    @Override
    public void onData(When when, Chronological next, boolean timeTick) {
        matchingEngine.onData(when, next, timeTick);
    }

    @Override
    public void onData(When when, Chronological data) {
        var target = getTarget();

        target.exitOrders(when);
        target.entryOrders(when, data);
        target.adjustRisk(when);
    }

    @Override
    public void exitOrders(When when) {
        getTarget().exitOrders(when);

        Position position = matchingEngine.getAccount().getPosition(when.getSymbol());
        if (position != null)
            getTarget().exitOrders(when, position);
    }

    @Override
    public final void exitOrders(When when, Position position) {
        // DO NOT propagate Positions from the outer scope
    }

    @Override
    public void onExecution(Execution execution, Order order) {
        // DO NOT propagate Executions from the outer scope
    }

    @Override
    public void exitOrderFilled(Order order, Execution execution) {
        // DO NOT propagate Executions from the outer scope
    }

    @Override
    public void entryOrderFilled(Order order, Execution execution) {
        // DO NOT propagate Executions from the outer scope
    }

    @Override
    public SimulationResult postSimulation() {
        SimpleMatchingEngine model = this.matchingEngine;
        if (model == null)
            throw new SimulationException("Simulation not started");

        this.matchingEngine = null;

        var result = model.getResult();
        var remainedOrders = model.getAccount().getPendingOrders();
        result.remainingOrders(model.getAccount().getPendingOrders());
        result.remainingOrderCount(remainedOrders
                .values().stream()
                .mapToInt(List::size)
                .sum());
        result.endTime(LocalDateTime.now());
        result.testDuration(Duration.between(result.getStartTime().toInstant(ZoneOffset.UTC), Instant.now()));
        result.state(SimulationResult.State.READY);
        return result.build();
    }

    private static TimeFrame getEquityArrayTimeFrame(List<TimeFrame> timeFrames) {
        if (timeFrames.isEmpty())
            throw new IllegalArgumentException("Simulation dataset is empty");

        // return the lowest used resolution as an equity tracking time frame
        return timeFrames.get(0);
    }
}
