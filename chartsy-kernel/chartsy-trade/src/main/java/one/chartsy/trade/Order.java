/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.*;

import lombok.Getter;
import one.chartsy.SymbolIdentity;
import one.chartsy.core.CustomValuesHolder;
import one.chartsy.time.Chronological;
import one.chartsy.time.Clock;
import one.chartsy.trade.data.OrderReportEvent;
import one.chartsy.trade.data.OrderRequestEvent;
import one.chartsy.trade.data.Position;
import one.chartsy.util.SequenceGenerator;

@Getter
public class Order implements java.io.Serializable, Cloneable, CustomValuesHolder {
    @Serial
    private static final long serialVersionUID = -4283839048397874813L;
    /** The empty array of {@code OrderStatusListener}'s shared between all new {@code Order} objects. */
    private static final OrderStatusListener[] EMPTY_LISTENER_LIST = new OrderStatusListener[0];
    /** The unique order identifier. */
    private String id;
    /** The order state. */
    private State state = State.NEWLY_CREATED;
    /** The previous order state. */
    private State previousState = State.NEWLY_CREATED;
    /** The symbol of the order. */
    private final SymbolIdentity symbol;
    /** The order type (market, limit, stop, ...) */
    private final OrderType type;
    /** The side of the order (buy, sell, sell short or buy to cover). */
    private final Side side;
    /** The order quantity. */
    private double quantity;
    /** The order's quantity filled so far. */
    private double filledQuantity;
    /** The stop loss price. */
    private double exitStop = Double.NaN;
    /** The take profit price. */
    private double exitLimit = Double.NaN;
    /** The price at which order has been filled, or {@code 0.0} if undefined. */
    private double averageFillPrice;
    /** The number of bars after which the order will be cancelled if it has not been filled. */
    private int barsValid;
    /** The time in force. */
    private TimeInForce timeInForce;
    /** The order expiration date and time. */
    private long expirationTime = Long.MAX_VALUE;
    /** The order validity start date and time. */
    private long validSinceTime = Long.MIN_VALUE;
    /** The trading algorithm from where the order was submitted. */
    private String sourceId;
    /** A user-definable rule/signal description for the order. */
    private String ruleLabel;
    /** The order that is replaced by this one. */
    private Order replacee;
    /** The order that replaced this one. */
    private Order replacement;
    /** The position on the order replacement history chain. */
    private int cancelReplaceCount;
    /** The date and time when this order was submitted. */
    private long timeSubmitted;
    /** The order submission latency in a backtest. */
    private long orderLatency;
    /** A user-definable set of custom values associated with the order. */
    private Map<String, Object> customValues = Collections.emptyMap();
    /** The rejection reason, may be null. */
    private String rejectionReason;
    /** The original order identifier for replacements (links to prior order). */
    private String originalOrderId;
    /** The destination/venue/broker identifier. */
    private String destinationId;
    /** The account identifier on which the order is placed. */
    private String accountId;
    /** The ISO currency code associated with the order. */
    private String currency;
    /** The minimum acceptable execution quantity. */
    private double minQuantity;
    /** The reference order identifier (external correlation link). */
    private String referenceOrderId;
    /** The client request creation time (epoch nanos). */
    private long requestTime;

    /** The list of {@code OrderStatusListener}'s associated with this order. */
    private OrderStatusListener[] listeners = EMPTY_LISTENER_LIST;
    

    /**
     * Reflects the side of an {@link Order} (Buy, Sell, Sell Short or Buy to Cover).
     * 
     * @author Mariusz Bernacki
     * 
     */
    public enum Side {
        /** The buy transaction. Closes a short transaction. */
        BUY(1),
        /** The sell transaction. */
        SELL(-2),
        /** The sell short transaction. Closes a long transaction. */
        SELL_SHORT(-1),
        /** The buy to cover transaction. */
        BUY_TO_COVER(2);
        
        /**
         * Unique tag number of this {@code Side} object, used internally by the framework.
         */
        public final int tag;
        
        Side(int tag) {
            this.tag = tag;
        }
        
        /**
         * Returns {@code true} if the order side is either {@link #BUY} or
         * {@link #BUY_TO_COVER} side.
         * 
         * @return {@code true} if the order side is a buy side
         */
        public boolean isBuy() {
            return tag > 0;
        }
        
        /**
         * Returns {@code true} if the order side represents an opening or
         * scale-in order, i.e. either {@link #BUY} or {@link #SELL_SHORT} side.
         * 
         * @return {@code true} if the order side is an entry
         */
        public boolean isEntry() {
            return (tag & 1) > 0;
        }
        
        public final Direction getDirection() {
            return (tag > 0)? Direction.LONG : Direction.SHORT;
        }
        
        public final Side opposite() {
            return switch (this) {
                case BUY, BUY_TO_COVER -> SELL_SHORT;
                case SELL, SELL_SHORT -> BUY;
            };
        }
    }

    public enum State {
        /** The order was cancelled by a user. */
        CANCELLED ("cancel"),
        /** The order cancellation is pending. */
        CANCELLING ("request cancelling"), // TODO: not implemented yet
        /** The completely filled order. */
        FILLED ("fill"),
        /** The partially filled order state. */
        PARTIALLY_FILLED ("partially fill"), // TODO: not implemented yet
        /** The order was received and acknowledged by the broker service or exchange. */
        ACCEPTED ("acknowledge"), // TODO: not implemented yet
        /** The order was rejected by the system or a user code. */
        REJECTED ("reject"),
        /** The state of an order submitted to the broker service or exchange. */
        SUBMITTED ("submit"),
        /** The newly created order state. */
        NEWLY_CREATED ("create"),
        /** The order has expired. */
        EXPIRED ("expire"); // TODO: not implemented yet

        private final String action;
        private Set<State> transitionsAllowed;

        State(String action) {
            this.action = action;
        }

        public boolean isDone() {
            return transitionsAllowed.isEmpty() || transitionsAllowed.contains(this) && transitionsAllowed.size() == 1;
        }

        public State to(State next) {
            if (!transitionsAllowed.contains(next)) {
                throw new IllegalStateException("Cannot " + next.action + " " + name() + " order");
            }
            return next;
        }
        
        public State toCancelled() {
            throw new IllegalStateException("Cannot cancel " + name() + " order");
        }
        
        public State toFilled() {
            return to(FILLED);
        }
        
        public final boolean isNewlyCreated() {
            return (this == NEWLY_CREATED);
        }
        
        public final boolean isSubmitted() {
            return (this == SUBMITTED);
        }
        
        public final boolean isCancelled() {
            return (this == CANCELLED);
        }

        public final boolean isFilled() {
            return (this == FILLED);
        }

        static {
            Set<State> NONE = EnumSet.noneOf(State.class);
            CANCELLING.transitionsAllowed = CANCELLED.transitionsAllowed = EnumSet.of(CANCELLED);
            FILLED.transitionsAllowed = NONE;
            EXPIRED.transitionsAllowed = NONE;
            REJECTED.transitionsAllowed = NONE;
            PARTIALLY_FILLED
                    .transitionsAllowed = EnumSet.of(
                            FILLED, PARTIALLY_FILLED, CANCELLING, CANCELLED);
            ACCEPTED
                    .transitionsAllowed = EnumSet.of(
                            FILLED, PARTIALLY_FILLED, CANCELLING, CANCELLED, EXPIRED);
            SUBMITTED
                    .transitionsAllowed = EnumSet.of(
                            ACCEPTED, FILLED, PARTIALLY_FILLED, CANCELLING, CANCELLED, EXPIRED, REJECTED);
            NEWLY_CREATED
                    .transitionsAllowed = EnumSet.of(
                            SUBMITTED, CANCELLING, CANCELLED);
        }
    }
    
    @Override
    public Order clone() {
        try {
            return (Order) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError("Shouldn't happen");
        }
    }
    
    public synchronized void addOrderStatusListener(OrderStatusListener l) {
        if (listeners == EMPTY_LISTENER_LIST) {
            // if this is the first listener added, 
            // initialize the lists
            listeners = new OrderStatusListener[] { l };
        } else {
            // Otherwise copy the array and add the new listener
            int i = listeners.length;
            OrderStatusListener[] tmp = Arrays.copyOf(listeners, i + 1);
            tmp[i] = l;
            listeners = tmp;
        }
    }
    
    public synchronized void removeOrderStatusListener(OrderStatusListener l) {
        // Is l on the list?
        int index = -1;
        
        for (int i = listeners.length - 1; i >= 0; i--) {
            if (listeners[i].equals(l)) {
                index = i;
                break;
            }
        }
        
        // If so,  remove it
        if (index != -1) {
            OrderStatusListener[] tmp = new OrderStatusListener[listeners.length - 1];
            // Copy the list up to index
            System.arraycopy(listeners, 0, tmp, 0, index);
            // Copy from two past the index, up to
            // the end of tmp (which is two elements
            // shorter than the old list)
            if (index < tmp.length)
                System.arraycopy(listeners, index + 1, tmp, index, tmp.length - index);
            // set the listener array to the new array or null
            listeners = (tmp.length == 0) ? EMPTY_LISTENER_LIST : tmp;
        }
    }
    
    protected void fireOrderStatusChanged(State oldStatus) {
        OrderStatusListener[] listeners = this.listeners;
        if (listeners.length > 0) {
            OrderStatusEvent e = new OrderStatusEvent(this, oldStatus);
            
            for (OrderStatusListener listener : listeners)
                listener.orderStatusChanged(e);
        }
    }
    
    /**
     * Returns the commission of opening or closing position by the given order.
     * 
     * @param price
     *            the current trade price
     * @param volume
     *            the current trade quantity
     * @param position
     *            the currently closing position or {@code null} if an opening
     *            commission is requested
     * @return the calculated commission
     */
    public double getCommission(double price, double volume, Position position) {
        //if (position != null)
        //    return 0.0003 * volume;
        //return 0.0039 * price * volume;
        //return 0.004 * price;
        //return 0.9;
        return 0.0;
    }
    
    public Order(SymbolIdentity symbol, OrderType type, Side side) {
        this(symbol, type, side, null);
    }
    
    public Order(SymbolIdentity symbol, OrderType type, Side side, TimeInForce tif) {
        this(symbol, type, side, side.isEntry()? 1 : Integer.MAX_VALUE, tif);
    }
    
    public Order(SymbolIdentity symbol, OrderType type, Side side, double volume) {
        this(symbol, type, side, volume, null);
    }
    
    public Order(SymbolIdentity symbol, OrderType type, Side side, double volume, TimeInForce tif) {
        this.symbol = symbol;
        this.type = type;
        this.side = side;
        this.quantity = volume;
        this.timeInForce = tif;
    }
    
    public Order(SymbolIdentity symbol, OrderType type, Side side, double volume, double exitStop, double exitLimit) {
        this(symbol, type, side, volume, exitStop, exitLimit, null);
    }
    
    public Order(SymbolIdentity symbol, OrderType type, Side side, double volume, double exitStop, double exitLimit, TimeInForce tif) {
        this.symbol = symbol;
        this.type = type;
        this.side = side;
        this.exitStop = exitStop;
        this.exitLimit = exitLimit;
        this.quantity = volume;
        this.timeInForce = tif;
    }
    
    public final String getId() {
        return id;
    }
    
    Order toSubmitted(String id, long time) {
        setState(state.to(State.SUBMITTED));
        this.id = id;
        this.timeSubmitted = time;
        return this;
    }

    private static long UNSPECIFIED = 0;
    /** The date and time when this order was cancelled. */
    private long cancelledTime;
    /** The date and time when the order was accepted by the broker. */
    private long acceptedTime;

    Order toExpired() {
        setState(State.EXPIRED);
        return this;
    }

    Order toCancelled(long time) {
        setState(state.to(State.CANCELLED));
        this.cancelledTime = time;
        return this;
    }

    Order toRejected() {
        return toRejected(null);
    }

    Order toRejected(String rejectionReason) {
        setState(state.to(State.REJECTED));
        this.rejectionReason = rejectionReason;
        return this;
    }

    public final String getRejectionReason() {
        return rejectionReason;
    }

    Order toAccepted(long time) {
        setState(State.ACCEPTED);
        this.acceptedTime = time;
        return this;
    }

    void setAcceptedTime(long time) {
        this.acceptedTime = time;
    }

    public long getAcceptedTime() {
        return acceptedTime;
    }

    public long getCancelledTime() {
        return cancelledTime;
    }

    public Optional<LocalDateTime> getCancelledDateTime() {
        return (cancelledTime == UNSPECIFIED)? Optional.empty() : Optional.of(Chronological.toDateTime(cancelledTime));
    }

    public final double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        if (quantity < 0.0)
            throw new IllegalArgumentException(String.format("Order quantity `%f` cannot be negative", quantity));
        var status = getState();
        if (status != State.NEWLY_CREATED)
            throw new InvalidOrderStatusException(status, String.format("Cannot setQuantity on %s order", status));

        this.quantity = quantity;
    }

    public void setFilledQuantity(double filledQuantity) {
        this.filledQuantity = filledQuantity;
    }

    /**
     * @return the stopLoss
     */
    public double getExitStop() {
        return exitStop;
    }
    
    /**
     * @param exitStop the exit stop loss to set
     */
    public void setExitStop(double exitStop) {
        this.exitStop = exitStop;
    }
    
    /**
     * @return the profitTarget
     */
    public double getExitLimit() {
        return exitLimit;
    }
    
    /**
     * @param exitLimit the profit target to set
     */
    public void setExitLimit(double exitLimit) {
        this.exitLimit = exitLimit;
    }
    
    /**
     * @return the barsValid
     */
    public int getBarsValid() {
        return barsValid;
    }
    
    /**
     * @param barsValid the barsValid to set
     */
    public void setBarsValid(int barsValid) {
        this.barsValid = barsValid;
    }
    
    public LocalDateTime getExpirationDate() {
        return (expirationTime == Long.MAX_VALUE)? null : Chronological.toDateTime(expirationTime);
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationDateTime(LocalDateTime expirationDate) {
        if (expirationDate == null)
            throw new IllegalArgumentException("expirationDate cannot be NULL");

        this.expirationTime = Chronological.toEpochNanos(expirationDate);
        this.timeInForce = TimeInForce.GTD;
    }

    public long getValidSinceTime() {
        return validSinceTime;
    }

    public Optional<LocalDateTime> getValidSinceDateTime() {
        return (validSinceTime == Long.MIN_VALUE)? Optional.empty() : Optional.of(Chronological.toDateTime(validSinceTime));
    }

    public void setValidSinceTime(long validSinceTime) {
        this.validSinceTime = validSinceTime;
    }

    /**
     * @return the symbol
     */
    public final SymbolIdentity getSymbol() {
        return symbol;
    }
    
    /**
     * @return the type
     */
    public final OrderType getType() {
        return type;
    }
    
    /**
     * Gives the side of the order.
     * 
     * @return the order side
     */
    public final Side getSide() {
        return side;
    }
    
    /**
     * @return the state
     */
    public final State getState() {
        return state;
    }

    public final State getPreviousState() {
        return previousState;
    }

    public void setState(State newState) {
        State oldState = this.state;
        if (oldState != newState) {
            this.previousState = oldState;
            this.state = newState;
            fireOrderStatusChanged(oldState);
        }
    }
    
    public void cancel() throws IllegalStateException {
        setState(state.toCancelled());
    }
    
    void cancelBySystem() throws IllegalStateException {
        setState(state.toCancelled());
    }
    
    // TODO: make it package private
    public void fill() {
        setState(state.toFilled());
    }
    
    public boolean isCancelled() {
        return state.isCancelled();
    }
    
    public boolean isFilled() {
        return state.isFilled();
    }
    
    /**
     * @return the fillPrice
     */
    public double getAverageFillPrice() {
        return averageFillPrice;
    }
    
    /**
     * @param averageFillPrice the fillPrice to set
     */
    public void setAverageFillPrice(double averageFillPrice) {
        this.averageFillPrice = averageFillPrice;
    }

    public long getTimeSubmitted() {
        return timeSubmitted;
    }

    public Optional<LocalDateTime> getDateTimeSubmitted() {
        long timeSubmitted = this.timeSubmitted;
        return (timeSubmitted == 0)? Optional.empty() : Optional.of(Chronological.toDateTime(timeSubmitted));
    }

    /**
     * The entry risk of the order, which is a difference between the order
     * entry price and the protective exit stop price.
     * <p>
     * This is not adjusted by the fill price, and it's recomputed on each method
     * call even if the order is already filled. If the order has no protective
     * stop {@code Double.NaN} is returned, otherwise the returned value is
     * always non-negative regardless of the order {@link #getType() type} and
     * {@link #getSide() side}. The calculated entry risk is automatically
     * copied by the framework to the instruments entry risk property once the
     * order is filled.
     * 
     * @return the order entry risk, or {@code Double.NaN} if undefined
     */
    public double getEntryRisk() {
        int directionTag = side.getDirection().intValue();
        return (getAverageFillPrice() - getExitStop()) * directionTag;
    }
    
    /**
     * Returns {@code true} if the order is either {@link Side#BUY} or
     * {@link Side#BUY_TO_COVER} side.
     * 
     * @return {@code true} if the order is a buy order
     */
    public final boolean isBuy() {
        return side.isBuy();
    }
    
    /**
     * Returns {@code true} if the order is an opening or scale-in order, i.e.
     * either {@link Side#BUY} or {@link Side#SELL_SHORT} side.
     * 
     * @return {@code true} if the order is an entry
     */
    public final boolean isEntry() {
        return side.isEntry();
    }
    
    @Override
    public Map<String, Object> getCustomValues() {
        Map<String, Object> customValues = this.customValues;
        if (customValues == null)
            return Collections.emptyMap();
        
        return customValues;
    }
    
    @Override
    public void setCustomValue(String name, Object value) {
        customValues = CustomValuesHolder.setCustomValue(customValues, name, value);
    }
    
    @Override
    public void removeCustomValue(String name) {
        customValues = CustomValuesHolder.removeCustomValue(customValues, name);
    }
    
    /**
     * @return the ruleLabel
     */
    public String getRuleLabel() {
        return ruleLabel;
    }
    
    /**
     * Tags an entry or exit order with a user-definable rule label.
     * <p>
     * Rule labels are used to identify executions resulting from the order on a
     * chart and on a performance reports.
     * 
     * @param ruleLabel
     *            the ruleLabel to set
     */
    public void setRuleLabel(String ruleLabel) {
        this.ruleLabel = ruleLabel;
    }
    
    /**
     * @return the timeInForce
     */
    public final TimeInForce getTimeInForce() {
        return timeInForce;
    }
    
    /**
     * Gives the order that is cancelled and replaced by the current order. The
     * method returns a predecessor in an order replacement history chain. Returns
     * {@code null} if this order has no replacee.
     * 
     * @return the order replacee, may be {@code null}
     */
    public final Order getReplacee() {
        return replacee;
    }
    
    /**
     * Gives the order that cancelled and replaced the current order. The method
     * returns a successor in an order replacement history chain. Returns
     * {@code null} if this order wasn't replaced.
     * 
     * @return the order replacement, may be {@code null}
     */
    public final Order getReplacement() {
        return replacement;
    }
    
    /**
     * Called internally by the framework to assign an order replacement.
     * 
     * @param replacement
     *            the order replacement to set
     */
    void setReplacement(Order replacement) {
        if (replacement.replacee != null && replacement.replacee != this)
            throw new IllegalStateException(
                    "The given replacement order already replaces another order #" + replacement.replacee.getId());
        if (this.replacement != null && this.replacement != replacement)
            throw new IllegalStateException(
                    "The current order is already replaced by another order #" + this.replacement.getId());
        
        this.replacement = replacement;
        replacement.replacee = this;
        replacement.cancelReplaceCount = 1 + this.cancelReplaceCount;
    }

    public long getOrderLatency() {
        return orderLatency;
    }

    public void setOrderLatency(long orderLatency) {
        if (orderLatency < -1)
            throw new IllegalArgumentException("orderLatency argument `" + orderLatency + "`must be between -1 and MAX_VALUE");

        this.orderLatency = orderLatency;
    }

    public String getSourceId() {
        return sourceId;
    }

    public void setSourceId(String sourceId) {
        if (!sourceId.equals(this.sourceId)) {
            if (this.sourceId != null)
                throw new InvalidOrderStatusException(getState(), String.format("Order.source already set (%s), can't change to %s", this.sourceId, sourceId));
            else
                this.sourceId = sourceId;
        }
    }

    // Inside class Order

    public static Order from(Order.New req) {
        Objects.requireNonNull(req, "req");
        Order o = new Order(req.symbol(), req.type(), req.side(), req.quantity(), req.timeInForce());
        o.expirationTime = req.expirationTime();
        o.validSinceTime = req.validSinceTime();
        o.setSourceId(req.sourceId());
        o.id = req.orderId();
        o.accountId = req.accountId();
        o.destinationId = req.destinationId();
        o.currency = req.currency();
        o.minQuantity = req.minQuantity();
        o.referenceOrderId = req.referenceOrderId();
        o.requestTime = req.time();
        return o;
    }

    public static Order from(Order.Replacement replacement) {
        Objects.requireNonNull(replacement, "replacement");
        Order o = from(replacement.newOrder());
        o.originalOrderId = replacement.originalOrderId();
        return o;
    }

    public static class Builder {
        private long time;
        private String sourceId;
        private String destinationId;
        private String orderId;
        private String originalOrderId;
        private String accountId;
        private SymbolIdentity symbol;
        private OrderType type;
        private Side side;
        private double quantity;
        private String currency;
        private TimeInForce timeInForce;
        private long expirationTime = Long.MAX_VALUE;
        private long validSinceTime = Long.MIN_VALUE;
        private double minQuantity;
        private String referenceOrderId;
        private final Clock clock;
        private final SequenceGenerator orderIdGenerator;

        public Builder() {
            this(null, null);
        }

        public Builder(Clock clock, SequenceGenerator orderIdGenerator) {
            this.clock = clock;
            this.orderIdGenerator = orderIdGenerator;
        }

        public Builder time(long time) {
            this.time = time;
            return this;
        }

        public Builder sourceId(String sourceId) {
            this.sourceId = sourceId;
            return this;
        }

        public Builder destinationId(String destinationId) {
            this.destinationId = destinationId;
            return this;
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder originalOrderId(String originalOrderId) {
            this.originalOrderId = originalOrderId;
            return this;
        }

        public Builder accountId(String accountId) {
            this.accountId = accountId;
            return this;
        }

        public Builder symbol(SymbolIdentity symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder type(OrderType type) {
            this.type = type;
            return this;
        }

        public Builder side(Side side) {
            this.side = side;
            return this;
        }

        public Builder quantity(double quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder currency(String currency) {
            this.currency = currency;
            return this;
        }

        public Builder timeInForce(TimeInForce tif) {
            this.timeInForce = tif;
            return this;
        }

        public Builder expirationTime(long expirationTime) {
            this.expirationTime = expirationTime;
            return this;
        }

        public Builder validSinceTime(long validSinceTime) {
            this.validSinceTime = validSinceTime;
            return this;
        }

        public Builder minQuantity(double minQuantity) {
            this.minQuantity = minQuantity;
            return this;
        }

        public Builder referenceOrderId(String referenceOrderId) {
            this.referenceOrderId = referenceOrderId;
            return this;
        }

        protected long time() {
            if (time != 0)
                return time;
            else if (clock != null)
                return clock.time();
            else
                throw new IllegalStateException("`time` is not set and no `clock` is provided to generate it");
        }

        protected String orderId() {
            if (orderId != null)
                return orderId;
            else if (orderIdGenerator != null)
                return orderIdGenerator.next();
            else
                throw new IllegalStateException("`orderId` is not set and no `orderIdGenerator` is provided to generate it");
        }

        public New toNewOrder() {
            return new New(
                    time(),
                    orderId(),
                    sourceId,
                    destinationId,
                    accountId,
                    symbol,
                    type,
                    side,
                    quantity,
                    currency,
                    timeInForce,
                    expirationTime,
                    validSinceTime,
                    minQuantity,
                    referenceOrderId
            );
        }

        public Replacement toReplacementOrder() {
            if (originalOrderId == null)
                throw new IllegalArgumentException("`originalOrderId` is required for order replacements");

            return new Replacement(originalOrderId, toNewOrder());
        }
    }

    /* --- Domain model for order requests and order reports. --- */

    public record New (
            long time,
            String orderId,
            String sourceId,
            String destinationId,
            String accountId,
            SymbolIdentity symbol,
            OrderType type,
            Side side,
            double quantity,
            String currency,
            TimeInForce timeInForce,
            long expirationTime,
            long validSinceTime,
            double minQuantity,
            String referenceOrderId
    ) implements OrderRequestEvent { }

    public record Replacement (String originalOrderId, New newOrder) implements OrderRequestEvent {

        @Override
        public String orderId() {
            return newOrder().orderId();
        }

        @Override
        public long time() {
            return newOrder().time();
        }
    }

    public record Cancellation (
            long time,
            String orderId,
            String sourceId,
            String destinationId,
            String originalOrderId,
            String reason
    ) implements OrderRequestEvent { }

    public record Rejected (
            long time,
            String orderId,
            String sourceId,
            String destinationId,
            String rejectionReason
    ) implements OrderReportEvent { }

    public record StatusChanged (
            long time,
            String orderId,
            String sourceId,
            String destinationId,
            State state
    ) implements OrderReportEvent { }

    public record Filled (
            long time,
            String orderId,
            String sourceId,
            String destinationId,
            String executionId,
            SymbolIdentity symbol,
            Side side,
            double tradeQuantity,
            double tradePrice,
            double cumulativeQuantity,
            double averagePrice
    ) implements OrderReportEvent, OrderTrade {

        @Override
        public long time() {
            return time();
        }

        @Override
        public boolean isBuy() {
            return side.isBuy();
        }
    }

    public record PartiallyFilled (double quantityLeft, Filled filled) implements OrderReportEvent, OrderTrade {
        @Override
        public String orderId() {
            return filled.orderId();
        }

        @Override
        public long time() {
            return filled.time();
        }

        @Override
        public boolean isBuy() {
            return filled().isBuy();
        }

        @Override
        public SymbolIdentity symbol() {
            return filled.symbol();
        }

        @Override
        public double tradePrice() {
            return filled.tradePrice();
        }

        @Override
        public double tradeQuantity() {
            return filled.tradeQuantity();
        }
    }
}
