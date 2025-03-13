/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm.data;

import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketMessageHandler;

import java.util.Collection;

/**
 * Processes market data messages and maintains per‑instrument state.
 *
 * @param <T> the type of instrument data
 */
public interface MarketDataProcessor<T extends InstrumentData> extends Iterable<T>, MarketMessageHandler, InstrumentUpdateHandler {

    /**
     * Gives the number of instruments in the set.
     *
     * @return the instrument count
     */
    int getInstrumentCount();

    /**
     * Retrieves an unmodifiable {@code Collection} view of all instruments in the set.
     *
     * @return a collection of available instruments
     */
    Collection<T> getInstruments();

    /**
     * Returns the existing data for the symbol or creates it if missing.
     *
     * @param symbol the instrument symbol
     * @return the existing or newly created instrument data
     */
    T getOrCreate(SymbolIdentity symbol);
}
