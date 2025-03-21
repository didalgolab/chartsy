/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.account;

import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.trade.Order.Side;
import org.immutables.value.Value;

@Value.Immutable
public interface TradeExecutionEvent extends MarketEvent {

    Side side();

    default boolean isBuy() {
        return side().isBuy();
    }

    double tradePrice();

    double tradeQuantity();

    static ImmutableTradeExecutionEvent.Builder builder() {
        return ImmutableTradeExecutionEvent.builder();
    }

    record Of(
            SymbolIdentity symbol,
            Side side,
            double tradePrice,
            double tradeQuantity,
            long getTime
    ) implements TradeExecutionEvent { }
}
