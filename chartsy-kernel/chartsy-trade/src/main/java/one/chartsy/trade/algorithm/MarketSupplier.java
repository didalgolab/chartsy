/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import one.chartsy.base.Disposable;
import one.chartsy.messaging.MarketMessageHandler;

/**
 * Supplies market data messages for consumption by trading algorithms.
 * <p>
 * Implementations of this interface manage the retrieval and delivery of sequential market events
 * (e.g., trade ticks, bar updates, sentiment indicators) from various data sources or simulations.
 * A {@code MarketSupplier} acts as a source of ordered market information, typically driven by an
 * underlying time-provider such as a {@code PlaybackClock}, facilitating deterministic and reproducible
 * backtesting or forward-testing scenarios.
 * <p>
 * The lifecycle of a {@code MarketSupplier} consists of:
 * <ul>
 *     <li>{@link #open()} – initialization and setup</li>
 *     <li>{@link #poll(MarketMessageHandler, int)} – continuous message retrieval loop</li>
 *     <li>{@link #close()} – finalization and resource release</li>
 * </ul>
 *
 * @see MarketMessageHandler
 * @see Disposable
 */
public interface MarketSupplier extends Disposable {

    /**
     * Opens and initializes the market supplier for subsequent message retrieval.
     * <p>
     * This method must be called exactly once before any call to {@link #poll(MarketMessageHandler, int)}.
     * It typically establishes connections, initializes state, and prepares the data feed for consumption.
     */
    void open();

    /**
     * Polls and delivers a sequential batch of market data messages corresponding to the next available timestamp.
     * <p>
     * This method retrieves and dispatches messages via the provided {@code handler} for the nearest upcoming timestamp,
     * up to the specified {@code messageLimit}. All returned messages must share the same timestamp to ensure chronological
     * integrity. If fewer messages are available than the specified limit, only those available are returned, ensuring
     * precise event-time alignment. Subsequent calls to this method will progress through the sequence of timestamps in ascending order.
     *
     * @param handler      the {@link MarketMessageHandler} that processes retrieved market messages
     * @param messageLimit the maximum number of messages to retrieve in this call; helps control memory usage and throughput
     * @return the actual number of messages retrieved and delivered to the handler; returns {@code 0} if no further messages remain
     */
    int poll(MarketMessageHandler handler, int messageLimit);

    /**
     * Closes and disposes of the market supplier, releasing underlying resources and terminating any active connections.
     * <p>
     * After invoking this method, no further calls to {@link #poll(MarketMessageHandler, int)} are permitted. Subsequent use
     * of the instance may result in undefined behavior or exceptions. Implementations must ensure that all resources,
     * connections, and internal states are gracefully terminated upon closure.
     */
    @Override
    void close();
}