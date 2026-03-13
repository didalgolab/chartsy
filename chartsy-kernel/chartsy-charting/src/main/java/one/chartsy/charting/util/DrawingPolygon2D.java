package one.chartsy.charting.util;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/// Adapts caller-owned polygon coordinate arrays to a transient [Shape] view for immediate painting.
///
/// `PlotStyle` uses this type on anti-aliased polyline, polygon-fill, and point-plot paths to avoid
/// allocating a fresh polygon object for each paint call. [#getInstance(double[], double[], int, boolean)]
/// returns a JVM-wide singleton, so callers must synchronize on [#getClassLock()] from configuration
/// through the terminal Java2D draw or fill call.
///
/// The wrapper retains both coordinate arrays and reuses one [PathIterator]. Neither the shape nor an
/// iterator obtained from it remains valid after the next call that reconfigures the singleton.
/// Accurate behavior is limited to path iteration; containment, intersection, and bounds methods
/// return placeholder values and should not be used for hit testing, clipping, or bounds-driven paint
/// calculations.
public final class DrawingPolygon2D implements Shape {
    
    /// Streams the coordinates of the currently configured polygon singleton.
    ///
    /// The iterator reads directly from the enclosing [DrawingPolygon2D] arrays and is reset in place
    /// for each [DrawingPolygon2D#getPathIterator(AffineTransform)] call. Consumers must finish
    /// iterating before the singleton is reconfigured.
    private final class PIterator implements PathIterator {
        private int index;
        private AffineTransform transform;
        
        private PIterator() {
        }
        
        void setTransform(AffineTransform tx) {
            AffineTransform candidate = tx;
            if (candidate != null)
                if (candidate.isIdentity())
                    candidate = null;
            transform = candidate;
            index = 0;
        }
        
        @Override
        public int currentSegment(double[] coords) {
            if (DrawingPolygon2D.this.closed)
                if (index >= DrawingPolygon2D.this.pointCount)
                    return PathIterator.SEG_CLOSE;
            coords[0] = DrawingPolygon2D.this.xCoords[index];
            coords[1] = DrawingPolygon2D.this.yCoords[index];
            if (transform != null)
                transform.transform(coords, 0, coords, 0, 1);
            return (index != 0) ? PathIterator.SEG_LINETO : PathIterator.SEG_MOVETO;
        }
        
        @Override
        public int currentSegment(float[] coords) {
            if (DrawingPolygon2D.this.closed)
                if (index >= DrawingPolygon2D.this.pointCount)
                    return PathIterator.SEG_CLOSE;
            coords[0] = (float) DrawingPolygon2D.this.xCoords[index];
            coords[1] = (float) DrawingPolygon2D.this.yCoords[index];
            if (transform != null)
                transform.transform(coords, 0, coords, 0, 1);
            return (index != 0) ? PathIterator.SEG_LINETO : PathIterator.SEG_MOVETO;
        }
        
        @Override
        public int getWindingRule() {
            return PathIterator.WIND_NON_ZERO;
        }
        
        @Override
        public boolean isDone() {
            return (!DrawingPolygon2D.this.closed)
                    ? index >= DrawingPolygon2D.this.pointCount
                    : index > DrawingPolygon2D.this.pointCount;
        }
        
        @Override
        public void next() {
            index++;
        }
    }
    
    private static DrawingPolygon2D INSTANCE = null;
    private static final Object CLASS_LOCK = new Object();
    
    /// Returns the monitor guarding the shared polygon singleton.
    ///
    /// Callers must hold this monitor while borrowing the instance and while any Java2D operation may
    /// still pull coordinates from the shape or its iterator.
    ///
    /// @return the monitor that serializes acquisition and use of the shared singleton
    public static Object getClassLock() {
        return DrawingPolygon2D.CLASS_LOCK;
    }
    
    /// Configures the shared polygon wrapper for a single immediate paint operation.
    ///
    /// The arrays are retained without copying, and this method validates only that both array
    /// references are non-`null`. Callers are responsible for supplying at least `pointCount` usable
    /// entries in each array and for keeping those entries stable until painting completes.
    ///
    /// @param xCoords x coordinates read directly by the returned shape
    /// @param yCoords matching y coordinates read directly by the returned shape
    /// @param pointCount number of coordinate pairs to expose from the arrays
    /// @param closed whether the iterator should emit a closing segment after the last point
    /// @return the shared shape wrapper configured to read the supplied arrays
    /// @throws IllegalArgumentException if either coordinate array is `null`
    public static DrawingPolygon2D getInstance(double[] xCoords, double[] yCoords, int pointCount, boolean closed) {
        if (xCoords != null)
            if (yCoords != null) {
                if (DrawingPolygon2D.INSTANCE == null)
                    DrawingPolygon2D.INSTANCE = new DrawingPolygon2D();
                return DrawingPolygon2D.INSTANCE.configure(xCoords, yCoords, pointCount, closed);
            }
        throw new IllegalArgumentException("Null coordinates");
    }
    
    private double[] xCoords;
    
    private double[] yCoords;
    
    private int pointCount;
    
    private boolean closed;
    
    private final DrawingPolygon2D.PIterator iterator;
    
    private DrawingPolygon2D() {
        iterator = new DrawingPolygon2D.PIterator();
    }
    
    private DrawingPolygon2D configure(double[] xCoords, double[] yCoords, int pointCount, boolean closed) {
        this.xCoords = xCoords;
        this.yCoords = yCoords;
        this.pointCount = pointCount;
        this.closed = closed;
        return this;
    }
    
    /// Returns `false` for every point query.
    ///
    /// This wrapper does not model polygon membership; it exists only to stream path segments into the
    /// Java2D renderer.
    @Override
    public boolean contains(double x, double y) {
        return false;
    }
    
    /// Returns `false` for every rectangle containment query.
    ///
    /// Callers needing real geometry checks should use a different [Shape] implementation.
    @Override
    public boolean contains(double x, double y, double width, double height) {
        return false;
    }
    
    @Override
    public boolean contains(Point2D point) {
        return this.contains(point.getX(), point.getY());
    }
    
    @Override
    public boolean contains(Rectangle2D rect) {
        return this.contains(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
    }
    
    /// Returns an empty integer bounds rectangle.
    ///
    /// This shared wrapper intentionally avoids advertising stable geometric bounds.
    @Override
    public Rectangle getBounds() {
        return new Rectangle();
    }
    
    /// Returns an empty floating-point bounds rectangle.
    ///
    /// This shared wrapper intentionally avoids computing or caching geometric bounds.
    @Override
    public Rectangle2D getBounds2D() {
        return new Rectangle2D.Float();
    }
    
    /// Resets and returns the reusable iterator over the current polygon path.
    ///
    /// Each invocation reinitializes the same iterator instance. Consumers must not retain the
    /// returned iterator beyond the synchronized paint section that obtained this shape.
    @Override
    public PathIterator getPathIterator(AffineTransform tx) {
        iterator.setTransform(tx);
        return iterator;
    }
    
    /// Returns the same reusable iterator as [#getPathIterator(AffineTransform)].
    ///
    /// `flatness` is ignored because the polygon path already consists only of straight-line segments.
    @Override
    public PathIterator getPathIterator(AffineTransform tx, double flatness) {
        return this.getPathIterator(tx);
    }
    
    /// Returns `true` for every rectangle intersection query.
    ///
    /// The permissive answer keeps rendering code from discarding the path prematurely, but it is not
    /// a real intersection test.
    @Override
    public boolean intersects(double x, double y, double width, double height) {
        return true;
    }
    
    @Override
    public boolean intersects(Rectangle2D rect) {
        return this.intersects(rect.getX(), rect.getY(), rect.getWidth(), rect.getHeight());
    }
}
