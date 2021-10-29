package one.chartsy.trade.strategy;

import one.chartsy.Candle;
import one.chartsy.CandleOpen;
import one.chartsy.When;
import one.chartsy.trade.strategy.annotation.BacktestLiveIncompatibility;
import one.chartsy.trade.strategy.annotation.IncompatibilityType;

public interface TradingAgent {

    void onInit(TradingAgentRuntime runtime);

    void onAfterInit();

    void onExit(ExitState state);

    void onCandleClose(When when, Candle c);

    @BacktestLiveIncompatibility(IncompatibilityType.DIFFERENT_BEHAVIOUR)
    void onCandleOpen(When when, CandleOpen open);
}
