/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.account;

import one.chartsy.SymbolIdentity;
import one.chartsy.api.messages.BarMessage;
import one.chartsy.core.event.ListenerList;
import one.chartsy.time.Chronological;
import one.chartsy.trade.Direction;
import one.chartsy.trade.Order;
import one.chartsy.trade.event.PositionValueChangeListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class AccountBalanceEntry {
    /** */
    private static final double QUANTITY_EPSILON = 0.000001;
    /** The initial account balance. */
    private final double initialBalance;
    /** The current account balance. */
    private double balance;
    /** The current profit of the account. */
    private double unrealizedPnL;
    /** The collection of traded instruments. */
    private final Map<SymbolIdentity, Position> instruments = new HashMap<>();
    /** The list of registered position value change listeners. */
    private final ListenerList<PositionValueChangeListener> positionValueChangeListeners = ListenerList.of(PositionValueChangeListener.class);

    public AccountBalanceEntry(double initialBalance) {
        this.balance = this.initialBalance = initialBalance;
    }

    public double getInitialBalance() {
        return initialBalance;
    }

    public double getBalance() {
        return balance;
    }

    public double getUnrealizedPnL() {
        return unrealizedPnL;
    }

    public double getEquity() {
        return getBalance() + getUnrealizedPnL();
    }

    public boolean hasPosition(SymbolIdentity symbol) {
        return instruments.containsKey(symbol);
    }

    public Position getPosition(SymbolIdentity symbol) {
        return instruments.get(symbol);
    }

    public void addPositionValueChangeListener(PositionValueChangeListener listener) {
        positionValueChangeListeners.addListener(listener);
    }

    public void removePositionValueChangeListener(PositionValueChangeListener listener) {
        positionValueChangeListeners.removeListener(listener);
    }

    private void firePositionValueChanged(Position position) {
        positionValueChangeListeners.fire().positionValueChanged(position);
    }

    public void updateProfit(SymbolIdentity symbol, double marketPrice, long marketTime) {
        Position position = getPosition(symbol);
        if (position != null) {
            unrealizedPnL += position.updateProfit(marketPrice, marketTime);
            firePositionValueChanged(position);
        }
    }

    public void onBarEvent(BarMessage message) {
        updateProfit(message.symbol(), message.bar().close(), message.getTime());
    }

    public void onOrderFill(TradeExecutionEvent event) {
        long tradeTime = event.getTime();
        Position position = getPosition(event.symbol());
        double tradePrice = event.tradePrice();
        double tradeQuantity = event.tradeQuantity();

        if (position == null) {
            Direction direction = !event.isBuy() ? Direction.SHORT : Direction.LONG;
            position = new Position(this, event.symbol(), direction, tradeQuantity, tradePrice, tradeTime);
            openPosition(position, tradePrice, tradeTime);
        } else {
            double tradeQuantityDelta = tradeQuantity * (position.getDirection().isLong() == event.isBuy() ? 1 : -1);
            double oldQuantity = position.getQuantity();
            double newQuantity = oldQuantity + tradeQuantityDelta;
            if (newQuantity < QUANTITY_EPSILON) {
                closePosition(position, tradePrice, tradeTime);
                if (Math.abs(newQuantity) < QUANTITY_EPSILON)
                    return;

                Direction direction = position.getDirection().reversed();
                position = new Position(this, event.symbol(), direction, -newQuantity, tradePrice, tradeTime);
                openPosition(position, tradePrice, tradeTime);
            } else {
                if (tradeQuantityDelta > 0) {
                    double oldAvgPrice = position.getAveragePrice();
                    double newAvgPrice = ((oldQuantity * oldAvgPrice) + (tradeQuantityDelta * tradePrice)) / newQuantity;
                    position.setAveragePrice(newAvgPrice);
                    position.setQuantity(newQuantity);
                } else {
                    scaleOutPosition(position, tradePrice, tradeTime, newQuantity);
                }
            }
        }
    }

    private void scaleOutPosition(Position position, double tradePrice, long tradeTime, double newQuantity) {
        unrealizedPnL += position.updateProfit(tradePrice, tradeTime);
        position.setQuantity(newQuantity);
        double realizedProfit = -position.updateProfit(tradePrice, tradeTime);
        unrealizedPnL -= realizedProfit;
        balance += realizedProfit;
        firePositionValueChanged(position);
    }

    private void closePosition(Position position, double tradePrice, long tradeTime) {
        unrealizedPnL -= position.getProfit();
        position.updateProfit(tradePrice, tradeTime);
        balance += position.getProfit() - position.getExtraCommission();
        instruments.remove(position.getSymbol());
        position.setClosed();
        firePositionValueChanged(position);
    }

    protected void openPosition(Position position, double tradePrice, long tradeTime) {
        instruments.put(position.getSymbol(), position);
        unrealizedPnL += position.updateProfit(tradePrice, tradeTime);
        firePositionValueChanged(position);
    }

    public void enterLong(SymbolIdentity symbol, double tradeQuantity, double tradePrice, long tradeTime) {
        closeOrReduceExistingPosition(symbol, tradeQuantity, tradePrice, tradeTime, Direction.LONG);
        scalePosition(symbol, tradeQuantity, tradePrice, tradeTime, Order.Side.BUY);
    }

    public void enterShort(SymbolIdentity symbol, double tradeQuantity, double tradePrice, long tradeTime) {
        closeOrReduceExistingPosition(symbol, tradeQuantity, tradePrice, tradeTime, Direction.SHORT);
        scalePosition(symbol, tradeQuantity, tradePrice, tradeTime, Order.Side.SELL_SHORT);
    }

    protected void closeOrReduceExistingPosition(SymbolIdentity symbol, double qty, double price, long time, Direction desired) {
        Position pos = getPosition(symbol);
        if (pos == null) return;
        double currentQty = pos.getQuantity();
        boolean opposite = pos.getDirection() != desired;
        double closeQty = opposite ? currentQty : Math.max(0, currentQty - qty);
        if (closeQty > QUANTITY_EPSILON) {
            boolean isBuy = pos.getDirection().isShort();
            onOrderFill(TradeExecutionEvent.builder()
                    .time(time)
                    .symbol(symbol)
                    .side(isBuy ? Order.Side.BUY_TO_COVER : Order.Side.SELL)
                    .tradePrice(price)
                    .tradeQuantity(closeQty)
                    .build());
        }
    }

    protected void scalePosition(SymbolIdentity symbol, double qty, double price, long time, Order.Side side) {
        Position pos = getPosition(symbol);
        double existing = (pos != null) ? pos.getQuantity() : 0.0;
        double needed = qty - existing;
        if (needed > QUANTITY_EPSILON) {
            onOrderFill(TradeExecutionEvent.builder()
                    .time(time)
                    .symbol(symbol)
                    .side(side)
                    .tradePrice(price)
                    .tradeQuantity(needed)
                    .build());
        }
    }

    public void exitPosition(SymbolIdentity symbol, double tradePrice, long tradeTime) {
        Position position = getPosition(symbol);
        if (position != null) {
            boolean isLong = position.getDirection().isLong();
            onOrderFill(TradeExecutionEvent.builder()
                    .symbol(symbol)
                    .side(isLong ? Order.Side.SELL : Order.Side.BUY_TO_COVER)
                    .tradePrice(tradePrice)
                    .tradeQuantity(position.getQuantity())
                    .time(tradeTime)
                    .build());
        }
    }

    public static class Position {
        /** The balance entry holding the position. */
        private final AccountBalanceEntry balanceEntry;
        /** The symbol of the position. */
        private final SymbolIdentity symbol;
        /** The position direction. */
        private final Direction direction;
        /** The position open price in points. */
        private final double entryPrice;
        /** The position open time. */
        private final long entryTime;
        /** The current quantity of this position. */
        private double quantity;
        /** The position average open price. */
        private double averagePrice;
        /** Current price of the position symbol in points. */
        private double marketPrice;
        /** The time of last current price update. */
        private long marketTime;
        /** The additional custom commission. */
        private double extraCommission;
        /** The current profit for this position. */
        private double profit;
        /** Measures the maximum profit that could have been extracted from the given trade position. */
        private double maxFavorableExcursion;
        /** Measures the largest loss suffered by this position while it is open (negative number). */
        private double maxAdverseExcursion;
        /** Indicates whether this position has been closed. */
        private boolean closed;

        public Position(AccountBalanceEntry balanceEntry, SymbolIdentity symbol, Direction direction, double quantity, double entryPrice, long entryTime) {
            this.balanceEntry = balanceEntry;
            this.symbol = symbol;
            this.direction = direction;
            this.quantity = quantity;
            this.entryPrice = this.averagePrice = entryPrice;
            this.entryTime = entryTime;
        }

        public AccountBalanceEntry getBalanceEntry() {
            return balanceEntry;
        }

        public SymbolIdentity getSymbol() {
            return symbol;
        }

        public Direction getDirection() {
            return direction;
        }

        public final double getEntryPrice() {
            return entryPrice;
        }

        public final long getEntryTime() {
            return entryTime;
        }

        public final LocalDateTime getEntryDateTime() {
            return Chronological.toDateTime(getEntryTime());
        }

        public double getQuantity() {
            return quantity;
        }

        public void setQuantity(double quantity) {
            this.quantity = quantity;
        }

        public double getAveragePrice() {
            return averagePrice;
        }

        public void setAveragePrice(double averagePrice) {
            this.averagePrice = averagePrice;
        }

        public double getMarketPrice() {
            return marketPrice;
        }

        public final long getMarketTime() {
            return marketTime;
        }

        public final LocalDateTime getMarketDateTime() {
            return Chronological.toDateTime(getMarketTime());
        }

        public double getExtraCommission() {
            return extraCommission;
        }

        public final double getProfit() {
            return profit;
        }

        public final double getMaxFavorableExcursion() {
            return maxFavorableExcursion;
        }

        public final double getMaxAdverseExcursion() {
            return maxAdverseExcursion;
        }

        public final boolean isClosed() {
            return closed;
        }

        protected void setClosed() {
            this.closed = true;
        }

        /**
         * Calculates open position profit based on the given quotation and calculator.
         *
         * @param marketPrice
         *            the current marketPrice
         * @param marketTime
         *            the current time
         */
        public double updateProfit(double marketPrice, long marketTime) {
            double delta = -profit;
            this.marketPrice = marketPrice;
            this.marketTime = marketTime;
            profit = (marketPrice - averagePrice) * quantity * direction.intValue();
            maxFavorableExcursion = Math.max(profit, maxFavorableExcursion);
            maxAdverseExcursion = Math.min(profit, maxAdverseExcursion);
            return delta + profit;
        }
    }
}
