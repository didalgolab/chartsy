/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.graphic;

import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.annotation.GraphicBag;

import java.awt.AWTEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;


public class ReshapeGraphicInteractor implements GraphicInteractor {
    /** The location of the handle that is currently active (pressed). */
    private Annotation.AnchorLocation anchor;
    /** The bounding box or the definition rectangle of the selected object. */
    private Rectangle2D bbox;
    /** Stores the starting point of the bbox diagonal. */
    private final Point2D.Double bboxStart = new Point2D.Double();
    /** Stores the ending point of the bbox diagonal. */
    private final Point2D.Double bboxEnd = new Point2D.Double();
    
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
    protected boolean mousePressed(MouseEvent e, RectangularSelection selection, OrganizedViewInteractorContext context) {
        CoordinateSystem coords = context.getCoordinateSystem();
        int index = selection.getHandle(e.getPoint(), coords);
        selection.setActiveHandle(index);
        if (index >= 0) {
            bbox = getBoundingBox(selection.getGraphic(), coords);
            bboxStart.setLocation(bbox.getX(), bbox.getY());
            bboxEnd.setLocation(bbox.getMaxX(), bbox.getMaxY());
            anchor = selection.getHandleLocation(index);
            return true;
        }
        return false;
    }
    
    /**
     * Returns the rectangle that defines the bounding box of the annotation
     * graphic.
     * 
     * @param graphic
     *            the annotation graphic
     * @param coords
     *            the coordinate system used to display the annotation graphic
     * @return the definition rectangle
     */
    protected Rectangle2D getBoundingBox(Annotation graphic, CoordinateSystem coords) {
        if (graphic instanceof RectangularShapeAware)
            return ((RectangularShapeAware) graphic).getDefinitionFrame(coords);
        else
            return graphic.getBoundingBox(coords);
    }
    
    protected boolean mouseDragged(MouseEvent e, RectangularSelection selection, OrganizedViewInteractorContext context) {
        if (anchor != null) {
            switch (anchor) {
            case TOP_LEFT:
                bboxStart.setLocation(e.getX(), e.getY());
                break;
            case TOP_RIGHT:
                bboxEnd.x = e.getX();
                // fall through
            case TOP:
                bboxStart.y = e.getY();
                break;
            case RIGHT:
                bboxEnd.x = e.getX();
                break;
            case BOTTOM_RIGHT:
                bboxEnd.setLocation(e.getX(), e.getY());
                break;
            case BOTTOM_LEFT:
                bboxStart.x = e.getX();
                // fall through
            case BOTTOM:
                bboxEnd.y = e.getY();
                break;
            case LEFT:
                bboxStart.x = e.getX();
                break;
            default:
                throw new IllegalArgumentException("Anchor: " + anchor);
            }
            
            bbox.setFrameFromDiagonal(bboxStart, bboxEnd);
            reshapeGraphic(selection, bbox, context.getCoordinateSystem());
            return true;
        }
        return false;
    }
    
    protected boolean mouseReleased(MouseEvent e, RectangularSelection selection, OrganizedViewInteractorContext context) {
        anchor = null;
        return false;
    }
    
    protected void reshapeGraphic(RectangularSelection selection, Rectangle2D bbox, CoordinateSystem coords) {
        Annotation graphic = selection.getGraphic();
        if (selection.getGraphic() instanceof RectangularShapeAware rectGraphic) {

            GraphicBag bag = graphic.getGraphicBag();
            if (bag != null)
                bag.applyManipulator(selection, null, (sel, nul) -> rectGraphic.setDefinitionFrame(bbox, coords));
        }
    }
    
    @Override
    public boolean processEvent(AWTEvent event, Annotation graphic, OrganizedViewInteractorContext context) {
        if (event instanceof MouseEvent e) {
            if (!(graphic instanceof RectangularSelection selection))
                return false;

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
