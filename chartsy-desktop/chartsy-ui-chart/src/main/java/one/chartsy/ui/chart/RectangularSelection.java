/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import one.chartsy.util.Pair;

/**
 * The default selection object for graphic objects.
 * <p>
 * The {@code BoundsSelection} indicates that the corresponding graphic
 * object is selected by painting eight small handles on the outline of the
 * bounding rectangle of the graphic object. There are four handles drawn on
 * each corner of the bounding rectangle and four handles in the middle of each
 * edge.
 * 
 * @author Mariusz Bernacki
 *
 */
public class RectangularSelection extends HandlesSelection {
    
    /** The {@code routingRectCache} object cached for {@code getHandle} batch. */
    private Pair<CoordinateSystem, Rectangle2D> routingRectCache;
    /** Determines the subset of anchor visible on this selection. */
    private final List<AnchorLocation> anchors;
    
    /**
     * Creates a new selection object corresponding to the given {@code graphic}
     * object.
     * 
     * @param graphic
     *            the corresponding selected graphic object
     * @throws NullPointerException
     *             if the specified {@code graphic} is {@code null}
     */
    public RectangularSelection(Annotation graphic) {
        this(graphic, EnumSet.allOf(AnchorLocation.class));
    }
    
    public RectangularSelection(Annotation graphic, AnchorLocation... anchors) {
        this(graphic, Arrays.asList(anchors));
    }
    
    public RectangularSelection(Annotation graphic, Collection<AnchorLocation> anchors) {
        this(graphic, EnumSet.copyOf(anchors));
    }
    
    public RectangularSelection(Annotation graphic, EnumSet<AnchorLocation> anchors) {
        this(new ArrayList<>(anchors), graphic);
    }
    
    protected RectangularSelection(List<AnchorLocation> anchors, Annotation graphic) {
        super(graphic);
        this.anchors = anchors;
    }
    
    
    @Override
    public Annotation copy() {
        return new RectangularSelection(anchors, getGraphic().copy());
    }
    
    @Override
    public Rectangle2D getBoundingBox(CoordinateSystem coords) {
        int h = getHandleSize();
        Rectangle2D rect = getGraphic().getBoundingBox(coords);
        rect = rect.createUnion(getHandleRoutingRect(coords));
        rect.setRect(rect.getX() - h/2.0 - 0.5, rect.getY() - h/2.0 - 0.5, rect.getWidth() + h + 1.0, rect.getHeight() + h + 1.0);
        return rect;
    }
    
    @Override
    public void startGetHandleBatch(CoordinateSystem coords) {
        routingRectCache = Pair.of(coords, getHandleRoutingRect(coords));
    }
    
    @Override
    public void endGetHandleBatch() {
        routingRectCache = null;
    }
    
    /**
     * Returns the rectangle on which perimeter handles are located.
     * <p>
     * The default implementation from the
     * {@link RectangularSelection#getHandleRoutingRect(CoordinateSystem)} gives
     * simply the bounding rectangle of the corresponding {@link #getGraphic()
     * graphic object}.
     * 
     * @param coords
     *            the coordinate system used to display this graphic object
     * @return the rectangle for the handles locations
     */
    protected Rectangle2D getHandleRoutingRect(CoordinateSystem coords) {
        Rectangle2D rect = getGraphic().getBoundingBox(coords);
        rect.setRect(rect.getX(), rect.getY(), rect.getWidth() - 1, rect.getHeight() - 1);
        return rect;
    }
    
    @Override
    public int getHandleCount() {
        return anchors.size();
    }
    
    @Override
    public Point2D getHandle(int index, CoordinateSystem coords) {
        return getHandle(getHandleLocation(index), coords);
    }
    
    /**
     * Returns the direction of the handle at the specified {@code index}.
     * 
     * @param index
     *            the handle index
     * @return the handle direction
     */
    public AnchorLocation getHandleLocation(int index) {
        return anchors.get(index);
    }
    
    public Point2D getHandle(AnchorLocation anchor, CoordinateSystem coords) {
        Rectangle2D rect;
        
        // Obtain the routing rectangle, either from the cache or recompute
        Pair<CoordinateSystem, Rectangle2D> cache = routingRectCache;
        if (cache != null && cache.getLeft() == coords)
            rect = cache.getRight();
        else
            rect = getHandleRoutingRect(coords);
        
        // Obtain the target point from the routing rectangle
        return switch (anchor) {
            case TOP_LEFT     -> new Point2D.Double(rect.getMinX(), rect.getMinY());
            case TOP          -> new Point2D.Double(rect.getCenterX(), rect.getMinY());
            case TOP_RIGHT    -> new Point2D.Double(rect.getMaxX(), rect.getMinY());
            case RIGHT        -> new Point2D.Double(rect.getMaxX(), rect.getCenterY());
            case BOTTOM_RIGHT -> new Point2D.Double(rect.getMaxX(), rect.getMaxY());
            case BOTTOM       -> new Point2D.Double(rect.getCenterX(), rect.getMaxY());
            case BOTTOM_LEFT  -> new Point2D.Double(rect.getMinX(), rect.getMaxY());
            case LEFT         -> new Point2D.Double(rect.getMinX(), rect.getCenterY());
        };
    }
}
