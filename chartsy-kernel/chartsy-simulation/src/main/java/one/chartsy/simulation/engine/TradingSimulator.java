/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.engine;

import one.chartsy.SymbolIdentity;
import one.chartsy.financial.price.MarketPriceService;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageHandler;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.simulation.engine.account.SimulatorAccount;
import one.chartsy.trade.Order;
import one.chartsy.trade.data.OrderRequestEvent;
import one.chartsy.trade.service.OrderReportHandler;
import one.chartsy.trade.service.connector.AbstractTradeConnector;
import one.chartsy.trade.service.connector.TradeConnectorContext;
import one.chartsy.util.SequenceGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class TradingSimulator extends AbstractTradeConnector implements MarketMessageHandler {

    private static final String DEFAULT_ACCOUNT_ID = "";

    private final SequenceGenerator executionsIds = SequenceGenerator.create();
    private final MarketPriceService priceService;
    private final SimulatorAccount defaultAccount;
    private final Map<String, SimulatorAccount> subAccounts = new HashMap<>();
    private final Map<SymbolIdentity, InstrumentOrders> instrumentOrders = new HashMap<>();
    private final Map<String, Order> orders = new HashMap<>();
    private final OrderReportHandler orderReportHandler;

    public TradingSimulator(TradeConnectorContext context) {
        super(context);
        this.priceService = context.getPriceService();
        this.defaultAccount = createDefaultAccount();
        this.orderReportHandler = context.getOrderReportHandler();
    }

    protected SimulatorAccount createDefaultAccount() {
        return createAccount(DEFAULT_ACCOUNT_ID);
    }

    protected SimulatorAccount createAccount(String accountId) {
        return new SimulatorAccount(accountId);
    }

    public final SimulatorAccount getDefaultAccount() {
        return defaultAccount;
    }

    public final SimulatorAccount getOrCreateNewAccount(String accountId) {
        if (accountId == null || accountId.isEmpty()) {
            return defaultAccount;
        }
        return subAccounts.computeIfAbsent(accountId, this::createAccount);
    }

    public void forEachAccount(Consumer<? super SimulatorAccount> action) {
        action.accept(defaultAccount);
        subAccounts.values().forEach(action);
    }

    @Override
    public void onOrderPlacement(Order.New order) {
        getInstrumentOrders(order.symbol()).enqueue(order);
    }

    @Override
    public void open() {
    }

    @Override
    public void close() {
    }

    @Override
    public void onMarketMessage(MarketEvent event) {
        var orders = getInstrumentOrders(event.symbol());

        if (!orders.inboundOrders.isEmpty()) {
            processInboundOrders(event.time(), orders.inboundOrders, event);
        }
        if (!orders.workingOrders.isEmpty()) {
            processWorkingOrders(event.time(), orders.workingOrders, event);
        }
        updateValuations(event);
    }

    protected void updateValuations(MarketEvent event) {
        if (event instanceof TradeBar bar)
            forEachAccount(account -> account.onTradeBar(bar));
    }

    protected Order createOrderFrom(Order.New request) {
        return Order.from(request);
    }

    protected Order.Filled createOrderTradeReport(long time, Order order, double tradePrice, double tradeQuantity) {
        var executionId = executionsIds.next();
        var cumulativeQuantity = order.getFilledQuantity() + tradeQuantity;
        var averagePrice = (cumulativeQuantity == 0.0)
                ? order.getAverageFillPrice()
                : ((order.getAverageFillPrice() * order.getFilledQuantity()) + (tradePrice * tradeQuantity)) / cumulativeQuantity;
        return new Order.Filled(time, order.getId(), getId(), order.getSourceId(), executionId, order.getSymbol(), order.getSide(), tradeQuantity, tradePrice, cumulativeQuantity, averagePrice);
    }

    protected void fillOrder(long time, Order order, double tradePrice, double tradeQuantity) {
        var tradeReport = createOrderTradeReport(time, order, tradePrice, tradeQuantity);
        var quantityLeft = Math.max(0.0, order.getQuantity() - tradeReport.cumulativeQuantity());
        order.setFilledQuantity(tradeReport.cumulativeQuantity());
        order.setAverageFillPrice(tradeReport.averagePrice());

        getOrCreateNewAccount(order.getAccountId()).getOrCreateNewBalance(order.getCurrency()).onOrderFill(tradeReport);
        if (quantityLeft == 0.0) {
            order.setState(Order.State.FILLED);
            orderReportHandler.onOrderFilled(tradeReport);
        } else {
            order.setState(Order.State.PARTIALLY_FILLED);
            orderReportHandler.onOrderPartiallyFilled(new Order.PartiallyFilled(quantityLeft, tradeReport));
        }
    }

    protected void processInboundOrders(long time, List<OrderRequestEvent> inboundQueue, MarketEvent event) {
        for (int i = 0; i < inboundQueue.size(); i++) {
            var req = inboundQueue.get(i);
            var processed = false;
            if (req.time() >= time) {
                continue;
            } else if (req instanceof Order.New newOrder) {
                processed = processOrderPlacement(time, newOrder, event);
            } else {
                rejectOrderRequest(time, req, "Unrecognized order request type: " + req.getClass().getSimpleName());
            }

            if (processed) {
                inboundQueue.remove(i--);
            }
        }
    }

    protected void processWorkingOrders(long time, List<Order> workingOrders, MarketEvent event) {
        for (int i = 0; i < workingOrders.size(); i++) {
            var order = workingOrders.get(i);
            if (order.getExpirationTime() < time) {
                changeOrderStatus(time, order, Order.State.EXPIRED);
                workingOrders.remove(i--);
            }


        }
    }

    protected void rejectOrderRequest(long time, OrderRequestEvent req, String rejectionReason) {
        orderReportHandler.onOrderRejected(
                new Order.Rejected(time, req.orderId(), getId(), req.sourceId(), rejectionReason));
    }

    protected void changeOrderStatus(long time, Order order, Order.State newState) {
        order.setState(newState);
        orderReportHandler.onOrderStatusChanged(
                new Order.StatusChanged(time, order.getId(), getId(), order.getSourceId(), newState));
    }

    protected boolean processOrderPlacement(long time, Order.New order, MarketEvent event) {
        var simOrder = createOrderFrom(order);
        if (simOrder.getExpirationTime() < time) {
            changeOrderStatus(time, simOrder, Order.State.EXPIRED);
        } else if (simOrder.getValidSinceTime() > time) {
            return false;
        } else if (simOrder.getType().isImmediateOrCancelOnly()) {
            return tryFillIoCOrder(time, order, simOrder, event);
        } else {
            orders.put(simOrder.getId(), simOrder);
            var workingOrders = getInstrumentOrders(order.symbol()).workingOrders;
            workingOrders.add(simOrder);
            changeOrderStatus(time, simOrder, Order.State.SUBMITTED);
        }

        return true;
    }

    protected boolean tryFillIoCOrder(long time, Order.New req, Order order, MarketEvent event) {
        var tradeQuantity = order.getQuantity() - order.getFilledQuantity();
        var filled = false;
        if (event instanceof TradeBar tradeBar) {
            fillOrder(time, order, tradeBar.get().open(), tradeQuantity);
            filled = true;
        }

        if (!filled) {
            rejectOrderRequest(time, req, "Cannot fill IOC order - no suitable market data event");
        }
        return filled;
    }

    private InstrumentOrders getInstrumentOrders(SymbolIdentity symbol) {
        return instrumentOrders.computeIfAbsent(symbol, s -> new InstrumentOrders());
    }

    private static class InstrumentOrders {
        private final List<OrderRequestEvent> inboundOrders = new ArrayList<>();
        private final List<Order> workingOrders = new ArrayList<>();

        void enqueue(OrderRequestEvent event) {
            inboundOrders.add(event);
        }
    }
}
