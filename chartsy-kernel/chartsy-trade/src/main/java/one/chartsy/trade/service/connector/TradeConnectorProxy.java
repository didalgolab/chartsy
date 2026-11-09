/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.service.connector;

import one.chartsy.data.stream.Message;
import one.chartsy.data.stream.QueuedMessageBuffer;
import one.chartsy.trade.Order;
import one.chartsy.trade.PositionTarget;
import one.chartsy.trade.PositionMaintenance;
import one.chartsy.trade.PositionClose;
import one.chartsy.trade.PositionStopUpdate;

import java.util.Queue;

public class TradeConnectorProxy implements TradeConnector {

    private final String id;
    private final Queue<Message> queue;
    private final TradeConnectorWorker worker;
    private final QueuedMessageBuffer<Message> buffer;
    private final TradeConnectorInvoker invoker;

    public TradeConnectorProxy(TradeConnector target, Queue<Message> queue) {
        this(target.getId(), target, queue);
    }

    protected TradeConnectorProxy(String id, TradeConnector target, Queue<Message> queue) {
        this.id = id;
        this.queue = queue;
        this.buffer = new QueuedMessageBuffer<>(queue);
        this.invoker = new TradeConnectorInvoker(target);
        this.worker = new TradeConnectorWorker(target, buffer, invoker, TradeConnectorWorker.DEFAULT_POLL_LIMIT);
    }

    /**
     * Dispatches the messages queued at this boundary, without an asynchronous worker quota.
     * Used by single-threaded playback before matching a complete decision batch. Messages
     * enqueued by handlers are beyond the captured size and wait for another dispatch.
     * The caller must own a FIFO queue with no concurrent consumer during this call.
     * This performs message delivery only; service-worker lifecycle hooks are not invoked.
     *
     * @return the number of messages dispatched
     */
    public final int dispatchPendingMessages() {
        int pending = queue.size();
        return buffer.read(invoker, pending);
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
    public void onPositionStopUpdate(PositionStopUpdate request) {
        offer(request);
    }

    @Override
    public void onPositionClose(PositionClose request) {
        offer(request);
    }

    @Override
    public void onPositionMaintenance(PositionMaintenance request) {
        offer(request);
    }

    @Override
    public void onPositionTarget(PositionTarget target) {
        offer(target);
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
