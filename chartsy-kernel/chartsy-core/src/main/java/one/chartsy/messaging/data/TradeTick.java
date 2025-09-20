/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.messaging.data;

import one.chartsy.SymbolIdentity;
import one.chartsy.data.market.Tick;
import one.chartsy.messaging.MarketDataEvent;

public interface TradeTick extends MarketDataEvent<Tick> {

    record Of(SymbolIdentity symbol, Tick get) implements TradeTick { }
}
