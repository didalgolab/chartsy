/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.Candle;
import one.chartsy.core.json.JsonFormatter;
import one.chartsy.core.json.JsonParseException;
import one.chartsy.time.Chronological;
import org.openide.util.Lookup;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.TreeMap;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;

/**
 * A simple implementation of the {@link Candle} interface, representing a financial
 * price bar with open, high, low, close prices, volume, turnover, and the number of trades.
 *
 * <p>
 * This class is immutable and provides factory methods for creating instances from
 * raw data or existing candles. It also supports JSON serialization and deserialization.
 *
 * @author Mariusz Bernacki
 */
public final class SimpleCandle implements Candle, Serializable {

    private final long time;
    private final double open;
    private final double high;
    private final double low;
    private final double close;
    private final double volume;
    private final double turnover;
    private final int trades;

    /**
     * Private constructor to enforce the use of factory methods.
     *
     * @param time     the timestamp of the candle
     * @param open     the opening price
     * @param high     the highest price
     * @param low      the lowest price
     * @param close    the closing price
     * @param volume   the total volume traded
     * @param turnover the total turnover traded
     * @param trades   the number of trades executed
     */
    private SimpleCandle(long time, double open, double high, double low, double close, double volume, double turnover, int trades) {
        this.time = time;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.turnover = turnover;
        this.trades = trades;
    }

    /**
     * Factory method to create a new {@code SimpleCandle} instance.
     * If turnover is not provided, it defaults to {@code volume * close}.
     *
     * @param time     the timestamp of the candle
     * @param open     the opening price
     * @param high     the highest price
     * @param low      the lowest price
     * @param close    the closing price
     * @param volume   the total volume traded
     * @param turnover the total turnover traded (optional)
     * @param trades   the number of trades executed
     * @return a new {@code SimpleCandle} instance
     */
    public static SimpleCandle of(long time, double open, double high, double low, double close, double volume, double turnover, int trades) {
        return new SimpleCandle(time, open, high, low, close, volume, turnover, trades);
    }

    /**
     * Factory method to create a new {@code SimpleCandle} instance with default turnover.
     *
     * @param time   the timestamp of the candle
     * @param open   the opening price
     * @param high   the highest price
     * @param low    the lowest price
     * @param close  the closing price
     * @param volume the total volume traded
     * @param trades the number of trades executed
     * @return a new {@code SimpleCandle} instance
     */
    public static SimpleCandle of(long time, double open, double high, double low, double close, double volume, int trades) {
        double turnover = volume * close;
        return new SimpleCandle(time, open, high, low, close, volume, turnover, trades);
    }

    /**
     * Factory method to create a {@code SimpleCandle} from an existing {@code Candle} instance.
     * If the provided candle is already a {@code SimpleCandle}, it is returned as-is.
     * Otherwise, a new {@code SimpleCandle} is created with the same properties.
     *
     * @param c the candle to convert
     * @return a {@code SimpleCandle} instance
     */
    public static SimpleCandle from(Candle c) {
        if (c instanceof SimpleCandle sc) {
            return sc;
        }
        if (c instanceof AbstractCandle ac) {
            Candle bc = ac.baseCandle();
            if (bc != c)
                return from(bc);
        }
        double calculatedTurnover = c.volume() * c.close();
        return new SimpleCandle(c.getTime(), c.open(), c.high(), c.low(), c.close(), c.volume(), calculatedTurnover, c.trades());
    }

    @Override
    public long getTime() {
        return time;
    }

    @Override
    public double open() {
        return open;
    }

    @Override
    public double high() {
        return high;
    }

    @Override
    public double low() {
        return low;
    }

    @Override
    public double close() {
        return close;
    }

    @Override
    public double volume() {
        return volume;
    }

    @Override
    public double turnover() {
        return turnover;
    }

    @Override
    public int trades() {
        return trades;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(close)
                ^ Double.hashCode(high)
                ^ Double.hashCode(low)
                ^ (31 * Double.hashCode(open))
                ^ (37 * Double.hashCode(volume))
                ^ (43 * Double.hashCode(turnover))
                ^ (47 * Long.hashCode(time))
                ^ (53 * trades);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SimpleCandle q) {
            return (time == q.time)
                    && eq(close, q.close)
                    && eq(high, q.high)
                    && eq(low, q.low)
                    && eq(open, q.open)
                    && eq(volume, q.volume)
                    && eq(turnover, q.turnover)
                    && trades == q.trades;
        }
        return false;
    }

    /**
     * Utility method to compare two double values for exact bit equality.
     *
     * @param v the first double value
     * @param x the second double value
     * @return {@code true} if both values are exactly equal, {@code false} otherwise
     */
    private static boolean eq(double v, double x) {
        return Double.doubleToLongBits(v) == Double.doubleToLongBits(x);
    }

    static DateTimeFormatter timeFormatter = new DateTimeFormatterBuilder()
            .append(ISO_LOCAL_DATE)
            .optionalStart()
            .appendLiteral(' ')
            .append(ISO_LOCAL_TIME)
            .optionalEnd()
            .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0)
            .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
            .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
            .toFormatter();

    @Override
    public String toString() {
        LocalDateTime dateTime = getDateTime();
        StringBuilder buf = new StringBuilder("{\"")
                .append(dateTime.toLocalDate());
        if (!LocalTime.MIDNIGHT.equals(dateTime.toLocalTime()))
            buf.append(' ').append(dateTime.toLocalTime());
        buf.append("\": {OHLC:[");

        if (open != close || open != low || open != high) {
            buf.append(open).append(", ");
            if (high != Math.max(open, close) || low != Math.min(open, close))
                buf.append(high).append(", ").append(low).append(", ");
        }
        buf.append(close).append(']');

        if (volume != 0.0)
            buf.append(", V:").append(volume);
        if (turnover != close * volume)
            buf.append(", turnover:").append(turnover);
        if (trades != 0)
            buf.append(", trades:").append(trades);

        return buf.append("}}").toString();
    }

    /**
     * Inner class for JSON formatting and parsing.
     */
    public static class JsonFormat extends TreeMap<String, JsonFormat.Data> {

        /**
         * Parses a JSON string to create a {@code SimpleCandle} instance.
         *
         * @param json the JSON string representing a candle
         * @return a {@code SimpleCandle} instance
         */
        public static SimpleCandle fromJson(String json) {
            return Lookup.getDefault().lookup(JsonFormatter.class).fromJson(json, JsonFormat.class).toCandle();
        }

        /**
         * Converts this {@code JsonFormat} instance to a {@code SimpleCandle}.
         *
         * @return a {@code SimpleCandle} instance
         */
        public SimpleCandle toCandle() {
            var timeKey = firstKey();
            var dateTime = LocalDateTime.parse(timeKey, timeFormatter);
            return get(timeKey).toCandle(Chronological.toEpochNanos(dateTime));
        }

        /**
         * Inner class representing the data structure within the JSON format.
         */
        public static class Data {
            double[] OHLC;
            double V;
            double turnover;
            int trades;

            /**
             * Converts this {@code Data} instance to a {@code SimpleCandle}.
             *
             * @param time the timestamp of the candle in epoch nanoseconds
             * @return a {@code SimpleCandle} instance
             * @throws JsonParseException if the OHLC array is invalid
             */
            public SimpleCandle toCandle(long time) {
                if (OHLC == null)
                    throw new JsonParseException("OHLC is null");
                if (OHLC.length == 0)
                    throw new JsonParseException("OHLC.length == " + OHLC.length);

                double open = OHLC[0], high, low, close;
                switch (OHLC.length) {
                    case 1 -> close = high = low = open;
                    case 2 -> {
                        close = OHLC[1];
                        high  = Math.max(open, close);
                        low   = Math.min(open, close);
                    }
                    case 4 -> {
                        high  = OHLC[1];
                        low   = OHLC[2];
                        close = OHLC[3];
                    }
                    default -> throw new JsonParseException("OHLC.length == " + OHLC.length);
                }
                double calculatedTurnover = (turnover != 0.0) ? turnover : close * V;
                return new SimpleCandle(time, open, high, low, close, V, calculatedTurnover, trades);
            }
        }
    }
}
