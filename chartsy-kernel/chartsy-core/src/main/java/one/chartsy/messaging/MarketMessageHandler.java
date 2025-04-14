/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.messaging;

/**
 * A central consumer interface, reacting to events emitted by the {@code MarketSupplier}'s.
 * <p>
 * Implementations of this interface define the logic for consuming, interpreting, and acting upon market
 * events (e.g., price updates, trade ticks, bars, sentiment updates). A {@code MarketMessageHandler}
 * represents the event-driven consumer component within a trading or backtesting framework, reacting
 * sequentially to incoming {@link MarketEvent} instances.
 * <p>
 * Handlers typically implement business-specific logic such as:
 * <ul>
 *     <li>Updating internal instrument data models with new market information</li>
 *     <li>Calculating trading indicators or derived signals</li>
 *     <li>Generating trading decisions or alerts based on processed data</li>
 * </ul>
 *
 * @see MarketEvent
 */
public interface MarketMessageHandler {

    /**
     * Processes a single market event.
     * <p>
     * This method is invoked by the framework or market data supplier for each incoming event. Implementations
     * should execute quickly and must avoid long-running or blocking operations to maintain throughput
     * in the event-processing pipeline. The order of events provided to this method is guaranteed to be
     * chronological, allowing implementations to rely on timestamp ordering without additional verification.
     *
     * @param event the event to be processed, containing relevant market data or updates
     */
    void onMarketMessage(MarketEvent event);
}
