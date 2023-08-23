/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import java.awt.geom.Rectangle2D;

/**
 * The interface used by annotation graphics that expose their definition rectangle.
 * 
 * @author Mariusz Bernacki
 *
 */
public interface RectangularShapeAware {
    
    /**
     * Gets the copy of the definition rectangle. The returned rectangle may be
     * further modified by the caller, thus the implementation should never
     * return a cached or internal rectangle instance.
     * 
     * @return the definition rectangle
     */
    Rectangle2D getDefinitionFrame(CoordinateSystem coords);
    
    /**
     * Sets the new definition rectangle.
     * 
     * @param rect
     *            the new definition rectangle to set
     */
    void setDefinitionFrame(Rectangle2D rect, CoordinateSystem coords);
    
    /**
     * Sets the new definition rectangle.
     * 
     * @param p1
     *            the starting point of the rectangle diagonal
     * @param p1
     *            the ending point of the rectangle diagonal
     */
    void setDefinitionFrame(ChartPoint p1, ChartPoint p2);
    
}
