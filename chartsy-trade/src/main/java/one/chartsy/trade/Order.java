/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.trade;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.*;

import one.chartsy.SymbolIdentity;
import one.chartsy.commons.CustomValuesHolder;
import one.chartsy.time.Chronological;
import one.chartsy.trade.data.Position;

public class Order implements java.io.Serializable, Cloneable, CustomValuesHolder {
    @Serial
    private static final long serialVersionUID = -4283839048397874813L;
    /** The empty array of {@code OrderStatusListener}'s shared between all new {@code Order} objects. */
    private static final OrderStatusListener[] EMPTY_LISTENER_LIST = new OrderStatusListener[0];
    /** The unique order identifier. */
    private int id;
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
    /** The stop loss price. */
    private double exitStop = Double.NaN;
    /** The take profit price. */
    private double exitLimit = Double.NaN;
    /** The price at which order has been filled, or {@code Double.NaN} if undefined. */
    private double fillPrice = Double.NaN;
    /** The number of bars after which the order will be cancelled if it has not been filled. */
    private int barsValid;
    /** The time in force. */
    private TimeInForce timeInForce;
    /** The order expiration date and time. */
    private long expirationTime = Long.MAX_VALUE;
    /** The order validity start date and time. */
    private long validSinceTime = Long.MIN_VALUE;
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
            switch (this) {
            case BUY:
            case BUY_TO_COVER:
                return SELL_SHORT;
            case SELL:
            case SELL_SHORT:
                return BUY;
            default:
                throw new UnsupportedOperationException(this + ".opposite()");
            }
        }
    }

    public enum State {
        /** The order was cancelled by a user. */
        CANCELLED ("cancel", true),
        /** The order cancellation is pending. */
        CANCELLING ("request cancelling", false, CANCELLED), // TODO: not implemented yet
        /** The completely filled order. */
        FILLED ("fill", false),
        /** The partially filled order state. */
        PARTIALLY_FILLED ("partially fill", true, FILLED, CANCELLING, CANCELLED), // TODO: not implemented yet
        /** The order was received and acknowledged by the broker service or exchange. */
        ACCEPTED ("acknowledge", false, FILLED, PARTIALLY_FILLED, CANCELLED), // TODO: not implemented yet
        /** The state of an order submitted to the broker service or exchange. */
        SUBMITTED ("submit", false, CANCELLED, CANCELLING, FILLED, PARTIALLY_FILLED, ACCEPTED),
        /** The newly created order state. */
        NEWLY_CREATED ("create", false, SUBMITTED, CANCELLING, CANCELLED),
        /** The order was rejected by the system or a user code. */
        REJECTED ("reject", false),
        /** The order has expired. */
        EXPIRED ("expire", false); // TODO: not implemented yet

        private final String action;
        private final boolean isFinal;
        private final Set<State> nextAllowed = new HashSet<>();

        State(String action, boolean selfTransitionAllowed, State... nextAllowed) {
            this.action = action;
            this.isFinal = (nextAllowed == null || nextAllowed.length == 0);
            if (!isFinal)
                this.nextAllowed.addAll(List.of(nextAllowed));
            if (selfTransitionAllowed)
                this.nextAllowed.add(this);
        }

        public boolean isFinal() {
            return isFinal;
        }

        public State to(State next) {
            if (!nextAllowed.contains(next))
                throw new IllegalStateException("Cannot " + next.action + " " + name() + " order");
            return next;
        }
        
        /**
         * Determines if the order with the current state can be cancelled.
         * 
         * @return the {@code CANCELLED} order state if and only if the
         *         current state allows canceling, otherwise throws
         *         {@code IllegalStateException}
         */
        public State toCancelled() {
            throw new IllegalStateException("Cannot cancel " + name() + " order");
        }
        
        public State toFilled() {
            return to(FILLED);
        }
        
        /**
         * Returns {@code true} if the order state is {@link #NEWLY_CREATED}
         * 
         * @return {@code true} when {@link #NEWLY_CREATED}
         */
        public final boolean isNewlyCreated() {
            return (this == NEWLY_CREATED);
        }
        
        /**
         * Returns {@code true} if the order state is {@link #SUBMITTED}
         * 
         * @return {@code true} when {@link #SUBMITTED}
         */
        public final boolean isSubmitted() {
            return (this == SUBMITTED);
        }
        
        /**
         * Returns {@code true} if the order state is {@link #CANCELLED}
         * 
         * @return {@code true} when {@link #CANCELLED}
         */
        public final boolean isCancelled() {
            return (this == CANCELLED);
        }
        
        /**
         * Returns {@code true} if the order state is {@link #FILLED}
         * 
         * @return {@code true} when {@link #FILLED}
         */
        public final boolean isFilled() {
            return (this == FILLED);
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
    
    public Order(SymbolIdentity symbol, OrderType type, Side side, double exitStop, double exitLimit, double volume) {
        this(symbol, type, side, exitStop, exitLimit, volume, null);
    }
    
    public Order(SymbolIdentity symbol, OrderType type, Side side, double exitStop, double exitLimit, double volume, TimeInForce tif) {
        this.symbol = symbol;
        this.type = type;
        this.side = side;
        this.exitStop = exitStop;
        this.exitLimit = exitLimit;
        this.quantity = volume;
        this.timeInForce = tif;
    }
    
    public final int getId() {
        return id;
    }
    
    Order toSubmitted(int id, long time) {
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
        setState(state.to(State.REJECTED));
        return this;
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


    /**
     * @return the quantity
     */
    public double getQuantity() {
        return quantity;
    }
    
    /**
     * @param quantity the quantity to set
     */
    public void setQuantity(double quantity) {
        this.quantity = quantity;
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

        this.expirationTime = Chronological.toEpochMicros(expirationDate);
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

    private void setState(State newState) {
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
    public double getFillPrice() {
        return fillPrice;
    }
    
    /**
     * @param fillPrice the fillPrice to set
     */
    public void setFillPrice(double fillPrice) {
        this.fillPrice = fillPrice;
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
     * This is not adjusted by the fill price and it's recomputed on each method
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
        int directionTag = side.getDirection().tag;
        return (getFillPrice() - getExitStop()) * directionTag;
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
}
