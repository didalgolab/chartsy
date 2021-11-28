/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.plot;

import java.awt.Graphics2D;
import java.awt.Paint;

/**
 * Defines a simple graphic shape.
 * 
 * @author Mariusz Bernacki
 *
 */
@FunctionalInterface
public interface Marker {
    
    /**
     * Draws the marker of the specified {@code size} at the given {@code (x,y)}
     * position using the given graphics context for drawing.
     * 
     * @param g
     *            the graphics context on which to draw
     * @param x
     *            the x-coordinate of the marker
     * @param y
     *            the y-coordinate of the marker
     * @param size
     *            the size of the marker
     * @param style
     *            the style used to draw the marker
     */
    void draw(Graphics2D g, int x, int y, int size, Paint style);
    
    
    /**
     * The invisible marker.  It's
     * {@link #draw(Graphics2D, int, int, int, Paint) draw} method does nothing.
     */
    Marker NONE = (g, x, y, size, style) -> {};
    
    /**
     * Constructs a marker larger twice than the original one.
     * <p>
     * The default implementation is equivalent to {@code larger(2f)}.
     * 
     * @return the marker larger by default factor than the original one.
     */
    default Marker larger() {
        return larger(2f);
    }
    
    /**
     * Returns a new marker with {@code size} larger than the current marker's
     * size by the specified magnification {@code factor}.
     * <p>
     * The {@code factor} argument must be positive, i.e. {@code > 0}. If the
     * factor is less than {@code 1.0} the returned marker is actually smaller
     * than the original one.
     * 
     * @param factor
     *            the factor by which the desired marker is magnified
     * @return the marker larger larger than the original by the specified
     *         {@code factor}
     * @throws IllegalArgumentException
     *             if {@code factor <= 0}
     */
    default Marker larger(float factor) {
        if (factor <= 0)
            throw new IllegalArgumentException("factor must be positive");
        return (g, x, y, size, style) -> draw(g, x, y, Math.round(size * factor), style);
    }
}
