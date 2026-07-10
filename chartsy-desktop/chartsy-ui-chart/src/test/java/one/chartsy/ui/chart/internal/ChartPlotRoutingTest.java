/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.internal;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.ui.chart.DynamicStudyIndicator;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.StudyRegistry;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class ChartPlotRoutingTest {

    @Test
    void routesEachCalculatedPlot_to_its_configuredPanel() {
        Indicator indicator = StudyRegistry.getDefault().getIndicator("Fractal Dimension");
        indicator.setPanelId(1);
        ChartPlotRouting.setPanelId(indicator, "insideNeutral", 0);
        ChartPlotRouting.setPanelId(indicator, "insideHigh", 2);
        indicator.setDataset(sampleDataset(60));
        indicator.calculate();

        List<ChartPlotRouting.Route> routes = ChartPlotRouting.routes(indicator);
        assertThat(routes).extracting(ChartPlotRouting.Route::plotId, ChartPlotRouting.Route::panelId)
                .contains(
                        tuple("insideNeutral", 0),
                        tuple("insideHigh", 2),
                        tuple("fdi", 1),
                        tuple("level14", 1),
                        tuple("level16", 1));
        assertThat(ChartPlotRouting.routesForPanel(List.of(indicator), 0))
                .extracting(ChartPlotRouting.Route::plotId)
                .containsExactly("insideNeutral");
        assertThat(ChartPlotRouting.routesForPanel(List.of(indicator), 2))
                .extracting(ChartPlotRouting.Route::plotId)
                .containsExactly("insideHigh");
    }

    @Test
    void defaultPanel_follows_theIndicatorsNaturalPane() {
        DynamicStudyIndicator indicator = (DynamicStudyIndicator) StudyRegistry.getDefault()
                .getIndicator("Chande Momentum Oscillator");
        indicator.setPanelId(4);

        assertThat(ChartPlotRouting.configuredPanelIds(indicator)).containsExactly(4);
    }

    private static CandleSeries sampleDataset(int size) {
        List<Candle> candles = new ArrayList<>(size);
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int index = 0; index < size; index++) {
            double open = 100 + index * 0.8;
            double close = open + Math.sin(index / 3.0);
            candles.add(Candle.of(
                    start.plusDays(index).atStartOfDay(),
                    open,
                    Math.max(open, close) + 1.0,
                    Math.min(open, close) - 1.0,
                    close,
                    1_000 + index));
        }
        return CandleSeries.of(
                SymbolResource.of(SymbolIdentity.of("ROUTING"), TimeFrame.Period.DAILY),
                candles);
    }
}
