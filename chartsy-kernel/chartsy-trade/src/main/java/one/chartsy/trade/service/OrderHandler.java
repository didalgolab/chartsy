/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.service;

/**
 * Aggregates all order-related request and report callbacks into a single handler.
 * Implementations receive both sides of the order flow:
 * <ul>
 *   <li>Order requests issued by a client or strategy (see {@link OrderRequestHandler}).</li>
 *   <li>Order reports emitted by an exchange, broker, or simulator in response to requests and market activity
 *       (see {@link OrderReportHandler}).</li>
 * </ul>
 *
 * <h6>Intent</h6>
 * This convenience interface allows components such as connectors, simulators, or test harnesses to expose a single
 * object for the full duplex order protocol. It does not add semantics beyond its parents.
 *
 * <h6>Typical usage</h6>
 * <pre>
 * public final class FixConnector implements OrderEventHandler {
 *     // Implement both request handling (inbound from strategy)
 *     // and report handling (inbound from venue, outbound to strategy).
 * }
 * </pre>
 *
 * <h6>Threading and ordering</h6>
 * Unless otherwise documented by the caller, invocations are expected to be serialized per order. Implementations
 * should remain non-blocking and resilient to duplicate or out-of-order notifications.
 *
 * @author Mariusz Bernacki
 * @see OrderRequestHandler
 * @see OrderReportHandler
 *
 */
public interface OrderHandler extends OrderRequestHandler, OrderReportHandler {
}
