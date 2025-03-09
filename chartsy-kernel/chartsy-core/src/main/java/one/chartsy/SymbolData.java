/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy;

/**
 * Represents data associated with a particular financial instrument or market entity symbol.
 * It contains ranking values and ranks used for sorting instruments for trading purposes,
 * along with custom data provided by users.
 *
 * @param <T> the type of custom data associated with the symbol
 */
public class SymbolData<T> {

    private final SymbolIdentity symbol;
    private final T customData;
    private double longRankingValue;
    private double shortRankingValue;
    private int longRank;
    private int shortRank;

    /**
     * Constructs a new {@code SymbolData} instance for the specified symbol.
     *
     * @param symbol the symbol identity
     */
    public SymbolData(SymbolIdentity symbol, T customData) {
        this.symbol = symbol;
        this.customData = customData;
    }

    /**
     * Returns the symbol associated with this data.
     *
     * @return the symbol
     */
    public final SymbolIdentity symbol() {
        return symbol;
    }

    public final T customData() {
        return customData;
    }

    /**
     * Returns the long ranking value for this symbol.
     * This value is used to rank the instrument for long trades.
     * Higher values indicate a higher ranking for long trades.
     *
     * @return the long ranking value
     */
    public final double longRankingValue() {
        return longRankingValue;
    }

    /**
     * Sets the long ranking value for this symbol.
     * This value is used to rank the instrument for long trades.
     * Higher values indicate a higher ranking for long trades.
     *
     * @param longRankingValue the long ranking value to set
     */
    public void setLongRankingValue(double longRankingValue) {
        this.longRankingValue = longRankingValue;
    }

    /**
     * Returns the short ranking value for this symbol.
     * This value is used to rank the instrument for short trades.
     * Lower values indicate a higher ranking for short trades.
     *
     * @return the short ranking value
     */
    public final double shortRankingValue() {
        return shortRankingValue;
    }

    /**
     * Sets the short ranking value for this symbol.
     * This value is used to rank the instrument for short trades.
     * Lower values indicate a higher ranking for short trades.
     *
     * @param shortRankingValue the short ranking value to set
     */
    public void setShortRankingValue(double shortRankingValue) {
        this.shortRankingValue = shortRankingValue;
    }

    /**
     * Returns the long rank for this symbol.
     * This rank is determined by sorting the instruments based on their long ranking values
     * in descending order. Instruments with higher long ranking values will have lower long ranks.
     *
     * @return the long rank
     */
    public final int longRank() {
        return longRank;
    }

    /**
     * Sets the long rank for this symbol.
     * This rank is determined by sorting the instruments based on their long ranking values
     * in descending order. Instruments with higher long ranking values will have lower long ranks.
     *
     * @param longRank the long rank to set
     */
    protected void setLongRank(int longRank) {
        this.longRank = longRank;
    }

    /**
     * Returns the short rank for this symbol.
     * This rank is determined by sorting the instruments based on their short ranking values
     * in ascending order. Instruments with lower short ranking values will have lower short ranks.
     *
     * @return the short rank
     */
    public final int shortRank() {
        return shortRank;
    }

    /**
     * Sets the short rank for this symbol.
     * This rank is determined by sorting the instruments based on their short ranking values
     * in ascending order. Instruments with lower short ranking values will have lower short ranks.
     *
     * @param shortRank the short rank to set
     */
    protected void setShortRank(int shortRank) {
        this.shortRank = shortRank;
    }

    @Override
    public String toString() {
        return "SymbolInformation: symbol=" + symbol + ", longRank=" + longRank + ", longRankingValue=" + longRankingValue + ", shortRank=" + shortRank;
    }
}
