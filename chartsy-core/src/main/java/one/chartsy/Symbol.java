package one.chartsy;

import lombok.Getter;
import one.chartsy.data.provider.DataProvider;

@Getter
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
        this.name = symbol.getName();
        this.type = symbol.getType();
        this.provider = provider;
    }
}
