package one.chartsy.trade;

import one.chartsy.When;
import one.chartsy.time.Chronological;
import one.chartsy.trade.data.Position;

import java.time.LocalDate;

public class TradingStrategyProxy implements TradingStrategy {

    private TradingStrategy target;

    public TradingStrategyProxy(TradingStrategy target) {
        setTarget(target);
    }


    @Override
    public void initTradingStrategy(TradingStrategyContext context) {
        getTarget().initTradingStrategy(context);
    }

    @Override
    public void onAfterInit() {
        getTarget().onAfterInit();
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

    public final TradingStrategy getTarget() {
        return target;
    }

    public void setTarget(TradingStrategy target) {
        if (target == null)
            throw new IllegalArgumentException("target is NULL");

        this.target = target;
    }
}
