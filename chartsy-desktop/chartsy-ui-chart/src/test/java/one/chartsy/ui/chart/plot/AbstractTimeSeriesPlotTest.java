/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.plot;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.base.dataset.ImmutableDoubleDataset;
import one.chartsy.data.CandleSeries;
import one.chartsy.ui.chart.ChartData;
import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.Overlay;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractTimeSeriesPlotTest {

    @Test
    void getVisibleData_empty_dataset_without_visible_window_returns_null() {
        SymbolResource<Candle> resource = SymbolResource.of(
                SymbolIdentity.of("EMPTY"), TimeFrame.Period.DAILY);
        var chartData = new ChartData();
        chartData.setDataset(CandleSeries.of(resource, List.of()));
        var chartFrame = new ChartFrame();
        chartFrame.setChartData(chartData);
        chartData.calculate(chartFrame);
        var plot = new Overlay.EmptyPlot(ImmutableDoubleDataset.EMPTY, Color.BLACK);

        assertThat(chartData.getVisible()).isNull();
        assertThat(plot.getVisibleData(chartFrame)).isNull();
    }
}
