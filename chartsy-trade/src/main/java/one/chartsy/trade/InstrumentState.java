package one.chartsy.trade;

import one.chartsy.Candle;
import one.chartsy.naming.SymbolIdentifier;

public class InstrumentState {

    private final SymbolIdentifier symbol;

    private Candle lastCandle;

    private boolean active = true;


    public InstrumentState(SymbolIdentifier symbol) {
        this.symbol = symbol;
    }

    public final SymbolIdentifier getSymbol() {
        return symbol;
    }

    public final Candle getLastCandle() {
        return lastCandle;
    }

    public void setLastCandle(Candle c) {
        if (c == null || lastCandle == null || c.isAfter(lastCandle))
            lastCandle = c;
    }

    public boolean isActiveSince(long lastTradeTime) {
        Candle c = lastCandle;
        return (c != null && c.getTime() >= lastTradeTime);
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
