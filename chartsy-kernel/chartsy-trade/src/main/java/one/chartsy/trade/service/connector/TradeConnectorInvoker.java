/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.service.connector;

import one.chartsy.core.event.AbstractInvoker;
import one.chartsy.data.stream.Message;
import one.chartsy.data.stream.MessageHandler;
import one.chartsy.trade.Order;
import one.chartsy.trade.data.OrderReportEvent;
import one.chartsy.trade.data.OrderRequestEvent;
import one.chartsy.trade.service.OrderReportHandler;
import one.chartsy.trade.service.OrderRequestHandler;

public class TradeConnectorInvoker extends AbstractInvoker implements MessageHandler {

    public TradeConnectorInvoker(TradeConnector connector) {
        super(connector.getClass());
        addService(connector);
    }

    @Override
    public void handleMessage(Message msg) {
        if (msg instanceof OrderRequestEvent request) {
            handleOrderRequest(request);
        } else if (msg instanceof OrderReportEvent report) {
            handleOrderReport(report);
        } else {
            throw new IllegalArgumentException("Unsupported message type: " + msg.getClass().getSimpleName());
        }
    }

    protected void handleOrderReport(OrderReportEvent report) {
        var handler = getHandler(OrderReportHandler.class);
        switch (report) {
            case Order.Rejected r -> handler.onOrderRejected(r);
            case Order.StatusChanged c -> handler.onOrderStatusChanged(c);
            case Order.Filled f -> handler.onOrderFilled(f);
            case Order.PartiallyFilled pf -> handler.onOrderPartiallyFilled(pf);
            default -> throw new IllegalArgumentException(
                    "Unsupported order report type: " + report.getClass().getSimpleName()
            );
        }
    }

    protected void handleOrderRequest(OrderRequestEvent request) {
        var handler = getHandler(OrderRequestHandler.class);
        switch (request) {
            case Order.New order -> handler.onOrderPlacement(order);
            case Order.Replacement replacement -> throw new UnsupportedOperationException(); // TODO
            case Order.Cancellation cancellation -> throw new UnsupportedOperationException(); // TODO
            default -> throw new IllegalArgumentException(
                    "Unsupported order request type: " + request.getClass().getSimpleName());
        }
    }
}
