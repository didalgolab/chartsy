package one.chartsy.trade;

import one.chartsy.naming.SymbolIdentifier;

public class InstrumentState {

    private final SymbolIdentifier symbol;

    private boolean active = true;


    public InstrumentState(SymbolIdentifier symbol) {
        this.symbol = symbol;
    }

    public final SymbolIdentifier getSymbol() {
        return symbol;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
