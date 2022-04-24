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

public final class SimpleCandle implements Candle, Serializable {
    private final long time;
    private final double open;
    private final double high;
    private final double low;
    private final double close;
    private final double volume;
    private final int count;

    private SimpleCandle(long time, double open, double high, double low, double close, double volume, int count) {
        this.time = time;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
        this.count = count;
    }

    public static SimpleCandle of(long time, double open, double high, double low, double close, double volume, int count) {
        return new SimpleCandle(time, open, high, low, close, volume, count);
    }

    public static SimpleCandle from(Candle c) {
        if (c instanceof SimpleCandle sc) {
            return sc;
        }
        if (c instanceof AbstractCandle ac) {
            Candle bc = ac.baseCandle();
            if (bc != c)
                return from(bc);
        }
        return new SimpleCandle(c.getTime(), c.open(), c.high(), c.low(), c.close(), c.volume(), c.count());
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
    public int count() {
        return count;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(close)
                ^ Double.hashCode(high)
                ^ Double.hashCode(low)
                ^ (31 * Double.hashCode(open))
                ^ (37 * Double.hashCode(volume))
                ^ (41 * Long.hashCode(time))
                ^ (43 * count);
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
                    && count == q.count;
        }
        return false;
    }

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
        if (count != 0)
            buf.append(", count:").append(count);

        return buf.append("}}").toString();
    }

    public static class JsonFormat extends TreeMap<String, JsonFormat.Data> {

        public static SimpleCandle fromJson(String json) {
            return Lookup.getDefault().lookup(JsonFormatter.class).fromJson(json, JsonFormat.class).toCandle();
        }

        public SimpleCandle toCandle() {
            var timeKey = firstKey();
            var dateTime = LocalDateTime.parse(timeKey, timeFormatter);
            return get(timeKey).toCandle(Chronological.toEpochMicros(dateTime));
        }

        public static class Data {
            double[] OHLC;
            double V;
            int count;

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
                return new SimpleCandle(time, open, high, low, close, V, count);
            }
        }
    }
}
