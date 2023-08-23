/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

/**
 * The amount of change in price needed to move a chart to the right.
 *
 * Most charts that do not account for time are a series of columns (variously conceptualized), each representing a
 * price. Thus, a reversal amount may be thought of as the amount of price movement needed to create a new column on the
 * chart.
 *
 *
 * @author Mariusz Bernacki
 */
public interface ReversalAmount {

    /**
     * Determines reversal trigger price. Based on the given price of last peak value, the method returns value greater
     * than provided {@code peakPrice} for bullish reversal, or value less than {@code peakPrice} for bearish reversal.
     *
     * @param peakPrice
     *            the last extreme price
     * @param type
     *            the reversal type
     * @return the reversal trigger price
     */
    double getTurnaroundPrice(double peakPrice, ReversalType type);

    /**
     * Is the reversal amount of fixed value?
     */
    boolean isFixed();

    /**
     * Is the reversal amount formula-based?
     */
    boolean isAlgorithmic();


    static ReversalAmount of(double priceChange) {
        return new Fixed(priceChange, 0);
    }

    static ReversalAmount ofPercentage(double priceChangePercentage) {
        return new Fixed(0, priceChangePercentage);
    }

    /**
     * Reflects a fixed-value reversal amount.
     */
    record Fixed(double minimalPriceChange, double minimalPriceChangePercentage) implements ReversalAmount {
        public Fixed {
            if (minimalPriceChange < 0.0)
                throw new IllegalArgumentException(String.format("MinimalPriceChange: %s cannot be negative", minimalPriceChange));
            if (minimalPriceChangePercentage < 0.0)
                throw new IllegalArgumentException(String.format("MinimalPriceChangePercentage: %s cannot be negative", minimalPriceChangePercentage));
        }

        @Override
        public boolean isFixed() {
            return true;
        }

        @Override
        public boolean isAlgorithmic() {
            return false;
        }

        @Override
        public double getTurnaroundPrice(double peakPrice, ReversalType type) {
            if (type == ReversalType.BULLISH)
                return peakPrice + Math.max(minimalPriceChange, peakPrice*minimalPriceChangePercentage/100.0);
            else
                return peakPrice - Math.max(minimalPriceChange, peakPrice*minimalPriceChangePercentage/(100.0 + minimalPriceChangePercentage));
        }
    }
}
