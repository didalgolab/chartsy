/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm.data;

import one.chartsy.SymbolIdentity;

/**
 * Factory for creating instances of {@link InstrumentData}.
 *
 * @param <T> concrete type of InstrumentData produced
 */
@FunctionalInterface
public interface InstrumentDataFactory<T extends InstrumentData> {

    /**
     * Creates a new instance of InstrumentData for the given instrument symbol.
     *
     * @param symbol the instrument's symbol
     * @return a newly created instance of {@code T}
     */
    T create(SymbolIdentity symbol);
}
