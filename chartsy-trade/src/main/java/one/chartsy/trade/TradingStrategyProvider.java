package one.chartsy.trade;

@FunctionalInterface
public interface TradingStrategyProvider {

    TradingStrategy createInstance();
}
