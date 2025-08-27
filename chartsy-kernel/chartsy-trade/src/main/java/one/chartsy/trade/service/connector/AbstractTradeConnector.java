/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.service.connector;

import one.chartsy.trade.Order;

public abstract class AbstractTradeConnector implements TradeConnector {

    protected final TradeConnectorContext context;

    protected AbstractTradeConnector(TradeConnectorContext context) {
        this.context = context;
    }

    @Override
    public final String getId() {
        return context.getId();
    }

    public final long currentTime() {
        return context.getClock().time();
    }

    @Override
    public void onOrderRejected(Order.Rejected rejection) {
    }

    @Override
    public void onOrderStatusChanged(Order.StatusChanged change) {
    }

    @Override
    public void onOrderFilled(Order.Filled fill) {
    }

    @Override
    public void onOrderPartiallyFilled(Order.PartiallyFilled partialFill) {
    }
}
