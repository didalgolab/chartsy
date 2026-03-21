/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Color;
import java.awt.Font;
import java.util.List;
import java.util.Objects;

/**
 * Provides the built-in chart template used by the desktop UI and export tools.
 */
public final class ChartTemplateDefaults {
    private static final Logger log = LogManager.getLogger(ChartTemplateDefaults.class);
    private static final String BASIC_CHART_NAME = "Basic Chart";
    private static final String DEFAULT_CHART = "Candle Stick";
    private static final List<String> DEFAULT_OVERLAYS = List.of(
            "FRAMA, Leading",
            "FRAMA, Trailing",
            "Sfora",
            "Volume",
            "Sentiment Bands"
    );
    private static final List<String> DEFAULT_INDICATORS = List.of("Fractal Dimension");

    private ChartTemplateDefaults() {
    }

    public static ChartTemplate basicChartTemplate() {
        ChartTemplate template = new ChartTemplate(BASIC_CHART_NAME);
        template.setChartProperties(createChartProperties());
        template.setChart(Objects.requireNonNull(ChartManager.getDefault().getChart(DEFAULT_CHART), "default chart"));

        StudyRegistry studies = StudyRegistry.getDefault();
        addOverlays(template, studies);
        addIndicators(template, studies);
        return template;
    }

    private static ChartProperties createChartProperties() {
        ChartProperties props = new ChartProperties();
        props.setAxisColor(new Color(0x2e3436));
        props.setAxisStrokeIndex(0);
        props.setAxisLogarithmicFlag(true);

        props.setBarWidth(4.0);
        props.setBarColor(new Color(0x2e3436));
        props.setBarStrokeIndex(0);
        props.setBarVisibility(true);
        props.setBarDownColor(new Color(0x2e3436));
        props.setBarDownVisibility(true);
        props.setBarUpColor(new Color(0xffffff));
        props.setBarUpVisibility(true);

        props.setGridHorizontalColor(new Color(0xeeeeec));
        props.setGridHorizontalStrokeIndex(0);
        props.setGridHorizontalVisibility(true);
        props.setGridVerticalColor(new Color(0xeeeeec));
        props.setGridVerticalStrokeIndex(0);
        props.setGridVerticalVisibility(true);

        props.setBackgroundColor(new Color(0xffffff));
        props.setFont(new Font("Dialog", Font.PLAIN, 12));
        props.setFontColor(new Color(0x2e3436));

        props.setMarkerVisibility(false);
        props.setToolbarVisibility(true);
        props.setToolbarSmallIcons(false);
        props.setToolbarShowLabels(true);
        props.setAnnotationLayerVisible(true);
        return props;
    }

    private static void addOverlays(ChartTemplate template, StudyRegistry studies) {
        for (String name : DEFAULT_OVERLAYS) {
            Overlay overlay = studies.getOverlay(name);
            if (overlay != null)
                template.addOverlay(overlay);
            else
                log.warn("Skipping unavailable default overlay `{}`", name);
        }
    }

    private static void addIndicators(ChartTemplate template, StudyRegistry studies) {
        for (String name : DEFAULT_INDICATORS) {
            Indicator indicator = studies.getIndicator(name);
            if (indicator != null)
                template.addIndicator(indicator);
            else
                log.warn("Skipping unavailable default indicator `{}`", name);
        }
    }
}
