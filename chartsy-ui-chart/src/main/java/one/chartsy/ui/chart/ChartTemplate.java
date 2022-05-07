/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import one.chartsy.ui.chart.Chart;
import one.chartsy.ui.chart.ChartProperties;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.Overlay;

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
        if (indicator != null)
            indicators.add(indicator);
    }
    
    public List<Indicator> getIndicators() {
        return indicators;
    }
}
