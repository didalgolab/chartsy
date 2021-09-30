package one.chartsy.ui.chart;

import one.chartsy.ui.chart.components.AnnotationPanel;

import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public class SelectionMoveInteractor extends OrganizedViewInteractor {
    
    /** The drag starting point, may be {@null}. */
    private Point2D startPoint;
    
    private final AnnotationPanel panel;
    
    private List<Selection> annotations;
    
    private List<Queue<Point2D>> geometry;
    
    
    /**
     * Constructs a new {@code DiagonalDragInteractor} object.
     */
    public SelectionMoveInteractor(AnnotationPanel panel) {
        this.panel = panel;
    }
    
    @Override
    protected void detach() {
        super.detach();
        if (startPoint != null)
            dragCancelled();
    }
    
    protected void dragCancelled() {
        
    }
    
    private static Point2D queueAndGet(Queue<Point2D> queue, Point2D point) {
        queue.add(point);
        return point;
    }
    
    @Override
    public void processMouseEvent(MouseEvent e) {
        int id = e.getID();
        if (startPoint == null && id == MouseEvent.MOUSE_PRESSED) {
            // Copy current selections bounds
            CoordinateSystem coords = getOrganizedView().getCoordinateSystem();
            annotations = new ArrayList<>(panel.getSelections());
            geometry = new ArrayList<>(panel.getSelectionCount());
            panel.getModel().applyManipulator(annotations, null, (sel, prm) -> {
                Queue<Point2D> points = new ArrayDeque<>(4);
                sel.applyTransform(p -> queueAndGet(points, p), coords);
                geometry.add(points);
            });
            // Save the press point
            startPoint = e.getPoint();
        } else if (startPoint != null && id == MouseEvent.MOUSE_RELEASED) {
            // Discard the previously saved press point
            startPoint = null;
            // Clear selected graphic bounds if any
            annotations = null;
            geometry = null;
            // Detach the interactor from the view
            getOrganizedView().popInteractor();
        }
    }
    
    @Override
    public void processMouseMotionEvent(MouseEvent e) {
        if (startPoint != null && e.getID() == MouseEvent.MOUSE_DRAGGED) {
            double dx = e.getX() - startPoint.getX();
            double dy = e.getY() - startPoint.getY();
            
            CoordinateSystem coords = getOrganizedView().getCoordinateSystem();
            panel.getModel().applyManipulator(annotations, geometry, (object, points) -> {
                Queue<Point2D> pts = new ArrayDeque<>(points);
                object.applyTransform(p -> new Point2D.Double(pts.peek().getX() + dx, pts.poll().getY() + dy), coords);
            });
        }
    }
}
