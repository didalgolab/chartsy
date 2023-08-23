/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.chartfx.service.execution;

import java.util.EventObject;

import one.chartsy.samples.chartfx.dos.Order;

public class OrderEvent extends EventObject {
    private static final long serialVersionUID = 3995883467037156877L;

    private final Order order;

    public OrderEvent(Object source, Order order) {
        super(source);
        this.order = order;
    }

    public Order getOrder() {
        return order;
    }
}
