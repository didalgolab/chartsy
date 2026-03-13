package one.chartsy.charting.financial;

import one.chartsy.charting.DataInterval;
import one.chartsy.charting.TimeUnit;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveCategoryTimeStepsTest {

    @Test
    void sessionShapedH6WindowsPromoteToMonthlyUpperTicks() {
        Date[] dates = weekdayH6Dates(2026, 1, 5, 24);
        var steps = new AdaptiveCategoryTimeSteps(dates, fixedDuration(6 * 60 * 60_000L, "HH:mm"));

        TimeUnit upperStep = steps.upperStepUnit(new DataInterval(0.0, dates.length), 1_481);

        assertThat(upperStep.getMillis()).isEqualTo(TimeUnit.MONTH.getMillis());
        assertThat(upperStep.getFormatString()).isEqualTo("MMM");
    }

    @Test
    void longRegularH6WindowsUseMonthAndYearLanes() {
        Date[] dates = regularH6Dates(2025, 9, 1, 420);
        var steps = new AdaptiveCategoryTimeSteps(dates, fixedDuration(6 * 60 * 60_000L, "HH:mm"));

        AdaptiveCategoryTimeSteps.LanePlan plan = steps.plan(new DataInterval(282.6, 419.5));

        assertThat(plan.upper()).isEqualTo(AdaptiveCategoryTimeSteps.Lane.MONTH);
        assertThat(plan.lower()).isEqualTo(AdaptiveCategoryTimeSteps.Lane.YEAR);
    }

    @Test
    void m15WindowsUseTimeAndDateLanes() {
        Date[] dates = regularDates(LocalDateTime.of(2026, 1, 10, 0, 0), 15 * 60, 900);
        var steps = new AdaptiveCategoryTimeSteps(dates, fixedDuration(15 * 60_000L, "HH:mm"));

        AdaptiveCategoryTimeSteps.LanePlan plan = steps.plan(new DataInterval(680.0, 900.0));
        TimeUnit upperStep = steps.upperStepUnit(new DataInterval(680.0, 900.0), 1_520);

        assertThat(plan.upper()).isEqualTo(AdaptiveCategoryTimeSteps.Lane.TIME);
        assertThat(plan.lower()).isEqualTo(AdaptiveCategoryTimeSteps.Lane.DATE);
        assertThat(upperStep.getFormatString()).isEqualTo("HH:mm");
        assertThat(upperStep.getMillis()).isEqualTo(3 * TimeUnit.HOUR.getMillis());
    }

    @Test
    void h1WindowsUseEightHourUpperTicks() {
        Date[] dates = regularDates(LocalDateTime.of(2025, 12, 20, 0, 0), 60 * 60, 600);
        var steps = new AdaptiveCategoryTimeSteps(dates, TimeUnit.HOUR);

        TimeUnit upperStep = steps.upperStepUnit(new DataInterval(462.6, 599.5), 1_520);

        assertThat(upperStep.getFormatString()).isEqualTo("HH:mm");
        assertThat(upperStep.getMillis()).isEqualTo(8 * TimeUnit.HOUR.getMillis());
    }

    @Test
    void h1TradingSessionWindowsStayOnTimeAndDateLanes() {
        Date[] dates = weekdaySessionDates(LocalDateTime.of(2026, 1, 5, 10, 0), 240, 10, 17);
        var steps = new AdaptiveCategoryTimeSteps(dates, TimeUnit.HOUR);
        DataInterval range = new DataInterval(144.0, 240.0);

        AdaptiveCategoryTimeSteps.LanePlan plan = steps.plan(range, 1_481);
        TimeUnit upperStep = steps.upperStepUnit(range, 1_481);

        assertThat(plan.upper()).isEqualTo(AdaptiveCategoryTimeSteps.Lane.TIME);
        assertThat(plan.lower()).isEqualTo(AdaptiveCategoryTimeSteps.Lane.DATE);
        assertThat(upperStep.getFormatString()).isEqualTo("HH:mm");
        assertThat(upperStep.getMillis()).isEqualTo(6 * TimeUnit.HOUR.getMillis());
    }

    @Test
    void lowerLaneTracksTheActualUpperStepChosenAtCurrentPlotWidth() {
        Date[] dates = regularDates(LocalDateTime.of(2026, 1, 10, 0, 0), 15 * 60, 900);
        var steps = new AdaptiveCategoryTimeSteps(dates, fixedDuration(15 * 60_000L, "HH:mm"));
        DataInterval range = new DataInterval(680.0, 900.0);

        TimeUnit upperStep = steps.upperStepUnit(range, 320);
        AdaptiveCategoryTimeSteps.LanePlan plan = steps.plan(range, 320);

        assertThat(upperStep.getMillis()).isEqualTo(TimeUnit.DAY.getMillis());
        assertThat(plan.upper()).isEqualTo(AdaptiveCategoryTimeSteps.Lane.DAY);
        assertThat(plan.lower()).isEqualTo(AdaptiveCategoryTimeSteps.Lane.MONTH);
    }

    @Test
    void dayTicksUseTheActualFirstSessionDateAfterWeekendGaps() {
        Date[] dates = weekdaySessionDates(LocalDateTime.of(2026, 1, 30, 16, 0), 8, 16, 20);
        var steps = new AdaptiveCategoryTimeSteps(dates, TimeUnit.HOUR);

        var ticks = steps.snapshotTicks(new DataInterval(0.0, dates.length), AdaptiveCategoryTimeSteps.Lane.DAY, false);

        assertThat(ticks).hasSize(2);
        assertThat(ticks.get(0).value()).isEqualTo(0.0);
        assertThat(ticks.get(1).value()).isEqualTo(4.0);
        assertThat(ticks.get(0).label()).isEqualTo(
                AdaptiveCategoryTimeSteps.format(AdaptiveCategoryTimeSteps.Lane.DAY, dates[0].getTime() * 1_000_000L, false)
        );
        assertThat(ticks.get(1).label()).isEqualTo(
                AdaptiveCategoryTimeSteps.format(AdaptiveCategoryTimeSteps.Lane.DAY, dates[4].getTime() * 1_000_000L, false)
        );
    }

    private static Date[] weekdayH6Dates(int year, int month, int day, int weekdays) {
        List<Date> dates = new ArrayList<>(weekdays * 4);
        LocalDateTime cursor = LocalDateTime.of(year, month, day, 0, 0);
        int emittedWeekdays = 0;
        while (emittedWeekdays < weekdays) {
            if (switch (cursor.getDayOfWeek()) {
                case SATURDAY, SUNDAY -> false;
                default -> true;
            }) {
                for (int hour = 0; hour < 24; hour += 6)
                    dates.add(Date.from(cursor.withHour(hour).toInstant(ZoneOffset.UTC)));
                emittedWeekdays++;
            }
            cursor = cursor.plusDays(1);
        }
        return dates.toArray(Date[]::new);
    }

    private static Date[] regularH6Dates(int year, int month, int day, int slots) {
        return regularDates(LocalDateTime.of(year, month, day, 0, 0), 6 * 60 * 60, slots);
    }

    private static Date[] regularDates(LocalDateTime start, int seconds, int count) {
        var dates = new Date[count];
        var cursor = start;
        for (int i = 0; i < count; i++) {
            dates[i] = Date.from(cursor.toInstant(ZoneOffset.UTC));
            cursor = cursor.plusSeconds(seconds);
        }
        return dates;
    }

    private static Date[] weekdaySessionDates(LocalDateTime start, int count, int startHour, int endHourExclusive) {
        List<Date> dates = new ArrayList<>(count);
        LocalDateTime cursor = start.withHour(startHour).withMinute(0).withSecond(0).withNano(0);
        while (dates.size() < count) {
            if (switch (cursor.getDayOfWeek()) {
                case SATURDAY, SUNDAY -> false;
                default -> true;
            } && cursor.getHour() >= startHour && cursor.getHour() < endHourExclusive) {
                dates.add(Date.from(cursor.toInstant(ZoneOffset.UTC)));
                cursor = cursor.plusHours(1);
            } else {
                cursor = nextTradingHour(cursor, startHour);
            }
        }
        return dates.toArray(Date[]::new);
    }

    private static LocalDateTime nextTradingHour(LocalDateTime time, int startHour) {
        LocalDateTime next = time.plusHours(1);
        if (next.getHour() >= startHour && next.getHour() < 17 && switch (next.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> false;
            default -> true;
        }) {
            return next;
        }
        next = next.toLocalDate().plusDays(1).atTime(startHour, 0);
        while (switch (next.getDayOfWeek()) {
            case SATURDAY, SUNDAY -> true;
            default -> false;
        }) {
            next = next.plusDays(1);
        }
        return next;
    }

    private static TimeUnit fixedDuration(long durationMillis, String pattern) {
        return new TimeUnit() {
            @Override
            public java.util.Calendar previousUnitTime(java.util.Calendar cal) {
                long instant = cal.getTimeInMillis();
                var dayStart = (java.util.Calendar) cal.clone();
                dayStart.set(java.util.Calendar.HOUR_OF_DAY, 0);
                dayStart.set(java.util.Calendar.MINUTE, 0);
                dayStart.set(java.util.Calendar.SECOND, 0);
                dayStart.set(java.util.Calendar.MILLISECOND, 0);
                long dayStartMillis = dayStart.getTimeInMillis();
                long floored = dayStartMillis + ((instant - dayStartMillis) / durationMillis) * durationMillis;
                cal.setTimeInMillis(floored);
                return cal;
            }

            @Override
            public java.util.Calendar incrementTime(java.util.Calendar cal) {
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
            public String getFormatString(java.util.Locale locale) {
                return pattern;
            }
        };
    }
}
