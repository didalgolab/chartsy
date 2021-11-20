package one.chartsy.trade.strategy;

public interface InstrumentUniverse {

    int totalSymbolCount();

    int activeSymbolCountSince(long lastTradeTime);

}
