package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.time.Chronological;

import java.time.ZoneId;

/** Maps a source candle to the instants at which its opening and completed data are available. */
@FunctionalInterface
public interface BarSession {
    Interval interval(Candle candle);

    record Interval(long openTime, long closeTime) {
        public Interval {
            if (closeTime <= openTime)
                throw new IllegalArgumentException("A bar must close after it opens");
        }
    }

    /**
     * Date-labelled daily OHLC, already aggregated in the supplied zone. The candle's UTC
     * calendar date is a label, not an instant. This mapping does not realign foreign-zone OHLC.
     */
    static BarSession daily(ZoneId zone) {
        return candle -> {
            var date = Chronological.toDateTime(candle.time()).toLocalDate();
            return new Interval(Chronological.toEpochNanos(date.atStartOfDay(zone)),
                    Chronological.toEpochNanos(date.plusDays(1).atStartOfDay(zone)));
        };
    }
}
