/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.api.messages;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;

public record ImmutableBarEvent(
        SymbolIdentity symbol,
        Candle bar,
        long getTime
) implements BarEvent {

    public ImmutableBarEvent(SymbolIdentity symbol, Candle bar) {
        this(symbol, bar, bar.getTime());
    }
}
