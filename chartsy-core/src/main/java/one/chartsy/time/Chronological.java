package one.chartsy.time;

import java.time.*;
import java.util.Comparator;
import java.util.Iterator;

/**
 * Interface represents an item that is defined, in part, by a point in time.
 * <p>
 * This software internally, across the entire library, uses a machine
 * representation of the time stored as a {@code long} number based on a number
 * of microseconds elapsed since the Epoch, measured using UTC clock.
 *
 * @author Mariusz Bernacki
 *
 */
@FunctionalInterface
public interface Chronological extends Comparable<Chronological> {

    /**
     * The system default time-zone.
     */
    ZoneId TIME_ZONE = ZoneId.systemDefault();

    /**
     * Converts the specified epoch microseconds to a {@code LocalDateTime} in UTC.
     * The method is performance efficient and always gives date expressed in the
     * Coordinated Universal Time (UTC). To get the date and time expressed in the
     * system default time zone (e.g. your wall clock time) use the
     * {@link #toDateTime(long, ZoneId)} method instead. The result may be converted
     * back to timestamp using the method {@link #toEpochMicros(LocalDateTime)}.
     *
     * @param epochMicros
     *            the number of microseconds elapsed since the "epoch"
     * @return the {@code LocalDateTime}
     */
    static LocalDateTime toDateTime(long epochMicros) {
        return LocalDateTime.ofEpochSecond(Math.floorDiv(epochMicros, 1000_000L),
                (int) Math.floorMod(epochMicros, 1000_000L) * 1000, ZoneOffset.UTC);
    }

    /**
     * Converts the specified epoch microseconds to an {@code Instant}.
     *
     * @param epochMicros
     *            the number of microseconds elapsed since the "epoch"
     * @return the {@code Instant}
     */
    static Instant toInstant(long epochMicros) {
        return Instant.ofEpochSecond(Math.floorDiv(epochMicros, 1000_000L),
                Math.floorMod(epochMicros, 1000_000L) * 1000);
    }

    /**
     * Converts the specified epoch microseconds to a {@code ZonedDateTime} at the
     * specified time zone.
     *
     * @param epochMicros
     *            the number of microseconds elapsed since the "epoch", i.e.
     *            1970-01-01Z
     * @param zone
     *            the target time zone of the result, if {@code null} the system
     *            default time-zone is used instead
     * @return the {@code ZonedDateTime}
     */
    static ZonedDateTime toDateTime(long epochMicros, ZoneId zone) {
        return ZonedDateTime.ofInstant(toInstant(epochMicros), (zone == null) ? TIME_ZONE : zone);
    }

    /**
     * Converts the specified epoch microseconds to an {@code OffsetDateTime} using
     * the specified time zone offset.
     *
     * @param epochMicros
     *            the number of microseconds elapsed since the "epoch", i.e.
     *            1970-01-01Z
     * @param offset
     *            the target time zone offset of the result
     * @return the {@code OffsetDateTime}
     */
    static OffsetDateTime toDateTime(long epochMicros, ZoneOffset offset) {
        LocalDateTime dateTime = LocalDateTime.ofEpochSecond(Math.floorDiv(epochMicros, 1000_000L),
                (int) Math.floorMod(epochMicros, 1000_000L) * 1000, offset);
        return OffsetDateTime.of(dateTime, offset);
    }

    /**
     * Converts the specified date and time to its internal timestamp
     * representation. The result may be converted back to {@code LocalDateTime}
     * using the method {@link #toDateTime(long)}.
     * <p>
     * If the {@code datetime} represents a point in time too far in the future or
     * past to fit in a {@code long} microseconds, then an
     * {@code ArithmeticException} is thrown. If the {@code datetime} has greater
     * than microsecond precision, then the conversion will drop any excess
     * precision information as though the amount in nanoseconds was subject to
     * integer division by one thousand.
     *
     * @param datetime
     *            the date and time to convert
     * @return the number of microseconds elapsed since the epoch, i.e.: 1970-01-01Z
     */
    static long toEpochMicros(LocalDateTime datetime) {
        return toEpochMicros(datetime.toInstant(ZoneOffset.UTC));
    }

    /**
     * Converts the specified {@code OffsetDateTime} to the epoch microseconds.
     *
     * @param datetime
     *            the date, time and zone offset to convert
     * @return the number of microseconds elapsed since the epoch, i.e.: 1970-01-01Z
     */
    static long toEpochMicros(OffsetDateTime datetime) {
        return toEpochMicros(datetime.toInstant());
    }

    /**
     * Converts the specified {@code ZonedDateTime} to the epoch microseconds.
     *
     * @param datetime
     *            the date, time and time zone to convert
     * @return the number of microseconds elapsed since the epoch, i.e.: 1970-01-01Z
     */
    static long toEpochMicros(ZonedDateTime datetime) {
        return toEpochMicros(datetime.toInstant());
    }

    /**
     * Converts the specified {@code Instant} to the epoch microseconds.
     *
     * @param instant
     *            the instant on the time-line
     * @return the number of microseconds elapsed since the epoch, i.e.: 1970-01-01Z
     */
    static long toEpochMicros(Instant instant) {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1000_000L), instant.getNano()/1000);
    }

    /**
     * Converts the specified {@code Duration} to number of microseconds suitable for adding to or removing from
     * the epoch-micros based timestamps.
     *
     * @param duration
     *            the time duration
     * @return the number of microseconds in {@code duration}
     */
    static long toMicros(Duration duration) {
        return duration.toNanos() / 1000L;
    }

    /**
     * Gives current time.
     *
     * @return the current time's epoch microseconds
     */
    static long now() {
        return toEpochMicros(Instant.now());
    }

    /**
     * Returns an object's time as a number of microseconds elapsed since the epoch
     * measured with a UTC clock.
     *
     * @return the object's time, i.e. the quote ending time when using with the
     *         {@code Quote} instances
     */
    long getTime();

    /**
     * Returns an objects's date and time as a {@code LocalDateTime} instance. The
     * method is performance efficient and always gives date expressed in the UTC
     * (Coordinated Universal Time). To get the date and time expressed in the
     * system default time zone (e.g. your wall clock time) use the
     * {@link #getDateTime(ZoneId)} method instead.
     * <p>
     * The method is equivalent to:
     *
     * <pre>
     * {@code Chronological.toDateTime(this.getTime())}
     * </pre>
     *
     * @return the {@code LocalDateTime} in UTC
     */
    default LocalDateTime getDateTime() {
        return toDateTime(getTime());
    }

    /**
     * Returns an object's date as a {@code LocalDate} instance. The method is
     * performance efficient and always gives date expressed in the UTC (Coordinated
     * Universal Time). The time component present in the current date-time is
     * ignored. The method is equivalent to:
     *
     * <pre>
     * {@code this.getDateTime().toLocalDate()}
     * </pre>
     *
     * @return the {@code LocalDate} in UTC
     */
    default LocalDate getDate() {
        return toDateTime(getTime()-1).toLocalDate();
    }

    /**
     * Returns an object's date and time as a {@code ZonedDateTime} instance
     * converted to the specified time-zone. The method is equivalent to:
     *
     * <pre>
     * {@code Chronological.toDateTime(this.getTime(), zone)}
     * </pre>
     *
     * @param zone
     *            the target time zone of the result, if {@code null} the system
     *            default time-zone is used instead
     * @return the {@code ZonedDateTime} using the given time zone
     */
    default ZonedDateTime getDateTime(ZoneId zone) {
        return toDateTime(getTime(), zone);
    }

    /**
     * Checks if this chronological is after the specified time.
     *
     * @param other the other chronological event to compare to, not null
     * @return {@code true} if and only if {@code this.getTime() > other.getTime()}
     */
    default boolean isAfter(Chronological other) {
        return getTime() > other.getTime();
    }

    /**
     * Checks if this chronological is before the specified time.
     *
     * @param other the other chronological event to compare to, not null
     * @return {@code true} if and only if {@code this.getTime() < other.getTime()}
     */
    default boolean isBefore(Chronological other) {
        return getTime() < other.getTime();
    }

    /**
     * Compares two chronological values for time ordering.
     *
     * @param other
     *            the {@code Chronological} to be compared
     * @return the value {@code 0} if the date of the argument is equal to this
     *         date; a value less than {@code 0} if this date is before the date
     *         of the argument; and a value greater than {@code 0} if this date
     *         is after the date of the argument
     * @throws NullPointerException
     *             if the {@code other} is null
     */
    @Override
    default int compareTo(Chronological other) {
        return Long.compare(getTime(), other.getTime());
    }

    /**
     * Indicates the chronological order of elements.
     *
     * @author Mariusz Bernacki
     *
     */
    enum Order {
        /** The elements are in natural chronological order. */
        CHRONOLOGICAL,
        /** The elements are in reverse chronological order - the most recent first. */
        REVERSE_CHRONOLOGICAL;

        /**
         * Returns the comparator which can be used to sort elements in this
         * chronological order.
         *
         * @return the comparator for this exact order
         */
        public Comparator<Chronological> comparator() {
            return equals(CHRONOLOGICAL)? naturalOrder : reverseOrder;
        }

        public boolean isReversed() {
            return equals(REVERSE_CHRONOLOGICAL);
        }

        public boolean isOrdered(Chronological[] elements) {
            boolean reversed = isReversed();
            int size = elements.length;
            if (size > 1) {
                long previous = elements[0].getTime();
                int i = 1;
                while (i < size) {
                    long current = elements[i++].getTime();
                    if (reversed && current > previous || !reversed && current < previous)
                        return false;
                    previous = current;
                }
            }
            return true;
        }

        public boolean isOrdered(Iterable<? extends Chronological> elements) {
            boolean reversed = isReversed();
            Iterator<? extends Chronological> iter = elements.iterator();
            if (iter.hasNext()) {
                long previous = iter.next().getTime();
                while (iter.hasNext()) {
                    long current = iter.next().getTime();
                    if (reversed && current > previous || !reversed && current < previous)
                        return false;
                    previous = current;
                }
            }
            return true;
        }

        private static final Comparator<Chronological> naturalOrder = Comparator.comparingLong(Chronological::getTime),
                reverseOrder = naturalOrder.reversed();
    }
}
