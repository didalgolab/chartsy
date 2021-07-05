package one.chartsy;

/**
 * The common types of financial assets.
 *
 * @author Mariusz Bernacki
 *
 */
public enum AssetTypes implements AssetType {
    BOND(true),
    CASH(true), // ie: Forex, no leverage
    CFD(true),
    COMBINATION(true),
    COMMODITY(true),
    CONTINUOUS_FUTURE(true),
    CRYPTOCURRENCY(true),
    ETF(true),
    EXCHANGE_TRADED_SYNTHETIC(true),
    FOREX(true),
    FUND(true),
    FUTURE(true),
    FUTURE_OPTION(true),
    GENERIC(true),
    INDEX(false),
    INTEREST_RATE(true),
    OPTION(true),
    STOCK(true);

    @Override
    public boolean isTradable() {
        return tradable;
    }

    AssetTypes(boolean tradable) {
        this.tradable = tradable;
    }
    private final boolean tradable;
}
