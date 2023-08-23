/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.plot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

import one.chartsy.core.Range;
import one.chartsy.data.DoubleDataset;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.ChartData;
import one.chartsy.ui.chart.data.VisibleValues;

public class HistogramPlot extends AbstractTimeSeriesPlot {
    /** The histogram positive color. */
    protected final Color histogramPositiveColor;
    /** The histogram negative color. */
    protected final Color histogramNegativeColor;
    
    public HistogramPlot(DoubleDataset dataset, Color barColor) {
        this(dataset, barColor, barColor);
    }
    
    public HistogramPlot(DoubleDataset dataset, Color histPositiveColor, Color histNegativeColor) {
        super(dataset, histPositiveColor);
        this.histogramPositiveColor = histPositiveColor;
        this.histogramNegativeColor = histNegativeColor;
    }
    
    @Override
    public void paint(Graphics2D g, ChartContext cf, Range range, Rectangle bounds) {
        VisibleValues values = getVisibleData(cf);
        if (values != null)
            paintHistogram(g, cf, range, bounds, values);
    }
    
    protected void paintHistogram(Graphics2D g, ChartContext cf, Range range, Rectangle bounds, VisibleValues dataset) {
        ChartData cd = cf.getChartData();
        boolean logarithmic = cf.getChartProperties().getAxisLogarithmicFlag();
        double zeroY = cd.getY(0D, bounds, range, logarithmic);
        Rectangle2D rect = new Rectangle2D.Double();
        
        // Paint positive histogram bars
        g.setColor(histogramPositiveColor);
        for (int i = 0; i < dataset.getLength(); i++) {
            double value = dataset.getValueAt(i);
            if (value > 0) {
                int x = (int)(0.5 + cd.getX(i, bounds));
                int y = (int)(0.5 + cd.getY(value, bounds, range, logarithmic));
                
                int width = (int)Math.floor(0.5 + cf.getChartProperties().getBarWidth());
                double height = Math.abs(y - zeroY);
                
                rect.setFrame(x - width/2, y, Math.max(width, 1), height);
                g.fill(rect);
            }
        }
        
        // Paint negative histogram bars
        g.setColor(histogramNegativeColor);
        for (int i = 0; i < dataset.getLength(); i++) {
            double value = dataset.getValueAt(i);
            if (value < 0) {
                int x = (int)(0.5 + cd.getX(i, bounds));
                int y = (int)(0.5 + cd.getY(value, bounds, range, logarithmic));
                
                int width = (int)Math.floor(0.5 + cf.getChartProperties().getBarWidth());
                double height = Math.abs(y - zeroY);
                
                rect.setFrame(x - width/2, y - height, Math.max(width, 1), height);
                g.fill(rect);
            }
        }
    }
}
