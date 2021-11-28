package one.chartsy.simulation.engine;

import one.chartsy.*;
import one.chartsy.core.event.ListenerList;
import one.chartsy.data.Priced;
import one.chartsy.simulation.*;
import one.chartsy.time.Chronological;
import one.chartsy.trade.*;
import one.chartsy.trade.data.Position;
import one.chartsy.trade.event.ExecutionListener;
import one.chartsy.trade.event.PositionChangeListener;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SimpleMatchingEngine extends OrderStatusUpdater implements OrderBroker, OrderFiller {
    //private final SimulationContext context;
    private final SimulationAccount account;
    private final SimulationProperties properties;
    private final SimulationResult.Builder result;
    private final double spread;
    private final boolean allowSameBarExit;
    private final boolean allowTakeProfitSlippage;

    public SimpleMatchingEngine(SimulationProperties properties, SimulationResult.Builder result) {
        this.result = result;
        this.account = new SimulationAccount(properties);
        this.properties = properties;
        this.spread = properties.getSpread();
        this.allowSameBarExit = properties.isAllowSameBarExit();
        this.allowTakeProfitSlippage = properties.isAllowTakeProfitSlippage();
    }

    private final ListenerList<OrderStatusListener> orderStatusListeners = ListenerList.of(OrderStatusListener.class);

    public void addOrderStatusListener(OrderStatusListener listener) {
        orderStatusListeners.addListener(listener);
    }

    public void removeOrderStatusListener(OrderStatusListener listener) {
        orderStatusListeners.removeListener(listener);
    }

    protected void fireOrderCancelled(Order order) {
        if (!orderStatusListeners.isEmpty())
            orderStatusListeners.fire().orderStatusChanged(new OrderStatusEvent(order));
    }

    @Override
    protected void fireOrderStatusChanged(Order order) {
        if (!orderStatusListeners.isEmpty())
            orderStatusListeners.fire().orderStatusChanged(new OrderStatusEvent(order));
    }

    private final ListenerList<ExecutionListener> executionListeners = new ListenerList<>(ExecutionListener.class);

    public void addExecutionListener(ExecutionListener listener) {
        executionListeners.addListener(listener);
    }

    public void removeExecutionListener(ExecutionListener listener) {
        executionListeners.removeListener(listener);
    }

    public void addPositionChangeListener(PositionChangeListener listener) {
        account.addPositionChangeListener(listener);
    }

    public void removePositionChangeListener(PositionChangeListener listener) {
        account.removePositionChangeListener(listener);
    }

    protected void fireOrderExecution(Execution execution) {
        if (!executionListeners.isEmpty())
            executionListeners.fire().onExecution(execution);
    }

    private final AtomicInteger orderID = new AtomicInteger();

    @Override
    public Order submitOrder(Order order) {
        SimulationInstrument instrument = account.getInstrument(order.getSymbol());
        //instrument.orders().add(order);
        instrument.getTransmitQueue().add(order);
        toSubmitted(order, orderID.incrementAndGet(), currentTime);
        return order;
    }

    public void onData(When when, Chronological data, boolean timeTick) {
        if (data instanceof Candle)
            onData(when, (Candle) data);
        else if (data instanceof Priced)
            onData(when, ((Priced) data).toCandle());
    }

    private long getOrSetAcceptedTime(Order order, long currentTime) {
        long acceptedTime = order.getAcceptedTime();
        if (acceptedTime == 0)
            setAcceptedTime(order, acceptedTime = (currentTime + order.getOrderLatency()));

        return Math.max(acceptedTime, order.getValidSinceTime());
    }

    protected int onDataAtTheClose(List<Order> transmitQueue, List<Order> workingOrders, double closePrice, long time) {
        Candle closeCandle = null;
        int orderCount = transmitQueue.size();
        for (int i = 0; i < orderCount; i++) {
            Order order = transmitQueue.get(i);

            if (order.isCancelled())
                toCancelled(order, time);
            else if (order.getExpirationTime() < time)
                toExpired(order);
            else if (getOrSetAcceptedTime(order, time) > time)
                continue;
            else if (order.getTimeInForce() == TimeInForce.Standard.CLOSE) {
                if (closeCandle == null)
                    closeCandle = Candle.of(time, closePrice);
                SimulatedExecution execution = fillOrder(order, closeCandle, closeCandle.open());
                if (execution != null)
                    fireOrderExecution(execution);
                else
                    toRejected(order);
            } else if (order.getTimeInForce() == TimeInForce.Standard.OPEN || order.getType().isImmediateOrCancelOnly()) {
                transmitQueue.add(orderCount, order); // move at-the-open or market order to the end of the queue
            } else
                workingOrders.add(order);

            transmitQueue.remove(i--);
            orderCount--;
        }
        return orderCount;
    }

    protected void onDataAtTheOpen(List<Order> transmitQueue, List<Order> workingOrders, double openPrice, long time, int fromIndex) {
        Candle openCandle = null;
        int orderCount = transmitQueue.size();
        for (int i = fromIndex; i < orderCount; i++) {
            Order order = transmitQueue.get(i);

            if (order.isCancelled())
                toCancelled(order, time);
            else if (order.getExpirationTime() < time)
                toExpired(order);
            else if (getOrSetAcceptedTime(order, time) > time)
                continue;
            else if (order.getTimeInForce() == TimeInForce.Standard.CLOSE)
                toRejected(order);
            else if (order.getTimeInForce() == TimeInForce.Standard.OPEN || order.getType().isImmediateOrCancelOnly()) {
                if (openCandle == null)
                    openCandle = Candle.of(time, openPrice);
                SimulatedExecution execution = fillOrder(order, openCandle, openCandle.open());
                if (execution != null)
                    fireOrderExecution(execution);
                else
                    toRejected(order);
            } else
                workingOrders.add(order);

            transmitQueue.remove(i--);
            orderCount--;
        }
    }

    long currentTime = Long.MIN_VALUE;

    public void onData(When when, Candle ohlc) {
        SimulationInstrument instrument = account.getInstrument(when.getSymbol());
        List<Order> transmitQueue = instrument.getTransmitQueue();
        List<Order> orders = instrument.orders();
        if (!transmitQueue.isEmpty()) {
            int orderCount = 0;
            Candle lastCandle = instrument.lastCandle();
            if (lastCandle != null)
                orderCount = onDataAtTheClose(transmitQueue, orders, lastCandle.close(), lastCandle.getTime());
            onDataAtTheOpen(transmitQueue, orders, ohlc.open(), ohlc.getTime(), orderCount);
        }

        currentTime = ohlc.getTime();
        instrument.setLastCandle(ohlc);
        int orderCount = orders.size();
        Position position = instrument.position();
        if (position != null) {
            int type = position.getDirection().tag;
            double sl = position.getExitStop(), tp = position.getExitLimit();
            // check position stop loss hit
            if (sl == sl && (type > 0 && ohlc.low() <= sl || type < 0 && ohlc.high() >= sl - spread)) {
                if ((type > 0) ^ (ohlc.open() > sl))
                    sl = ohlc.open();
                SimulatedExecution execution = closePosition(position, ohlc, sl);
                execution.setStopLossHit(true);
                fireOrderExecution(/*position.getEntryOrder(),*/ execution);
            } else if (tp == tp && (type > 0 && ohlc.high() >= tp || type < 0 && ohlc.low() <= tp - spread)) {
                if ((type < 0) ^ (ohlc.open() > tp) && allowTakeProfitSlippage)
                    tp = ohlc.open();
                SimulatedExecution execution = closePosition(position, ohlc, tp);
                execution.setProfitTargetHit(true);
                fireOrderExecution(/*position.getEntryOrder(),*/ execution);
            }
        }

        orderCount = Math.min(orderCount, orders.size());
        for (int i = 0; i < orderCount; i++) {
            Order order = orders.get(i);

            if (order.isCancelled()) {
                toCancelled(order, ohlc.getTime());
            } else if (order.getExpirationTime() < currentTime) {
                toExpired(order);
            } else {
                // process an order
                Execution execution = order.getType().tryFill(order, ohlc, this);
                if (order.isFilled()) {
                    orders.remove(i--);
                    orderCount--;
                    if (execution != null) {
                        fireOrderExecution(execution);
                        if (allowSameBarExit) {
                            int tag = order.getSide().tag;
                            double sl = order.getExitStop(), tp = order.getExitLimit();
                            if (tag < 0 && ohlc.close() > sl || tag > 0 && ohlc.close() < sl) {
                                SimulatedExecution execution2 = closePosition(instrument.position(), ohlc, sl);
                                execution2.setStopLossHit(true);
                                fireOrderExecution(execution2);
                            } else if (tag < 0 && ohlc.close() < tp || tag > 0 && ohlc.close() > tp) {
                                SimulatedExecution execution2 = closePosition(instrument.position(), ohlc, tp);
                                execution2.setProfitTargetHit(true);
                                fireOrderExecution(execution2);
                            }
                        }
                    }
                }
                continue;
            }
            orders.remove(i--);
            orderCount--;
        }
        account.updateProfit(instrument.getSymbol(), ohlc);
    }

    protected SimulatedExecution closePosition(Position position, Candle ohlc, double price) {
        Order.Side exitOrderSide = (position.getDirection() == Direction.LONG)? Order.Side.SELL : Order.Side.BUY_TO_COVER;
        Order exitOrder = new Order(position.getSymbol(), OrderType.MARKET, exitOrderSide, position.getQuantity());
        //
        Direction side = position.getDirection().reverse();
        String executionId = String.valueOf(executionIds.incrementAndGet());
        SimulatedExecution execution = new SimulatedExecution(executionId, exitOrder, ohlc.getTime(), price, position.getQuantity());
        execution.setScaleOut(true);
        execution.setClosingCommission(position.getEntryOrder().getCommission(price, position.getQuantity(), position));

        account.exitPosition(position, execution);
        return execution;
    }

    private final AtomicLong executionIds = new AtomicLong();

    private final AtomicInteger positionIds = new AtomicInteger();

    @Override
    public SimulatedExecution fillOrder(Order order, Candle ohlc, double price) {
        SimulatedExecution execution = fillAtMarket(order, price, ohlc);
        if (execution != null)
            order.fill();
        return execution;
    }

    protected SimulatedExecution fillAtMarket(Order order, double price, Candle ohlc) {
        if ((price < ohlc.low() || price > ohlc.high()))
            throw new SimulationException(
                    "Non-transactional " + order.getSymbol() + " order price " + price + " at bar " + ohlc);

        SimulationInstrument instrument = account.getInstrument(order.getSymbol());
        Position position = instrument.position(); //

        if (order.isBuy())
            price += instrument.getSymbol().getSpread();
        order.setFillPrice(price);
        double volume = order.getQuantity();
        double openingCommission;
        Direction positionType;
        double positionSize;
        double tradeVolume = volume;
        SimulatedExecution execution;
        if (position != null) {
            if (position.getDirection().tag == order.getSide().tag) { // scale-in
                positionType = position.getDirection();
                positionSize = position.getQuantity() + volume;
                openingCommission = order.getCommission(price, volume, null);

                String executionId = String.valueOf(executionIds.incrementAndGet());
                execution = new SimulatedExecution(executionId, order, ohlc.getTime(), price, tradeVolume);
                execution.setScaleIn(true);
                execution.setOpeningCommission(order.getCommission(price, volume, null));

            } else if (position.getDirection().tag == -order.getSide().tag) { // stop and reverse
                positionType = position.getDirection().reverse();
                positionSize = volume;
                openingCommission = order.getCommission(price, volume, null);
                tradeVolume += position.getQuantity();

                String executionId = String.valueOf(executionIds.incrementAndGet());
                execution = new SimulatedExecution(executionId, order, ohlc.getTime(), price, tradeVolume);
                execution.setScaleIn(true);
                execution.setOpeningCommission(order.getCommission(price, volume, null));
                execution.setClosingCommission(order.getCommission(price, position.getQuantity(), position));

            } else if (position.getDirection().tag * order.getSide().tag < 0) { // scale-out
                positionType = position.getDirection();
                positionSize = position.getQuantity() - volume;
                openingCommission = 0.0;

                String executionId = String.valueOf(executionIds.incrementAndGet());
                execution = new SimulatedExecution(executionId, order, ohlc.getTime(), price, tradeVolume);
                execution.setScaleOut(true);
                execution.setClosingCommission(order.getCommission(price, volume, position));

            } else {
                return null;
            }
            account.exitPosition(position, execution);
            if (positionSize < 0.000001)
                return execution;

        } else {
            int tag = order.getSide().tag;
            if (tag * tag != 1)
                return null;
            openingCommission = order.getCommission(price, volume, null);
            positionType = order.getSide().getDirection();
            positionSize = order.getQuantity();

            String executionId = String.valueOf(executionIds.incrementAndGet());
            execution = new SimulatedExecution(executionId, order, ohlc.getTime(), price, positionSize);
            execution.setScaleIn(true);
            execution.setOpeningCommission(order.getCommission(price, volume, null));
        }
        position = new Position(
                positionIds.incrementAndGet(),
                order.getSymbol(),
                positionType,
                price,
                positionSize,
                order,
                openingCommission,
                ohlc.getTime()
        );
        account.enterPosition(position, ohlc.close(), ohlc.getTime());
        return execution;
    }

    public SimulationResult.Builder getResult() {
        return result;
    }

    public Account getAccount() {
        return account;
    }

    public void afterLast(SymbolIdentity symbol, Candle ohlc) {
        if (properties.isCloseAllPositionsAfterSimulation())
            closeAllPositionsAfterSimulation(symbol, ohlc);
    }

    private void closeAllPositionsAfterSimulation(SymbolIdentity symbol, Candle ohlc) {
        Position position = account.getPosition(symbol);
        if (position != null)
            closePosition(position, ohlc, ohlc.close());
    }
}
