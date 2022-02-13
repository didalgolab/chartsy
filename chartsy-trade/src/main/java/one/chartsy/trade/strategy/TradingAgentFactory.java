package one.chartsy.trade.strategy;

@FunctionalInterface
public interface TradingAgentFactory {

    TradingAgent createInstance();
}
