package one.chartsy.data.market;

import one.chartsy.time.Chronological;

import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjuster;

import static java.time.LocalTime.MIDNIGHT;

public record PeriodCandleAlignment(
        ZoneId dailyAlignmentTimeZone,
        LocalTime dailyAlignment,
        TemporalAdjuster... moreAlignments
) {

    public long getRegularCandleCloseTime(long time, Period candleGranularity) {
        ZonedDateTime timeAtZone = Chronological.toDateTime(time, dailyAlignmentTimeZone);
        ZonedDateTime candleEndTime = timeAtZone.with(dailyAlignment);
        if (moreAlignments != null)
            for (TemporalAdjuster nextAlignment : moreAlignments)
                candleEndTime = candleEndTime.with(nextAlignment);

        if (!MIDNIGHT.equals(dailyAlignment))
            candleEndTime = candleEndTime.minusDays(1);
        while (candleEndTime.isBefore(timeAtZone))
            candleEndTime = candleEndTime.plus(candleGranularity);

        return Chronological.toEpochMicros(candleEndTime);
    }
}
