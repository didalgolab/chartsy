/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade;

public interface OrderStatusListener extends java.util.EventListener {
    
    /**
     * Indicates that the order status has been changed
     * 
     * @param e the order event
     */
    void orderStatusChanged(OrderStatusEvent e);
    
}
