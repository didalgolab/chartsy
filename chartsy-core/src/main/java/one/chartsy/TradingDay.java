/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import lombok.Getter;
import one.chartsy.time.Chronological;

import java.time.*;

@Getter
public class TradingDay {

    private final ZoneId timeZone;
    private final ZonedDateTime startDateTime;
    private final ZonedDateTime endDateTime;
    private final long startTime;
    private final long endTime;


    public TradingDay(ZoneId timeZone, LocalDate date, LocalTime alignment) {
        this.timeZone = timeZone;
        this.startDateTime = ZonedDateTime.of(date, alignment, timeZone);
        this.startTime = Chronological.toEpochMicros(startDateTime);
        this.endDateTime = ZonedDateTime.of(date.plusDays(1), alignment, timeZone);
        this.endTime = Chronological.toEpochMicros(endDateTime);
    }

    public boolean isAfter(long time) {
        return (time <= startTime);
    }

    public boolean isBefore(long time) {
        return (time > endTime);
    }

    public boolean contains(long time) {
        return !isBefore(time) && !isAfter(time);
    }

    public long getRegularCandleCloseTime(long time, Duration candleGranularity) {
        if (!contains(time))
            throw new IllegalArgumentException("`time` not within TradingDay[" + getStartTime() + "]");

        long candleSizeInMicros = Math.multiplyExact(candleGranularity.toSeconds(), 1000_000L);
        return startTime + ((time - startTime - 1)/candleSizeInMicros + 1)*candleSizeInMicros;
    }
}
