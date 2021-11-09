package one.chartsy.trade;

import one.chartsy.Candle;

/**
 * The type of order.
 * 
 * @author Mariusz Bernacki
 *
 */
public interface OrderType {
    /** Specifies the market order type (shared instance). */
    MarketOrderType MARKET = new MarketOrderType();
    
    Execution tryFill(Order order, Candle ohlc, OrderFiller filler);

    default boolean isImmediateOrCancelOnly() {
        return false;
    }
}
