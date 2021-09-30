/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import one.chartsy.commons.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleDataset;
import one.chartsy.ui.chart.data.VisibleValues;
import one.chartsy.ui.chart.plot.AbstractTimeSeriesPlot;

import javax.swing.*;

/**
 * The overlay displays new information derived from historical price and/or
 * volume data to the end users assisting them in making buying or selling
 * decisions.
 * <p>
 * It differs from {@link Indicator}'s by the fact of drawing on the top of an
 * existing price chart plot, and usually sharing the same price axis. For
 * example an exponential moving average of the close prices is an overlay
 * because it is much easier for the end user to interpret the moving average
 * when drawn over the original price chart.
 * <p>
 * The overlay is composed of one or more {@link Painter}'s responsible for
 * proving a graphical representation of the values calculated by an overlay
 * from the price or volume data.
 * 
 * @author Mariusz Bernacki
 */
public abstract class Overlay extends ChartPlugin<Overlay> implements Serializable {

    private transient CandleSeries dataset;
    /** The overlay plots currently being displayed. */
    protected final Map<String, Plot> plots = createPlotsMap();
    /** Determines whether the overlay is active. */
    protected boolean active = true;
    
    
    public Overlay(String name) {
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
    
    public String getFontHTML(Color color, String text) {
        String html = "<font color=\"" + Integer.toHexString(color.getRGB() & 0x00ffffff) + "\">" + text + "</font>";
        return html;
    }
    
    public CandleSeries getDataset() {
        return dataset;
    }
    
    public void setDataset(CandleSeries quotes) {
        this.dataset = quotes;
    }
    
    public void clearPlots() {
        plots.clear();
    }
    
    public void addPlot(String key, Plot plot) {
        plots.put(key, plot);
    }
    
    public VisibleValues visibleDataset(ChartContext cf, String key) {
        Plot plot = plots.get(key);
        if (plot instanceof TimeSeriesPlot)
            return ((TimeSeriesPlot) plot).getVisibleData(cf);
        return null;
    }
    
    
    @Override
    public abstract Overlay newInstance();
    
    // TODO: change to Marker objects.
    public LinkedHashMap<String, String> getHTML(ChartContext cf, int i) {
        LinkedHashMap<String, String> ht = new LinkedHashMap<>();
        DecimalFormat df = new DecimalFormat("#,##0.00");
        
        ht.put(getLabel(), " ");
        for (String key : plots.keySet()) {
            VisibleValues dataset = visibleDataset(cf, key);
            if (dataset != null) {
                double value = dataset.getValueAt(i);
                Color color = plots.get(key).getPrimaryColor();
                ht.put(getFontHTML(color, key.concat(":")), getFontHTML(color, df.format(value)));
            }
        }
        
        return ht;
    }
    
    public Range getRange(ChartContext cf) {
        Range range = null;
        Range.Builder rv = new Range.Builder();
        for (String key : plots.keySet()) {
            VisibleValues dataset = visibleDataset(cf, key);
            if (dataset != null) {
                rv = dataset.getRange(rv);
                double min = rv.min;
                double max = rv.max;
                if (min <= max) {
                    double margin = (max - min) * 0.00;//TODO: changed
                    //double margin = Math.log(max - min) * 0.01;
                    if (range == null) {
                        range = new Range(min - margin, max + margin);
                    } else {
                        range = Range.combine(range, new Range(min - margin, max + margin));
                    }
                }
            }
        }
        if (range == null)
            range = Range.of(0.0, Double.POSITIVE_INFINITY);
        return range;
    }
    
    public void paint(Graphics2D g, ChartContext cf, Rectangle bounds) {
        Range range = cf.getMainPanel().getChartPanel().getRange();
        for (Plot plot : plots.values())
            plot.paint(g, cf, range, bounds);
    }
    
    public abstract void calculate();
    
    public Color[] getColors() {
        Color[] colors = new Color[plots.size()];
        int k = 0;
        for (Object key : plots.keySet())
            colors[k++] = plots.get(key).getPrimaryColor();
        return colors;
    }
    
    // public abstract double[] getValues(ChartFrame cf);
    
    // public abstract double[] getValues(ChartFrame cf, int i);
    
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
    
    // public abstract String getPrice();
    
    /**
     * If an override in the overlay class sets this to false that overlay is
     * not included in the range calculation of the chart.
     * 
     * @return whether to include this overlay in chart range
     */
    public boolean isIncludedInRange() {
        return true;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public boolean isActive() {
        return active;
    }
    
    @Override
    public void datasetChanged(CandleSeries quotes) {
        setDataset(quotes);
        calculate();
    }
}
