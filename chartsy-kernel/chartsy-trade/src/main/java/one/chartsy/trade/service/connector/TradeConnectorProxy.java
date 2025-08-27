/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.service.connector;

import one.chartsy.data.stream.Message;
import one.chartsy.data.stream.QueuedMessageBuffer;
import one.chartsy.trade.Order;

import java.util.Queue;

public class TradeConnectorProxy implements TradeConnector {

    private final String id;
    private final Queue<Message> queue;
    private final TradeConnectorWorker worker;

    public TradeConnectorProxy(TradeConnector target, Queue<Message> queue) {
        this(target.getId(), target, queue);
    }

    protected TradeConnectorProxy(String id, TradeConnector target, Queue<Message> queue) {
        this.id = id;
        this.queue = queue;
        this.worker = createWorker(target, queue);
    }

    private static TradeConnectorWorker createWorker(TradeConnector target, Queue<Message> queue) {
        return new TradeConnectorWorker(target, new QueuedMessageBuffer<>(queue));
    }

    @Override
    public void open() {
        worker.onOpen();
    }

    @Override
    public void close() {
        worker.onClose();
    }

    @Override
    public final String getId() {
        return id;
    }

    public final TradeConnectorWorker getWorker() {
        return worker;
    }

    @Override
    public void onOrderPlacement(Order.New order) {
        offer(order);
    }

    @Override
    public void onOrderRejected(Order.Rejected rejection) {
        offer(rejection);
    }

    @Override
    public void onOrderStatusChanged(Order.StatusChanged change) {
        offer(change);
    }

    @Override
    public void onOrderFilled(Order.Filled fill) {
        offer(fill);
    }

    @Override
    public void onOrderPartiallyFilled(Order.PartiallyFilled partialFill) {
        offer(partialFill);
    }

    protected void offer(Message message) {
        queue.offer(message);
    }
}
