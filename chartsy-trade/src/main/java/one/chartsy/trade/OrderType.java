/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
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
