/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.base.DoubleDataset;
import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.study.StudyPresentationPlan;
import one.chartsy.ui.chart.data.VisualRange;

import java.awt.Color;
import java.text.DecimalFormat;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The indicator displays new information derived from historical price and/or
 * volume data to the end users assisting them in making buying or selling
 * decisions.
 * <p>
 * It differs from {@link Overlay}'s by the fact of drawing on separate plot
 * areas and usually using their own axis. For example the Relative Strength
 * Index is an indicator displaying sequence of values in range 0 to 100 and
 * thus using axis different than original price axis.
 * <p>
 * The indicator is composed of one or more {@link Painter}'s responsible for
 * proving a graphical representation of the values calculated by an indicator
 * from the price or volume data.
 * 
 * @author Mariusz Bernacki
 */
public abstract class Indicator extends ChartPlugin<Indicator> {
    /** The dataset used by this indicator. */
    private transient CandleSeries dataset;
    /** Indicates whether the indicator plot should be paint when minimized. */
    private boolean minimizedPaint;
    /** The identifier of the panel to which this indicator should be added to. */
    private @Parameter(name = "panelId") UUID panelId = UUID.randomUUID();
    /** The native presentation plan used by the engine-backed renderer. */
    private transient StudyPresentationPlan presentationPlan = StudyPresentationPlan.empty(null);
    /** The indicator plots currently being displayed. */
    protected final Map<String, Plot> plots = createPlotsMap();
    
    
    public Indicator(String name) {
        super(name);
    }
    
    protected CandleSeries getDataset() {
        return dataset;
    }
    
    public void setDataset(CandleSeries dataset) {
        this.dataset = dataset;
    }

    @Override
    public abstract Indicator newInstance();
    
    public VisualRange getRange(ChartContext cf) {
        if (plots.isEmpty()) {
            return new VisualRange(Range.empty());
        }
        Range.Builder rv = new Range.Builder();
        for (Plot plot : plots.values())
            rv = plot.contributeRange(rv, cf);
        Range range = rv.toRange();
        if (range.isEmpty())
            return new VisualRange(Range.of(0.0, Double.POSITIVE_INFINITY));

        double margin = range.length() * 0.01;
        return new VisualRange(rv.add(range.min() - margin).add(range.max() + margin).toRange());
    }

    /**
     * Calculates all values for the indicator.
     */
    public abstract void calculate();
    
    public Color[] getColors() {
        Color[] colors = new Color[plots.size()];
        int k = 0;
        for (Plot plot : plots.values())
            colors[k++] = plot.getPrimaryColor();
        return colors;
    }
    
    //public abstract double[] getValues(ChartFrame cf);
    
    //public abstract double[] getValues(ChartFrame cf, int i);
    
    public double[] getValues(ChartContext cf) {
        return getValues(cf, cf.getChartData().getPeriod() - 1);
    }
    
    public double[] getValues(ChartContext cf, int i) {
        double[] values = new double[plots.size()];
        int k = 0;
        for (String key : plots.keySet()) {
            var dataset = visibleDataset(cf, key);
            int barNo = (i < 0 && dataset != null) ? i + dataset.getLength() : i;
            values[k++] = (dataset != null) ? dataset.getValueAt(barNo) : Double.NaN;
        }
        return values;
    }
    
    public abstract boolean getMarkerVisibility();
    
    public boolean paintValues() {
        return true;
    }
    
    public abstract double[] getStepValues(ChartContext cf);
    
    @Override
    public void datasetChanged(CandleSeries dataset) {
        setDataset(dataset);
        calculate();
    }
    
    public boolean isMinimizedPaint() {
        return minimizedPaint;
    }
    
    public void setMinimizedPaint(boolean minimizedPaint) {
        this.minimizedPaint = minimizedPaint;
    }

    public StudyPresentationPlan getPresentationPlan() {
        return presentationPlan != null ? presentationPlan : StudyPresentationPlan.empty(null);
    }

    protected final void setPresentationPlan(StudyPresentationPlan presentationPlan) {
        this.presentationPlan = presentationPlan != null ? presentationPlan : StudyPresentationPlan.empty(null);
    }

    public void clearPlots() {
        plots.clear();
    }

    public void addPlot(String key, Plot plot) {
        plots.put(key, plot);
    }

    public Map<String, Plot> getPlots() {
        return Collections.unmodifiableSequencedMap(new LinkedHashMap<>(plots));
    }

    public one.chartsy.ui.chart.data.VisibleValues visibleDataset(ChartContext cf, String key) {
        Plot plot = plots.get(key);
        if (plot instanceof one.chartsy.ui.chart.plot.TimeSeriesPlot timeSeriesPlot)
            return timeSeriesPlot.getVisibleData(cf);
        return null;
    }
    
    public LinkedHashMap<String, String> getHTML(ChartContext cf, int i) {
        LinkedHashMap<String, String> ht = new LinkedHashMap<>();

        DecimalFormat df = new DecimalFormat("#,##0.00");
        ht.put(getLabel(), " ");
        if (!plots.isEmpty()) {
            int j = 0;
            Color[] colors = getColors();
            for (String key : plots.keySet()) {
                double value = getValues(cf, i)[j];
                String text = Double.isNaN(value) ? "n/a" : df.format(value);
                Color color = (j < colors.length) ? colors[j] : Color.BLACK;
                ht.put(getFontHTML(color, key.concat(":")), getFontHTML(color, text));
                j++;
            }
        }

        return ht;
    }

    public String getFontHTML(Color color, String text) {
        String html = "<font color=\"" + Integer.toHexString(color.getRGB() & 0x00ffffff) + "\">" + text + "</font>";
        return html;
    }

    public UUID getPanelId() {
        return panelId;
    }

    public void setPanelId(UUID panelId) {
        this.panelId = panelId;
    }
}
