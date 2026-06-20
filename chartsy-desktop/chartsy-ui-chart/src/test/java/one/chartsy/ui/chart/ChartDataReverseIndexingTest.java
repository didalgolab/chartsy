/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.provider.DataProvider;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChartDataReverseIndexingTest {

    @Test
    void historicalSlotsExposeReverseIndexedSeriesAsOldestToNewestViewportOrder() {
        CandleSeries series = sampleSeries();
        ChartData data = new ChartData();

        data.setDataset(series);

        assertThat(series.get(0).close()).isEqualTo(30.5);
        assertThat(series.get(2).close()).isEqualTo(10.5);
        assertThat(data.getCandleAtSlot(0).close()).isEqualTo(10.5);
        assertThat(data.getCandleAtSlot(1).close()).isEqualTo(20.5);
        assertThat(data.getCandleAtSlot(2).close()).isEqualTo(30.5);
        assertThat(data.getSlotTime(0)).isEqualTo(series.get(2).time());
        assertThat(data.getSlotTime(2)).isEqualTo(series.get(0).time());
    }

    @Test
    void slotTimeAndSlotIndexRoundTripHistoricalAndFutureDailySlots() {
        ChartData data = new ChartData();
        data.setDataset(sampleSeries());

        for (int slot = 0; slot < data.getHistoricalSlotCount(); slot++)
            assertThat(data.getSlotIndex(data.getSlotTime(slot))).isEqualTo(slot);

        int firstFutureSlot = data.getHistoricalSlotCount();
        long firstFutureTime = data.getSlotTime(firstFutureSlot);
        long secondFutureTime = data.getSlotTime(firstFutureSlot + 1);

        assertThat(data.getSlotIndex(firstFutureTime)).isEqualTo(firstFutureSlot);
        assertThat(data.getSlotIndex(secondFutureTime)).isEqualTo(firstFutureSlot + 1);
        assertThat(secondFutureTime).isGreaterThan(firstFutureTime);
    }

    @Test
    void dailySlotDisplayTimeKeepsStartStampedTradingDate() {
        ChartData data = new ChartData();
        data.setDataset(sampleSeries());

        assertThat(data.getSlotDateLabel(0)).isEqualTo("2024-01-02");
        assertThat(data.getSlotDateLabel(1)).isEqualTo("2024-01-03");
        assertThat(data.getSlotDateLabel(2)).isEqualTo("2024-01-04");
    }

    @Test
    void dailySlotDisplayTimeUsesPreviousDateForExclusiveEndStampedCandles() {
        ChartData data = new ChartData();
        data.setDataset(exclusiveEndStampedSeries());

        assertThat(data.getSlotDateLabel(0)).isEqualTo("2026-01-02");
        assertThat(data.getSlotDateLabel(1)).isEqualTo("2026-01-05");
        assertThat(data.getSlotDateLabel(2)).isEqualTo("2026-01-06");
    }

    @Test
    void legacyDailyDateAxisMapsTicksToDisplayedCandleDates() {
        ChartFrame chartFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                sampleSeries(),
                ChartTemplateDefaults.basicChartTemplate(),
                new Dimension(640, 480)
        );

        var dateScale = chartFrame.getChartData().getDateAxisScale();

        assertThat(dateScale.getMarkCount()).isEqualTo(3);
        assertThat(dateScale.mapMark(0)).isEqualTo(0.0);
        assertThat(dateScale.mapMark(1)).isEqualTo(1.0);
        assertThat(dateScale.mapMark(2)).isEqualTo(2.0);
    }

    @Test
    void negativeSlotTimeRequestsClampToOldestHistoricalBar() {
        CandleSeries series = sampleSeries();
        ChartData data = new ChartData();

        data.setDataset(series);

        assertThat(data.getSlotTime(-1)).isEqualTo(series.get(2).time());
        assertThat(data.getSlotTime(-25)).isEqualTo(series.get(2).time());
    }

    @Test
    void lastDisplayedCandleTracksRightmostVisibleHistoricalSlot() {
        ChartData data = new ChartData();
        data.setDataset(sampleSeries());

        data.setVisibleSlotCount(2);
        data.setVisibleStartSlot(0);
        assertThat(data.getLastDisplayedHistoricalSlot()).isEqualTo(1);
        assertThat(data.getLastDisplayedCandle().close()).isEqualTo(20.5);

        data.setVisibleStartSlot(1);
        assertThat(data.getLastDisplayedHistoricalSlot()).isEqualTo(2);
        assertThat(data.getLastDisplayedCandle().close()).isEqualTo(30.5);
    }

    @Test
    void emptyDataset_gives_empty_calculated_range() {
        ChartData data = new ChartData();
        data.setDataset(emptySeries());

        assertThat(data.hasDataset()).isTrue();
        assertThat(data.getDatasetLength()).isZero();

        data.calculateRange(null, List.of());
        assertThat(data.getVisibleRange()).isEqualTo(Range.empty());
    }

    private static CandleSeries sampleSeries() {
        SymbolResource<Candle> resource = SymbolResource.of(SymbolIdentity.of("REVERSAL-TEST"), TimeFrame.Period.DAILY);
        List<Candle> candles = List.of(
                Candle.of(LocalDate.of(2024, 1, 2).atStartOfDay(), 10.0, 11.0, 9.0, 10.5, 1_000.0),
                Candle.of(LocalDate.of(2024, 1, 3).atStartOfDay(), 20.0, 21.0, 19.0, 20.5, 2_000.0),
                Candle.of(LocalDate.of(2024, 1, 4).atStartOfDay(), 30.0, 31.0, 29.0, 30.5, 3_000.0)
        );
        return CandleSeries.of(resource, candles);
    }

    private static CandleSeries exclusiveEndStampedSeries() {
        SymbolResource<Candle> resource = SymbolResource.of(SymbolIdentity.of("EXCLUSIVE-END-TEST"), TimeFrame.Period.DAILY);
        List<Candle> candles = List.of(
                Candle.of(LocalDate.of(2026, 1, 3).atStartOfDay(), 10.0, 11.0, 9.0, 10.5, 1_000.0),
                Candle.of(LocalDate.of(2026, 1, 6).atStartOfDay(), 20.0, 21.0, 19.0, 20.5, 2_000.0),
                Candle.of(LocalDate.of(2026, 1, 7).atStartOfDay(), 30.0, 31.0, 29.0, 30.5, 3_000.0)
        );
        return CandleSeries.of(resource, candles);
    }

    private static CandleSeries emptySeries() {
        SymbolResource<Candle> resource = SymbolResource.of(SymbolIdentity.of("EMPTY-RANGE-TEST"), TimeFrame.Period.DAILY);
        return CandleSeries.of(resource, List.of());
    }
}
