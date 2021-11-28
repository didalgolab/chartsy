/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.plot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;

import one.chartsy.core.Range;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.Plot;

public abstract class AbstractPlot implements Plot {
    /** The primary color associated with this plot. */
    protected Color primaryColor;
    
    
    protected AbstractPlot(Color primaryColor) {
        this.primaryColor = primaryColor;
    }
    
    @Override
    public Color getPrimaryColor() {
        return primaryColor;
    }
    
    @Override
    public abstract void paint(Graphics2D g, ChartContext cf, Range range, Rectangle bounds);
    
}
