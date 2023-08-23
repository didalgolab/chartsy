/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.market;

import one.chartsy.time.Chronological;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjuster;
import java.time.temporal.TemporalAmount;

import static java.time.LocalTime.MIDNIGHT;

public record DateCandleAlignment(
        ZoneId dailyAlignmentTimeZone,
        LocalTime dailyAlignment,
        TemporalAdjuster... moreAlignments
) {

    public long getRegularCandleCloseTime(long time, TemporalAmount candlePeriod) {
        ZonedDateTime timeAtZone = Chronological.toDateTime(time, dailyAlignmentTimeZone);
        ZonedDateTime candleEndTime = timeAtZone.with(dailyAlignment);
        if (moreAlignments != null)
            for (TemporalAdjuster nextAlignment : moreAlignments)
                candleEndTime = candleEndTime.with(nextAlignment);

        if (!MIDNIGHT.equals(dailyAlignment))
            candleEndTime = candleEndTime.minusDays(1);
        while (candleEndTime.isBefore(timeAtZone))
            candleEndTime = candleEndTime.plus(candlePeriod);

        return Chronological.toEpochMicros(candleEndTime);
    }
}
