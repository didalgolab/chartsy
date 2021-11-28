/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart;

import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.Stroke;

/**
 * Defines a rendering style that controls the drawings appearance.
 * 
 * @author Mariusz Bernacki
 *
 */
public class PlotStyle {
    /** The {@code Stroke} for this style. */
    private Stroke stroke;
    /** The stroke paint of this style. */
    private Paint strokePaint;
    /** The fill paint of this style. */
    private Paint fillPaint;
    
    //	public static final Stroke DEFAULT_STROKE = 
    
    /**
     * Creates a default filled and stroked style.
     *
     */
    public PlotStyle() {
        //TODO
    }
    
    /**
     * Creates a stroked and filled style with the specified {@code Paint}'s for
     * stroke and fill.
     *
     */
    public PlotStyle(Paint strokePaint, Paint fillPaint) {
        this.strokePaint = strokePaint;
        this.fillPaint = fillPaint;
    }
    
    /**
     * Indicates whether this style uses a stroke when drawing.
     * 
     * @return {@code true} if using style while drawing, and {@code false}
     *         otherwise
     */
    public final boolean isStrokeOn() {
        return stroke != null;
    }
    
    /**
     * 
     * 
     * @param on
     * @return
     */
    //	public IlvStyle setStrokeOn(boolean on) {
    //		// TODO
    //	}
    
    /**
     * Draws the outline of the given shape with the current stroke.
     * 
     * @param g2 the graphics context
     * @param shape the shape to draw
     */
    public void draw(Graphics2D g2, Shape shape) {
        Paint old = g2.getPaint();
        try {
            if (stroke != null)
                g2.setStroke(stroke);
            g2.setPaint(strokePaint);
            g2.draw(shape);
        } finally {
            g2.setPaint(old);
        }
    }
    
    /**
     * Fills the interior of the given shape with the current paint.
     * 
     * @param g2 the graphics context
     * @param shape the shape to fill
     */
    public void fill(Graphics2D g2, Shape shape) {
        Paint old = g2.getPaint();
        try {
            g2.setPaint(fillPaint);
            g2.fill(shape);
        } finally {
            g2.setPaint(old);
        }
    }
    
    /**
     * @return the fillPaint
     */
    public Paint getFillPaint() {
        return fillPaint;
    }
    
    /**
     * @param fillPaint the fillPaint to set
     */
    public void setFillPaint(Paint fillPaint) {
        this.fillPaint = fillPaint;
    }
    
    /**
     * @return the strokePaint
     */
    public Paint getStrokePaint() {
        return strokePaint;
    }
    
    /**
     * @param strokePaint the strokePaint to set
     */
    public void setStrokePaint(Paint strokePaint) {
        this.strokePaint = strokePaint;
    }
}
