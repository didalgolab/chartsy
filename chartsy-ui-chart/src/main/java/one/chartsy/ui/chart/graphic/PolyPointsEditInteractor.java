/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.graphic;

import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.annotation.GraphicBag;

import java.awt.AWTEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

/**
 * The graphic interactor used to edit graphic objects that implement the
 * {@link PolyPointsAware} interface.
 * 
 * @author Mariusz Bernacki
 *
 */
public class PolyPointsEditInteractor implements GraphicInteractor {
    /** The location of the handle that is currently active (pressed). */
    private int anchorIndex = -1;
    
    
    /**
     * Handles the mouse button press events.
     * 
     * @param e
     *            the mouse event
     * @param selection
     *            the selection object at which the mouse press event occurred
     * @param context
     *            the context corresponding to the graphic view that generated
     *            the event
     * @return {@code true} if the event was successfully handled by this
     *         interactor, {@code false} otherwise
     */
    protected boolean mousePressed(MouseEvent e, PolyPointsSelection selection, OrganizedViewInteractorContext context) {
        CoordinateSystem coords = context.getCoordinateSystem();
        anchorIndex = selection.getHandle(e.getPoint(), coords);
        selection.setActiveHandle(anchorIndex);
        return (anchorIndex >= 0);
    }
    
    /**
     * Handles the mouse button drag events.
     * 
     * @param e
     *            the mouse event
     * @param selection
     *            the selection object for which the mouse drag event occurred
     * @param context
     *            the context corresponding to the graphic view that generated
     *            the event
     * @return {@code true} if the event was successfully handled by this
     *         interactor, {@code false} otherwise
     */
    protected boolean mouseDragged(MouseEvent e, PolyPointsSelection selection, OrganizedViewInteractorContext context) {
        if (anchorIndex >= 0) {
            movePoint(selection, e.getPoint(), context.getCoordinateSystem());
            return true;
        }
        return false;
    }
    
    /**
     * Handles the mouse button release events.
     * 
     * @param e
     *            the mouse event
     * @param selection
     *            the selection object for which the mouse release event
     *            occurred
     * @param context
     *            the context corresponding to the graphic view that generated
     *            the event
     * @return {@code true} if the event was successfully handled by this
     *         interactor, {@code false} otherwise
     */
    protected boolean mouseReleased(MouseEvent e, PolyPointsSelection selection, OrganizedViewInteractorContext context) {
        anchorIndex = -1;
        return false;
    }
    
    /**
     * Called when the point was moved as a result of dragging the handle with
     * the mouse pointer or other user interaction supported by the GUI system.
     * 
     * @param selection
     *            the selection object
     * @param point
     *            the new point location
     * @param coords
     *            the coordinate system used to display the graphic object
     */
    protected void movePoint(PolyPointsSelection selection, Point2D point, CoordinateSystem coords) {
        Annotation graphic = selection.getGraphic();
        if (selection.getGraphic() instanceof PolyPointsAware) {
            PolyPointsAware polyPoints = (PolyPointsAware) selection.getGraphic();
            
            GraphicBag bag = graphic.getGraphicBag();
            if (bag != null)
                bag.applyManipulator(selection, null, (sel, nul) -> polyPoints.movePoint(anchorIndex, point, coords));
        }
    }
    
    @Override
    public boolean processEvent(AWTEvent event, Annotation graphic, OrganizedViewInteractorContext context) {
        if (event instanceof MouseEvent) {
            if (!(graphic instanceof PolyPointsSelection))
                return false;
            
            PolyPointsSelection selection = (PolyPointsSelection) graphic;
            MouseEvent e = (MouseEvent) event;
            switch (e.getID()) {
            case MouseEvent.MOUSE_PRESSED:
                if (e.getButton() == MouseEvent.BUTTON1)
                    return mousePressed(e, selection, context);
                break;
            case MouseEvent.MOUSE_DRAGGED:
                return mouseDragged(e, selection, context);
            case MouseEvent.MOUSE_RELEASED:
                return mouseReleased(e, selection, context);
            }
        }
        return false;
    }
}
