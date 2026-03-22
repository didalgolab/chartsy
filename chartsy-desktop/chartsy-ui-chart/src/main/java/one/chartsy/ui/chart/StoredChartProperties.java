/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import java.awt.Color;
import java.awt.Font;
import java.util.Objects;

record StoredChartProperties(
        int axisColor,
        int axisStrokeIndex,
        boolean axisLogarithmicFlag,
        double barWidth,
        double slotFillPercent,
        int barColor,
        int barStrokeIndex,
        boolean barVisibility,
        int barDownColor,
        boolean barDownVisibility,
        int barUpColor,
        boolean barUpVisibility,
        int gridHorizontalColor,
        int gridHorizontalStrokeIndex,
        boolean gridHorizontalVisibility,
        int gridVerticalColor,
        int gridVerticalStrokeIndex,
        boolean gridVerticalVisibility,
        int backgroundColor,
        String fontName,
        int fontStyle,
        int fontSize,
        int fontColor,
        boolean markerVisibility,
        boolean toolbarVisibility,
        boolean toolbarSmallIcons,
        boolean toolbarShowLabels,
        boolean annotationLayerVisible) {

    private static final StoredChartProperties DEFAULT_VALUE = fromChartProperties(ChartTemplateDefaults.defaultChartProperties());

    public StoredChartProperties {
        fontName = normalizeFontName(fontName);
    }

    static StoredChartProperties defaultValue() {
        return DEFAULT_VALUE;
    }

    static StoredChartProperties fromChartProperties(ChartProperties chartProperties) {
        ChartProperties properties = Objects.requireNonNullElseGet(
                chartProperties,
                ChartTemplateDefaults::defaultChartProperties
        );
        Font font = properties.getFont();
        return new StoredChartProperties(
                properties.getAxisColor().getRGB(),
                properties.getAxisStrokeIndex(),
                properties.getAxisLogarithmicFlag(),
                properties.getBarWidth(),
                properties.getSlotFillPercent(),
                properties.getBarColor().getRGB(),
                properties.getBarStrokeIndex(),
                properties.getBarVisibility(),
                properties.getBarDownColor().getRGB(),
                properties.getBarDownVisibility(),
                properties.getBarUpColor().getRGB(),
                properties.getBarUpVisibility(),
                properties.getGridHorizontalColor().getRGB(),
                properties.getGridHorizontalStrokeIndex(),
                properties.getGridHorizontalVisibility(),
                properties.getGridVerticalColor().getRGB(),
                properties.getGridVerticalStrokeIndex(),
                properties.getGridVerticalVisibility(),
                properties.getBackgroundColor().getRGB(),
                font.getName(),
                font.getStyle(),
                font.getSize(),
                properties.getFontColor().getRGB(),
                properties.getMarkerVisibility(),
                properties.getToolbarVisibility(),
                properties.getToolbarSmallIcons(),
                properties.getToolbarShowLabels(),
                properties.isAnnotationLayerVisible()
        );
    }

    ChartProperties toChartProperties() {
        ChartProperties properties = new ChartProperties();
        properties.setAxisColor(decodeColor(axisColor));
        properties.setAxisStrokeIndex(axisStrokeIndex);
        properties.setAxisLogarithmicFlag(axisLogarithmicFlag);
        properties.setBarWidth(barWidth);
        properties.setSlotFillPercent(slotFillPercent);
        properties.setBarColor(decodeColor(barColor));
        properties.setBarStrokeIndex(barStrokeIndex);
        properties.setBarVisibility(barVisibility);
        properties.setBarDownColor(decodeColor(barDownColor));
        properties.setBarDownVisibility(barDownVisibility);
        properties.setBarUpColor(decodeColor(barUpColor));
        properties.setBarUpVisibility(barUpVisibility);
        properties.setGridHorizontalColor(decodeColor(gridHorizontalColor));
        properties.setGridHorizontalStrokeIndex(gridHorizontalStrokeIndex);
        properties.setGridHorizontalVisibility(gridHorizontalVisibility);
        properties.setGridVerticalColor(decodeColor(gridVerticalColor));
        properties.setGridVerticalStrokeIndex(gridVerticalStrokeIndex);
        properties.setGridVerticalVisibility(gridVerticalVisibility);
        properties.setBackgroundColor(decodeColor(backgroundColor));
        properties.setFont(new Font(fontName, fontStyle, fontSize));
        properties.setFontColor(decodeColor(fontColor));
        properties.setMarkerVisibility(markerVisibility);
        properties.setToolbarVisibility(toolbarVisibility);
        properties.setToolbarSmallIcons(toolbarSmallIcons);
        properties.setToolbarShowLabels(toolbarShowLabels);
        properties.setAnnotationLayerVisible(annotationLayerVisible);
        return properties;
    }

    private static Color decodeColor(int rgb) {
        return new Color(rgb, true);
    }

    private static String normalizeFontName(String fontName) {
        if (fontName == null || fontName.isBlank())
            return ChartProperties.MAIN_FONT.getName();
        return fontName.strip();
    }
}
