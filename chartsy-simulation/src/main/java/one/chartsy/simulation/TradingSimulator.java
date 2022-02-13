package one.chartsy.simulation;

import one.chartsy.*;
import one.chartsy.data.Series;
import one.chartsy.data.TimedEntry;
import one.chartsy.simulation.engine.SimpleMatchingEngine;
import one.chartsy.simulation.time.SimulationClock;
import one.chartsy.time.Chronological;
import one.chartsy.trade.*;
import one.chartsy.trade.data.Position;
import one.chartsy.trade.strategy.SimulatorOptions;
import one.chartsy.trade.strategy.TradingAgent;
import one.chartsy.trade.strategy.TradingAgentAdapter;
import org.openide.util.Lookup;

import java.time.*;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class TradingSimulator extends TradingAgentAdapter implements TradingService, SimulationDriver {

    private final SimulationClock clock = new SimulationClock(ZoneId.systemDefault(), 0);
    private final EventCorrelator eventCorrelator = new EventCorrelator();
    private int currentDayNumber;

    protected SimpleMatchingEngine matchingEngine;

    public TradingSimulator(TradingAgent agent) {
        super(agent);
    }

    protected SimpleMatchingEngine createMatchingEngine(SimulatorOptions properties, SimulationResult.Builder result) {
        SimpleMatchingEngine model = new SimpleMatchingEngine(properties, result);
        model.addExecutionListener((getTarget()::onExecution));
        return model;
    }

    @Override
    public List<Account> getAccounts() {
        return List.of(matchingEngine.getAccount());
    }

    public Account getMainAccount() {
        return matchingEngine.getAccount();
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
        currentDayNumber = 0;
        eventCorrelator.clear();
        initDataSource(context.configuration().simulatorOptions(), context.dataSeries());
        super.onInit(context.withTradingService(this).withClock(clock).withScheduler(eventCorrelator));
        super.onAfterInit();
    }

    protected void initDataSource(
            SimulatorOptions properties,
            Collection<? extends Series<?>> datasets)
    {
        if (matchingEngine != null)
            throw new SimulationException("Simulation already performed");

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
    public void onTradingDayEnd(LocalDate date) {
        currentDayNumber++;
        clock.setTime(date.atStartOfDay().plusDays(1));
        super.onTradingDayEnd(date);
    }

    @Override
    public void onTradingDayStart(LocalDate date) {
        clock.setTime(date.atStartOfDay());
        super.onTradingDayStart(date);
    }

    @Override
    public void onData(When when, Chronological next, boolean timeTick) {
        super.onData(when, next, timeTick);
        matchingEngine.onData(when, next, timeTick);
    }

    @Override
    public void onData(When when, Chronological data) {
        eventCorrelator.triggerEventsUpTo(data.getTime(), clock);
        var target = getTarget();

        target.onExitManagement(when);
        target.entryOrders(when, data);
        target.adjustRisk(when);
    }

    @Override
    public void onExitManagement(When when) {
        getTarget().onExitManagement(when);

        Position position = matchingEngine.getAccount().getPosition(when.getSymbol());
        if (position != null)
            getTarget().exitOrders(when, position);
    }

    @Override
    public final void exitOrders(When when, Position position) {
        // DO NOT propagate Positions from the outer scope
    }

    @Override
    public void onExecution(Execution execution) {
        // DO NOT propagate Executions from the outer scope
    }

    public <T> void addReminder(long time, T state) {
        eventCorrelator.addTimedEvent(new TimedEntry<>(time, state), onReminderCallback);
    }

    public <T> void addReminder(long time, T state, Consumer<T> onReminder) {
        var event = new ReminderActivity<>(time, state, onReminder);
        eventCorrelator.addTimedEvent(new TimedEntry<>(time, state), event);
    }

    public <T> void addReminder(LocalDateTime datetime, T state, Consumer<T> onReminder) {
        addReminder(Chronological.toEpochMicros(datetime), state, onReminder);
    }

    public <T> void addReminder(LocalDateTime datetime, T state, BiConsumer<LocalDateTime, T> onReminder) {
        addReminder(Chronological.toEpochMicros(datetime), state, __ -> onReminder.accept(datetime, state));
    }

    public void onReminder(LocalDateTime time, Object state) {
        // TODO: impl here
    }

    protected void fireOnReminder(Chronological event) {
        if (event instanceof TimedEntry entry)
            onReminder(Chronological.toDateTime(entry.getTime()), entry.getValue());
    }

    private final EventCorrelator.EventHandler onReminderCallback = this::fireOnReminder;

    static record ReminderActivity<T>(
            long time,
            T state,
            Consumer<T> onReminder
    ) implements EventCorrelator.EventHandler {

        @Override
        public void handle(Chronological event) {
            if (onReminder != null)
                onReminder.accept(state);
        }
    }

    @Override
    public SimulationResult postSimulation() {
        SimpleMatchingEngine model = this.matchingEngine;
        if (model == null)
            throw new SimulationException("Simulation not started");

        var endTime = LocalDateTime.now();
        var result = model.getResult();
        var remainedOrders = model.getAccount().getPendingOrders();
        //result.remainingOrders(model.getAccount().getPendingOrders());
        result.remainingOrderCount(remainedOrders
                .values().stream()
                .mapToInt(List::size)
                .sum());
        result.endTime(endTime);
        result.testDuration(Duration.between(result.getStartTime(), endTime));
        result.testDays(currentDayNumber);
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
