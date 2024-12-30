/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.plot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import one.chartsy.base.DoubleDataset;
import one.chartsy.core.Range;
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
