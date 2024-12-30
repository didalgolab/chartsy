/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.data;

import one.chartsy.SymbolIdentity;
import one.chartsy.trade.Direction;
import one.chartsy.trade.Order;

public class Position implements Cloneable {
    /** The unique position identifier. */
    private final int ID;
    /** The symbol of the position. */
    private final SymbolIdentity symbol;
    /** The position direction. */
    private final Direction direction;
    /** The current quantity of this position. */
    private final double quantity;
    /** The position open price in points. */
    private final double entryPrice;
    /** The position open time. */
    private final long entryTime;
    /** The entry order associated with this position. */
    private final Order entryOrder;
    /** The entry risk on the day of position entry. */
    private final double entryRisk;
    /** Current price of the position symbol in points. */
    private double marketPrice;
    /** The time of last current price update. */
    private long marketTime;
    /** The commission costs paid so far for this position. */
    private double commission;
    /** The additional custom commission. */
    private double extraCommission;
    /** The cumulative swap. */
    private double swap;
    /** The current profit for this position. */
    private double profit;
    /** The number of bars the position has been or was open for. */
    private int barsHeld;
    // TODO: position units

    /**
     * Units represent concurrent positions taken in the same instrument.
     * 
     * @author Mariusz Bernacki
     *
     */
    public static class Unit {
        private int unitNumber;
        private final Order entryOrder;
        private final long entryTime;
        private final double quantity;
        
        
        public Unit(Order entryOrder, long entryTime, double quantity) {
            this.entryOrder = entryOrder;
            this.entryTime = entryTime;
            this.quantity = quantity;
        }

        public int getUnitNumber() {
            return unitNumber;
        }
    }

    //	public Order createStopAndReverseOrder(double volume, long setupTime) {
    //		Side transactionType = order.getSide().opposite();
    //		double price = openPrice - direction.tag * order.getStopLoss();
    //		double stopLoss = order.getStopLoss();
    //		double profitTarget = order.getProfitTarget();
    //		
    //		return new Order(symbol, Type.STOP, transactionType, price, stopLoss, profitTarget, volume, setupTime);
    //	}
    
    /**
     * Calculates open position profit and swap based on the given quotation and
     * calculator.
     * 
     * @param price
     *            the current price
     * @param marketTime
     *            the current time
     */
    public double updateProfit(double price, long marketTime) {
        double delta = -profit - swap;
        marketPrice = price;
        this.marketTime = marketTime;
        profit = (price - entryPrice) * quantity * direction.tag;
        maxFavorableExcursion = Math.max(profit, maxFavorableExcursion);
        maxAdverseExcursion = Math.min(profit, maxAdverseExcursion);
        // TODO
        //swap = symbol.getCalculator().getSwap(epochMicros, this);
        return delta + profit + swap;
    }
    
    /** Measures the maximum profit that could have been extracted from the given trade position. */
    private double maxFavorableExcursion;
    /** Measures the largest loss suffered by this position while it is open (negative number). */
    private double maxAdverseExcursion;
    /** Indicates whether this position has been closed. */
    private boolean closed;
    /** The commission resulted in closing the position. */
    private double closingCommission;
    
    
    public Position(int ID, SymbolIdentity symbol, Direction type, double entryPrice, double quantity, Order order, double commission, long entryTime) {
        this.ID = ID;
        this.symbol = symbol;
        this.direction = type;
        this.entryPrice = entryPrice;
        this.quantity = quantity;
        this.entryOrder = order;
        this.entryRisk = order.getEntryRisk();
        this.commission = commission;
        this.entryTime = entryTime;
    }

    @Override
    public Position clone() {
        try {
            return (Position) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError("Shouldn't happen");
        }
    }
    
    public void close(double closingCommission) {
        if (!isClosed()) {
            this.closed = true;
            this.closingCommission = closingCommission;
        }
    }
    
    public final boolean isClosed() {
        return closed;
    }
    
    /**
     * @return the quantity
     */
    public final double getQuantity() {
        return quantity;
    }
    
    /**
     * @return the stopLoss
     */
    public double getExitStop() {
        return entryOrder.getExitStop();
    }
    
    /**
     * @return the profitTarget
     */
    public double getExitLimit() {
        return entryOrder.getExitLimit();
    }
    
    public double getMarketPrice() {
        return marketPrice;
    }

    public long getMarketTime() {
        return marketTime;
    }

    /**
     * @return the commission
     */
    public double getCommission() {
        return commission;
    }
    
    /**
     * @param commission the commission to set
     */
    public void setCommission(double commission) {
        this.commission = commission;
    }
    
    /**
     * @return the swap
     */
    public double getSwap() {
        return swap;
    }
    
    /**
     * @param swap the swap to set
     */
    public void setSwap(double swap) {
        this.swap = swap;
    }
    
    /**
     * @return the realizedProfit
     */
    public double getProfit() {
        return profit;
    }
    
    /**
     * @return the iD
     */
    public int getID() {
        return ID;
    }
    
    /**
     * @return the symbol
     */
    public final SymbolIdentity getSymbol() {
        return symbol;
    }
    
    /**
     * @return the direction
     */
    public final Direction getDirection() {
        return direction;
    }
    
    /**
     * @return the openPrice
     */
    public double getEntryPrice() {
        return entryPrice;
    }
    
    /**
     * @return the openTime
     */
    public long getEntryTime() {
        return entryTime;
    }
    
    /**
     * @return the order
     */
    public Order getEntryOrder() {
        return entryOrder;
    }
    
    /**
     * @return the entryRisk
     */
    public double getEntryRisk() {
        return entryRisk;
    }
    
    /**
     * @return the maxFavorableExcursion
     */
    public double getMaxFavorableExcursion() {
        return maxFavorableExcursion;
    }
    
    /**
     * @return the maxAdverseExcursion
     */
    public double getMaxAdverseExcursion() {
        return maxAdverseExcursion;
    }
    
    /**
     * The amount of custom commission added to the position through the use of
     * {@link #addCommission(double)} method.
     * 
     * @return the accrued custom commission amount
     */
    public final double getExtraCommission() {
        return extraCommission;
    }
    
    /**
     * Adds the specified amount of commission per share/contract to the current
     * position.
     * <p>
     * The {@code addCommission(commission)} increases the amount of
     * {@link #getExtraCommission()} by {@code commission * getQuantity()}.
     * 
     * @param commission
     *            the commission amount to add per share or contract
     */
    public void addCommission(double commission) {
        extraCommission += commission * getQuantity();
    }

    /**
     * @return the closingCommission
     */
    public double getClosingCommission() {
        return closingCommission;
    }

}
