/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.ui.chart.graphic.GraphicInteractor;
import one.chartsy.ui.chart.graphic.ReshapeGraphicInteractor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * A selection object that draws several handles around the boundary of a
 * selected graphic.
 * 
 * @author Mariusz Bernacki
 *
 */
public abstract class HandlesSelection extends Selection {
    /** The size of handles in pixels. */
    private int handleSize = SysParams.ANNOTATION_HANDLE_SIZE.intValue();
    /** The index of the active handle in this selection. */
    private int activeHandle = -1;
    
    
    /**
     * Constructs a new handles selection object for the specified
     * {@code graphic}.
     * 
     * @param graphic
     *            the graphic object which the selection is created for
     */
    protected HandlesSelection(Annotation graphic) {
        super(graphic);
    }
    
    /**
     * Returns the bounding rectangle of this object.
     */
    @Override
    public Rectangle2D getBoundingBox(CoordinateSystem coords) {
        int h = getHandleSize();
        Rectangle2D rect = getGraphic().getBoundingBox(coords);
        rect.setRect(rect.getX() - h/2.0, rect.getY() - h/2.0, rect.getWidth() + h, rect.getHeight() + h);
        return rect;
    }
    
    @Override
    public void paint(Graphics2D g, CoordinateSystem coords, int width, int height) {
        g.setColor(Color.black);
        startGetHandleBatch(coords);
        try {
            int count = getHandleCount();
            int activeHandle = getActiveHandle();
            for (int i = 0; i < count; i++) {
                Point2D handle = getHandle(i, coords);
                boolean active = (i == activeHandle);
                drawHandle(g, handle.getX(), handle.getY(), active);
            }
        } finally {
            endGetHandleBatch();
        }
    }
    
    protected void drawHandle(Graphics2D g, double x, double y, boolean active) {
        int rx = (int) Math.round(x);
        int ry = (int) Math.round(y);
        int handleSize = getHandleSize();
        if (active)
            g.fillRect(rx - handleSize/2, ry - handleSize/2, handleSize + 1, handleSize + 1);
        else
            g.drawRect(rx - handleSize/2, ry - handleSize/2, handleSize, handleSize);
    }
    
    /**
     * Called by the framework before issuing multiple
     * {@link #getHandle(int, CoordinateSystem)} calls with the specified
     * coordinate system.
     * <p>
     * A subclass may override this method in order to do preparations so that
     * further calls to the {@link #getHandle(int, CoordinateSystem)} method can
     * be as fast as possible.
     * 
     * @param coords
     *            the coordinate system used to display this object
     */
    public void startGetHandleBatch(CoordinateSystem coords) {
    }
    
    /**
     * Called by the framework after finishing the series of calls to the
     * {@link #getHandle(int, CoordinateSystem)} method initiated by the
     * {@link #startGetHandleBatch(CoordinateSystem)} method.
     */
    public void endGetHandleBatch() {
    }
    
    /**
     * Returns the size of handles in pixels.
     * 
     * @return the size of handles
     */
    public int getHandleSize() {
        return handleSize;
    }
    
    /**
     * Sets the new size for handles in pixels.
     * 
     * @param handleSize the handle size to set
     */
    public void setHandleSize(int handleSize) {
        this.handleSize = handleSize;
    }
    
    /**
     * Returns the number of handles for this object.
     * 
     * @return the number of handles
     */
    public abstract int getHandleCount();
    
    /**
     * Returns the location of a handle.
     * <p>
     * The specified handle index must be in the range from {@code 0}
     * (inclusive) to {@link #getHandleCount() handle count} (exclusive).
     * 
     * @param index
     *            the index of the handle
     * @param coords
     *            the coordinate system used to display the graphic object
     * @return the handle location
     * @throws IndexOutOfBoundsException
     *             if the index {@code i} is outside of allowable range
     */
    public abstract Point2D getHandle(int index, CoordinateSystem coords);
    
    /**
     * Searches for a handle located at the given {@code point}.
     * <p>
     * The method returns the index of the handle at the given {@code point}
     * using the specified coordinate system. The method wraps internal search
     * loop between the {@link #startGetHandleBatch(CoordinateSystem)} and
     * {@link #endGetHandleBatch()} method calls.
     * 
     * @param point
     *            the point to be searched for
     * @param coords
     *            the coordinate system used to display this selection object
     * @return the index of the handle, or {@code -1} if no handle could be
     *         found
     */
    public int getHandle(Point2D point, CoordinateSystem coords) {
        startGetHandleBatch(coords);
        try {
            int halfSize = getHandleSize()/2 + 1;
            int count = getHandleCount();
            double x = point.getX(), y = point.getY();
            for (int index = 0; index < count; index++) {
                Point2D handle = getHandle(index, coords);
                if (Math.abs(x - handle.getX()) < halfSize && Math.abs(y - handle.getY()) < halfSize)
                    return index;
            }
        } finally {
            endGetHandleBatch();
        }
        return -1;
    }
    
    @Override
    public boolean contains(double x, double y, CoordinateSystem coords) {
        return super.contains(x, y, coords) && 0 <= getHandle(new Point2D.Double(x, y), coords);
    }
    
    @Override
    public Class<? extends GraphicInteractor> getDefaultInteractorType() {
        return ReshapeGraphicInteractor.class;
    }
    
    /**
     * Returns the index of the active handle. The active handle in a
     * {@code HandlesSelection} object is an at most one handle which is
     * currently or was recently pressed by a user with intent to adjust the
     * selection object shape or location.
     * <p>
     * The active handle is drawn distinctly by the
     * {@link #drawHandle(Graphics2D, double, double, boolean)} method.<br>
     * The method returns {@code -1} if no handles are currently active.
     * 
     * @return the index of the active handle
     */
    public int getActiveHandle() {
        return activeHandle;
    }
    
    /**
     * Changes the active handle.
     * 
     * @param index
     *            the new index of the active handle
     * @see #getActiveHandle()
     */
    public void setActiveHandle(int index) {
        this.activeHandle = index;
    }
}
