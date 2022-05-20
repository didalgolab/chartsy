/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.plot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;

import one.chartsy.core.Range;
import one.chartsy.data.DoubleSeries;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.ChartData;
import one.chartsy.ui.chart.data.VisibleValues;

public class FillPlot extends AbstractTimeSeriesPlot {
    
    private final double f, t;
    private final boolean upper;
    
    
    public FillPlot(DoubleSeries values, double f, double t, boolean upper, Color color) {
        super(values.values(), color);
        this.f = f;
        this.t = t;
        this.upper = upper;
    }
    
    @Override
    public void paint(Graphics2D g, ChartContext cf, Range range, Rectangle bounds) {
        VisibleValues values = getVisibleData(cf);
        if (values != null)
            paintFill(g, cf, range, bounds, values, f, t);
    }
    
    protected void paintFill(Graphics2D g, ChartContext cf, Range range, Rectangle bounds, VisibleValues dataset, double f, double t) {
        double min = Math.min(f, t);
        double max = Math.max(f, t);
        
        if (upper) {
            max = Math.max(max, range.getMax());
        } else {
            min = Math.max(f, t);
            max = Math.min(f, t);
        }
        
        ChartData cd = cf.getChartData();
        int count = dataset.getLength();
        
        double x, dx, y = cd.getY(min, bounds, range, false);
        Range fillRange = Range.of(min, max);
        
        Color oldColor = g.getColor();
        g.setColor(primaryColor);
        GeneralPath gp = null;
        Point2D.Double p1 = new Point2D.Double();
        Point2D.Double p2 = new Point2D.Double();
        for (int i = 1; i < count; i++) {
            double value1 = dataset.getValueAt(i - 1);
            double value = dataset.getValueAt(i);
            if (value1 == value1 && value == value) {
                
                p1 = cd.getPoint(i - 1, value1, range, bounds, false, p1);
                p2 = cd.getPoint(i, value, range, bounds, false, p2);
                
                if (!fillRange.contains(value1) && fillRange.contains(value)) {
                    dx = (y - p1.getY()) / (p2.getY() - p1.getY());
                    x = p1.getX() + dx * (p2.getX() - p1.getX());
                    
                    if (gp == null)
                        gp = new GeneralPath();
                    else {
                        gp.lineTo(p2.getX(), y);
                        gp.closePath();
                        g.fill(gp);
                        gp.reset();
                    }
                    gp.moveTo(x, y);
                    gp.lineTo(p2.getX(), p2.getY());
                } else if (fillRange.contains(value1) && fillRange.contains(value)) {
                    if (gp == null) {
                        gp = new GeneralPath();
                        gp.moveTo(p1.getX(), y);
                        gp.lineTo(p1.getX(), p1.getY());
                    }
                    gp.lineTo(p2.getX(), p2.getY());
                } else if (fillRange.contains(value1) && !fillRange.contains(value)) {
                    dx = (y - p1.getY()) / (p2.getY() - p1.getY());
                    x = p1.getX() + dx * (p2.getX() - p1.getX());
                    
                    if (gp == null) {
                        gp = new GeneralPath();
                        gp.moveTo(p1.getX(), p1.getY());
                    }
                    gp.lineTo(x, y);
                    gp.lineTo(p1.getX(), y);
                    gp.closePath();
                    g.fill(gp);
                    gp = null;
                }
            }
        }
        if (gp != null) {
            gp.lineTo(p2.getX(), y);
            gp.closePath();
            g.fill(gp);
        }
        g.setColor(oldColor);
    }
}
