package one.chartsy.data.market;

import one.chartsy.TradingDay;
import one.chartsy.time.Chronological;

import java.time.*;

public record TimeCandleAlignment(ZoneId dailyAlignmentTimeZone, LocalTime dailyAlignment) {

    public TradingDay getTradingDay(long time) {
        ZonedDateTime timeAtZone = Chronological.toDateTime(time, dailyAlignmentTimeZone);

        LocalDate date = timeAtZone.toLocalDate();
        if (!timeAtZone.toLocalTime().isAfter(dailyAlignment))
            date = date.minusDays(1);
        return new TradingDay(dailyAlignmentTimeZone, date, dailyAlignment);
    }
}
