/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.internal.engine;

import one.chartsy.TimeFrame;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.TimeUnit;
import one.chartsy.charting.financial.AdaptiveCategoryTimeSteps;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveCategoryTimeStepsBridgeTest {

    @Test
    void dayTicksStartAtFirstVisibleSessionInsteadOfSkippingToNextDay() {
        Date[] dates = {
                date(2026, 1, 5, 15, 30),
                date(2026, 1, 5, 21, 30),
                date(2026, 1, 6, 15, 30),
                date(2026, 1, 6, 21, 30)
        };
        var steps = new AdaptiveCategoryTimeSteps(dates, EngineSeriesAdapter.timeUnit(TimeFrame.Period.H6));

        var ticks = steps.snapshotTicks(new DataInterval(0.0, 4.0), AdaptiveCategoryTimeSteps.Lane.DAY, false);

        assertThat(ticks).isNotEmpty();
        assertThat(ticks.getFirst().value()).isEqualTo(0.0);
    }

    @Test
    void sparseIntradayWindowsPromoteToMonthlyUpperTicksWhenSpanGetsLong() {
        TimeUnit upperStep = AdaptiveCategoryTimeSteps.selectUpperStepUnit(
                one.chartsy.charting.TimeUnit.DAY.getMillis(),
                70.0,
                70.0,
                1_481
        );

        assertThat(upperStep.getMillis()).isEqualTo(one.chartsy.charting.TimeUnit.MONTH.getMillis());
        assertThat(upperStep.getFormatString()).isEqualTo("MMM");
    }

    @Test
    void denseIntradayWindowsChooseCoarserHourTicks() {
        TimeUnit upperStep = AdaptiveCategoryTimeSteps.selectUpperStepUnit(
                one.chartsy.charting.TimeUnit.HOUR.getMillis(),
                96.0,
                4.0,
                1_481
        );

        assertThat(upperStep.getMillis()).isGreaterThan(one.chartsy.charting.TimeUnit.HOUR.getMillis());
        assertThat(upperStep.getMillis()).isLessThanOrEqualTo(one.chartsy.charting.TimeUnit.DAY.getMillis());
        assertThat(upperStep.getFormatString()).isEqualTo("HH:mm");
    }

    @Test
    void sessionShapedH6WindowsUseActualCalendarSpanForUpperTicks() {
        Date[] dates = weekdayH6Dates(2026, 1, 5, 24);
        var steps = new AdaptiveCategoryTimeSteps(dates, EngineSeriesAdapter.timeUnit(TimeFrame.Period.H6));

        TimeUnit upperStep = steps.upperStepUnit(new DataInterval(0.0, dates.length), 1_481);

        assertThat(upperStep.getMillis()).isEqualTo(one.chartsy.charting.TimeUnit.MONTH.getMillis());
        assertThat(upperStep.getFormatString()).isEqualTo("MMM");
    }

    @Test
    void longRegularH6WindowsAlsoPromoteToMonthlyUpperTicks() {
        Date[] dates = regularH6Dates(2025, 9, 1, 420);
        var steps = new AdaptiveCategoryTimeSteps(dates, EngineSeriesAdapter.timeUnit(TimeFrame.Period.H6));

        TimeUnit upperStep = steps.upperStepUnit(new DataInterval(282.6, 419.5), 1_481);

        assertThat(upperStep.getMillis()).isEqualTo(one.chartsy.charting.TimeUnit.MONTH.getMillis());
        assertThat(upperStep.getFormatString()).isEqualTo("MMM");
    }

    private static Date date(int year, int month, int day, int hour, int minute) {
        return Date.from(LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC));
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
        List<Date> dates = new ArrayList<>(slots);
        LocalDateTime cursor = LocalDateTime.of(year, month, day, 0, 0);
        for (int i = 0; i < slots; i++) {
            dates.add(Date.from(cursor.toInstant(ZoneOffset.UTC)));
            cursor = cursor.plusHours(6);
        }
        return dates.toArray(Date[]::new);
    }
}
