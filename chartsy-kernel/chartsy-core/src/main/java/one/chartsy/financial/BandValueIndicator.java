/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial;

/**
 * A generic interface representing indicators characterized by dynamically calculated upper and lower bands.
 *
 * <p>Band indicators provide two boundary values—upper and lower bands—that encapsulate a price series,
 * volatility range, or market sentiment. They are commonly employed in technical analysis for signaling
 * market conditions, trend strength, and volatility states. Implementations of this interface are
 * typically stateful and intended for online (real-time) usage.</p>
 *
 * <p>This interface defines common operations expected from any band-based indicator, promoting
 * consistency and usability across different indicator implementations.</p>
 *
 * @param <V> the type representing the pair of upper and lower band values
 * @author Mariusz Bernacki
 */
public interface BandValueIndicator<V extends BandValueIndicator.BandValues> extends ValueIndicator.Of<V> {

    /**
     * Determines if the indicator has accumulated sufficient historical data to produce valid band outputs.
     *
     * @return {@code true} if the indicator is ready to provide reliable band values; otherwise, {@code false}
     */
    boolean isReady();

    /**
     * Gives the most recent calculated value of the upper band.
     *
     * @return the current upper band value
     */
    double getUpperBand();

    /**
     * Gives the most recent calculated value of the lower band.
     *
     * @return the current lower band value
     */
    double getLowerBand();

    /**
     * Gives the most recent computed pair of upper and lower band values.
     *
     * @return an instance of {@code V} containing both the upper and lower band values
     */
    @Override
    V getLast();

    /**
     * Gives the current market sentiment side (BULLISH, BEARISH, or NEUTRAL).
     *
     * @return the last computed band side
     */
    BandSide getLastSide();

    /**
     * Gives the market sentiment side from the previous candle update.
     *
     * @return the previously computed band side
     */
    BandSide getPreviousSide();

    /**
     * Gives the last recorded non-neutral sentiment side (BULLISH or BEARISH).
     *
     * @return the last non-neutral sentiment side
     */
    BandSide getLastNonNeutralSide();

    /**
     * Checks if a non-neutral sentiment side change has occurred (BULLISH to BEARISH or vice versa).
     *
     * @return {@code true} if a non-neutral sentiment side changed; otherwise, {@code false}
     */
    boolean isNonNeutralSideChanged();

    /**
     * Checks if the sentiment side has changed since the previous candle update.
     *
     * @return {@code true} if the sentiment side changed; otherwise, {@code false}
     */
    default boolean isSideChanged() {
        return getPreviousSide() != getLastSide();
    }

    /**
     * Enumerates possible market sentiment or positional sides derived from band indicator values.
     *
     * <p>This enum typically represents the position of a price series relative to the calculated bands,
     * commonly indicating a bullish, bearish, or neutral market condition.</p>
     */
    enum BandSide {
        /** Indicates a bullish sentiment (typically price above upper band). */
        BULLISH(1),
        /** Indicates a bearish sentiment (typically price below lower band). */
        BEARISH(-1),
        /** Indicates a neutral sentiment (typically price between upper and lower bands). */
        NEUTRAL(0);

        private final int intValue;

        BandSide(int intValue) {
        this.intValue = intValue;
    }

        /**
         * Determines if the current band side represents a non-neutral sentiment (BULLISH or BEARISH).
         *
         * @return {@code true} if the side is BULLISH or BEARISH; {@code false} if NEUTRAL
         */
        public boolean isNonNeutral() {
            return this != NEUTRAL;
        }

        /**
         * Checks if the current sentiment side is bullish.
         *
         * @return {@code true} if the side is bullish, {@code false} otherwise
         */
        public boolean isBullish() {
            return this == BULLISH;
        }

        /**
         * Checks if the current sentiment side is bearish.
         *
         * @return {@code true} if the side is bearish, {@code false} otherwise
         */
        public boolean isBearish() {
            return this == BEARISH;
        }

        /**
         * Returns a numeric representation of the band side.
         *
         * @return {@code 1} for BULLISH, {@code -1} for BEARISH, {@code 0} for NEUTRAL
         */
        public int intValue() {
            return intValue;
        }
    }

    /**
     * Represents a generic pair of upper and lower band values provided by band-based indicators.
     *
     * <p>This interface provides a standardized way to access upper and lower band values,
     * promoting consistency across different band-based indicator implementations.</p>
     */
    interface BandValues {

        /**
         * Gives the upper band value.
         *
         * @return the calculated upper band value
         */
        double upperBand();

        /**
         * Gives the lower band value.
         *
         * @return the calculated lower band value
         */
        double lowerBand();
    }
}
