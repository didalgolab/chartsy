/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade;

/**
 * Indicates that an operation cannot be performed with an order because it's in invalid state or status.
 *
 * @author Mariusz Bernacki
 *
 */
public class InvalidOrderStatusException extends RuntimeException {

    private final Order.State orderStatus;

    public InvalidOrderStatusException(Order.State orderStatus, String message) {
        super(message);
        this.orderStatus = orderStatus;
    }

    public final Order.State getOrderStatus() {
        return orderStatus;
    }
}
