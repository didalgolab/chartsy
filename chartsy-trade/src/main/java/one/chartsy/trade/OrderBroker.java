/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
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
     * @param order
     *            the order
     * @return for convenience, the reference to the {@code order} parameter
     *         without change
     */
    Order submitOrder(Order order);
    
    /**
     * Handles order submission of all specified orders, possibly in an atomic
     * way whenever possible.
     * <p>
     * The method is effectively equivalent to:
     * <pre>
     * submitOrders(Arrays.asList(orders));
     * </pre>
     * 
     * @param orders
     *            the orders to be submitted to the execution system (i.e. a
     *            broker, a paper account, ...)
     */
    default void submitOrders(Order... orders) {
        submitOrders(Arrays.asList(orders));
    }
    
    /**
     * Handles order submission of all specified orders, possibly in an atomic
     * way whenever possible.
     * 
     * @param orders
     *            the orders to be submitted to the execution system (i.e. a
     *            broker, a paper account, ...)
     */
    default void submitOrders(Iterable<Order> orders) {
        orders.forEach(this::submitOrder);
    }
    
    default void replaceOrder(Order replacee, Order replacement) {
        replacee.cancel();
        replacee.setReplacement(replacement);
        submitOrder(replacement);
    }
}
