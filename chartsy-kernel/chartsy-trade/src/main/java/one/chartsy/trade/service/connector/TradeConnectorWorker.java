/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.service.connector;

import one.chartsy.data.stream.MessageBuffer;
import one.chartsy.service.QueuedServiceWorker;

public class TradeConnectorWorker extends QueuedServiceWorker<TradeConnector> {

    protected static final int DEFAULT_POLL_LIMIT = Integer.getInteger("trade.connector.poll.limit", 32);

    public TradeConnectorWorker(TradeConnector connector, MessageBuffer queue) {
        super(connector, queue, createInvoker(connector), DEFAULT_POLL_LIMIT);
    }

    public TradeConnectorWorker(TradeConnector connector, MessageBuffer queue, TradeConnectorInvoker invoker, int pollLimit) {
        super(connector, queue, invoker, pollLimit);
    }

    private static TradeConnectorInvoker createInvoker(TradeConnector connector) {
        return new TradeConnectorInvoker(connector);
    }
}
