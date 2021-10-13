package one.chartsy.trade;

import one.chartsy.Candle;

/**
 * Specifies an order to trade (buy or sell) a security at the current market
 * prices.
 * 
 * @author Mariusz Bernacki
 */
public final class MarketOrderType implements OrderType {
    
    @Override
    public Execution tryFill(Order order, Candle ohlc, OrderFiller filler) {
        return filler.fillOrder(order, ohlc, ohlc.open());
    }

    @Override
    public boolean isCancellable() {
        return false;
    }
}
