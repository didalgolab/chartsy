/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.metrics;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;

import one.chartsy.math.Occurence;
import one.chartsy.trade.Direction;
import one.chartsy.trade.data.TransactionData;

/**
 * StatisticsData provides access to build-in simulation statistics and metrics
 * calculated based on simulated transaction history.
 * 
 * @author Mariusz Bernacki
 *
 */
public class PerformanceData {
    /** The number of transactions included in the calculation. */
    protected int numberOfTransactions;
    
    protected Occurence profits = new Occurence();
    
    protected int consecutiveWins, consecutiveLosses;
    
    protected int maxConsecutiveWins, maxConsecutiveLosses;
    /** The transaction closing date when max consecutive win occurred. */
    protected LocalDateTime maxConsecutiveWinDate;
    /** The transaction closing date when max consecutive loss occurred. */
    protected LocalDateTime maxConsecutiveLossDate;
    
    protected double totalSharesTraded;
    
    
    public interface CustomType<R extends PerformanceData> extends Predicate<TransactionData> {
    }
    
    public enum Type implements CustomType<PerformanceData> {
        
        ALL, LONG_ONLY, SHORT_ONLY;
        
        @Override
        public final boolean test(TransactionData trn) {
            return switch (this) {
                case ALL -> true;
                case LONG_ONLY -> trn.getDirection() == Direction.LONG;
                case SHORT_ONLY -> trn.getDirection() == Direction.SHORT;
            };
        }
    }
    
    
    public PerformanceData() {
    }
    
    public PerformanceData(List<TransactionData> transactions) {
        addAll(transactions);
    }
    
    public void addAll(List<TransactionData> transactions) {
        for (TransactionData t : transactions)
            add(t);
    }
    
    public void add(TransactionData t) {
        double profit = t.getProfit();
        profits.add(profit);
        if (profit > 0) {
            // Max Consecutive Winners/Losers calculation
            consecutiveLosses = 0;
            if (++consecutiveWins > maxConsecutiveWins) {
                maxConsecutiveWins = consecutiveWins;
                maxConsecutiveWinDate = t.getExitDate();
            }
        } else if (profit < 0) {
            // Max Consecutive Winners/Losers calculation
            consecutiveWins = 0;
            if (++consecutiveLosses > maxConsecutiveLosses) {
                maxConsecutiveLosses = consecutiveLosses;
                maxConsecutiveLossDate = t.getExitDate();
            }
        }
        totalSharesTraded += t.getQuantity();
        numberOfTransactions++;
    }
    
    /**
     * @return the numberOfWins
     */
    public int getNumberOfWins() {
        return profits.getPositiveCount();
    }
    
    /**
     * @return the numberOfLosses
     */
    public int getNumberOfLosses() {
        return profits.getNegativeCount();
    }
    
    /**
     * @return the sumOfWins
     */
    public double getSumOfWins() {
        return profits.getPositiveTotal();
    }
    
    /**
     * @return the sumOfLosses
     */
    public double getSumOfLosses() {
        return profits.getNegativeTotal();
    }
    
    public double getProfit() {
        return getSumOfWins() - getSumOfLosses();
    }
    
    public double getAverageWin() {
        int numberOfWins = getNumberOfWins();
        return (numberOfWins == 0)? 0.0 : getSumOfWins() / numberOfWins;
    }
    
    public double getAverageLoss() {
        int numberOfLosses = getNumberOfLosses();
        return (numberOfLosses == 0)? 0.0 : getSumOfLosses() / numberOfLosses;
    }
    
    /**
     * @return the maximumWin
     */
    public double getMaximumWin() {
        return profits.getMaximum();
    }
    
    /**
     * @return the maximumLoss
     */
    public double getMaximumLoss() {
        return profits.getMinimum();
    }
    
    /**
     * @return the numberOfTransactions
     */
    public int getNumberOfTransactions() {
        return numberOfTransactions;
    }
    
    public double getPercentProfitable() {
        int numberOfTransactions = getNumberOfTransactions();
        return (numberOfTransactions == 0)? 0.0 : 100.0*getNumberOfWins()/numberOfTransactions;
    }
    
    public double getExpectancy() {
        double totalShares = this.totalSharesTraded;
        return (totalShares == 0.0)? 0.0 : getProfit() / totalShares;
    }
}
