/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.market;

import one.chartsy.Candle;
import one.chartsy.data.SimpleCandleBuilder;
import one.chartsy.time.Chronological;
import one.chartsy.time.DayOfMonth;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.LinkedList;
import java.util.function.Supplier;

import static one.chartsy.time.Chronological.toEpochMicros;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PeriodCandleAggregatorTest {
    static final long MICROSECOND = 1000L; //number of microseconds in a nanosecond
    static final Period DAILY = Period.ofDays(1), WEEKLY = Period.ofWeeks(1), MONTHLY = Period.ofMonths(1);
    static final DateCandleAlignment BREAK_AT_MIDNIGHT_UTC = new DateCandleAlignment(ZoneOffset.UTC, LocalTime.MIDNIGHT);
    static final DateCandleAlignment BREAK_AT_MIDNIGHT_CET = new DateCandleAlignment(ZoneId.of("Europe/Paris"), LocalTime.MIDNIGHT);
    static final Supplier<SimpleCandleBuilder> candleBuilder = SimpleCandleBuilder::create;

    LinkedList<Candle> emitted = new LinkedList<>();

    @Test
    void does_NOT_emit_incomplete_Candles() {
        var agg = new PeriodCandleAggregator<>(candleBuilder.get(), DAILY, BREAK_AT_MIDNIGHT_UTC);
        agg.addCandle(Candle.of(Chronological.now(), 1.0), emitted::add);

        assertEquals(0, emitted.size(), "emitted count");
    }

    @Test
    void gives_handle_to_incomplete_Candle_which_could_be_stateful() {
        var agg = new PeriodCandleAggregator<>(candleBuilder.get(), DAILY, BREAK_AT_MIDNIGHT_UTC);
        var now = Chronological.now();
        var incompleteCandleHandle =
                agg.addCandle(Candle.of(now, 1.0), emitted::add);
                agg.addCandle(Candle.of(now, 2.0), emitted::add);

        assertEquals(0, emitted.size(), "emitted count");
        assertEquals(Candle.of(now, 1, 2, 1, 2), incompleteCandleHandle.get(), "incomplete candle");
    }

    @Test
    void aggregates_and_emits_completed_Candles() {
        var agg = new PeriodCandleAggregator<>(candleBuilder.get(), DAILY, BREAK_AT_MIDNIGHT_UTC);
        // first hour candles
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 10, 0, 1), 1.0), emitted::add);
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 10, 23, 59, 59), 2.0), emitted::add);
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 11, 0, 0), 3.0), emitted::add);
        // next hour candle
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 11, 0, 1), 4.0), emitted::add);

        assertEquals(1, emitted.size(), "first hour candle");
        assertEquals(
                Candle.of(toEpochMicros(LocalDateTime.of(2021, 10, 11, 0, 0)), 1, 3, 1, 3),
                emitted.getFirst(), "is first candle aggregated");
    }

    @Test
    void breaks_daily_Candles_at_specified_daily_alignment_time_and_zone() {
        var agg = new PeriodCandleAggregator<>(candleBuilder.get(), DAILY, BREAK_AT_MIDNIGHT_CET);
        // candles before midnight CET
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 10, 22, 1), 1.0), emitted::add);
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 11,  0, 0), 2.0), emitted::add);
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 11, 22, 0), 3.0), emitted::add);
        // candle after midnight CET
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 11, 22, 1), 4.0), emitted::add);

        assertEquals(1, emitted.size(), "first hour candle");
        assertEquals(
                Candle.of(toEpochMicros(LocalDateTime.of(2021, 10, 11, 22, 0)), 1, 3, 1, 3),
                emitted.getFirst(), "first aggregated candle");
    }

    @Test
    void can_break_weekly_Candles_at_specified_day_of_week_here_assuming_SUNDAY() {
        var BREAK_BEFORE_SUNDAY_CET = new DateCandleAlignment(ZoneId.of("Europe/Paris"), LocalTime.MIDNIGHT, DayOfWeek.SUNDAY);
        var agg = new PeriodCandleAggregator<>(candleBuilder.get(), WEEKLY, BREAK_BEFORE_SUNDAY_CET);
        // candles before Sunday midnight CET
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10,  9, 22, 1), 1.0), emitted::add);
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 14,  0, 0), 2.0), emitted::add);
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 16, 22, 0), 3.0), emitted::add);
        // candle after Sunday
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 16, 22, 1), 4.0), emitted::add);

        assertEquals(1, emitted.size(), "first hour candle");
        assertEquals(
                Candle.of(toEpochMicros(LocalDateTime.of(2021, 10, 16, 22, 0)), 1, 3, 1, 3),
                emitted.getFirst(), "first aggregated candle");
    }

    @Test
    void can_break_weekly_Candles_at_specified_day_of_week_here_assuming_MONDAY() {
        var BREAK_BEFORE_MONDAY_CET = new DateCandleAlignment(ZoneId.of("Europe/Paris"), LocalTime.MIDNIGHT, DayOfWeek.MONDAY);
        var agg = new PeriodCandleAggregator<>(candleBuilder.get(), WEEKLY, BREAK_BEFORE_MONDAY_CET);
        // candles before midnight CET
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 10, 22, 1), 1.0), emitted::add);
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 11,  0, 0), 2.0), emitted::add);
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 17, 22, 0), 3.0), emitted::add);
        // candle after midnight CET
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 17, 22, 1), 4.0), emitted::add);

        assertEquals(1, emitted.size(), "first hour candle");
        assertEquals(
                Candle.of(toEpochMicros(LocalDateTime.of(2021, 10, 17, 22, 0)), 1, 3, 1, 3),
                emitted.getFirst(), "first aggregated candle");
    }

    @Test
    void supports_MONTHLY_Candles() {
        var BREAK_ON_EACH_NEW_MONTH_MIDNIGHT_UTC = new DateCandleAlignment(ZoneOffset.UTC, LocalTime.MIDNIGHT, DayOfMonth.of(1));
        var agg = new PeriodCandleAggregator<>(candleBuilder.get(), MONTHLY, BREAK_ON_EACH_NEW_MONTH_MIDNIGHT_UTC);
        // candles before midnight CET
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 1, 0, 0).plusNanos(MICROSECOND), 1.0), emitted::add);
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 31,23, 59, 59), 2.0), emitted::add);
        agg.addCandle(newCandle(LocalDateTime.of(2021, 11, 1, 0, 0), 3.0), emitted::add);
        // candle after midnight CET
        agg.addCandle(newCandle(LocalDateTime.of(2021, 11, 1, 0, 0).plusNanos(MICROSECOND), 4.0), emitted::add);

        assertEquals(1, emitted.size(), "first hour candle");
        assertEquals(
                Candle.of(toEpochMicros(LocalDateTime.of(2021, 11, 1, 0, 0)), 1, 3, 1, 3),
                emitted.getFirst(), "first aggregated candle");
    }

    private static Candle newCandle(LocalDateTime datetime, double price) {
        return Candle.of(toEpochMicros(datetime), price);
    }
}