/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.algorithm;

import one.chartsy.messaging.MarketMessageHandler;
import one.chartsy.messaging.common.handlers.ShutdownRequestHandler;
import one.chartsy.service.Service;
import one.chartsy.trade.service.OrderHandler;

/**
 * Represents a generic algorithm used in an algorithmic trading framework.
 * An implementing class would define the specific logic and behavior of a
 * trading strategy.
 */
public interface Algorithm extends Service, MarketMessageHandler, OrderHandler, ShutdownRequestHandler {

    /**
     * Closes the algorithm, performing any necessary cleanup operations.
     * This might include closing open positions, releasing resources,
     * or finalizing logs. This method is called when the algorithm
     * is no longer needed or before shutting down the system.
     */
    @Override void close();
}
