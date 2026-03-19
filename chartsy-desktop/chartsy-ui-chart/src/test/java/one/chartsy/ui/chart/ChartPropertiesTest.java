/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;

import static org.assertj.core.api.Assertions.assertThat;

class ChartPropertiesTest {

    @Test
    void copyFromCopiesAllMutableProperties() {
        ChartProperties source = new ChartProperties();
        source.setAxisColor(Color.BLUE);
        source.setAxisStroke(BasicStrokes.THICK_SOLID);
        source.setAxisLogarithmicFlag(false);
        source.setBarWidth(13.5);
        source.setBarColor(Color.CYAN);
        source.setBarStroke(BasicStrokes.DASHED);
        source.setBarVisibility(false);
        source.setBarDownColor(Color.RED);
        source.setBarDownVisibility(false);
        source.setBarUpColor(Color.GREEN);
        source.setBarUpVisibility(false);
        source.setGridHorizontalColor(Color.GRAY);
        source.setGridHorizontalStroke(BasicStrokes.ULTRATHIN_SOLID);
        source.setGridHorizontalVisibility(false);
        source.setGridVerticalColor(Color.BLACK);
        source.setGridVerticalStroke(BasicStrokes.DOTTED);
        source.setGridVerticalVisibility(false);
        source.setBackgroundColor(Color.ORANGE);
        source.setFont(new Font("Dialog", Font.BOLD, 18));
        source.setFontColor(Color.MAGENTA);
        source.setMarkerVisibility(true);
        source.setToolbarVisibility(false);
        source.setToolbarSmallIcons(true);
        source.setToolbarShowLabels(false);
        source.setAnnotationLayerVisible(false);

        ChartProperties copy = new ChartProperties();
        copy.copyFrom(source);

        assertThat(copy.getAxisColor()).isEqualTo(source.getAxisColor());
        assertThat(copy.getAxisStrokeIndex()).isEqualTo(source.getAxisStrokeIndex());
        assertThat(copy.getAxisLogarithmicFlag()).isEqualTo(source.getAxisLogarithmicFlag());
        assertThat(copy.getBarWidth()).isEqualTo(source.getBarWidth());
        assertThat(copy.getBarColor()).isEqualTo(source.getBarColor());
        assertThat(copy.getBarStrokeIndex()).isEqualTo(source.getBarStrokeIndex());
        assertThat(copy.getBarVisibility()).isEqualTo(source.getBarVisibility());
        assertThat(copy.getBarDownColor()).isEqualTo(source.getBarDownColor());
        assertThat(copy.getBarDownVisibility()).isEqualTo(source.getBarDownVisibility());
        assertThat(copy.getBarUpColor()).isEqualTo(source.getBarUpColor());
        assertThat(copy.getBarUpVisibility()).isEqualTo(source.getBarUpVisibility());
        assertThat(copy.getGridHorizontalColor()).isEqualTo(source.getGridHorizontalColor());
        assertThat(copy.getGridHorizontalStrokeIndex()).isEqualTo(source.getGridHorizontalStrokeIndex());
        assertThat(copy.getGridHorizontalVisibility()).isEqualTo(source.getGridHorizontalVisibility());
        assertThat(copy.getGridVerticalColor()).isEqualTo(source.getGridVerticalColor());
        assertThat(copy.getGridVerticalStrokeIndex()).isEqualTo(source.getGridVerticalStrokeIndex());
        assertThat(copy.getGridVerticalVisibility()).isEqualTo(source.getGridVerticalVisibility());
        assertThat(copy.getBackgroundColor()).isEqualTo(source.getBackgroundColor());
        assertThat(copy.getFont()).isEqualTo(source.getFont());
        assertThat(copy.getFontColor()).isEqualTo(source.getFontColor());
        assertThat(copy.getMarkerVisibility()).isEqualTo(source.getMarkerVisibility());
        assertThat(copy.getToolbarVisibility()).isEqualTo(source.getToolbarVisibility());
        assertThat(copy.getToolbarSmallIcons()).isEqualTo(source.getToolbarSmallIcons());
        assertThat(copy.getToolbarShowLabels()).isEqualTo(source.getToolbarShowLabels());
        assertThat(copy.isAnnotationLayerVisible()).isEqualTo(source.isAnnotationLayerVisible());
    }
}
