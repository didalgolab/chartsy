/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.service;

import one.chartsy.trade.Order;

/**
 * Receives order entry requests originating from a client, strategy, or orchestration layer, such as
 * new, replace, cancel, and status queries.
 *
 * <h6>Responsibilities</h6>
 * <ul>
 *   <li>Validate and route incoming requests to the underlying execution venue, broker, or simulator.</li>
 *   <li>Ensure request correlation so that subsequent reports can be matched to the correct order and client context.</li>
 *   <li>Surface business validation outcomes via report events rather than by throwing, wherever practical.</li>
 * </ul>
 *
 * <h6>Protocol</h6>
 * The interface models an imperative, side-effecting API. Requests are typically followed by one or more reports that
 * confirm acceptance, rejection, fills, or state changes. Implementations should be idempotent with respect to retries.
 *
 * <h6>Mass and list operations</h6>
 * Default implementations of mass cancel, mass status, and order list requests throw
 * {@link UnsupportedOperationException}. Adapters that support these operations should override the defaults.
 *
 * <h6>Threading and backpressure</h6>
 * Callers may invoke methods from a single-threaded event loop. Implementations should avoid blocking I/O and consider
 * applying internal queuing if interaction with the venue is asynchronous or rate limited.
 *
 * @author Mariusz Bernacki
 *
 */
public interface OrderRequestHandler {

    void onOrderPlacement(Order.New order);
}
