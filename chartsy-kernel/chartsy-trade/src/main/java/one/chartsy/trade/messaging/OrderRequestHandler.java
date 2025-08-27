/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.messaging;

import one.chartsy.trade.Order;

public interface OrderRequestHandler {

    void onEntryOrderRequest(Order order);
}
