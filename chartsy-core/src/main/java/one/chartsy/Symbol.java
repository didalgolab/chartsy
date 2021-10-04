package one.chartsy;

import one.chartsy.data.provider.DataProvider;

public class Symbol implements SymbolIdentity {
    private String name;
    private AssetType type;
    private String exchange;
    private String displayName;
    private DataProvider provider;

    public Symbol(String name, DataProvider provider) {
        this(SymbolIdentity.of(name), provider);
    }

    public Symbol(SymbolIdentity symbol, DataProvider provider) {
        this.name = symbol.name();
        this.type = symbol.type();
        this.provider = provider;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public AssetType type() {
        return type;
    }

    public String getExchange() {
        return exchange;
    }

    public DataProvider getProvider() {
        return provider;
    }

    public String getDisplayName() {
        return displayName;
    }
}
