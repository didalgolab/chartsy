/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm.data;

import one.chartsy.SymbolIdentity;

/**
 * An abstract base class for the {@code InstrumentData} instances.
 *
 * @see InstrumentData
 * @see SymbolIdentity
 */
public abstract class AbstractInstrumentData implements InstrumentData {
    /** The unique identity of the instrument. */
    private final SymbolIdentity symbol;

    protected AbstractInstrumentData(SymbolIdentity symbol) {
        this.symbol = symbol;
    }

    @Override
    public final SymbolIdentity getSymbol() {
        return symbol;
    }
}
