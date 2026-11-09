package one.chartsy.simulation.engine;

import one.chartsy.time.Chronological;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;

/** Maps a quote to its nominal signal interval; null excludes the quote from execution and signals. */
@FunctionalInterface
public interface QuoteSession {
    BarSession.Interval interval(long quoteTime);

    static QuoteSession daily(ZoneId zone) {
        return time -> {
            var date = Chronological.toInstant(time).atZone(zone).toLocalDate();
            return new BarSession.Interval(Chronological.toEpochNanos(date.atStartOfDay(zone)),
                    Chronological.toEpochNanos(date.plusDays(1).atStartOfDay(zone)));
        };
    }

    /** Weekday sessions with configurable Monday start and Friday finish in the supplied zone. */
    static QuoteSession weekdays(ZoneId zone, LocalTime mondayStart, LocalTime fridayFinish) {
        return time -> {
            var local = Chronological.toInstant(time).atZone(zone);
            var date = local.toLocalDate();
            var day = date.getDayOfWeek();
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return null;
            long open = Chronological.toEpochNanos(date.atTime(day == DayOfWeek.MONDAY ? mondayStart : LocalTime.MIDNIGHT).atZone(zone));
            long close = Chronological.toEpochNanos(day == DayOfWeek.FRIDAY ? date.atTime(fridayFinish).atZone(zone) : date.plusDays(1).atStartOfDay(zone));
            return time < open || time >= close ? null : new BarSession.Interval(open, close);
        };
    }
}
