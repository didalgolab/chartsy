package one.chartsy.trade.strategy;

import one.chartsy.When;
import one.chartsy.time.Chronological;
import one.chartsy.trade.Execution;
import one.chartsy.trade.data.Position;

import java.time.LocalDate;

public class TradingAgentAdapter implements TradingAgent {

    private TradingAgent target;

    public TradingAgentAdapter(TradingAgent target) {
        setTarget(target);
    }


    @Override
    public void onInit(TradingAgentRuntime runtime) {
        getTarget().onInit(runtime);
    }

    @Override
    public void onAfterInit() {
        getTarget().onAfterInit();
    }

    @Override
    public void onExit(ExitState state) {
        getTarget().onExit(state);
    }

    @Override
    public void onTradingDayStart(LocalDate date) {
        getTarget().onTradingDayStart(date);
    }

    @Override
    public void onTradingDayEnd(LocalDate date) {
        getTarget().onTradingDayEnd(date);
    }

    @Override
    public void onExitManagement(When when) {
        getTarget().onExitManagement(when);
    }

    @Override
    public void exitOrders(When when, Position position) {
        getTarget().exitOrders(when, position);
    }

    @Override
    public void entryOrders(When when, Chronological data) {
        getTarget().entryOrders(when, data);
    }

    @Override
    public void adjustRisk(When when) {
        getTarget().adjustRisk(when);
    }

    @Override
    public void onData(When when, Chronological next, boolean timeTick) {
        getTarget().onData(when, next, timeTick);
    }

    @Override
    public void onExecution(Execution execution) {
        getTarget().onExecution(execution);
    }

    public final TradingAgent getTarget() {
        return target;
    }

    public void setTarget(TradingAgent target) {
        if (target == null)
            throw new IllegalArgumentException("target is NULL");

        this.target = target;
    }
}
