package one.chartsy.naming;

import one.chartsy.AssetType;
import one.chartsy.AssetTypes;
import one.chartsy.SymbolIdentity;

public final class SymbolIdentifier implements SymbolIdentity, Comparable<SymbolIdentifier> {
    private final String name;
    private final AssetType type;

    public SymbolIdentifier(String name, AssetType type) {
        this.name = name;
        this.type = type;
    }

    public SymbolIdentifier(SymbolIdentity symbol) {
        this(symbol.getName(), symbol.getType());
    }

    public static SymbolIdentifier of(String name) {
        return new SymbolIdentifier(name, AssetTypes.GENERIC);
    }

    public static SymbolIdentifier of(SymbolIdentity symbol) {
        return (symbol instanceof SymbolIdentifier)? (SymbolIdentifier) symbol : new SymbolIdentifier(symbol);
    }

    @Override
    public final String getName() {
        return name;
    }

    @Override
    public final AssetType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof SymbolIdentifier) {
            SymbolIdentifier other = (SymbolIdentifier)o;
            return name.equals(other.name) && type.equals(other.type);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return name.hashCode() + type.hashCode();
    }

    @Override
    public int compareTo(SymbolIdentifier o) {
        return SymbolIdentityComparator.LazyHolder.INSTANCE.compare(this, o);
    }
}
