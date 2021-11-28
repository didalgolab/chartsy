/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.trade.data;

import java.util.Collections;
import java.util.Map;

import one.chartsy.SymbolIdentity;
import one.chartsy.core.CustomValuesHolder;
import one.chartsy.time.ChronologicalEntry;
import one.chartsy.time.ChronologicalExit;
import one.chartsy.trade.Direction;

public final class TransactionData implements ChronologicalEntry, ChronologicalExit, Comparable<ChronologicalExit>, CustomValuesHolder {
    /** The transaction id. */
    private final long id;
    /** The transaction symbol. */
    private final SymbolIdentity symbol;
    /** The direction type of this transaction. */
    private final Direction direction;
    /** The transaction volume (quantity). */
    private final double quantity;
    /** The transaction open price in points. */
    private final double entryPrice;
    /** The transaction open time. */
    private final long entryTime;
    /** The entry risk on the day of transaction entry. */
    private final double entryRisk;
    /** The transaction close price in points. */
    private final double exitPrice;
    /** The transaction close time. */
    private final long exitTime;
    /** The commission. */
    private final double commission;
    /** The transaction swap. */
    private final double swap;
    /** The transaction profit. */
    private final double profit;
    /** The maximum profit that could have been extracted from the given transaction. */
    private double maxFavorableExcursion;
    /** The largest loss suffered by this transaction while it was open. */
    private double maxAdverseExcursion;
    /** The user-provided custom values copied from the entry order. */
    private Map<String, Object> customValues;
    
    
    public long getId() {
        return id;
    }
    
    public SymbolIdentity getSymbol() {
        return symbol;
    }
    
    public Direction getDirection() {
        return direction;
    }
    
    public double getQuantity() {
        return quantity;
    }
    
    public double getEntryPrice() {
        return entryPrice;
    }
    
    @Override
    public long getEntryTime() {
        return entryTime;
    }
    
    public double getExitPrice() {
        return exitPrice;
    }
    
    @Override
    public long getExitTime() {
        return exitTime;
    }
    
    public double getCommission() {
        return commission;
    }
    
    public double getSwap() {
        return swap;
    }
    
    public double getProfit() {
        return profit;
    }
    
    public double getNetProfit() {
        return profit - commission;
    }
    
    public TransactionData(long id, Position position, double closePrice, long closeTime, double commission) {
        this.id = id;
        this.symbol = position.getSymbol();
        this.direction = position.getDirection();
        this.quantity = position.getQuantity();
        this.entryPrice = position.getEntryPrice();
        this.entryTime = position.getEntryTime();
        this.entryRisk = position.getEntryRisk();
        this.swap = position.getSwap();
        this.profit = position.getProfit();
        this.exitPrice = closePrice;
        this.exitTime = closeTime;
        this.commission = commission;
        this.maxFavorableExcursion = position.getMaxFavorableExcursion();
        this.maxAdverseExcursion = position.getMaxAdverseExcursion();
        this.customValues = position.getEntryOrder().getCustomValues();
    }
    
    @Override
    public int compareTo(ChronologicalExit other) {
        return Long.compare(getExitTime(), other.getExitTime());
    }
    
    public double getEntryRisk() {
        return entryRisk;
    }
    
    @Override
    public Map<String, Object> getCustomValues() {
        Map<String, Object> customValues = this.customValues;
        return (customValues != null)? customValues: Collections.emptyMap();
    }
    
    @Override
    public void setCustomValue(String name, Object value) {
        customValues = CustomValuesHolder.setCustomValue(customValues, name, value);
    }
    
    @Override
    public void removeCustomValue(String name) {
        customValues = CustomValuesHolder.removeCustomValue(customValues, name);
    }
    
    public double getMaxFavorableExcursion() {
        return maxFavorableExcursion;
    }
    
    public void setMaxFavorableExcursion(double maxFavorableExcursion) {
        this.maxFavorableExcursion = maxFavorableExcursion;
    }
    
    public double getMaxAdverseExcursion() {
        return maxAdverseExcursion;
    }
    
    public void setMaxAdverseExcursion(double maxAdverseExcursion) {
        this.maxAdverseExcursion = maxAdverseExcursion;
    }
    
    /**
     * Indicates whether the transaction is a cash transfer transaction. A cash
     * transfer transaction may represent for example:
     * 
     * <ul>
     * <li>cash deposit</li>
     * <li>cash withdrawal</li>
     * <li>stock dividend distribution</li>
     * <li>interest rate charge</li>
     * </ul>
     * 
     * @return {@code true} if the transaction is a cash transfer, or {@code false}
     *         otherwise
     */
    // TODO: not implemented currently
    public boolean isCashTransfer() {
        throw new UnsupportedOperationException();
    }
}
