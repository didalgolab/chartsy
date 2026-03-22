/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.ui.chart.Chart;
import one.chartsy.ui.chart.ChartProperties;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.Overlay;
import one.chartsy.ui.chart.internal.IndicatorPaneSupport;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author Mariusz Bernacki
 */
public class ChartTemplate implements Serializable {
    
    private final String name;
    private Chart chart;
    private ChartProperties chartProperties;
    private final List<Overlay> overlays;
    private final List<Indicator> indicators;
    
    public ChartTemplate(String name) {
        this.name = name;
        this.chartProperties = new ChartProperties();
        this.overlays = new ArrayList<>();
        this.indicators = new ArrayList<>();
    }
    
    public String getName() {
        return name;
    }
    
    public void setChart(Chart chart) {
        if (chart != null)
            this.chart = chart;
    }
    
    public Chart getChart() {
        return chart;
    }
    
    public void setChartProperties(ChartProperties chartProperties) {
        if (chartProperties != null)
            this.chartProperties = chartProperties;
    }
    
    public ChartProperties getChartProperties() {
        return chartProperties;
    }
    
    public void addOverlay(Overlay overlay) {
        if (overlay != null)
            overlays.add(overlay);
    }
    
    public List<Overlay> getOverlays() {
        return overlays;
    }
    
    public void addIndicator(Indicator indicator) {
        if (indicator == null)
            return;

        assignPaneIdIfNeeded(indicator);
        indicators.add(indicator);
    }
    
    public List<Indicator> getIndicators() {
        return indicators;
    }

    private void assignPaneIdIfNeeded(Indicator indicator) {
        if (IndicatorPaneSupport.isOwnPanelIndicator(indicator) && indicator.getPanelId() <= 0)
            indicator.setPanelId(IndicatorPaneSupport.nextPanelId(indicators));
    }
}
