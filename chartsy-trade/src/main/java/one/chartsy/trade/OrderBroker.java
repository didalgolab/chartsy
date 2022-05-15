/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade;

import java.util.Arrays;

/**
 * Defines the interface for objects than can accept submitted orders.
 * 
 * @author Mariusz Bernacki
 *
 */
@FunctionalInterface
public interface OrderBroker {
    
    /**
     * Handles an order submission.
     *
     * @param context
     *            the source context (trader or algorithm) from where the order originated
     * @param order
     *            the order
     * @return for convenience, the reference to the {@code order} parameter
     *         without change
     */
    Order submitOrder(OrderContext context, Order order);
    
    /**
     * Handles order submission of all specified orders, possibly in an atomic
     * way whenever possible.
     * <p>
     * The method is effectively equivalent to:
     * <pre>
     * submitOrders(Arrays.asList(orders));
     * </pre>
     *
     * @param context
     *            the source context (trader or algorithm) from where the order originated
     * @param orders
     *            the orders to be submitted to the execution system (i.e. a
     *            broker, a paper account, ...)
     */
    default void submitOrders(OrderContext context, Order... orders) {
        submitOrders(context, Arrays.asList(orders));
    }
    
    /**
     * Handles order submission of all specified orders, possibly in an atomic
     * way whenever possible.
     *
     * @param context
     *            the source context (trader or algorithm) from where the order originated
     * @param orders
     *            the orders to be submitted to the execution system (i.e. a
     *            broker, a paper account, ...)
     */
    default void submitOrders(OrderContext context, Iterable<Order> orders) {
        orders.forEach(order -> submitOrder(context, order));
    }
    
    default void replaceOrder(OrderContext context, Order replacee, Order replacement) {
        replacee.cancel();
        replacee.setReplacement(replacement);
        submitOrder(context, replacement);
    }
}
