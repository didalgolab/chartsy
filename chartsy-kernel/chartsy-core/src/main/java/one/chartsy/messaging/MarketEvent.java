/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.messaging;

import one.chartsy.SymbolIdentity;
import one.chartsy.time.Chronological;

/**
 * A discrete market-related event tied to a specific symbol and timestamp.
 * <p>
 * A {@code MarketEvent} encapsulates market data points, such as trade ticks, price bars,
 * quote updates, sentiment signals, or other market-relevant occurrences. It serves as the fundamental
 * unit of information exchange within the trading and simulation ecosystem, enabling chronological
 * event-driven architectures.
 * <p>
 * Each event is defined by:
 * <ul>
 *     <li><b>Time:</b> a high-resolution timestamp measured as nanoseconds since the Epoch (UTC),
 *         provided through the inherited {@link Chronological} interface.</li>
 *     <li><b>Symbol Identity:</b> identifying the market instrument (e.g., a stock, commodity,
 *         currency pair) associated with the event.</li>
 * </ul>
 * <p>
 * Implementations extend {@code MarketEvent} to include specific attributes relevant to their
 * domain (such as price levels, volumes, indicators, sentiment scores, or order book snapshots).
 * The generality of this interface allows uniform handling of diverse market events within the
 * processing pipeline.
 * <p>
 * {@code MarketEvent} objects are consumed sequentially by {@link MarketMessageHandler} implementations,
 * forming the basis for strategies, analytical modules, and real-time decision-making components.
 *
 * @see Chronological
 * @see SymbolIdentity
 * @see MarketMessageHandler
 */
public interface MarketEvent extends Chronological {

    /**
     * Returns the {@link SymbolIdentity} associated with this market event.
     * <p>
     * This identifier links the event to a specific market instrument or entity, providing context
     * essential for event-processing logic such as updating instrument-specific data structures,
     * indicators, or triggering trading decisions.
     *
     * @return the symbol identity for the associated market instrument, never {@code null}
     */
    SymbolIdentity symbol();
}
