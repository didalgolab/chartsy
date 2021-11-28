/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.plot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import one.chartsy.core.Range;
import one.chartsy.data.DoubleDataset;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.data.VisibleValues;
import one.chartsy.ui.chart.internal.Graphics2DHelper;

public class InsideFillPlot extends AbstractPlot {
    /** The upper boundary of the fill. */
    protected final DoubleDataset upper;
    /** The lower boundary of the fill. */
    protected final DoubleDataset lower;
    
    
    public InsideFillPlot(DoubleDataset upper, DoubleDataset lower, Color color) {
        super(color);
        this.upper = upper;
        this.lower = lower;
    }
    
    @Override
    public void paint(Graphics2D g, ChartContext cf, Range range, Rectangle bounds) {
        paintFill(g, cf, range, bounds);
    }
    
    private void paintFill(Graphics2D g, ChartContext cf, Range range, Rectangle bounds) {
        VisibleValues upperLine = cf.getChartData().getVisible().getVisibleDataset(upper);
        VisibleValues lowerLine = cf.getChartData().getVisible().getVisibleDataset(lower);
        Graphics2DHelper.insideFill(g, cf, range, bounds, upperLine, lowerLine, primaryColor);
    }
}
