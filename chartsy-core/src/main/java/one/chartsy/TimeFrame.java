package one.chartsy;

import one.chartsy.core.TimeFrameServices;
import one.chartsy.data.SimpleCandleBuilder;
import one.chartsy.data.market.DateCandleAlignment;
import one.chartsy.data.market.TimeCandleAlignment;
import one.chartsy.time.Chronological;
import one.chartsy.time.DayOfMonth;
import one.chartsy.time.Months;
import one.chartsy.time.Seconds;

import java.time.*;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.TemporalAmount;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public interface TimeFrame {

    List<TimeFrameUnit> getUnits();

    Number getSize(TimeFrameUnit unit);

    String getDisplayName();

    TimeFrameAggregator<Candle, Chronological> getAggregator(TimeFrameServices services);

    default TimeFrameAggregator<Candle, Chronological> getAggregator() {
        return getAggregator(TimeFrameServices.getDefault());
    }

    default LocalTime getDailyAlignment() {
        return LocalTime.MIDNIGHT;
    }

    default ZoneId getDailyAlignmentTimeZone() {
        return ZoneOffset.UTC;
    }

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
        public String getDisplayName() {
            return timeFrame.getDisplayName();
        }

        @Override
        public LocalTime getDailyAlignment() {
            return timeFrame.getDailyAlignment();
        }

        @Override
        public ZoneId getDailyAlignmentTimeZone() {
            return timeFrame.getDailyAlignmentTimeZone();
        }

        @Override
        public Optional<?> getCandleAlignment() {
            return timeFrame.getCandleAlignment();
        }

        @Override
        public TimeFrameAggregator<Candle, Chronological> getAggregator(TimeFrameServices services) {
            return timeFrame.getAggregator(services);
        }

        public TemporallyRegular withDailyAlignment(ZoneId dailyAlignmentTimeZone) {
            return timeFrame.withDailyAlignment(dailyAlignmentTimeZone);
        }

        public TemporallyRegular withDailyAlignment(LocalTime dailyAlignment, ZoneId dailyAlignmentTimeZone) {
            return timeFrame.withDailyAlignment(dailyAlignment, dailyAlignmentTimeZone);
        }

        public TemporallyRegular withCandleAlignment(TemporalAdjuster candleAlignment) {
            return timeFrame.withCandleAlignment(candleAlignment);
        }
    }

    class CustomPeriod<T extends TemporalAmount & Comparable<T>> implements TimeFrame, TemporallyRegular {
        private final T duration;
        private final List<TimeFrameUnit> units;
        private final String displayName;
        private final LocalTime dailyAlignment;
        private final ZoneId dailyAlignmentTimeZone;
        private final TemporalAdjuster candleAlignment;


        public static CustomPeriod<Duration> of(Duration duration, String displayName) {
            return of(duration, displayName, LocalTime.MIDNIGHT, ZoneOffset.UTC, null);
        }

        public static CustomPeriod<Duration> of(Duration duration, String displayName, LocalTime dailyAlignment) {
            return of(duration, displayName, dailyAlignment, ZoneOffset.UTC, null);
        }

        public static CustomPeriod<Duration> of(Duration duration, String displayName, LocalTime dailyAlignment, ZoneId dailyAlignmentTimeZone) {
            return of(duration, displayName, dailyAlignment, dailyAlignmentTimeZone, null);
        }

        public static CustomPeriod<Duration> of(Duration duration, String displayName, LocalTime dailyAlignment, ZoneId dailyAlignmentTimeZone, TemporalAdjuster candleAlignment) {
            return new CustomPeriod<>(duration, displayName, dailyAlignment, dailyAlignmentTimeZone, candleAlignment);
        }

        public static CustomPeriod<Months> of(Months duration, String displayName, LocalTime dailyAlignment, ZoneId dailyAlignmentTimeZone, TemporalAdjuster candleAlignment) {
            return new CustomPeriod<>(duration, displayName, dailyAlignment, dailyAlignmentTimeZone, candleAlignment);
        }

        protected CustomPeriod(T duration, String displayName, LocalTime dailyAlignment, ZoneId dailyAlignmentTimeZone, TemporalAdjuster candleAlignment) {
            this.duration = duration;
            this.displayName = displayName;
            this.dailyAlignment = dailyAlignment;
            this.dailyAlignmentTimeZone = dailyAlignmentTimeZone;
            this.candleAlignment = candleAlignment;

            List<TimeFrameUnit> units = List.of();
            if (duration instanceof Months)
                units = List.of(StandardTimeFrameUnit.MONTHS);
            if (duration instanceof Duration)
                units = List.of(StandardTimeFrameUnit.SECONDS);
            this.units = units;
        }

        public CustomPeriod<T> withDailyAlignment(ZoneId dailyAlignmentTimeZone) {
            if (dailyAlignmentTimeZone.equals(this.dailyAlignmentTimeZone)) {
                return this;
            }
            return new CustomPeriod<T>(duration, displayName, dailyAlignment, dailyAlignmentTimeZone, candleAlignment);
        }

        public CustomPeriod<T> withDailyAlignment(LocalTime dailyAlignment, ZoneId dailyAlignmentTimeZone) {
            if (dailyAlignmentTimeZone.equals(this.dailyAlignmentTimeZone) && Objects.equals(dailyAlignment, this.dailyAlignment)) {
                return this;
            }
            return new CustomPeriod<T>(duration, displayName, dailyAlignment, dailyAlignmentTimeZone, candleAlignment);
        }

        public CustomPeriod<T> withCandleAlignment(TemporalAdjuster candleAlignment) {
            if (candleAlignment.equals(this.candleAlignment)) {
                return this;
            }
            return new CustomPeriod<T>(duration, displayName, dailyAlignment, dailyAlignmentTimeZone, candleAlignment);
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
        public TimeFrameAggregator<Candle, Chronological> getAggregator(TimeFrameServices services) {
            try {
                final var SECS_IN_DAY = Duration.ofDays(1).getSeconds();
                var exactDuration = Duration.from(duration);
                var seconds = exactDuration.getSeconds();
                if (SECS_IN_DAY % seconds == 0)
                    return (TimeFrameAggregator) services.createTimeCandleAggregator(exactDuration, new SimpleCandleBuilder(), new TimeCandleAlignment(getDailyAlignmentTimeZone(), getDailyAlignment()));

            } catch (DateTimeException ignored) {
                // ignored, fallback to generic-period aggregator
            }
            return (TimeFrameAggregator) services.createPeriodCandleAggregator(duration, new SimpleCandleBuilder(), new DateCandleAlignment(getDailyAlignmentTimeZone(), getDailyAlignment(), getCandleAlignment().orElse(null)));
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public LocalTime getDailyAlignment() {
            return dailyAlignment;
        }

        @Override
        public ZoneId getDailyAlignmentTimeZone() {
            return dailyAlignmentTimeZone;
        }

        @Override
        public Optional<TemporalAdjuster> getCandleAlignment() {
            return Optional.ofNullable(candleAlignment);
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

        @Override
        public String getDisplayName() {
            return "Ticks";
        }

        @SuppressWarnings("unchecked")
        @Override
        public TimeFrameAggregator<Candle, Chronological> getAggregator(TimeFrameServices services) {
            return (TimeFrameAggregator) services.createTickOnlyAggregator();
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
