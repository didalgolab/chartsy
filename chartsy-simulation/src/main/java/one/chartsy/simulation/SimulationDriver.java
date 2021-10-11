package one.chartsy.simulation;

import one.chartsy.When;
import one.chartsy.time.Chronological;
import one.chartsy.trade.TradingStrategyContext;

import java.time.LocalDate;

public interface SimulationDriver {
    void setTradingStrategyContext(TradingStrategyContext context);
    void onTradingDayStart(LocalDate date);
    void onTradingDayEnd(LocalDate date);

    void onData(When when, Chronological next, boolean timeTick);
    void onData(When when, Chronological last);

    default SimulationResult postSimulation() {
        return new SimulationResult();
    }
}
