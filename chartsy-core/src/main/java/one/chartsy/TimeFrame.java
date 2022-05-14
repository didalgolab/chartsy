/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import one.chartsy.core.TimeFrameServices;
import one.chartsy.data.SimpleCandleBuilder;
import one.chartsy.data.market.DateCandleAlignment;
import one.chartsy.data.market.Tick;
import one.chartsy.data.market.TimeCandleAlignment;
import one.chartsy.time.DayOfMonth;
import one.chartsy.time.Months;
import one.chartsy.time.Seconds;

import java.time.*;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Optional;

/**
 * Holds the attributes which specify the time interval of the candle.
 */
public interface TimeFrame {

    /**
     * Gives all units of measure uniquely identifying the time frame.
     */
    List<TimeFrameUnit> getUnits();

    /**
     * Gives the length of the time frame quantified in the specified unit.
     *
     * @param unit the time frame unit (e.g. SECONDS, TICKS, PRICE_RANGE,...)
     * @return length of the time frame as a number, usually of type {@code Long} or {@code BigDecimal}
     */
    Number getSize(TimeFrameUnit unit);

    /**
     * A human-readable string describing the time frame.
     */
    @Override
    String toString();

    TimeFrameAggregator<Candle, Tick> getAggregator(TimeFrameServices services);

    default TimeFrameAggregator<Candle, Tick> getAggregator() {
        return getAggregator(TimeFrameServices.getDefault());
    }

    /**
     * Gives the time zone associated with the time frame. The specified time zone may be used by the
     * {@link #getDailyAlignment() dailyAlignment} and other time zone parameters if supported by the subclass.
     */
    default ZoneId getTimeZone() {
        return ZoneOffset.UTC;
    }

    /**
     * Gives the expected alignment of candles in respect of time.
     */
    default LocalTime getDailyAlignment() {
        return LocalTime.MIDNIGHT;
    }

    /**
     * Gives an additional alignment to which candles represented by this time frame are aligned to.
     */
    default Optional<?> getCandleAlignment() {
        return Optional.empty();
    }

    enum Period implements TimeFrame, TemporallyRegular {
        /** The 1 day bars, day alignment. */
        DAILY(Duration.ofSeconds(86400), "Daily", LocalTime.MIDNIGHT),
        /** The 1 week bars, aligned to start of a week. */
        WEEKLY(Duration.ofSeconds(604800), "Weekly", DayOfWeek.MONDAY),
        /** The 1 month bars, aligned to first day of a month. */
        MONTHLY(Months.of(1), "Monthly", DayOfMonth.of(1)),
        /** The 1 quarter bars, aligned to first day of a quarter. */
        QUARTERLY(Months.of(3), "Quarterly", t -> t.with(IsoFields.DAY_OF_QUARTER, 1)),
        /** The 1 year bars, aligned to first day of a year. */
        YEARLY(Months.of(12), "Yearly", TemporalAdjusters.firstDayOfYear()),
        /** The 1 second bars, can be also interpreted as and used instead of ticks. */
        S1(Duration.ofSeconds(1), "S1"),
        /** The 5 second bars with a minute alignment. */
        S5(Duration.ofSeconds(5), "S5"),
        /** The 10 second bars with a minute alignment. */
        S10(Duration.ofSeconds(10), "S10"),
        /** The 15 second bars with a minute alignment. */
        S15(Duration.ofSeconds(15), "S15"),
        /** The 30 second bars with a minute alignment. */
        S30(Duration.ofSeconds(30), "S30"),
        /** The 1 minute bars with a minute alignment.*/
        M1(Duration.ofSeconds(60), "M1"),
        /** The 2 minute bars with an hour alignment. */
        M2(Duration.ofSeconds(120), "M2"),
        /** The 3 minute bars with an hour alignment. */
        M3(Duration.ofSeconds(180), "M3"),
        /** The 4 minute bars with an hour alignment. */
        M4(Duration.ofSeconds(240), "M4"),
        /** The 5 minute bars with an hour alignment. */
        M5(Duration.ofSeconds(300), "M5"),
        /** The 6 minute bars with an hour alignment. */
        M6(Duration.ofSeconds(360), "M6"),
        /** The 10 minute bars with an hour alignment. */
        M10(Duration.ofSeconds(600), "M10"),
        /** The 12 minute bars with an hour alignment. */
        M12(Duration.ofSeconds(720), "M12"),
        /** The 15 minute bars with an hour alignment. */
        M15(Duration.ofSeconds(900), "M15"),
        /** The 20 minute bars with an hour alignment. */
        M20(Duration.ofSeconds(1200), "M20"),
        /** The 30 minute bars with an hour alignment. */
        M30(Duration.ofSeconds(1800), "M30"),
        /** The 45 minute bars with a day alignment. */
        M45(Duration.ofSeconds(2700), "M45"),
        /** The 1 hour bars with an hour alignment. */
        H1(Duration.ofSeconds(3600), "H1"),
        /** The 90 minute bars with a day alignment. */
        M90(Duration.ofSeconds(5400), "M90"),
        /** The 2 hour bars with a day alignment. */
        H2(Duration.ofSeconds(7200), "H2"),
        /** The 3 hour bars with a day alignment. */
        H3(Duration.ofSeconds(10800), "H3"),
        /** The 4 hour bars with a day alignment. */
        H4(Duration.ofSeconds(14400), "H4"),
        /** The 6 hour bars with a day alignment. */
        H6(Duration.ofSeconds(21600), "H6"),
        /** The 8 hour bars with a day alignment. */
        H8(Duration.ofSeconds(28800), "H8"),
        /** The 12 hour bars with a day alignment. */
        H12(Duration.ofSeconds(43200), "H12");

        /** The underlying custom time frame object. */
        private final CustomPeriod<?> timeFrame;

        Period(Duration duration, String name) {
            timeFrame = CustomPeriod.of(duration, name);
        }

        Period(Duration duration, String name, TemporalAdjuster alignment) {
            timeFrame = CustomPeriod.of(duration, name, LocalTime.MIDNIGHT, ZoneOffset.UTC, alignment);
        }

        Period(Months months, String name, TemporalAdjuster alignment) {
            timeFrame = CustomPeriod.of(months, name, LocalTime.MIDNIGHT, ZoneOffset.UTC, alignment);
        }

        @Override
        public List<TimeFrameUnit> getUnits() {
            return timeFrame.getUnits();
        }

        @Override
        public TemporalAmount getRegularity() {
            return timeFrame.getRegularity();
        }

        @Override
        public Number getSize(TimeFrameUnit unit) {
            return timeFrame.getSize(unit);
        }

        @Override
        public LocalTime getDailyAlignment() {
            return timeFrame.getDailyAlignment();
        }

        @Override
        public ZoneId getTimeZone() {
            return timeFrame.getTimeZone();
        }

        @Override
        public Optional<?> getCandleAlignment() {
            return timeFrame.getCandleAlignment();
        }

        @Override
        public TimeFrameAggregator<Candle, Tick> getAggregator(TimeFrameServices services) {
            return timeFrame.getAggregator(services);
        }

        public TemporallyRegular withTimeZone(ZoneId timeZone) {
            return timeFrame.withTimeZone(timeZone);
        }

        public TemporallyRegular withDailyAlignment(LocalTime dailyAlignment) {
            return timeFrame.withDailyAlignment(dailyAlignment);
        }

        public TemporallyRegular withDailyAlignment(LocalTime dailyAlignment, ZoneId timeZone) {
            return timeFrame.withDailyAlignment(dailyAlignment, timeZone);
        }

        public TemporallyRegular withCandleAlignment(TemporalAdjuster candleAlignment) {
            return timeFrame.withCandleAlignment(candleAlignment);
        }

        @Override
        public String toString() {
            return timeFrame.toString();
        }
    }

    class CustomPeriod<T extends TemporalAmount & Comparable<T>> implements TimeFrame, TemporallyRegular {
        private final T duration;
        private final List<TimeFrameUnit> units;
        private final String name;
        private final ZoneId timeZone;
        private final LocalTime dailyAlignment;
        private final TemporalAdjuster candleAlignment;


        public static CustomPeriod<Duration> of(Duration duration, String name) {
            return of(duration, name, LocalTime.MIDNIGHT, ZoneOffset.UTC, null);
        }

        public static CustomPeriod<Duration> of(Duration duration, String name, LocalTime dailyAlignment) {
            return of(duration, name, dailyAlignment, ZoneOffset.UTC, null);
        }

        public static CustomPeriod<Duration> of(Duration duration, String name, LocalTime dailyAlignment, ZoneId dailyAlignmentTimeZone) {
            return of(duration, name, dailyAlignment, dailyAlignmentTimeZone, null);
        }

        public static CustomPeriod<Duration> of(Duration duration, String name, LocalTime dailyAlignment, ZoneId dailyAlignmentTimeZone, TemporalAdjuster candleAlignment) {
            return new CustomPeriod<>(duration, name, dailyAlignmentTimeZone, dailyAlignment, candleAlignment);
        }

        public static CustomPeriod<Months> of(Months duration, String name, LocalTime dailyAlignment, ZoneId dailyAlignmentTimeZone, TemporalAdjuster candleAlignment) {
            return new CustomPeriod<>(duration, name, dailyAlignmentTimeZone, dailyAlignment, candleAlignment);
        }

        protected CustomPeriod(T duration, String name, ZoneId timeZone, LocalTime dailyAlignment, TemporalAdjuster candleAlignment) {
            this.duration = duration;
            this.name = name;
            this.timeZone = timeZone;
            this.dailyAlignment = dailyAlignment;
            this.candleAlignment = candleAlignment;

            List<TimeFrameUnit> units = List.of();
            if (duration instanceof Months)
                units = List.of(StandardTimeFrameUnit.MONTHS);
            if (duration instanceof Duration)
                units = List.of(StandardTimeFrameUnit.SECONDS);
            this.units = units;
        }

        public CustomPeriod<T> withTimeZone(ZoneId timeZone) {
            if (timeZone.equals(this.timeZone))
                return this;
            return new CustomPeriod<T>(duration, name, timeZone, dailyAlignment, candleAlignment);
        }

        public CustomPeriod<T> withDailyAlignment(LocalTime dailyAlignment) {
            if (dailyAlignment.equals(this.dailyAlignment))
                return this;
            return new CustomPeriod<T>(duration, name, timeZone, dailyAlignment, candleAlignment);
        }

        public CustomPeriod<T> withDailyAlignment(LocalTime dailyAlignment, ZoneId timeZone) {
            if (timeZone.equals(this.timeZone) && dailyAlignment.equals(this.dailyAlignment))
                return this;
            return new CustomPeriod<T>(duration, name, timeZone, dailyAlignment, candleAlignment);
        }

        public CustomPeriod<T> withCandleAlignment(TemporalAdjuster candleAlignment) {
            if (candleAlignment.equals(this.candleAlignment))
                return this;
            return new CustomPeriod<T>(duration, name, timeZone, dailyAlignment, candleAlignment);
        }

        @Override
        public T getRegularity() {
            return duration;
        }

        @Override
        public List<TimeFrameUnit> getUnits() {
            return units;
        }

        @Override
        public Number getSize(TimeFrameUnit unit) {
            var units = getUnits();
            if (!units.isEmpty() && units.get(0).equals(unit))
                return duration.get(duration.getUnits().get(0));
            else
                return null;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        @Override
        public TimeFrameAggregator<Candle, Tick> getAggregator(TimeFrameServices services) {
            try {
                final var SECS_IN_DAY = Duration.ofDays(1).getSeconds();
                var exactDuration = Duration.from(duration);
                var seconds = exactDuration.getSeconds();
                if (SECS_IN_DAY % seconds == 0)
                    return (TimeFrameAggregator) services.createTimeCandleAggregator(exactDuration, SimpleCandleBuilder.create(), new TimeCandleAlignment(getTimeZone(), getDailyAlignment()));

            } catch (DateTimeException ignored) {
                // ignored, fallback to generic-period aggregator
            }
            return (TimeFrameAggregator) services.createPeriodCandleAggregator(duration, SimpleCandleBuilder.create(), new DateCandleAlignment(getTimeZone(), getDailyAlignment(), getCandleAlignment().orElse(null)));
        }

        @Override
        public LocalTime getDailyAlignment() {
            return dailyAlignment;
        }

        @Override
        public ZoneId getTimeZone() {
            return timeZone;
        }

        @Override
        public Optional<TemporalAdjuster> getCandleAlignment() {
            return Optional.ofNullable(candleAlignment);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    TimeFrame TICKS = new TimeFrame() {
        @Override
        public List<TimeFrameUnit> getUnits() {
            return List.of(StandardTimeFrameUnit.TICKS);
        }

        @Override
        public Number getSize(TimeFrameUnit unit) {
            return 1L;
        }

        @SuppressWarnings("unchecked")
        @Override
        public TimeFrameAggregator<Candle, Tick> getAggregator(TimeFrameServices services) {
            return (TimeFrameAggregator) services.createTickOnlyAggregator();
        }

        @Override
        public String toString() {
            return "Ticks";
        }
    };

    /** Indicates an irregular, possibly non-continuous (or overlapping) bar sequences. */
    TimeFrame IRREGULAR = new TimeFrame() {
        @Override
        public List<TimeFrameUnit> getUnits() {
            return List.of();
        }

        @Override
        public Number getSize(TimeFrameUnit unit) {
            return 0L;
        }

        @Override
        public TimeFrameAggregator<Candle, Tick> getAggregator(TimeFrameServices services) {
            throw new UnsupportedOperationException("TimeFrame is IRREGULAR");
        }

        @Override
        public String toString() {
            return "Irregular";
        }
    };

    default boolean isAssignableFrom(TimeFrame other) {
        return equals(other);
    }

    default Optional<Seconds> getAsSeconds() {
        if (this instanceof TemporallyRegular tbf) {
            try {
                return Optional.of(Seconds.from(tbf.getRegularity()));
            } catch (DateTimeException ignored) {}// not convertible to seconds-only instance
        }
        return Optional.empty();
    }

    default Optional<Months> getAsMonths() {
        if (this instanceof TemporallyRegular tbf) {
            try {
                return Optional.of(Months.from(tbf.getRegularity()));
            } catch (DateTimeException ignored) {}// not convertible to seconds-only instance
        }
        return Optional.empty();
    }

    interface TemporallyRegular extends TimeFrame {

        TemporalAmount getRegularity();
    }
}
