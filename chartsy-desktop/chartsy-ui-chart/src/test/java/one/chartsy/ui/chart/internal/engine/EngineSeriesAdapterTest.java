/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.internal.engine;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.base.dataset.ImmutableDoubleDataset;
import one.chartsy.charting.Scale;
import one.chartsy.charting.TimeUnit;
import one.chartsy.data.CandleSeries;
import one.chartsy.time.Chronological;
import one.chartsy.ui.chart.ChartData;
import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.Rectangle;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EngineSeriesAdapterTest {

    @Test
    void engineDatasetsAreAdaptedInOldestToNewestOrder() {
        CandleSeries series = sampleSeries();

        var adapted = EngineSeriesAdapter.adapt("Price", series);

        assertThat(adapted.open().getYData(0)).isEqualTo(10.0);
        assertThat(adapted.open().getYData(1)).isEqualTo(20.0);
        assertThat(adapted.open().getYData(2)).isEqualTo(30.0);
        assertThat(adapted.close().getYData(0)).isEqualTo(10.5);
        assertThat(adapted.close().getYData(2)).isEqualTo(30.5);
    }

    @Test
    void engineDatesFollowViewportChronologyInsteadOfSeriesIndexOrder() {
        CandleSeries series = sampleSeries();
        ChartData data = new ChartData();
        data.setDataset(series);

        Date[] dates = EngineSeriesAdapter.dates(data);

        assertThat(dates).hasSize(3);
        assertThat(dates[0].toInstant()).isBefore(dates[1].toInstant());
        assertThat(dates[1].toInstant()).isBefore(dates[2].toInstant());
        assertThat(dates[2].toInstant()).isEqualTo(Chronological.toInstant(series.get(0).time()));
    }

    @Test
    void closedHostsReturnEmptyPlotBoundsInsteadOfThrowing() {
        EngineChartHost host = new EngineChartHost(new Scale());

        host.close();

        assertThat(host.plotBounds(new JPanel())).isEqualTo(new Rectangle());
    }

    @Test
    void shortenedStudySeriesAlignToTheNewestHistoricalSlots() {
        var values = ImmutableDoubleDataset.ofReversedSameIndexingOrder(new double[] { 50.0, 40.0, 30.0 });

        var adapted = EngineSeriesAdapter.adapt("Study", values, 5);

        assertThat(adapted.getXData(0)).isEqualTo(2.0);
        assertThat(adapted.getXData(1)).isEqualTo(3.0);
        assertThat(adapted.getXData(2)).isEqualTo(4.0);
        assertThat(adapted.getYData(0)).isEqualTo(30.0);
        assertThat(adapted.getYData(2)).isEqualTo(50.0);
    }

    @Test
    void multiHourTimeFramesPreserveTheirRealSlotDuration() {
        TimeUnit timeUnit = EngineSeriesAdapter.timeUnit(TimeFrame.Period.H6);

        assertThat(timeUnit.getMillis()).isEqualTo(TimeFrame.Period.H6.getAsSeconds().orElseThrow().getAmount() * 1000.0);
    }

    @Test
    void sparseIntradayDatasetsUseObservedCadenceForTimeScale() {
        SymbolResource<Candle> resource = SymbolResource.of(SymbolIdentity.of("ENGINE-H6-SESSION"), TimeFrame.Period.H6);
        CandleSeries series = CandleSeries.of(resource, List.of(
                Candle.of(LocalDateTime.of(2026, 1, 5, 15, 30), 10.0, 11.0, 9.0, 10.5, 1_000.0),
                Candle.of(LocalDateTime.of(2026, 1, 6, 15, 30), 20.0, 21.0, 19.0, 20.5, 2_000.0),
                Candle.of(LocalDateTime.of(2026, 1, 7, 15, 30), 30.0, 31.0, 29.0, 30.5, 3_000.0),
                Candle.of(LocalDateTime.of(2026, 1, 8, 15, 30), 40.0, 41.0, 39.0, 40.5, 4_000.0)
        ));
        ChartData data = new ChartData();
        data.setDataset(series);

        TimeUnit timeUnit = EngineSeriesAdapter.timeUnit(data);

        assertThat(timeUnit.getMillis()).isEqualTo(TimeUnit.DAY.getMillis());
    }

    private static CandleSeries sampleSeries() {
        SymbolResource<Candle> resource = SymbolResource.of(SymbolIdentity.of("ENGINE-ORDER"), TimeFrame.Period.DAILY);
        List<Candle> candles = List.of(
                Candle.of(LocalDate.of(2024, 1, 2).atStartOfDay(), 10.0, 11.0, 9.0, 10.5, 1_000.0),
                Candle.of(LocalDate.of(2024, 1, 3).atStartOfDay(), 20.0, 21.0, 19.0, 20.5, 2_000.0),
                Candle.of(LocalDate.of(2024, 1, 4).atStartOfDay(), 30.0, 31.0, 29.0, 30.5, 3_000.0)
        );
        return CandleSeries.of(resource, candles);
    }
}
