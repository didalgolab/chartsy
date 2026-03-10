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
import one.chartsy.ui.chart.overlays.Volume;
import one.chartsy.ui.chart.type.CandlestickChart;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChartStudyIntegrationTest {

    @Test
    void basicChartTemplateIncludesGeneratedStudiesAlongsideVolumeOverlay() {
        ChartTemplate template = ChartTemplateDefaults.basicChartTemplate();

        assertThat(template.getOverlays())
                .extracting(Overlay::getName)
                .contains("FRAMA, Leading", "FRAMA, Trailing", "Sfora", "Sentiment Bands", "Volume");
        assertThat(template.getIndicators())
                .extracting(Indicator::getName)
                .containsExactly("Fractal Dimension");
    }

    @Test
    void volumeOverlayKeepsZeroBaselineInsideItsBottomStrip() {
        ChartTemplate template = new ChartTemplate("Volume");
        template.setChartProperties(new ChartProperties());
        template.setChart(new CandlestickChart());
        template.addOverlay(new Volume());

        CandleSeries dataset = CandleSeries.of(
                SymbolResource.of(SymbolIdentity.of("TEST"), TimeFrame.Period.DAILY).withDataType(Candle.class),
                List.of(
                        Candle.of(LocalDate.of(2026, 1, 1).atStartOfDay(), 100, 104, 98, 103, 1_000),
                        Candle.of(LocalDate.of(2026, 1, 2).atStartOfDay(), 103, 106, 101, 105, 6_000),
                        Candle.of(LocalDate.of(2026, 1, 3).atStartOfDay(), 105, 107, 102, 104, 3_500),
                        Candle.of(LocalDate.of(2026, 1, 4).atStartOfDay(), 104, 108, 103, 107, 8_000)
                )
        );

        ChartFrame chartFrame = ChartExporter.createChartFrame(DataProvider.EMPTY, dataset, template, new Dimension(1280, 800));
        Volume volume = (Volume) chartFrame.getMainPanel().getChartPanel().getOverlays().stream()
                .filter(overlay -> overlay instanceof Volume)
                .findFirst()
                .orElseThrow();

        Rectangle chartBounds = chartFrame.getMainPanel().getChartPanel().getBounds();
        Rectangle overlayBounds = new Rectangle(
                chartBounds.x,
                chartBounds.y + chartBounds.height - chartBounds.height / 4,
                chartBounds.width,
                chartBounds.height / 4
        );
        Range range = volume.getRange(chartFrame);
        double zeroY = chartFrame.getChartData().getY(0.0, overlayBounds, range, false);

        assertThat(range.contains(0.0)).isTrue();
        assertThat(zeroY).isBetween(overlayBounds.getMinY(), overlayBounds.getMaxY() + 1.0);
    }
}
