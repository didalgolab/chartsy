/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.data;

import one.chartsy.data.stream.Message;

/**
 * A base for all order-related events in the trading system.
 */
public interface OrderEvent extends Message {

    /** The unique user-assigned order id. */
    String orderId();

    /** The date and time when the request was created or accepted for processing. */
    long time();
}
