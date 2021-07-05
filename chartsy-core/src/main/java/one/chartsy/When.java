package one.chartsy;

import one.chartsy.time.Chronological;

public interface When {

    SymbolResource<?> getResource();

    int index();

    Chronological current();

    boolean hasNext();

    default SymbolIdentity getSymbol() {
        return getResource().getSymbol();
    }
}
