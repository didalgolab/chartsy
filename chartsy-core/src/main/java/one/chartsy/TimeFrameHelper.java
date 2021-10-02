package one.chartsy;

import one.chartsy.time.Chronological;

import java.time.LocalDateTime;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;

public final class TimeFrameHelper {

    public static boolean isIntraday(TimeFrame timeFrame) {
        return true;
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
