package one.chartsy;

import one.chartsy.time.Chronological;
import one.chartsy.time.Seconds;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.OptionalInt;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;

public final class TimeFrameHelper {

    public static OptionalInt toSeconds(TimeFrame tf) {
        var seconds = tf.getAsSeconds();
        return seconds.isEmpty()? OptionalInt.empty(): OptionalInt.of(seconds.get().getAmount());
    }

    public static int toSecondsExact(TimeFrame tf) {
        return toSeconds(tf).orElseThrow(InvalidTimeFrameException::new);
    }

    public static OptionalInt toMonths(TimeFrame tf) {
        var months = tf.getAsMonths();
        return months.isEmpty()? OptionalInt.empty(): OptionalInt.of(months.get().getAmount());
    }

    public static int toMonthsExact(TimeFrame tf) {
        return toMonths(tf).orElseThrow(InvalidTimeFrameException::new);
    }

    public static boolean isIntraday(TimeFrame tf) {
        return (tf != TimeFrame.Period.DAILY && tf != TimeFrame.Period.WEEKLY && tf != TimeFrame.Period.QUARTERLY && tf != TimeFrame.Period.MONTHLY && tf != TimeFrame.Period.YEARLY);
    }

    public static String formatDate(TimeFrame timeFrame, long epochMicros) {
        LocalDateTime date = Chronological.toDateTime(epochMicros);
        if (isIntraday(timeFrame))
            return ISO_LOCAL_DATE.format(date) + " " + ISO_LOCAL_TIME.format(date);
        else
            return ISO_LOCAL_DATE.format(date);
    }

    public static String getName(TimeFrame tf) {
        return tf.toString();
    }
}
