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
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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
public record SimpleCandle(
        long time,
        double open,
        double high,
        double low,
        double close,
        double volume) implements Candle, Serializable {

    /**
     * Factory method to create a new {@code SimpleCandle} instance.
     * If turnover is not provided, it defaults to {@code volume * close}.
     *
     * @param time   the timestamp of the candle
     * @param open   the opening price
     * @param high   the highest price
     * @param low    the lowest price
     * @param close  the closing price
     * @param volume the total volume traded
     * @return a new {@code SimpleCandle} instance
     */
    public static SimpleCandle of(long time, double open, double high, double low, double close, double volume) {
        return new SimpleCandle(time, open, high, low, close, volume);
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
        if (c instanceof AbstractCandle<?> ac) {
            Candle bc = ac.baseCandle();
            if (bc != c)
                return from(bc);
        }
        return new SimpleCandle(c.time(), c.open(), c.high(), c.low(), c.close(), c.volume());
    }

    @Override
    public int hashCode() {
        return Double.hashCode(close)
                ^ Double.hashCode(high)
                ^ Double.hashCode(low)
                ^ (31 * Double.hashCode(open))
                ^ (37 * Double.hashCode(volume))
                ^ (43 * Long.hashCode(time));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof SimpleCandle(long time2, double open2, double high2, double low2, double close2, double volume2)) {
            return (time == time2)
                    && eq(close, close2)
                    && eq(high, high2)
                    && eq(low, low2)
                    && eq(open, open2)
                    && eq(volume, volume2);
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
        ZonedDateTime dateTime = instant().atZone(ZoneOffset.UTC);
        StringBuilder buf = new StringBuilder("{\"").append(dateTime.toLocalDate());
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
                        high = Math.max(open, close);
                        low = Math.min(open, close);
                    }
                    case 4 -> {
                        high = OHLC[1];
                        low = OHLC[2];
                        close = OHLC[3];
                    }
                    default -> throw new JsonParseException("OHLC.length == " + OHLC.length);
                }
                return new SimpleCandle(time, open, high, low, close, V);
            }
        }
    }
}
