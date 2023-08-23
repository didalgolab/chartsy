/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.market;

import one.chartsy.Candle;
import one.chartsy.data.SimpleCandleBuilder;
import one.chartsy.time.Chronological;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.LinkedList;
import java.util.function.Supplier;

import static one.chartsy.time.Chronological.toEpochMicros;
import static org.junit.jupiter.api.Assertions.*;

class TimeCandleAggregatorTest {
    static final Duration HOURLY = Duration.ofHours(1), DAILY = Duration.ofDays(1);
    static final TimeCandleAlignment BREAK_AT_MIDNIGHT_UTC = new TimeCandleAlignment(ZoneOffset.UTC, LocalTime.MIDNIGHT);
    static final TimeCandleAlignment BREAK_AT_MIDNIGHT_CET = new TimeCandleAlignment(ZoneId.of("Europe/Paris"), LocalTime.MIDNIGHT);
    static final Supplier<SimpleCandleBuilder> candleBuilder = SimpleCandleBuilder::create;

    LinkedList<Candle> emitted = new LinkedList<>();

    @Test
    void does_NOT_emit_incomplete_Candles() {
        var agg = new TimeCandleAggregator<>(candleBuilder.get(), HOURLY, BREAK_AT_MIDNIGHT_UTC);
        agg.addCandle(Candle.of(Chronological.now(), 1.0), emitted::add);

        assertEquals(0, emitted.size(), "emitted count");
    }

    @Test
    void gives_handle_to_incomplete_Candle_which_could_be_stateful() {
        var agg = new TimeCandleAggregator<>(candleBuilder.get(), HOURLY, BREAK_AT_MIDNIGHT_UTC);
        var now = Chronological.now();
        var incompleteCandleHandle =
                agg.addCandle(Candle.of(now, 1.0), emitted::add);
                agg.addCandle(Candle.of(now, 2.0), emitted::add);

        assertEquals(0, emitted.size(), "emitted count");
        assertEquals(Candle.of(now, 1, 2, 1, 2), incompleteCandleHandle.get(), "incomplete candle");
    }

    @Test
    void aggregates_and_emits_completed_Candles() {
        var agg = new TimeCandleAggregator<>(candleBuilder.get(), HOURLY, BREAK_AT_MIDNIGHT_UTC);
        // first hour candles
        agg.addCandle(newCandle(LocalDateTime.of(2021, 12, 31, 1, 1), 1.0), emitted::add);
        agg.addCandle(newCandle(LocalDateTime.of(2021, 12, 31, 1, 2), 2.0), emitted::add);
        agg.addCandle(newCandle(LocalDateTime.of(2021, 12, 31, 2, 0), 3.0), emitted::add);
        // next hour candle
        agg.addCandle(newCandle(LocalDateTime.of(2021, 12, 31, 2, 1), 4.0), emitted::add);

        assertEquals(1, emitted.size(), "first hour candle");
        assertEquals(
                Candle.of(toEpochMicros(LocalDateTime.of(2021, 12, 31, 2, 0)), 1, 3, 1, 3),
                emitted.getFirst(), "is first candle aggregated");
    }

    @Test
    void breaks_daily_Candles_at_specified_daily_alignment_time_and_zone() {
        var agg = new TimeCandleAggregator<>(candleBuilder.get(), DAILY, BREAK_AT_MIDNIGHT_CET);
        // candles before midnight CET
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 10, 0, 0), 1.0), emitted::add);
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 10, 12, 0), 2.0), emitted::add);
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 10, 22, 0), 3.0), emitted::add);
        // candle after midnight CET
        agg.addCandle(newCandle(LocalDateTime.of(2021, 10, 10, 22, 1), 4.0), emitted::add);

        assertEquals(1, emitted.size(), "first hour candle");
        assertEquals(
                Candle.of(toEpochMicros(LocalDateTime.of(2021, 10, 10, 22, 0)), 1, 3, 1, 3),
                emitted.getFirst(), "first aggregated candle");
    }

    private static Candle newCandle(LocalDateTime datetime, double price) {
        return Candle.of(toEpochMicros(datetime), price);
    }
}