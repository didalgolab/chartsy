/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.internal;

import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 *
 * @author Mariusz Bernacki
 */
public class CoordCalc
{
    
    protected CoordCalc()
    {}
    
    public static Line2D.Double line(Point2D p1, Point2D p2)
    {
        return new Line2D.Double(p1, p2);
    }
    
    public static Line2D.Double line(double x1, double y1, double x2, double y2)
    {
        return new Line2D.Double(x1, y1, x2, y2);
    }
    
    // TODO: [MB] remove this method it is inefficient
    public static Rectangle2D.Double rectangle(double x, double y, double w, double h)
    {
        return new Rectangle2D.Double(x, y, w, h);
    }
    
}
