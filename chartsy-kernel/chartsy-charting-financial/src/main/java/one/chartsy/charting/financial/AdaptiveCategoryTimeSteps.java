package one.chartsy.charting.financial;

import one.chartsy.charting.DataInterval;
import one.chartsy.charting.TimeUnit;
import one.chartsy.charting.util.text.DateFormatFactoryExt;

import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdaptiveCategoryTimeSteps extends CategoryTimeSteps {
    private static final double MIN_INTRADAY_TICK_SPACING_PX = 72.0d;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("d");
    private static final DateTimeFormatter DAY_WITH_MONTH_FORMAT = DateTimeFormatter.ofPattern("d MMM");
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("MMM");
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");

    public enum Lane {
        TIME,
        DAY,
        MONTH,
        YEAR,
        DATE,
        NONE
    }

    public record LanePlan(Lane upper, Lane lower) {
    }

    public record TickValue(double value, String label) {
    }

    private final Date[] dates;
    private final TimeUnit baseUnit;

    public AdaptiveCategoryTimeSteps(Date[] dates, TimeUnit unit) {
        super(dates, unit);
        this.dates = dates;
        this.baseUnit = unit;
    }

    public double baseUnitMillis() {
        return baseUnit.getMillis();
    }

    public LanePlan plan(DataInterval range) {
        return plan(range, resolvePlotWidth());
    }

    public LanePlan plan(DataInterval range, int plotWidth) {
        double visibleSlots = Math.max(1.0d, Math.abs(range.getLength()));
        double visibleDays = visibleSpanDays(range, visibleSlots);
        TimeUnit upperStep = selectUpperStepUnit(baseUnit.getMillis(), visibleSlots, visibleDays, plotWidth);
        return lanePlan(visibleDays, baseUnit.getMillis(), upperStep);
    }

    public double visibleDays(DataInterval range) {
        double visibleSlots = Math.max(1.0d, Math.abs(range.getLength()));
        return visibleSpanDays(range, visibleSlots);
    }

    public TimeUnit upperStepUnit(DataInterval range, int plotWidth) {
        double visibleSlots = Math.max(1.0d, Math.abs(range.getLength()));
        double visibleDays = visibleSpanDays(range, visibleSlots);
        return selectUpperStepUnit(baseUnit.getMillis(), visibleSlots, visibleDays, plotWidth);
    }

    public List<TickValue> snapshotUpperTicks(DataInterval range, int plotWidth) {
        if (range == null || range.isEmpty() || dates.length == 0)
            return List.of();
        return snapshotTicks(range, upperStepUnit(range, plotWidth));
    }

    @Override
    protected TimeUnit computeStepUnit(DataInterval range) {
        return upperStepUnit(range, resolvePlotWidth());
    }

    private double visibleSpanDays(DataInterval range, double fallbackVisibleSlots) {
        double baseUnitMillis = Math.max(1.0d, getBaseUnit().getMillis());
        if (dates.length == 0)
            return Math.abs(fallbackVisibleSlots * baseUnitMillis / TimeUnit.DAY.getMillis());

        double leadingProjectedMillis = Math.max(0.0d, -range.getMin()) * baseUnitMillis;
        double trailingProjectedMillis = Math.max(0.0d, range.getMax() - dates.length) * baseUnitMillis;
        double visibleSlotMillis = Math.max(
                baseUnitMillis,
                Math.abs(range.getLength()) * baseUnitMillis
        );
        return (visibleSlotMillis + leadingProjectedMillis + trailingProjectedMillis) / TimeUnit.DAY.getMillis();
    }

    public static LanePlan plan(long firstTime, long lastTime, double observedBaseUnitMillis) {
        double visibleDays = Math.abs(lastTime - firstTime) / (double) TimeUnit.DAY.getMillis() / 1_000_000.0d;
        return plan(visibleDays, observedBaseUnitMillis);
    }

    public static LanePlan plan(double visibleDays, double observedBaseUnitMillis) {
        double effectiveBaseUnitMillis = (observedBaseUnitMillis > 0.0d) ? observedBaseUnitMillis : TimeUnit.DAY.getMillis();
        if (effectiveBaseUnitMillis < TimeUnit.DAY.getMillis()) {
            boolean fineGrainedIntraday = effectiveBaseUnitMillis < 7_200_000.0d;
            boolean coarseIntraday = effectiveBaseUnitMillis >= 10_800_000.0d;
            double timeLaneLimit = fineGrainedIntraday ? 7.0d : 3.0d;
            double dayLaneLimit = coarseIntraday ? 21.0d : 45.0d;
            if (visibleDays <= timeLaneLimit)
                return new LanePlan(Lane.TIME, Lane.DATE);
            if (visibleDays <= dayLaneLimit)
                return new LanePlan(Lane.DAY, Lane.MONTH);
            return visibleDays >= 365.0d * 4.0d
                    ? new LanePlan(Lane.YEAR, Lane.NONE)
                    : new LanePlan(Lane.MONTH, Lane.YEAR);
        }

        if (visibleDays >= 365.0d * 4.0d)
            return new LanePlan(Lane.YEAR, Lane.NONE);
        if (effectiveBaseUnitMillis >= TimeUnit.WEEK.getMillis() || visibleDays > 45.0d)
            return new LanePlan(Lane.MONTH, Lane.YEAR);
        return new LanePlan(Lane.DAY, Lane.MONTH);
    }

    private static LanePlan lanePlan(double visibleDays, double observedBaseUnitMillis, TimeUnit upperStep) {
        double upperStepMillis = (upperStep != null) ? upperStep.getMillis() : observedBaseUnitMillis;
        if (upperStepMillis < TimeUnit.DAY.getMillis())
            return new LanePlan(Lane.TIME, Lane.DATE);
        if (upperStepMillis < TimeUnit.MONTH.getMillis())
            return new LanePlan(Lane.DAY, Lane.MONTH);
        if (upperStepMillis < TimeUnit.YEAR.getMillis())
            return new LanePlan(Lane.MONTH, Lane.YEAR);
        return new LanePlan(Lane.YEAR, Lane.NONE);
    }

    public List<TickValue> snapshotTicks(DataInterval range, Lane lane, boolean compactDay) {
        if (range == null || range.isEmpty() || lane == Lane.NONE || dates.length == 0)
            return List.of();

        return snapshotTicks(range, explicitStepUnit(lane, compactDay));
    }

    public List<TickValue> snapshotAutoTicks(DataInterval range) {
        if (range == null || range.isEmpty() || dates.length == 0)
            return List.of();

        return snapshotTicks(range, computeStepUnit(range));
    }

    private List<TickValue> snapshotTicks(DataInterval range, TimeUnit stepUnit) {
        int categoryUnit = Math.max(1, (int) Math.ceil(stepUnit.getMillis() / baseUnit.getMillis()));
        int firstDate = getFirstDate(range, stepUnit, categoryUnit);
        if (firstDate == -1 || firstDate >= dates.length)
            return List.of();

        Locale locale = getLocale();
        DateFormat dateFormat = DateFormatFactoryExt.getInstance(stepUnit.getFormatString(locale), locale, null);
        List<TickValue> ticks = new ArrayList<>();
        int idx = firstDate;
        Calendar cal = stepBoundary(dates[idx], stepUnit, locale);
        Calendar labelCal = Calendar.getInstance(locale);
        while (idx < range.getMax() && idx < dates.length) {
            labelCal.setTime(dates[idx]);
            ticks.add(new TickValue(idx, stepUnit.format(dateFormat, labelCal, locale)));
            int previousIdx = idx;
            do {
                cal = stepUnit.incrementTime(cal);
                idx = Arrays.binarySearch(dates, cal.getTime());
                if (idx < 0)
                    idx = -idx - 1;
            } while (idx <= previousIdx && idx < dates.length);
        }
        return List.copyOf(ticks);
    }

    public static String format(Lane lane, long epochNanos, boolean compactDay) {
        LocalDateTime time = toDateTime(epochNanos);
        return switch (lane) {
            case TIME -> TIME_FORMAT.localizedBy(Locale.getDefault(Locale.Category.FORMAT)).format(time);
            case DAY -> (compactDay ? DAY_FORMAT : DAY_WITH_MONTH_FORMAT)
                    .localizedBy(Locale.getDefault(Locale.Category.FORMAT))
                    .format(time);
            case MONTH -> MONTH_FORMAT.localizedBy(Locale.getDefault(Locale.Category.FORMAT)).format(time);
            case YEAR -> YEAR_FORMAT.format(time);
            case DATE -> DAY_WITH_MONTH_FORMAT.localizedBy(Locale.getDefault(Locale.Category.FORMAT)).format(time);
            case NONE -> "";
        };
    }

    public static String key(Lane lane, long epochNanos) {
        LocalDateTime time = toDateTime(epochNanos);
        return switch (lane) {
            case TIME -> "%04d-%02d-%02dT%02d".formatted(
                    time.getYear(), time.getMonthValue(), time.getDayOfMonth(), time.getHour());
            case DAY, DATE -> "%04d-%02d-%02d".formatted(
                    time.getYear(), time.getMonthValue(), time.getDayOfMonth());
            case MONTH -> "%04d-%02d".formatted(time.getYear(), time.getMonthValue());
            case YEAR -> Integer.toString(time.getYear());
            case NONE -> "";
        };
    }

    public static TimeUnit selectUpperStepUnit(double baseUnitMillis,
                                               double visibleSlots,
                                               double visibleDays,
                                               int plotWidth) {
        LanePlan plan = plan(visibleDays, baseUnitMillis);
        if (plan.upper() == Lane.TIME)
            return intradayUpperStepUnit(baseUnitMillis, visibleSlots, visibleDays, plotWidth);
        return switch (plan.upper()) {
            case YEAR -> formatted(TimeUnit.YEAR, "yyyy");
            case MONTH -> formatted(TimeUnit.MONTH, "MMM");
            case DAY -> formatted(TimeUnit.DAY, "d");
            case TIME -> intradayUpperStepUnit(baseUnitMillis, visibleSlots, visibleDays, plotWidth);
            case DATE -> formatted(TimeUnit.DAY, "d MMM");
            case NONE -> formatted(TimeUnit.DAY, "d");
        };
    }

    private static TimeUnit intradayUpperStepUnit(double baseUnitMillis,
                                                  double visibleSlots,
                                                  double visibleDays,
                                                  int plotWidth) {
        if (baseUnitMillis >= TimeUnit.DAY.getMillis()) {
            if (visibleDays <= 180.0d)
                return formatted(TimeUnit.DAY, "d");
            return (visibleDays >= 365.0d * 4.0d)
                    ? formatted(TimeUnit.YEAR, "yyyy")
                    : formatted(TimeUnit.MONTH, "MMM");
        }

        double pixelsPerBaseUnit = (plotWidth > 0)
                ? Math.max(1.0d, plotWidth / Math.max(1.0d, visibleSlots))
                : 0.0d;
        double requiredMillis = (pixelsPerBaseUnit > 0.0d)
                ? Math.max(baseUnitMillis, baseUnitMillis * Math.ceil(MIN_INTRADAY_TICK_SPACING_PX / pixelsPerBaseUnit))
                : baseUnitMillis;

        long selectedDurationMillis = canonicalIntradayDurations().stream()
                .filter(durationMillis -> durationMillis >= Math.round(baseUnitMillis))
                .filter(durationMillis -> durationMillis >= Math.round(requiredMillis))
                .findFirst()
                .orElse(TimeUnit.DAY.getMillis() < Math.round(requiredMillis)
                        ? (long) TimeUnit.DAY.getMillis()
                        : Math.round(baseUnitMillis));

        if (selectedDurationMillis >= (long) TimeUnit.DAY.getMillis()) {
            if (visibleDays >= 365.0d * 4.0d)
                return formatted(TimeUnit.YEAR, "yyyy");
            if (visibleDays > 45.0d)
                return formatted(TimeUnit.MONTH, "MMM");
            return formatted(TimeUnit.DAY, "d");
        }
        return durationUnit(selectedDurationMillis, "HH:mm");
    }

    private static List<Long> canonicalIntradayDurations() {
        return List.of(
                60_000L,
                5 * 60_000L,
                10 * 60_000L,
                15 * 60_000L,
                30 * 60_000L,
                60 * 60_000L,
                2 * 60 * 60_000L,
                3 * 60 * 60_000L,
                4 * 60 * 60_000L,
                6 * 60 * 60_000L,
                8 * 60 * 60_000L,
                12 * 60 * 60_000L,
                24 * 60 * 60_000L
        );
    }

    private static TimeUnit durationUnit(long durationMillis, String pattern) {
        if (durationMillis == 60_000L)
            return formatted(TimeUnit.MINUTE, pattern);
        if (durationMillis == 3_600_000L)
            return formatted(TimeUnit.HOUR, pattern);
        return new FixedDurationTimeUnit(durationMillis, pattern);
    }

    private int getFirstDate(DataInterval range, TimeUnit stepUnit, int categoryUnit) {
        if (range.getMax() < 0 || range.getMin() > dates.length - 1)
            return -1;
        int min = (int) Math.ceil(range.getMin());
        if (categoryUnit == 1)
            return Math.max(0, min);
        int idx = Math.max(0, min);
        Calendar cal = stepBoundary(dates[idx], stepUnit, getLocale());
        idx = Arrays.binarySearch(dates, cal.getTime());
        return (idx < 0) ? -idx - 1 : idx;
    }

    private static Calendar stepBoundary(Date date, TimeUnit stepUnit, Locale locale) {
        Calendar cal = Calendar.getInstance(locale);
        cal.setTime(date);
        return stepUnit.previousUnitTime(cal);
    }

    private static TimeUnit explicitStepUnit(Lane lane, boolean compactDay) {
        return switch (lane) {
            case TIME -> formatted(TimeUnit.HOUR, "HH:mm");
            case DAY -> formatted(TimeUnit.DAY, compactDay ? "d" : "d MMM");
            case MONTH -> formatted(TimeUnit.MONTH, "MMM");
            case YEAR -> formatted(TimeUnit.YEAR, "yyyy");
            case DATE -> formatted(TimeUnit.DAY, "d MMM");
            case NONE -> throw new IllegalArgumentException("Cannot compute ticks for lane NONE");
        };
    }

    private static TimeUnit formatted(TimeUnit delegate, String pattern) {
        return new FormattingTimeUnit(delegate, pattern);
    }

    private static LocalDateTime toDateTime(long epochNanos) {
        return LocalDateTime.ofEpochSecond(
                Math.floorDiv(epochNanos, 1_000_000_000L),
                (int) Math.floorMod(epochNanos, 1_000_000_000L),
                ZoneOffset.UTC
        );
    }

    private static final class FormattingTimeUnit extends TimeUnit {
        private final TimeUnit delegate;
        private final String pattern;

        private FormattingTimeUnit(TimeUnit delegate, String pattern) {
            this.delegate = delegate;
            this.pattern = pattern;
        }

        @Override
        public Calendar previousUnitTime(Calendar cal) {
            return delegate.previousUnitTime(cal);
        }

        @Override
        public Calendar incrementTime(Calendar cal) {
            return delegate.incrementTime(cal);
        }

        @Override
        public double getMillis() {
            return delegate.getMillis();
        }

        @Override
        public String getFormatString() {
            return pattern;
        }

        @Override
        public String getFormatString(Locale locale) {
            return pattern;
        }
    }

    private static final class FixedDurationTimeUnit extends TimeUnit {
        private final long durationMillis;
        private final String pattern;

        private FixedDurationTimeUnit(long durationMillis, String pattern) {
            this.durationMillis = durationMillis;
            this.pattern = pattern;
        }

        @Override
        public Calendar previousUnitTime(Calendar cal) {
            long instant = cal.getTimeInMillis();
            var dayStart = (Calendar) cal.clone();
            dayStart.set(Calendar.HOUR_OF_DAY, 0);
            dayStart.set(Calendar.MINUTE, 0);
            dayStart.set(Calendar.SECOND, 0);
            dayStart.set(Calendar.MILLISECOND, 0);
            long dayStartMillis = dayStart.getTimeInMillis();
            long floored = dayStartMillis + ((instant - dayStartMillis) / durationMillis) * durationMillis;
            cal.setTimeInMillis(floored);
            return cal;
        }

        @Override
        public Calendar incrementTime(Calendar cal) {
            cal.setTimeInMillis(cal.getTimeInMillis() + durationMillis);
            return cal;
        }

        @Override
        public double getMillis() {
            return durationMillis;
        }

        @Override
        public String getFormatString() {
            return pattern;
        }

        @Override
        public String getFormatString(Locale locale) {
            return pattern;
        }
    }
}
