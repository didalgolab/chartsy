/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.ui.chart.graphic.GraphicInteractor;
import one.chartsy.ui.chart.graphic.PolyPointsEditInteractor;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * The simple selection object for all graphic objects, represented by a set of
 * points or point segments.
 * <p>
 * The {@code PolyPointsSelection} can be used as a selection for any graphic
 * object that implement the {@link PolyPointsAware} interface. The
 * {@code PolyPointsSelection} extends the {@link HandlesSelection} and draws
 * handles corresponding the points exposed by the {@code PolyPointsAware}
 * interface. The drawn handles can be dragged with the mouse pointer, and the
 * new established handles positions are set back to the graphic object through
 * the same {@code PolyPointsAware} interface.
 * 
 * @author Mariusz Bernacki
 *
 */
public class PolyPointsSelection extends HandlesSelection {
    /** The selected poly-points graphic object. */
    private final PolyPointsAware graphic;
    
    /**
     * Creates a new selection object corresponding to the given {@code graphic}
     * object. The {@code graphic} must be the {@link Annotation} and implement
     * the {@link PolyPointsAware} interface.
     * 
     * @param graphic
     *            the selected poly-points graphic object
     * @throws NullPointerException
     *             if the specified {@code graphic} is {@code null}
     */
    public <T extends Annotation & PolyPointsAware> PolyPointsSelection(T graphic) {
        super(graphic);
        this.graphic = graphic;
    }
    
    
    @Override
    public Annotation copy() {
        return copyImpl();
    }
    
    @SuppressWarnings("unchecked")
    private <T extends Annotation & PolyPointsAware> PolyPointsSelection copyImpl() {
        return new PolyPointsSelection((T) getGraphic().copy());
    }
    
    /**
     * Returns the rectangle on which perimeter handles are located.
     * <p>
     * The default implementation from the
     * {@link PolyPointsSelection#getHandleRoutingRect(CoordinateSystem)} gives
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
        return graphic.getPointCount();
    }
    
    @Override
    public Point2D getHandle(int index, CoordinateSystem coords) {
        return graphic.getPoint(index, coords);
    }
    
    @Override
    public Class<? extends GraphicInteractor> getDefaultInteractorType() {
        return PolyPointsEditInteractor.class;
    }
}
