/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.market;

import one.chartsy.Candle;
import one.chartsy.FilteredTimeFrameAggregator;
import one.chartsy.TimeFrameAggregator;
import one.chartsy.time.Chronological;

import java.time.*;
import java.util.function.Predicate;

public record SimpleSessionFilter(
        DayOfWeek startDayOfWeek,
        LocalTime startTime,
        DayOfWeek endDayOfWeek,
        LocalTime endTime,
        ZoneId timezone
) {

    public <C extends Candle, T extends Tick> TimeFrameAggregator<C, T> wrap(TimeFrameAggregator<C, T> aggregator) {
        return new FilteredTimeFrameAggregator<>(new FilterState<>(), aggregator);
    }

    protected class FilterState<E extends Chronological> implements Predicate<E> {
        private long startTime = Long.MIN_VALUE, endTime = Long.MIN_VALUE;

        @Override
        public boolean test(E event) {
            long time = event.getTime();
            if (time <= startTime)
                return false;
            if (time <= endTime)
                return true;

            ZonedDateTime dateTime = Chronological.toDateTime(time, timezone());
            ZonedDateTime start = dateTime.with(startDayOfWeek()).with(startTime());
            ZonedDateTime end = dateTime.with(endDayOfWeek()).with(endTime());
            startTime = Chronological.toEpochMicros(start);
            endTime = Chronological.toEpochMicros(end);
            if (endTime <= time) {
                startTime = Chronological.toEpochMicros(start.plusDays(7));
                endTime = Chronological.toEpochMicros(end.plusDays(7));
            }
            return (time > startTime && time <= endTime);
        }
    }
}
