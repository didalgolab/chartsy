/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import java.awt.Rectangle;
import java.awt.geom.Point2D;

public interface CoordinateSystem {
    
    Rectangle getBounds();
    
    Point2D transform(ChartPoint p);
    
    ChartPoint inverseTransform(double x, double y);
    
    default ChartPoint inverseTransform(Point2D p) {
        return inverseTransform(p.getX(), p.getY());
    }
    
    int getSlotIndex(long time);
}
