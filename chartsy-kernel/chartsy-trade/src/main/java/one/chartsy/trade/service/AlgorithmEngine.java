/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.service;

import one.chartsy.messaging.common.ShutdownResponse;
import one.chartsy.messaging.common.handlers.ShutdownResponseHandler;
import one.chartsy.service.Service;
import one.chartsy.time.Clock;
import one.chartsy.trade.Order;
import one.chartsy.trade.PositionTarget;
import one.chartsy.trade.PositionMaintenance;
import one.chartsy.trade.PositionClose;
import one.chartsy.trade.PositionRequest;
import one.chartsy.trade.PositionStopUpdate;
import one.chartsy.trade.algorithm.Algorithm;
import one.chartsy.trade.service.connector.TradeConnector;
import one.chartsy.trade.service.engine.EngineServiceRegistry;
import one.chartsy.trade.service.engine.OrderRoutingStrategy;
import one.chartsy.util.SequenceGenerator;

import java.util.Locale;

/**
 * Mode-agnostic engine that orchestrates a trading {@link Algorithm} and its surrounding
 * infrastructure - queues, trading connector worker, and an event bus - within a single,
 * deterministic event loop.
 * <p>
 * The {@code AlgorithmEngine} is intentionally neutral with respect to execution mode:
 * it can drive the same algorithm in backtest or live environments, provided that the
 * supplied dependencies (time source, market data, trading connector, event bus) are
 * appropriate for the chosen mode.
 *
 * @author Mariusz Bernacki
 */
public class AlgorithmEngine implements OrderHandler, ShutdownResponseHandler {

    private final Clock clock;
    private final SequenceGenerator sequenceGenerator = SequenceGenerator.create();
    private final EngineServiceRegistry serviceRegistry = new EngineServiceRegistry();
    private OrderRoutingStrategy orderRoutingStrategy = OrderRoutingStrategy.BY_REQUEST_DESTINATION;
    private volatile boolean shutdown;

    public AlgorithmEngine(Clock clock) {
        this.clock = clock;
    }

    public void addAlgorithm(Algorithm algorithm) {
        serviceRegistry.addAlgorithm(algorithm);
    }

    public void addTradeConnector(TradeConnector connector) {
        serviceRegistry.addTradeConnector(connector);
    }

    public void addCustomService(Service service) {
        serviceRegistry.addCustomService(service);
    }

    public final SequenceGenerator getSequenceGenerator() {
        return sequenceGenerator;
    }

    public final boolean isShutdown() {
        return shutdown;
    }

    @Override
    public void onOrderPlacement(Order.New order) {
        String destinationId = order.destinationId();
        if (destinationId == null) {
            destinationId = orderRoutingStrategy.getServiceId(order, null);
        }

        serviceRegistry.getOrderRequestService(destinationId).onOrderPlacement(order);
    }

    @Override
    public void onPositionStopUpdate(PositionStopUpdate request) {
        destinationOf(request, "Position stop update").onPositionStopUpdate(request);
    }

    @Override
    public void onPositionClose(PositionClose request) {
        destinationOf(request, "Position close").onPositionClose(request);
    }

    @Override
    public void onPositionMaintenance(PositionMaintenance request) {
        destinationOf(request, "Position maintenance").onPositionMaintenance(request);
    }

    @Override
    public void onPositionTarget(PositionTarget target) {
        destinationOf(target, "Position target").onPositionTarget(target);
    }

    /** Position requests are never routed by strategy: their destination owns the position. */
    private OrderRequestHandler destinationOf(PositionRequest request, String requestName) {
        String destinationId = request.destinationId();
        if (destinationId == null)
            throw new IllegalArgumentException(requestName + " requires an explicit destination");
        var handler = serviceRegistry.getOrderRequestService(destinationId);
        if (handler == null)
            throw new IllegalArgumentException("Unknown " + requestName.toLowerCase(Locale.ROOT)
                    + " destination: " + destinationId);
        return handler;
    }

    @Override
    public void onOrderRejected(Order.Rejected rejection) {
        serviceRegistry.getOrderReportService(rejection.destinationId()).onOrderRejected(rejection);
    }

    @Override
    public void onOrderStatusChanged(Order.StatusChanged change) {
        serviceRegistry.getOrderReportService(change.destinationId()).onOrderStatusChanged(change);
    }

    @Override
    public void onOrderFilled(Order.Filled fill) {
        serviceRegistry.getOrderReportService(fill.destinationId()).onOrderFilled(fill);
    }

    @Override
    public void onOrderPartiallyFilled(Order.PartiallyFilled partialFill) {
        serviceRegistry.getOrderReportService(partialFill.destinationId()).onOrderPartiallyFilled(partialFill);
    }

    @Override
    public void onShutdownResponse(ShutdownResponse response) {
        this.shutdown = true;
    }
}
