/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.account;

import one.chartsy.DirectionInformation;
import one.chartsy.SymbolIdentity;
import one.chartsy.core.event.ListenerList;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.time.Chronological;
import one.chartsy.trade.Direction;
import one.chartsy.trade.OrderTrade;
import one.chartsy.trade.event.PositionValueChangeListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an account balance entry, tracking positions, PnL and equity for a single currency.
 * This class acts as the core logic unit within an {@code Account} for handling trading activities.
 * It maintains the state of open positions and recalculates equity based on market data.
 *
 * @author Mariusz Bernacki
 */
public class AccountBalanceEntry {
    /** A small value used for floating-point comparisons involving negligible quantities. */
    private static final double QUANTITY_EPSILON = 0.000000005;
    /** The starting/initial account capital. */
    private final double startingBalance;
    private final ProfitConversion profitConversion;
    /** The current total realized profit/loss for the account. */
    private double realizedPnL;
    /** The current total unrealized profit/loss across all open positions. */
    private double unrealizedPnL;
    /** The collection of currently open positions, mapped by symbol. */
    private final Map<SymbolIdentity, Position> instruments = new HashMap<>();
    /** The list of registered position value change listeners. */
    private final ListenerList<PositionValueChangeListener> positionValueChangeListeners = ListenerList.of(PositionValueChangeListener.class);

    public AccountBalanceEntry(double startingBalance) {
        this(startingBalance, ProfitConversion.QUOTE_CURRENCY);
    }

    /** All realized and unrealized totals use account currency; entry prices and quantities keep their original units. */
    public AccountBalanceEntry(double startingBalance, ProfitConversion profitConversion) {
        if (profitConversion == null) throw new IllegalArgumentException("Profit conversion is required");
        this.startingBalance = startingBalance;
        this.profitConversion = profitConversion;
    }

    /**
     * Gets the capital the account started with at a particular point in time.
     *
     * @return the starting/initial balance
     */
    public final double getStartingBalance() {
        return startingBalance;
    }

    /**
     * Gets the current cash balance of the account.
     * This is the running total of initial balance plus any {@link #getRealizedPnL() realized PnL}.
     * It represents the liquid cash available, excluding any open (unrealized) profit or loss
     * from positions not yet closed.
     *
     * @return the current cash balance
     */
    public final double getCashBalance() {
        return startingBalance + realizedPnL;
    }

    /**
     * Gets the running total of realized profits and losses from closed or scaled-out positions.
     *
     * @return the sum of profit/loss from closed positions
     */
    public final double getRealizedPnL() {
        return realizedPnL;
    }

    /**
     * Gets the running total of unrealized profits and losses from positions not yet closed.
     *
     * @return the sum of profit/loss from open positions
     */
    public final double getUnrealizedPnL() {
        return unrealizedPnL;
    }

    /**
     * Gives the total value of the account if all open positions were liquidated immediately.
     *
     * @return the current equity (or net liquidation value)
     */
    public final double getEquity() {
        return getCashBalance() + getUnrealizedPnL();
    }

    public boolean hasPosition(SymbolIdentity symbol) {
        return instruments.containsKey(symbol);
    }

    /**
     * Returns the position associated with the given symbol, if one exists.
     *
     * @param symbol the symbol identity
     * @return the position, or {@code null} if no position exists for the symbol
     */
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

    /**
     * Updates the unrealized PnL for a specific symbol based on the latest market price.
     *
     * @param symbol the symbol identity
     * @param marketPrice the current market price
     * @param marketTime the time of the market price update
     */
    public void updateMarketData(SymbolIdentity symbol, double marketPrice, long marketTime) {
        Position position = getPosition(symbol);
        if (position != null) {
            unrealizedPnL += position.updateProfit(marketPrice, marketTime);
            firePositionValueChanged(position);
        }
    }

    /**
     * Convenience method to update market data from a BarMessage.
     *
     * @param message the bar message containing symbol, price, and time info
     */
    public void onBarEvent(TradeBar message) {
        updateMarketData(message.symbol(), message.get().close(), message.time());
    }

    /**
     * Processes a trade execution event, updating positions, balance, and PnL accordingly.
     *
     * @param event the trade execution event
     */
    public void onOrderFill(OrderTrade event) {
        long tradeTime = event.time();
        Position position = getPosition(event.symbol());
        double tradePrice = event.tradePrice();
        double tradeQuantity = event.tradeQuantity();

        if (position == null) {
            Direction direction = event.isBuy() ? Direction.LONG : Direction.SHORT;
            position = new Position(this, event.symbol(), direction, tradeQuantity, tradePrice, tradeTime);
            openPosition(position, tradePrice, tradeTime);
            return;
        }
        double tradeQuantityDelta = tradeQuantity * (position.getDirection().isLong() == event.isBuy() ? 1 : -1);
        double oldQuantity = position.getQuantity();
        double newQuantity = oldQuantity + tradeQuantityDelta;

        if (newQuantity < -QUANTITY_EPSILON) { // Position reversal
            Position replacement = new Position(this, event.symbol(), position.getDirection().reversed(),
                    -newQuantity, tradePrice, tradeTime);
            double closingProfit = position.profitAt(tradePrice, tradeTime);
            double openingProfit = replacement.profitAt(tradePrice, tradeTime);
            closePosition(position, tradePrice, tradeTime, closingProfit);
            openPosition(replacement, tradePrice, tradeTime, openingProfit);
        } else if (newQuantity < QUANTITY_EPSILON) { // Position closed
            closePosition(position, tradePrice, tradeTime);
        } else if (tradeQuantityDelta < 0) { // Scale out
            scaleOutPosition(position, tradePrice, tradeTime, newQuantity);
        } else { // Scale in
            scaleInPosition(position, tradePrice, tradeTime, newQuantity);
        }
    }

    private void scaleInPosition(Position position, double tradePrice, long tradeTime, double newQuantity) {
        double oldQuantity = position.getQuantity();
        double oldQtyRatio = oldQuantity / newQuantity;
        double oldAvgPrice = position.getAveragePrice();
        double newAvgPrice = oldQtyRatio * oldAvgPrice + (1 - oldQtyRatio) * tradePrice;
        double newProfit = position.profitAt(tradePrice, tradeTime, newQuantity, newAvgPrice);
        position.setAveragePrice(newAvgPrice);
        position.setQuantity(newQuantity);
        unrealizedPnL += position.applyProfit(tradePrice, tradeTime, newProfit);
        firePositionValueChanged(position);
    }

    private void scaleOutPosition(Position position, double tradePrice, long tradeTime, double newQuantity) {
        // Both conversions must succeed before either the account or position is changed.
        double wholeProfit = position.profitAt(tradePrice, tradeTime);
        double remainingProfit = position.profitAt(tradePrice, tradeTime, newQuantity, position.getAveragePrice());
        unrealizedPnL += position.applyProfit(tradePrice, tradeTime, wholeProfit);
        position.setQuantity(newQuantity);
        double realizedProfit = -position.applyProfit(tradePrice, tradeTime, remainingProfit);
        unrealizedPnL -= realizedProfit;
        realizedPnL += realizedProfit;
        firePositionValueChanged(position);
    }

    private void closePosition(Position position, double tradePrice, long tradeTime) {
        closePosition(position, tradePrice, tradeTime, position.profitAt(tradePrice, tradeTime));
    }

    private void closePosition(Position position, double tradePrice, long tradeTime, double closingProfit) {
        unrealizedPnL -= position.getProfit();
        position.applyProfit(tradePrice, tradeTime, closingProfit);
        realizedPnL += position.getProfit() - position.getExtraCommission();
        instruments.remove(position.getSymbol());
        position.setClosed();
        firePositionValueChanged(position);
    }

    private void openPosition(Position position, double tradePrice, long tradeTime) {
        openPosition(position, tradePrice, tradeTime, position.profitAt(tradePrice, tradeTime));
    }

    private void openPosition(Position position, double tradePrice, long tradeTime, double openingProfit) {
        instruments.put(position.getSymbol(), position);
        unrealizedPnL += position.applyProfit(tradePrice, tradeTime, openingProfit);
        firePositionValueChanged(position);
    }

    public void enterLong(SymbolIdentity symbol, double desiredQuantity, double tradePrice, long tradeTime) {
        enterPosition(symbol, Direction.LONG, desiredQuantity, tradePrice, tradeTime);
    }

    public void enterShort(SymbolIdentity symbol, double desiredQuantity, double tradePrice, long tradeTime) {
        enterPosition(symbol, Direction.SHORT, desiredQuantity, tradePrice, tradeTime);
    }

    /**
     * Exits any open position for the specified symbol.
     *
     * @param symbol    the symbol identity
     * @param exitPrice the price at which to exit the position
     * @param exitTime  the time at which the exit occurs
     */
    public void exitPosition(SymbolIdentity symbol, double exitPrice, long exitTime) {
        Position position = getPosition(symbol);
        if (position != null)
            closePosition(position, exitPrice, exitTime);
    }

    /**
     * Enters or modifies a position to match the desired quantity and direction.
     *
     * @param symbol           the symbol identity
     * @param desiredDirection the target direction (LONG or SHORT)
     * @param desiredQuantity  the target quantity for the position
     * @param tradePrice       the price for any required trades
     * @param tradeTime        the time for any required trades
     * @throws IllegalArgumentException if {@code desiredQuantity} is negative, or
     *                                  if {@code desiredDirection} is FLAT and {@code desiredQuantity} is non-zero.
     */
    public void enterPosition(SymbolIdentity symbol, DirectionInformation desiredDirection, double desiredQuantity, double tradePrice, long tradeTime) {
        if (desiredQuantity < -QUANTITY_EPSILON)
            throw new IllegalArgumentException("`desiredQuantity` must be non-negative but was " + desiredQuantity);
        if (desiredDirection.isFlat() && desiredQuantity > QUANTITY_EPSILON)
            throw new IllegalArgumentException("Cannot specify a non-zero desired quantity (" + desiredQuantity
                    + ") with a FLAT direction. Use LONG or SHORT, or set quantity to 0 for FLAT.");

        // Exit case: if desired quantity is effectively zero, exit any existing position.
        if (desiredQuantity <= QUANTITY_EPSILON) {
            exitPosition(symbol, tradePrice, tradeTime);
            return;
        }

        // Proceed with entering or modifying the position.
        Direction direction = desiredDirection.isLong() ? Direction.LONG : Direction.SHORT;
        Position position = getPosition(symbol);
        if (position == null || position.getDirection() != direction) {
            Position replacement = new Position(this, symbol, direction, desiredQuantity, tradePrice, tradeTime);
            double closingProfit = position == null ? 0 : position.profitAt(tradePrice, tradeTime);
            double openingProfit = replacement.profitAt(tradePrice, tradeTime);
            if (position != null)
                closePosition(position, tradePrice, tradeTime, closingProfit);
            openPosition(replacement, tradePrice, tradeTime, openingProfit);
        } else {
            double qtyDifference = desiredQuantity - position.getQuantity();
            if (qtyDifference > QUANTITY_EPSILON)
                scaleInPosition(position, tradePrice, tradeTime, desiredQuantity);
            else if (qtyDifference < -QUANTITY_EPSILON)
                scaleOutPosition(position, tradePrice, tradeTime, desiredQuantity);
        }
    }

    public static class Position {
        /** The balance entry holding the position. */
        private final AccountBalanceEntry balanceEntry;
        /** The symbol of the position. */
        private final SymbolIdentity symbol;
        /** The position direction. */
        private final Direction direction;
        /** The position initial open price in points. */
        private final double entryPrice;
        /** The position initial open time. */
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
        /** Indicates if the position is fully closed. */
        private boolean closed;

        public Position(AccountBalanceEntry balanceEntry, SymbolIdentity symbol, Direction direction, double quantity, double entryPrice, long entryTime) {
            this.balanceEntry = balanceEntry;
            this.symbol = symbol;
            this.direction = direction;
            this.quantity = quantity;
            this.marketPrice = this.entryPrice = this.averagePrice = entryPrice;
            this.marketTime = this.entryTime = entryTime;
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

        protected void setQuantity(double quantity) {
            this.quantity = quantity;
        }

        public double getAveragePrice() {
            return averagePrice;
        }

        protected void setAveragePrice(double averagePrice) {
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

        /**
         * Gets the current unrealized profit/loss of the position based on the last known market price.
         *
         * @return the unrealized PnL
         */
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
         * Calculates and updates the unrealized PnL based on the given market price.
         * Also updates market price/time, MFE, and MAE.
         *
         * @param marketPrice the current market price
         * @param marketTime  the current market time
         * @return the <i>change</i> in unrealized PnL since the last update
         */
        protected double updateProfit(double marketPrice, long marketTime) {
            return applyProfit(marketPrice, marketTime, profitAt(marketPrice, marketTime));
        }

        private double profitAt(double marketPrice, long marketTime) {
            return profitAt(marketPrice, marketTime, quantity, averagePrice);
        }

        private double profitAt(double marketPrice, long marketTime, double quantity, double averagePrice) {
            double quoteProfit = (marketPrice - averagePrice) * quantity * direction.intValue();
            double converted = balanceEntry.profitConversion.toAccountCurrency(symbol, quoteProfit, marketPrice, marketTime);
            if (!Double.isFinite(converted))
                throw new ArithmeticException("Profit conversion returned a non-finite amount for " + symbol);
            return converted;
        }

        private double applyProfit(double marketPrice, long marketTime, double newProfit) {
            double delta = -profit;
            this.marketPrice = marketPrice;
            this.marketTime = marketTime;
            profit = newProfit;
            maxFavorableExcursion = Math.max(profit, maxFavorableExcursion);
            maxAdverseExcursion = Math.min(profit, maxAdverseExcursion);
            return delta + profit;
        }
    }
}
