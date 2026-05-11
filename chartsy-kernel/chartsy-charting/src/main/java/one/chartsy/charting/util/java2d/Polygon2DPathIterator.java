package one.chartsy.charting.util.java2d;

import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;

/// [PathIterator] for [Polygon2D].
///
/// The iterator emits one `SEG_MOVETO`, one `SEG_LINETO` for each remaining vertex, and finally a
/// synthetic `SEG_CLOSE` when the polygon has been fully traversed.
///
/// The iterator reads the polygon's live vertices on demand, so mutations performed through
/// [Polygon2D#getPoint(int)] or [Polygon2D#getPoints()] are reflected in later segments that have
/// not yet been emitted.
class Polygon2DPathIterator implements PathIterator {
    private final Polygon2D polygon;
    private final AffineTransform transform;
    private int index;
    
    Polygon2DPathIterator(Polygon2D polygon, AffineTransform transform) {
        this.polygon = polygon;
        this.transform = transform;
        index = 0;
    }

    /// Returns the current polygon vertex and copies it into `coords`.
    private Point2D writeCurrentPoint(double[] coords) {
        Point2D point = polygon.getPoint(index);
        coords[0] = point.getX();
        coords[1] = point.getY();
        if (transform != null)
            transform.transform(coords, 0, coords, 0, 1);
        return point;
    }

    /// Returns the current polygon vertex and copies it into `coords`.
    private Point2D writeCurrentPoint(float[] coords) {
        Point2D point = polygon.getPoint(index);
        coords[0] = (float) point.getX();
        coords[1] = (float) point.getY();
        if (transform != null)
            transform.transform(coords, 0, coords, 0, 1);
        return point;
    }
    
    @Override
    public int currentSegment(double[] coords) {
        if (index >= polygon.getNumberOfPoints())
            return SEG_CLOSE;
        writeCurrentPoint(coords);
        return index == 0 ? SEG_MOVETO : SEG_LINETO;
    }
    
    @Override
    public int currentSegment(float[] coords) {
        if (index >= polygon.getNumberOfPoints())
            return SEG_CLOSE;
        writeCurrentPoint(coords);
        return index == 0 ? SEG_MOVETO : SEG_LINETO;
    }
    
    /// Returns [PathIterator#WIND_EVEN_ODD], matching `Polygon2D`'s historical path semantics.
    @Override
    public int getWindingRule() {
        return WIND_EVEN_ODD;
    }
    
    @Override
    public boolean isDone() {
        return index > polygon.getNumberOfPoints();
    }
    
    @Override
    public void next() {
        index++;
    }
}
