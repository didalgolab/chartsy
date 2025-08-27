/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.service.engine;

import one.chartsy.trade.Order;

public interface OrderRoutingStrategy {

    OrderRoutingStrategy BY_REQUEST_DESTINATION = (request, order) -> request.destinationId();

    String getServiceId(Order.New orderRequest, Order order);
}
