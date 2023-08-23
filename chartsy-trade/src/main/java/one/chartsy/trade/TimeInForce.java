/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.trade;

/**
 * Represents the time in force property for an order.
 * 
 * @author Mariusz Bernacki
 *
 */
public interface TimeInForce {
    
    TimeInForce GTC = Standard.GTC;
    
    TimeInForce DAY = Standard.DAY;
    
    TimeInForce OPEN = Standard.OPEN;
    
    TimeInForce CLOSE = Standard.CLOSE;

    TimeInForce GTD = Standard.GTD;

    
    enum Standard implements TimeInForce {
        GTC, DAY, OPEN, CLOSE, GTD;
    }
}
