/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.service.engine;

import one.chartsy.service.Service;
import one.chartsy.trade.algorithm.Algorithm;
import one.chartsy.trade.service.OrderReportHandler;
import one.chartsy.trade.service.OrderRequestHandler;
import one.chartsy.trade.service.connector.TradeConnector;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EngineServiceRegistry implements ServiceRegistrar, ServiceDirectory {

    private final Map<String, Service> serviceMap = new ConcurrentHashMap<>();
    private final Map<String, OrderRequestHandler> orderRequestServiceMap = new ConcurrentHashMap<>();
    private final Map<String, OrderReportHandler> orderReportServiceMap = new ConcurrentHashMap<>();

    @Override
    public void addAlgorithm(Algorithm algorithm) {
        String id = algorithm.getId();
        addServiceImpl(id, algorithm);
    }

    @Override
    public void addTradeConnector(TradeConnector connector) {
        String id = connector.getId();
        addServiceImpl(id, connector);
    }

    @Override
    public void addCustomService(Service service) {
        String id = service.getId();
        addServiceImpl(id, service);
    }

    @Override
    public final OrderRequestHandler getOrderRequestService(String id) {
        return orderRequestServiceMap.get(id);
    }

    @Override
    public final OrderReportHandler getOrderReportService(String id) {
        return orderReportServiceMap.get(id);
    }

    protected void addOrderRequestService(String id, OrderRequestHandler handler) {
        orderRequestServiceMap.put(id, handler);
    }

    protected void addOrderReportService(String id, OrderReportHandler service) {
        orderReportServiceMap.put(id, service);
    }

    protected void addServiceImpl(String id, Service service) {
        Service existing = serviceMap.putIfAbsent(id, service);
        if (existing != null) {
            String existingType = existing.getClass().getSimpleName();
            throw new IllegalArgumentException("Registry already has service of type " + existingType + " with id: " + id);
        }

        if (service instanceof OrderRequestHandler handler)
            addOrderRequestService(id, handler);
        if (service instanceof OrderReportHandler handler)
            addOrderReportService(id, handler);
    }
}
