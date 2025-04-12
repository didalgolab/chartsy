/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.time;

import java.time.*;
import java.util.Comparator;
import java.util.Iterator;

/**
 * Interface represents an item that is defined, in part, by a point in time.
 * <p>
 * This software internally, across the entire library, uses a machine
 * representation of the time expressed as a {@code long} number based on a number
 * of nanoseconds elapsed since the Epoch, measured using UTC clock.
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
     * Converts the specified epoch nanoseconds to a {@code LocalDateTime} in UTC.
     * <p>
     * The method is performance efficient but always gives date expressed in the
     * Coordinated Universal Time (UTC). To get the date and time expressed in the
     * system default time zone (e.g. your wall clock) use the
     * {@link #toDateTime(long, ZoneId)} method instead. The result may be converted
     * back to timestamp using the method {@link #toEpochNanos(LocalDateTime)}.
     *
     * @param epochNanos
     *            the number of nanoseconds elapsed since the "epoch"
     * @return the {@code LocalDateTime} in the Coordinated Universal Time (UTC)
     */
    static LocalDateTime toDateTime(long epochNanos) {
        return LocalDateTime.ofEpochSecond(Math.floorDiv(epochNanos, 1_000_000_000L),
                (int) Math.floorMod(epochNanos, 1_000_000_000L), ZoneOffset.UTC);
    }

    /**
     * Returns an object's date and time as a {@code LocalDateTime} instance.
     *
     * @return the {@code LocalDateTime} in the Coordinated Universal Time (UTC)
     * @see #toDateTime(long)
     */
    default LocalDateTime getDateTime() {
        return toDateTime(getTime());
    }

    /**
     * Returns an object's date as a {@code LocalDate} instance.
     *
     * @return the {@code LocalDate} in the Coordinated Universal Time (UTC)
     * @see #toDateTime(long)
     */
    default LocalDate getDate() {
        return toDateTime(getTime() - 1).toLocalDate();
    }

    /**
     * Converts the specified epoch nanoseconds to an {@code Instant}.
     *
     * @param epochNanos
     *            the number of nanoseconds elapsed since the "epoch"
     * @return the {@code Instant}
     */
    static Instant toInstant(long epochNanos) {
        return Instant.ofEpochSecond(0, epochNanos);
    }

    /**
     * Converts the specified epoch nanoseconds to a {@code ZonedDateTime} at the
     * specified time zone.
     *
     * @param epochNanos
     *            the number of nanoseconds elapsed since the "epoch", i.e.
     *            1970-01-01Z
     * @param zone
     *            the target time zone of the result, if {@code null} the system
     *            default time-zone is used instead
     * @return the {@code ZonedDateTime}
     */
    static ZonedDateTime toDateTime(long epochNanos, ZoneId zone) {
        return ZonedDateTime.ofInstant(toInstant(epochNanos), (zone == null) ? TIME_ZONE : zone);
    }

    /**
     * Converts the specified epoch nanoseconds to an {@code OffsetDateTime} using
     * the specified time zone offset.
     *
     * @param epochNanos
     *            the number of nanoseconds elapsed since the "epoch", i.e.
     *            1970-01-01Z
     * @param offset
     *            the target time zone offset of the result
     * @return the {@code OffsetDateTime}
     */
    static OffsetDateTime toDateTime(long epochNanos, ZoneOffset offset) {
        LocalDateTime dateTime = LocalDateTime.ofEpochSecond(Math.floorDiv(epochNanos, 1_000_000_000L),
                (int) Math.floorMod(epochNanos, 1_000_000_000L), offset);
        return OffsetDateTime.of(dateTime, offset);
    }

    /**
     * Converts the specified date and time to its internal timestamp
     * representation. The result may be converted back to {@code LocalDateTime}
     * using the method {@link #toDateTime(long)}.
     * <p>
     * If the {@code datetime} represents a point in time too far in the future or
     * past to fit in a {@code long} nanoseconds, then an
     * {@code ArithmeticException} is thrown. If the {@code datetime} has greater
     * than nanosecond precision, then the conversion will drop any excess
     * precision information.
     *
     * @param datetime
     *            the date and time to convert
     * @return the number of nanoseconds elapsed since the epoch, i.e.: 1970-01-01Z
     * @throws ArithmeticException if the result overflows a {@code long},
     *         i.e. {@code instant} is before 1677-09-21Z or after 2262-04-11Z
     */
    static long toEpochNanos(LocalDateTime datetime) {
        return toEpochNanos(datetime.toInstant(ZoneOffset.UTC));
    }

    /**
     * Converts the specified {@code OffsetDateTime} to the epoch nanoseconds.
     *
     * @param datetime
     *            the date, time and zone offset to convert
     * @return the number of nanoseconds elapsed since the epoch, i.e.: 1970-01-01Z
     * @throws ArithmeticException if the result overflows a {@code long},
     *         i.e. {@code instant} is before 1677-09-21Z or after 2262-04-11Z
     */
    static long toEpochNanos(OffsetDateTime datetime) {
        return toEpochNanos(datetime.toInstant());
    }

    /**
     * Converts the specified {@code ZonedDateTime} to the epoch nanoseconds.
     *
     * @param datetime
     *            the date, time and time zone to convert
     * @return the number of nanoseconds elapsed since the epoch, i.e.: 1970-01-01Z
     * @throws ArithmeticException if the result overflows a {@code long},
     *         i.e. {@code instant} is before 1677-09-21Z or after 2262-04-11Z
     */
    static long toEpochNanos(ZonedDateTime datetime) {
        return toEpochNanos(datetime.toInstant());
    }

    /**
     * Converts the specified {@code Instant} to the epoch nanoseconds.
     *
     * @param instant
     *            the instant on the time-line
     * @return the number of nanoseconds elapsed since the epoch, i.e.: 1970-01-01Z
     * @throws ArithmeticException if the result overflows a {@code long},
     *         i.e. {@code instant} is before 1677-09-21Z or after 2262-04-11Z
     */
    static long toEpochNanos(Instant instant) {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
    }

    /**
     * Converts the specified {@code Duration} to number of nanoseconds suitable for adding to or removing from
     * the epoch-nanos based timestamps.
     *
     * @param duration
     *            the time duration
     * @return the number of nanoseconds in {@code duration}
     */
    static long toNanos(Duration duration) {
        return duration.toNanos();
    }

    /**
     * Gives current time.
     *
     * @return the current time's epoch nanoseconds
     */
    static long now() {
        return toEpochNanos(Instant.now());
    }

    /**
     * Returns an object's time as a number of nanoseconds elapsed since the epoch
     * measured with a UTC clock.
     *
     * @return the object's time, i.e. the quote ending time when using with the
     *         {@code Quote} instances
     */
    long getTime();

    /**
     * Returns an object's date and time as a {@code ZonedDateTime} instance
     * converted to the specified time-zone.
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
     * Checks if this chronological is after the specified local date-time.
     * The comparison is performed by converting the given {@code LocalDateTime}
     * to epoch nanoseconds assuming UTC via {@link #toEpochNanos(LocalDateTime)}.
     *
     * @param dateTime the local date-time to compare to
     * @return {@code true} if this object's time is strictly after the instant represented
     *         by the {@code dateTime} in UTC
     * @throws ArithmeticException if the conversion of {@code dateTime} overflows a {@code long}
     */
    default boolean isAfter(LocalDateTime dateTime) {
        return getTime() > Chronological.toEpochNanos(dateTime);
    }

    /**
     * Checks if this chronological is before the specified local date-time.
     * The comparison is performed by converting the given {@code LocalDateTime}
     * to epoch nanoseconds assuming UTC via {@link #toEpochNanos(LocalDateTime)}.
     *
     * @param dateTime the local date-time to compare to, not null
     * @return {@code true} if this object's time is strictly before the instant represented
     *         by the {@code dateTime} in UTC
     * @throws ArithmeticException if the conversion of {@code dateTime} overflows a {@code long}
     */
    default boolean isBefore(LocalDateTime dateTime) {
        return getTime() < Chronological.toEpochNanos(dateTime);
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
    enum ChronoOrder {
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
