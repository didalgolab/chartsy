/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm.data;

import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketMessageHandler;

/**
 * Represents the data associated with a financial instrument within the trading system.
 * <p>
 * This interface provides the essential properties for an instrument, such as its symbol and type.
 * Implementations of this interface are used to hold state and indicators for trading algorithms,
 * and they are expected to process market messages and instrument update events.
 * </p>
 *
 * <p>
 * Implementations of {@code InstrumentData} must provide fast access to its identifying properties,
 * as these are used frequently within performance-sensitive trading loops.
 * </p>
 *
 * @see MarketMessageHandler
 * @see InstrumentUpdateHandler
 */
public interface InstrumentData extends MarketMessageHandler, InstrumentUpdateHandler {

    /**
     * Returns the symbol that uniquely identifies the instrument.
     *
     * @return the instrument's symbol.
     */
    SymbolIdentity getSymbol();
}
