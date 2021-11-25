package one.chartsy.trade;

import one.chartsy.When;
import one.chartsy.time.Chronological;
import one.chartsy.trade.annotations.LookAheadBiasHazard;
import one.chartsy.trade.data.Position;

import java.time.LocalDate;

public interface TradingStrategy {

    void initTradingStrategy(TradingStrategyContext context);

    void onAfterInit();

    void onTradingDayStart(LocalDate date);

    void onTradingDayEnd(LocalDate date);

    void onExitManagement(When when);

    void exitOrders(When when, Position position);

    void entryOrders(When when, Chronological data);

    void adjustRisk(When when);

    @LookAheadBiasHazard
    default void onData(When when, Chronological next, boolean timeTick) { }

    void onExecution(Execution execution);
}
