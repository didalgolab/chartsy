/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import one.chartsy.commons.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleDataset;
import one.chartsy.ui.chart.data.VisibleValues;
import one.chartsy.ui.chart.data.VisualRange;
import one.chartsy.ui.chart.plot.AbstractTimeSeriesPlot;

import javax.swing.*;

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
    /** The overlay plots currently being displayed. */
    protected final Map<String, Plot> plots = createPlotsMap();
    /** Indicates whether the indicator plot should be paint when minimized. */
    private boolean minimizedPaint;
    /** The identifier of the panel to which this indicator should be added to. */
    private @Parameter(name = "panelId") UUID panelId = UUID.randomUUID();
    
    
    public Indicator(String name) {
        super(name);
    }
    
    public static class EmptyPlot extends AbstractTimeSeriesPlot {
        
        public EmptyPlot(DoubleDataset data, Color primaryColor) {
            super(data, primaryColor);
        }
        
        @Override
        public void paint(Graphics2D g, ChartContext cf, Range range, Rectangle bounds) {
            // TODO Auto-generated method stub
        }
        
    }
    
    public LinkedHashMap<String, String> getHTML(ChartContext cf, int i) {
        LinkedHashMap<String, String> ht = new LinkedHashMap<>();
        
        DecimalFormat df = new DecimalFormat("#,##0.00");
        double[] values = getValues(cf, i);
        
        ht.put(getLabel(), " ");
        if (values.length > 0) {
            int j = 0;
            Color[] colors = getColors();
            for (String key : plots.keySet()) {
                ht.put(getFontHTML(colors[j], key.concat(":")), getFontHTML(colors[j], df.format(values[j])));
                j++;
            }
        }
        
        return ht;
    }
    
    public String getFontHTML(Color color, String text) {
        String html = "<font color=\"" + Integer.toHexString(color.getRGB() & 0x00ffffff) + "\">" + text + "</font>";
        return html;
    }
    
    protected CandleSeries getDataset() {
        return dataset;
    }
    
    public void setDataset(CandleSeries dataset) {
        this.dataset = dataset;
    }
    
    protected void addPlot(String key, Plot plot) {
        plots.put(key, plot);
    }
    
    protected final boolean hasPlot(String key) {
        return getPlot(key) != null;
    }
    
    protected Plot getPlot(String key) {
        return plots.get(key);
    }
    
    public VisibleValues visibleDataset(ChartContext view, String key) {
        Plot plot = plots.get(key);
        if (plot instanceof TimeSeriesPlot)
            return ((TimeSeriesPlot) plot).getVisibleData(view);
        return null;
    }
    
    public void clearPlots() {
        plots.clear();
    }
    
    @Override
    public abstract Indicator newInstance();
    
    public VisualRange getRange(ChartContext cf) {
        if (plots.isEmpty()) {
            return new Range();
        }
        
        Range range = null;
        Iterator<String> it = plots.keySet().iterator();
        
        DataInterval rv = new DataInterval();
        while (it.hasNext()) {
            VisibleValues d = visibleDataset(cf, it.next());
            
            if (d != null) {
                rv = d.getRange(rv);
                double min = rv.min;
                double max = rv.max;
                if (min <= max) {
                    if (range == null) {
                        range = new Range(min - (max - min) * 0.01, max + (max - min) * 0.01);
                    } else {
                        range = Range.combine(range, new Range(min - (max - min) * 0.01, max + (max - min) * 0.01));
                    }
                }
            }
        }
        
        if (range == null)
            range = new Range();
        return range;
    }
    
    public void paint(Graphics2D g, ChartContext view, Rectangle bounds) {
        Range range = getRange(view).range();
        for (Plot plot : plots.values())
            plot.paint(g, view, range, bounds);
    }
    
    /**
     * Calculates all values for the indicator.
     */
    public abstract void calculate();
    
    public Color[] getColors() {
        Color[] colors = new Color[plots.size()];
        int k = 0;
        for (Object key : plots.keySet())
            colors[k++] = plots.get(key).getPrimaryColor();
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
            VisibleValues dataset = visibleDataset(cf, key);
            int barNo = (i < 0)? i + dataset.getLength() : i;
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
    
    public UUID getPanelId() {
        return panelId;
    }
    
    public void setPanelId(UUID panelId) {
        this.panelId = panelId;
    }
}
