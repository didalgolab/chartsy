/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.trade;

public interface OrderStatusListener extends java.util.EventListener {
    
    /**
     * Indicates that the order status has been changed
     * 
     * @param e the order event
     */
    void orderStatusChanged(OrderStatusEvent e);
    
}
