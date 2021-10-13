package one.chartsy.trade;

import one.chartsy.When;
import one.chartsy.time.Chronological;
import one.chartsy.trade.data.Position;

import java.time.LocalDate;

public interface TradingStrategy {

    void initTradingStrategy(TradingStrategyContext context);

    void onTradingDayStart(LocalDate date);

    void onTradingDayEnd(LocalDate date);

    void exitOrders(When when);

    void exitOrders(When when, Position position);

    void entryOrders(When when, Chronological data);

    void adjustRisk(When when);

    default void onData(When when, Chronological next, boolean timeTick) { }

    default void onExecution(Execution execution, Order order) {
        if (execution.isScaleIn())
            entryOrderFilled(order, execution);
        else
            exitOrderFilled(order, execution);
    }

    void entryOrderFilled(Order order, Execution execution);

    void exitOrderFilled(Order order, Execution execution);
}
