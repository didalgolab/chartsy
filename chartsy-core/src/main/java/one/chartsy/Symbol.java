package one.chartsy;

import one.chartsy.data.provider.DataProvider;
import one.chartsy.data.provider.DataProviderLoader;

import java.util.List;
import java.util.Optional;

public class Symbol implements SymbolIdentity, SymbolGroupContent {
    private String name;
    private AssetType type;
    private String exchange;
    private String displayName;
    private DataProvider provider;
    private double spread;
    private double bigPointValue = 1.0;
    private double roundLot;

    public Symbol(String name, DataProvider provider) {
        this(SymbolIdentity.of(name), provider);
    }

    public Symbol(SymbolIdentity symbol) {
        this(symbol, null);
    }

    public Symbol(SymbolIdentity symbol, DataProvider provider) {
        this.name = symbol.name();
        this.type = symbol.type();
        this.provider = provider;
    }

    /**
     * Gives a standard minimum trading size for a security or asset.
     */
    public double roundLot() {
        return roundLot;
    }

    /**
     * Gives how much value (per 1 contract) has 1 full price point move.
     */
    public double bigPointValue() {
        return bigPointValue;
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

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public DataProvider getProvider() {
        return provider;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getSpread() {
        return spread;
    }

    @Override
    public Long getId() {
        return null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Type getContentType() {
        return Type.SYMBOL;
    }

    @Override
    public Optional<SymbolIdentity> getSymbol() {
        return Optional.of(this);
    }

    @Override
    public List<SymbolGroupContent> getContent(SymbolGroupContentRepository repo, DataProviderLoader loader) {
        return List.of();
    }
}
