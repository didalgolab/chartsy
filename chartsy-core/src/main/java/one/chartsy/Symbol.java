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
    private Double lastPrice;
    private Double dailyChangePercentage;

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

    public Symbol(Builder builder) {
        this.name = builder.symbol.name();
        this.type = builder.symbol.type();
        this.provider = builder.provider;
        this.displayName = builder.displayName;
        this.exchange = builder.exchange;
        this.lastPrice = builder.lastPrice;
        this.dailyChangePercentage = builder.dailyChangePercentage;
    }

    public static class Builder {
        private final SymbolIdentity symbol;
        private final DataProvider provider;
        private String displayName;
        private String exchange;
        private Double lastPrice;
        private Double dailyChangePercentage;

        public Builder(SymbolIdentity symbol, DataProvider provider) {
            this.symbol = symbol;
            this.provider = provider;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder exchange(String exchange) {
            this.exchange = exchange;
            return this;
        }

        public Builder lastPrice(double lastPrice) {
            this.lastPrice = lastPrice;
            return this;
        }

        public Builder dailyChangePercentage(double dailyChangePercentage) {
            this.dailyChangePercentage = dailyChangePercentage;
            return this;
        }

        public Symbol build() {
            return new Symbol(this);
        }
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

    public String exchange() {
        return exchange;
    }

    public Double lastPrice() {
        return lastPrice;
    }

    public Double dailyChangePercentage() {
        return dailyChangePercentage;
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
    public Optional<Symbol> getAsSymbol() {
        return Optional.of(this);
    }

    @Override
    public List<SymbolGroupContent> getContent(SymbolGroupContentRepository repo, DataProviderLoader loader) {
        return List.of();
    }
}
