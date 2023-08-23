/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
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
    public boolean isImmediateOrCancelOnly() {
        return true;
    }
}
