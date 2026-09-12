/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.misc;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PriceSeriesThumbnailTest {
    private static final SymbolResource<Candle> RESOURCE = SymbolResource.of("TEST", TimeFrame.Period.DAILY);

    @Test
    void of_seven_calendar_months_includes_cutoff_and_excludes_earlier_dates() {
        Candle cutoff = daily("2026-02-04", 100);
        Candle latest = daily("2026-09-04", 125);
        PriceSeriesThumbnail thumbnail = PriceSeriesThumbnail.of(series(
                daily("2026-02-03", 1), cutoff, daily("2026-06-01", 90), latest), 7);

        assertEquals(3, thumbnail.size());
        assertEquals(LocalDate.of(2026, 2, 4), thumbnail.startDate());
        assertEquals(LocalDate.of(2026, 9, 4), thumbnail.endDate());
        assertEquals(cutoff.time(), thumbnail.timeAt(0));
        assertEquals(latest.time(), thumbnail.timeAt(2));
        assertEquals(100, thumbnail.closeAt(0));
        assertEquals(90, thumbnail.closeAt(1));
        assertEquals(125, thumbnail.closeAt(2));
        assertEquals(90, thumbnail.min());
        assertEquals(125, thumbnail.max());
        assertEquals(25, thumbnail.changePercent(), 1e-12);
    }

    @Test
    void of_month_end_in_leap_year_uses_calendar_cutoff() {
        PriceSeriesThumbnail thumbnail = PriceSeriesThumbnail.of(series(
                daily("2024-02-28", 1), daily("2024-02-29", 2), daily("2024-09-30", 3)), 7);

        assertEquals(2, thumbnail.size());
        assertEquals(LocalDate.of(2024, 2, 29), thumbnail.startDate());
        assertEquals(LocalDate.of(2024, 9, 30), thumbnail.endDate());
    }

    @Test
    void of_exclusive_midnight_uses_actual_trading_date() {
        PriceSeriesThumbnail thumbnail = PriceSeriesThumbnail.of(series(
                daily("2025-02-28", 10), daily("2025-09-30", 20)), 7);

        assertEquals(2, thumbnail.size());
        assertEquals(LocalDate.of(2025, 2, 28), thumbnail.startDate());
        assertEquals(LocalDate.of(2025, 9, 30), thumbnail.endDate());
    }

    @Test
    void of_sparse_history_reports_available_dates_without_padding() {
        PriceSeriesThumbnail thumbnail = PriceSeriesThumbnail.of(series(
                daily("2026-06-01", 10), daily("2026-09-04", 20)), 7);

        assertEquals(2, thumbnail.size());
        assertEquals(LocalDate.of(2026, 6, 1), thumbnail.startDate());
        assertEquals(100, thumbnail.changePercent());
        assertEquals("2026-06-01 to 2026-09-04 (+100.00%)", thumbnail.toString());
    }

    @Test
    void of_single_candle_has_finite_flat_range() {
        PriceSeriesThumbnail thumbnail = PriceSeriesThumbnail.of(series(daily("2026-09-04", 42)), 7);

        assertEquals(1, thumbnail.size());
        assertEquals(thumbnail.startDate(), thumbnail.endDate());
        assertEquals(42, thumbnail.min());
        assertEquals(42, thumbnail.max());
        assertEquals(0, thumbnail.changePercent());
    }

    @Test
    void of_flat_series_has_zero_change() {
        PriceSeriesThumbnail thumbnail = PriceSeriesThumbnail.of(series(
                daily("2026-02-04", 42), daily("2026-09-04", 42)), 7);

        assertEquals(42, thumbnail.min());
        assertEquals(42, thumbnail.max());
        assertEquals(0, thumbnail.changePercent());
    }

    @Test
    void of_nonfinite_interior_prices_preserves_gaps_without_distorting_extrema() {
        PriceSeriesThumbnail thumbnail = PriceSeriesThumbnail.of(series(
                daily("2026-02-04", 10), daily("2026-03-01", Double.NaN),
                daily("2026-04-01", Double.POSITIVE_INFINITY),
                daily("2026-05-01", Double.NEGATIVE_INFINITY), daily("2026-09-04", 20)), 7);

        assertEquals(5, thumbnail.size());
        assertTrue(Double.isNaN(thumbnail.closeAt(1)));
        assertTrue(Double.isNaN(thumbnail.closeAt(2)));
        assertTrue(Double.isNaN(thumbnail.closeAt(3)));
        assertEquals(10, thumbnail.min());
        assertEquals(20, thumbnail.max());
        assertEquals(100, thumbnail.changePercent());
    }

    @Test
    void of_nonfinite_endpoint_reports_unavailable_change() {
        PriceSeriesThumbnail thumbnail = PriceSeriesThumbnail.of(series(
                daily("2026-02-04", 10), daily("2026-09-04", Double.NaN)), 7);

        assertEquals(10, thumbnail.min());
        assertEquals(10, thumbnail.max());
        assertTrue(Double.isNaN(thumbnail.changePercent()));
        assertTrue(thumbnail.toString().contains("change unavailable"));
    }

    @Test
    void of_all_nonfinite_prices_has_no_finite_statistics() {
        PriceSeriesThumbnail thumbnail = PriceSeriesThumbnail.of(series(
                daily("2026-02-04", Double.NaN), daily("2026-09-04", Double.POSITIVE_INFINITY)), 7);

        assertTrue(Double.isNaN(thumbnail.min()));
        assertTrue(Double.isNaN(thumbnail.max()));
        assertTrue(Double.isNaN(thumbnail.changePercent()));
    }

    @Test
    void of_zero_first_close_reports_unavailable_change() {
        PriceSeriesThumbnail thumbnail = PriceSeriesThumbnail.of(series(
                daily("2026-02-04", 0), daily("2026-09-04", 20)), 7);

        assertTrue(Double.isNaN(thumbnail.changePercent()));
        assertEquals(0, thumbnail.min());
        assertEquals(20, thumbnail.max());
    }

    @Test
    void of_empty_series_has_no_dates_or_statistics() {
        PriceSeriesThumbnail thumbnail = PriceSeriesThumbnail.of(series(), 7);

        assertEquals(0, thumbnail.size());
        assertNull(thumbnail.startDate());
        assertNull(thumbnail.endDate());
        assertTrue(Double.isNaN(thumbnail.min()));
        assertTrue(Double.isNaN(thumbnail.max()));
        assertTrue(Double.isNaN(thumbnail.changePercent()));
        assertEquals("No closing prices", thumbnail.toString());
    }

    @Test
    void of_nonpositive_months_rejects_invalid_window() {
        assertThrows(IllegalArgumentException.class, () -> PriceSeriesThumbnail.of(series(), 0));
        assertThrows(IllegalArgumentException.class, () -> PriceSeriesThumbnail.of(series(), -1));
    }

    @Test
    void of_source_collection_changed_snapshot_retains_original_points() {
        var candles = new ArrayList<>(List.of(daily("2026-02-04", 10), daily("2026-09-04", 20)));
        PriceSeriesThumbnail thumbnail = PriceSeriesThumbnail.of(CandleSeries.of(RESOURCE, candles), 7);

        candles.clear();
        candles.add(daily("2026-09-05", 300));

        assertEquals(2, thumbnail.size());
        assertEquals(10, thumbnail.closeAt(0));
        assertEquals(20, thumbnail.closeAt(1));
        assertEquals(LocalDate.of(2026, 9, 4), thumbnail.endDate());
    }

    @Test
    void compareTo_orders_by_period_change() {
        PriceSeriesThumbnail gain = PriceSeriesThumbnail.of(series(
                daily("2026-02-04", 100), daily("2026-09-04", 120)), 7);
        PriceSeriesThumbnail loss = PriceSeriesThumbnail.of(series(
                daily("2026-02-04", 100), daily("2026-09-04", 90)), 7);

        assertTrue(loss.compareTo(gain) < 0);
        assertTrue(gain.compareTo(loss) > 0);
        assertEquals(0, gain.compareTo(gain));
    }

    private static CandleSeries series(Candle... candles) {
        return CandleSeries.of(RESOURCE, List.of(candles));
    }

    private static Candle daily(String tradingDate, double close) {
        return Candle.of(LocalDate.parse(tradingDate).plusDays(1).atStartOfDay(), close);
    }
}
