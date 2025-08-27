/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.service;

import one.chartsy.trade.Order;

/**
 * Receives order lifecycle and trade reports emitted by an exchange, broker, or simulator.
 * Reports convey acceptance, rejection, state transitions, and executions that result from
 * prior requests and market activity.
 *
 * <h6>Event categories</h6>
 * <ul>
 *   <li><b>Lifecycle</b>: pending new, new, reject, pending cancel, cancel, cancel reject,
 *       pending replace, replace, replace reject, restate.</li>
 *   <li><b>Trade flow</b>: trade report, trade cancel (bust), trade correct (amend), and status snapshots.</li>
 * </ul>
 *
 * <h6>Delivery semantics</h6>
 * <ul>
 *   <li>Reports should correlate to an order identifier and, where applicable, to the originating client request.</li>
 *   <li>Duplicates may occur; handlers should be idempotent.</li>
 *   <li>Out-of-order delivery may occur during recovery; implementations should tolerate and reconcile ordering.</li>
 * </ul>
 *
 * <h6>Error handling</h6>
 * Business-level failures are communicated as report events (for example, rejects). Throwing from a handler should be
 * reserved for unrecoverable issues such as misconfiguration; callers may treat such errors as fatal.
 *
 * <h6>Threading</h6>
 * Unless documented otherwise, callers invoke methods on a single thread per session or order stream. Handlers should
 * avoid blocking and should offload heavy work if necessary.
 *
 * @author Mariusz Bernacki
 *
 */
public interface OrderReportHandler {

    void onOrderRejected(Order.Rejected rejection);

    void onOrderStatusChanged(Order.StatusChanged change);

    void onOrderFilled(Order.Filled fill);

    void onOrderPartiallyFilled(Order.PartiallyFilled partialFill);
}
