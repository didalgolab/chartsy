package one.chartsy.ui.chart;

import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.EventObject;

/**
 * An event that is fired by the {@code DiagonalDragInteractor} when the user
 * continues to drag the mouse.
 * <p>
 * While dragging only the start and end drag points are delivered to listeners
 * in this event, hence the name of this event.
 * 
 * @author Mariusz Bernacki
 *
 */
public class DiagonalDragEvent extends EventObject {
    /** The starting point of the current drag behavior. */
    private final Point2D startPoint;
    /** The ending point of the current drag behavior. */
    private final Point2D endPoint;
    /** The input event (usually {@code MouseMotionEvent}) associated with the drag. */
    private final InputEvent event;
    
    
    /**
     * Creates a new event with the specified parameters.
     * 
     * @param source
     *            the event source
     * @param startPoint
     *            the drag starting point
     * @param event
     *            the originating drag event
     */
    public DiagonalDragEvent(Object source, Point2D startPoint, MouseEvent event) {
        this(source, startPoint, event.getPoint(), event);
    }
    
    /**
     * Creates a new event with the specified parameters.
     * 
     * @param source
     *            the event source
     * @param startPoint
     *            the drag starting point
     * @param endPoint
     *            the drag ending point
     * @param event
     *            the originating drag event
     */
    public DiagonalDragEvent(Object source, Point2D startPoint, Point2D endPoint, InputEvent event) {
        super(source);
        this.startPoint = startPoint;
        this.endPoint = endPoint;
        this.event = event;
    }
    
    public Point2D getStartPoint() {
        return startPoint;
    }
    
    public Point2D getEndPoint() {
        return endPoint;
    }

    public InputEvent getEvent() {
        return event;
    }

    /**
     * Returns the framing rectangle determined by the start and end points of
     * this event.
     * 
     * @return the rectangle from the drag diagonal
     */
    public Rectangle2D getRectangle() {
        Rectangle rect = new Rectangle();
        rect.setFrameFromDiagonal(getStartPoint(), getEndPoint());
        return rect;
    }
}
