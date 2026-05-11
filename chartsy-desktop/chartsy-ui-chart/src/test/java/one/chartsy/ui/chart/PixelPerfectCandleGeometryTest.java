/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import org.junit.jupiter.api.Test;
import org.assertj.core.data.Offset;

import static org.assertj.core.api.Assertions.assertThat;

class PixelPerfectCandleGeometryTest {

    @Test
    void defaultPresetUsesSevenPixelBodiesAndEightPixelSlots() {
        assertThat(PixelPerfectCandleGeometry.DEFAULT_BODY_WIDTH).isEqualTo(7);
        assertThat(PixelPerfectCandleGeometry.slotStep(PixelPerfectCandleGeometry.DEFAULT_BODY_WIDTH)).isEqualTo(8);
        assertThat(PixelPerfectCandleGeometry.fillPercent(PixelPerfectCandleGeometry.DEFAULT_BODY_WIDTH))
                .isCloseTo(87.5, Offset.offset(1.0e-9));
    }

    @Test
    void chartPropertiesSnapBodyWidthAndKeepOnePixelGap() {
        ChartProperties properties = new ChartProperties();

        properties.setBarWidth(4.1);

        assertThat(properties.getBarWidth()).isEqualTo(5.0);
        assertThat(properties.getSlotFillPercent())
                .isCloseTo(83.33333333333333, Offset.offset(1.0e-9));
        assertThat(PixelPerfectCandleGeometry.slotStep(properties.getBarWidth())).isEqualTo(6);
    }

    @Test
    void zoomWalksDiscreteOddBodyWidths() {
        ChartData chartData = new ChartData();

        assertThat(chartData.zoomOut(5.0)).isEqualTo(3.0);
        assertThat(chartData.zoomIn(5.0)).isEqualTo(7.0);
        assertThat(chartData.zoomIn(4.0)).isEqualTo(7.0);
    }
}
