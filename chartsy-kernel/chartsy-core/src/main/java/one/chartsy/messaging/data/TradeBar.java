/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.messaging.data;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketDataEvent;

public interface TradeBar extends MarketDataEvent<Candle> {

    record Of(SymbolIdentity symbol, Candle get) implements TradeBar { }
}
