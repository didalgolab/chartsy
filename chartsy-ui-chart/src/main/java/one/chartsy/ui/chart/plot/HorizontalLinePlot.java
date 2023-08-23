/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.plot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;

import one.chartsy.core.Range;
import one.chartsy.ui.chart.ChartContext;

public class HorizontalLinePlot extends AbstractPlot {
    /** The stroke used by this plot. */
    protected final Stroke stroke;
    /** The horizontal line value. */
    protected final double value;
    
    
    public HorizontalLinePlot(double value, Color color, Stroke stroke) {
        super(color);
        this.stroke = stroke;
        this.value = value;
    }
    
    @Override
    public void paint(Graphics2D g, ChartContext cf, Range range, Rectangle bounds) {
        paintHorizontalLine(g, cf, range, bounds);
    }
    
    private void paintHorizontalLine(Graphics2D g, ChartContext cf, Range range, Rectangle bounds) {
        boolean isLog = cf.getChartProperties().getAxisLogarithmicFlag();
        Stroke oldStroke = g.getStroke();
        try {
            if (stroke != null)
                g.setStroke(stroke);
            g.setPaint(primaryColor);
            int y = (int) Math.round(cf.getChartData().getY(value, bounds, range, isLog));
            g.drawLine(bounds.x, y, bounds.x + bounds.width - 1, y);
        } finally {
            g.setStroke(oldStroke);
        }
    }
}
