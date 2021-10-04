package one.chartsy.naming;

import one.chartsy.AssetType;
import one.chartsy.SymbolIdentity;

public record SymbolIdentifier(String name, AssetType type) implements SymbolIdentity, Comparable<SymbolIdentifier> {

    public SymbolIdentifier(SymbolIdentity symbol) {
        this(symbol.name(), symbol.type());
    }

    @Override
    public int compareTo(SymbolIdentifier o) {
        return SymbolIdentityComparator.LazyHolder.INSTANCE.compare(this, o);
    }
}
