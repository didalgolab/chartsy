/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.plot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import one.chartsy.commons.Range;
import one.chartsy.data.DoubleDataset;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.data.VisibleValues;
import one.chartsy.ui.chart.internal.Graphics2DHelper;

public class BarPlot extends AbstractTimeSeriesPlot {
    
    public BarPlot(DoubleDataset timeSeries, Color color) {
        super(timeSeries, color);
    }
    
    @Override
    public void paint(Graphics2D g, ChartContext cf, Range range, Rectangle bounds) {
        VisibleValues values = getVisibleData(cf);
        if (values != null)
            paintBars(g, cf, range, bounds, values);
    }
    
    protected void paintBars(Graphics2D g, ChartContext cf, Range range, Rectangle bounds, VisibleValues values) {
        Graphics2DHelper.bar(g, cf, range, bounds, values, primaryColor);
    }
}
