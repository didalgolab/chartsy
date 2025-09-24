/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy;

import one.chartsy.data.SimpleCandle;
import one.chartsy.time.Chronological;

import java.time.LocalDateTime;
import java.time.temporal.ChronoField;

/**
 * Represents a symbol's price record, also known as a "bar", within a specific time frame.
 * This interface defines the essential properties of a candle, including open, high, low,
 * close prices, volume, turnover, and the number of trades.
 *
 * <p>
 * Each price record includes a set of price values ({@link #open()},
 * {@link #high()}, {@link #low()}, {@link #close()}) along with optional
 * {@link #volume()}. Every price record has an associated
 * {@link #getTime()} timestamp representing the moment in time when the price bar
 * occurred, down to nanosecond accuracy. For continuous time spans (e.g., 15 minutes),
 * the {@link #getTime()} reflects the <b>end time</b> of each time frame period.
 * For daily periods, the time component might be irrelevant and omitted.
 *
 * <p>
 * Note that {@link Candle#getTime()} uses an internal representation of time with
 * nanosecond accuracy and is not equivalent to a Unix timestamp or Java milliseconds
 * from the epoch. Use {@link Chronological#toDateTime(long)} and
 * {@link Chronological#toEpochNanos(LocalDateTime)} to convert between internal
 * representation and Java date/time objects.
 *
 * @author Mariusz Bernacki
 */
public interface Candle extends Chronological {

    /**
     * Returns the opening price of the candle.
     *
     * @return the open price
     */
    double open();

    /**
     * Returns the highest price of the candle.
     *
     * @return the high price
     */
    double high();

    /**
     * Returns the lowest price of the candle.
     *
     * @return the low price
     */
    double low();

    /**
     * Returns the closing price of the candle.
     *
     * @return the close price
     */
    double close();

    /**
     * Returns the total volume traded during the candle's period.
     *
     * @return the volume
     */
    double volume();

    /**
     * Returns the total turnover traded during the candle's period, which may be
     * a simple approximation of {@code volume * close}.
     *
     * @return the actual turnover in the period or its approximation
     */
    default double turnover() {
        return volume() * close();
    }

    /**
     * Retrieves the specified field from the candle's timestamp.
     *
     * @param field the temporal field to get
     * @return the value of the specified field
     */
    default int get(ChronoField field) {
        return getDateTime().get(field);
    }

    /**
     * Indicates whether the candle represents a bullish period (close price higher than open price).
     *
     * @return {@code true} if bullish, {@code false} otherwise
     */
    default boolean isBullish() {
        return open() < close();
    }

    /**
     * Indicates whether the candle represents a bearish period (close price lower than open price).
     *
     * @return {@code true} if bearish, {@code false} otherwise
     */
    default boolean isBearish() {
        return open() > close();
    }

    /**
     * Indicates whether the candle represents a doji (open price equals close price).
     *
     * @return {@code true} if doji, {@code false} otherwise
     */
    default boolean isDoji() {
        return open() == close();
    }

    /**
     * Calculates the weighted close price.
     *
     * @return the weighted close price
     */
    default double weightedClose() {
        return (2 * close() + high() + low()) / 4;
    }

    /**
     * Calculates the average price of the candle.
     *
     * @return the average price
     */
    default double averagePrice() {
        return (open() + high() + low() + close()) / 4;
    }

    /**
     * Calculates the typical price of the candle.
     *
     * @return the typical price
     */
    default double typicalPrice() {
        return (close() + high() + low()) / 3;
    }

    /**
     * Calculates the median price of the candle.
     *
     * @return the median price
     */
    default double medianPrice() {
        return (high() + low()) / 2;
    }

    /**
     * Calculates the range of the candle (high - low).
     *
     * @return the range
     */
    default double range() {
        return high() - low();
    }

    /**
     * Creates a new {@code Candle} instance with only the specified time and price.
     * The open, high, low, and close prices are all set to the given price.
     * Volume and turnover are set to zero, and trades are set to zero.
     *
     * @param time  the timestamp of the candle
     * @param price the price to set for open, high, low, and close
     * @return a new {@code Candle} instance
     */
    static Candle of(long time, double price) {
        return of(time, price, price, price, price, 0.0);
    }

    /**
     * Creates a new {@code Candle} instance with specified time, open, high, low, and close prices.
     * Volume and turnover are set to zero, and trades are set to zero.
     *
     * @param time  the timestamp of the candle
     * @param open  the opening price
     * @param high  the highest price
     * @param low   the lowest price
     * @param close the closing price
     * @return a new {@code Candle} instance
     */
    static Candle of(long time, double open, double high, double low, double close) {
        return of(time, open, high, low, close, 0.0);
    }

    /**
     * Creates a new {@code Candle} instance with all specified properties.
     *
     * @param time     the timestamp of the candle
     * @param open     the opening price
     * @param high     the highest price
     * @param low      the lowest price
     * @param close    the closing price
     * @param volume   the total volume traded
     * @return a new {@code Candle} instance
     */
    static Candle of(long time, double open, double high, double low, double close, double volume) {
        return SimpleCandle.of(time, open, high, low, close, volume);
    }

    /**
     * Creates a new {@code Candle} instance with specified {@link LocalDateTime} and price.
     *
     * @param dateTime the timestamp of the candle
     * @param price    the price to set for open, high, low, and close
     * @return a new {@code Candle} instance
     */
    static Candle of(LocalDateTime dateTime, double price) {
        return of(Chronological.toEpochNanos(dateTime), price);
    }

    /**
     * Creates a new {@code Candle} instance with specified {@link LocalDateTime}, open, high, low, close prices, and volume.
     * Turnover is set to {@code volume * close}, and trades are set to zero.
     *
     * @param dateTime the timestamp of the candle
     * @param open     the opening price
     * @param high     the highest price
     * @param low      the lowest price
     * @param close    the closing price
     * @param volume   the total volume traded
     * @return a new {@code Candle} instance
     */
    static Candle of(LocalDateTime dateTime, double open, double high, double low, double close, double volume) {
        return of(Chronological.toEpochNanos(dateTime), open, high, low, close, volume);
    }

    /**
     * Determines the direction of the candle based on its open and close prices.
     *
     * @param c the candle to evaluate
     * @return {@code 1} if bullish, {@code 0} otherwise
     */
    static int direction(Candle c) {
        return (c.close() > c.open()) ? 1 : 0;
    }
}
