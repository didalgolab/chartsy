package one.chartsy.ui.chart;

import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

import one.chartsy.core.event.ListenerList;

/**
 * The global interactor that allows the user to specify a diagonal or a
 * rectangular region from the diagonal in a managed view by dragging the mouse.
 * 
 * @author Mariusz Bernacki
 *
 */
public class DiagonalDragInteractor extends OrganizedViewInteractor {

    /** The drag starting point, may be {@code null}. */
    private Point2D startPoint;
    /** The listeners receiving current drag events. */
    private final ListenerList<DiagonalDragListener> diagonalDragListeners = ListenerList.of(DiagonalDragListener.class);
    
    
    public DiagonalDragInteractor() {
    }
    
    /**
     * Constructs a new {@code DiagonalDragInteractor} using the specified
     * {@code listener} for listening of future drag events.
     * 
     * @param listener
     *            the diagonal drag listener
     */
    public DiagonalDragInteractor(DiagonalDragListener listener) {
        addDiagonalDragListener(listener);
    }
    
    public void addDiagonalDragListener(DiagonalDragListener listener) {
        diagonalDragListeners.addListener(listener);
    }
    
    public void removeDiagonalDragListener(DiagonalDragListener listener) {
        diagonalDragListeners.removeListener(listener);
    }
    
    @Override
    public void processMouseEvent(MouseEvent e) {
        int id = e.getID();
        if (startPoint == null && id == MouseEvent.MOUSE_PRESSED) {
            // Save the press point
            startPoint = e.getPoint();
            // Emit the first diagonal dragging event
            diagonalDragListeners.fire().diagonalDragging(new DiagonalDragEvent(this, startPoint, e));
        } else if (startPoint != null && id == MouseEvent.MOUSE_RELEASED) {
            // Emit the last, diagonal drag completion event
            diagonalDragListeners.fire().diagonalDragged(new DiagonalDragEvent(this, startPoint, e));
            // Discard the previously saved press point
            startPoint = null;
            // Detach the interactor from the view
            getOrganizedView().popInteractor();
        }
    }
    
    @Override
    public void processMouseMotionEvent(MouseEvent e) {
        if (startPoint != null && e.getID() == MouseEvent.MOUSE_DRAGGED) {
            // Emit the ongoing diagonal dragging event
            diagonalDragListeners.fire().diagonalDragging(new DiagonalDragEvent(this, startPoint, e));
        }
    }
}
