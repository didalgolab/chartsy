/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.kernel.StartupMetrics;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Color;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Provides the built-in chart template used by the desktop UI and export tools.
 */
public final class ChartTemplateDefaults {
    private static final Logger log = LogManager.getLogger(ChartTemplateDefaults.class);
    private static final String BASE_CHART_NAME = "Base Chart";
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

    public static ChartTemplate baseChartTemplate() {
        return baseChartTemplate(BASE_CHART_NAME);
    }

    public static ChartTemplate baseChartTemplate(String name) {
        ChartTemplate template = new ChartTemplate(Objects.requireNonNull(name, "name"));
        template.setChartProperties(defaultChartProperties());
        template.setChart(Objects.requireNonNull(ChartManager.getDefault().getChart(defaultChartName()), "default chart"));
        return template;
    }

    public static String defaultChartName() {
        return DEFAULT_CHART;
    }

    public static ChartProperties defaultChartProperties() {
        return createChartProperties();
    }

    public static ChartTemplate basicChartTemplate() {
        StartupMetrics.mark("chartTemplates:basicTemplate:create:start");
        ChartTemplate template = baseChartTemplate(BASIC_CHART_NAME);
        StudyRegistry studies = StudyRegistry.getDefault();
        StartupMetrics.mark("chartTemplates:basicTemplate:registry:ready");
        Map<String, Overlay> overlaysByName = overlaysByName(studies);
        StartupMetrics.mark("chartTemplates:basicTemplate:overlaysLookup:ready");
        addOverlays(template, overlaysByName);
        StartupMetrics.mark("chartTemplates:basicTemplate:overlaysApplied:ready");
        Map<String, Indicator> indicatorsByName = indicatorsByName(studies);
        StartupMetrics.mark("chartTemplates:basicTemplate:indicatorsLookup:ready");
        addIndicators(template, indicatorsByName);
        StartupMetrics.mark("chartTemplates:basicTemplate:indicatorsApplied:ready");
        StartupMetrics.mark("chartTemplates:basicTemplate:create:ready");
        return template;
    }

    private static ChartProperties createChartProperties() {
        ChartProperties props = new ChartProperties();
        props.setAxisColor(new Color(0x2e3436));
        props.setAxisStrokeIndex(0);
        props.setAxisLogarithmicFlag(true);

        props.setBarWidth(PixelPerfectCandleGeometry.DEFAULT_BODY_WIDTH);
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

    private static void addOverlays(ChartTemplate template, Map<String, Overlay> overlaysByName) {
        for (String name : DEFAULT_OVERLAYS) {
            Overlay overlay = overlaysByName.get(name);
            if (overlay != null)
                template.addOverlay(overlay);
            else
                log.warn("Skipping unavailable default overlay `{}`", name);
        }
    }

    private static void addIndicators(ChartTemplate template, Map<String, Indicator> indicatorsByName) {
        for (String name : DEFAULT_INDICATORS) {
            Indicator indicator = indicatorsByName.get(name);
            if (indicator != null)
                template.addIndicator(indicator);
            else
                log.warn("Skipping unavailable default indicator `{}`", name);
        }
    }

    private static Map<String, Overlay> overlaysByName(StudyRegistry studies) {
        var overlaysByName = new LinkedHashMap<String, Overlay>();
        for (Overlay overlay : studies.getOverlaysList()) {
            if (overlay != null)
                overlaysByName.putIfAbsent(overlay.getName(), overlay);
        }
        return overlaysByName;
    }

    private static Map<String, Indicator> indicatorsByName(StudyRegistry studies) {
        var indicatorsByName = new LinkedHashMap<String, Indicator>();
        for (Indicator indicator : studies.getIndicatorsList()) {
            if (indicator != null)
                indicatorsByName.putIfAbsent(indicator.getName(), indicator);
        }
        return indicatorsByName;
    }
}
