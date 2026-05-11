package one.chartsy.charting.util.java2d;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;

/// Abstract closed polygon [Shape] backed by an ordered vertex list.
///
/// The polygon is implicitly closed between the last and first vertex. Concrete subclasses differ
/// only in the precision of the stored point objects: [Polygon2D.Double] uses
/// [Point2D.Double] vertices and [Polygon2D.Float] uses [Point2D.Float] vertices.
///
/// The constructors of the concrete subclasses copy their input arrays, but [#getPoint(int)] and
/// [#getPoints()] expose the live stored vertex objects. Mutating those returned objects mutates
/// the polygon itself.
///
/// ### API Notes
///
/// - [#contains(double, double)] and [#containsInInterior(double, double)] both treat boundary
///   points as outside.
/// - Point-in-polygon queries use even-odd semantics, matching the winding rule exposed by
///   [#getPathIterator(AffineTransform)].
/// - Rectangle containment and intersection queries delegate to a lazily cached [GeneralPath].
///   If callers mutate the live vertices after that path has been created, path-based queries may
///   observe stale geometry until a fresh polygon instance is built.
public abstract class Polygon2D implements Shape, Cloneable {
    /// Double-precision polygon implementation.
    ///
    /// Constructor inputs are copied into owned [Point2D.Double] instances, and [#clone()] deep
    /// copies that storage.
    public static class Double extends Polygon2D {
        private final int pointCount;
        private Point2D.Double[] points;

        /// Creates a polygon from parallel double-precision coordinate arrays.
        public Double(double[] xPoints, double[] yPoints, int pointCount) {
            requireNonEmpty(pointCount, "polygon cannot be empty");
            this.pointCount = pointCount;
            points = new Point2D.Double[pointCount];
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++)
                points[pointIndex] = new Point2D.Double(xPoints[pointIndex], yPoints[pointIndex]);
        }

        /// Creates a double-precision polygon from float coordinate arrays.
        public Double(float[] xPoints, float[] yPoints, int pointCount) {
            requireNonEmpty(pointCount, "polygon cannot be empty");
            this.pointCount = pointCount;
            points = new Point2D.Double[pointCount];
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++)
                points[pointIndex] = new Point2D.Double(xPoints[pointIndex], yPoints[pointIndex]);
        }

        /// Creates a double-precision polygon from integer coordinate arrays.
        public Double(int[] xPoints, int[] yPoints, int pointCount) {
            requireNonEmpty(pointCount, "polygon cannot be empty");
            this.pointCount = pointCount;
            points = new Point2D.Double[pointCount];
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++)
                points[pointIndex] = new Point2D.Double(xPoints[pointIndex], yPoints[pointIndex]);
        }

        /// Creates a polygon by copying the supplied points into double-precision storage.
        public Double(Point2D[] points) {
            requireNonEmpty(points.length, "points should not be empty");
            pointCount = points.length;
            this.points = new Point2D.Double[pointCount];
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                Point2D point = points[pointIndex];
                this.points[pointIndex] = new Point2D.Double(point.getX(), point.getY());
            }
        }

        /// Creates a deep copy of this polygon and all stored vertex objects.
        @Override
        public Double clone() {
            try {
                Double copy = (Double) super.clone();
                copy.points = new Point2D.Double[pointCount];
                for (int pointIndex = 0; pointIndex < pointCount; pointIndex++)
                    copy.points[pointIndex] = (Point2D.Double) points[pointIndex].clone();
                copy.clearCachedPath();
                return copy;
            } catch (CloneNotSupportedException e) {
                throw new InternalError();
            }
        }

        @Override
        public Rectangle2D getBounds2D() {
            double minX = java.lang.Double.POSITIVE_INFINITY;
            double maxX = java.lang.Double.NEGATIVE_INFINITY;
            double minY = java.lang.Double.POSITIVE_INFINITY;
            double maxY = java.lang.Double.NEGATIVE_INFINITY;
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                Point2D.Double point = points[pointIndex];
                minX = Math.min(minX, point.x);
                maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y);
                maxY = Math.max(maxY, point.y);
            }
            return new Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY);
        }

        @Override
        public int getNumberOfPoints() {
            return pointCount;
        }

        @Override
        public Point2D getPoint(int index) {
            return points[index];
        }

        @Override
        public Point2D[] getPoints() {
            return points;
        }

        @Override
        public int hashCode() {
            return 31 * getClass().hashCode() + Arrays.hashCode(points);
        }
    }

    /// Single-precision polygon implementation.
    ///
    /// Constructor inputs are copied into owned [Point2D.Float] instances, and [#clone()] deep
    /// copies that storage.
    public static class Float extends Polygon2D {
        private final int pointCount;
        private Point2D.Float[] points;

        /// Creates a polygon from parallel float coordinate arrays.
        public Float(float[] xPoints, float[] yPoints, int pointCount) {
            requireNonEmpty(pointCount, "polygon cannot be empty");
            this.pointCount = pointCount;
            points = new Point2D.Float[pointCount];
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++)
                points[pointIndex] = new Point2D.Float(xPoints[pointIndex], yPoints[pointIndex]);
        }

        /// Creates a polygon by copying the supplied points into single-precision storage.
        public Float(Point2D[] points) {
            requireNonEmpty(points.length, "points should not be empty");
            pointCount = points.length;
            this.points = new Point2D.Float[pointCount];
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                Point2D point = points[pointIndex];
                this.points[pointIndex] = new Point2D.Float((float) point.getX(), (float) point.getY());
            }
        }

        /// Creates a deep copy of this polygon and all stored vertex objects.
        @Override
        public Float clone() {
            try {
                Float copy = (Float) super.clone();
                copy.points = new Point2D.Float[pointCount];
                for (int pointIndex = 0; pointIndex < pointCount; pointIndex++)
                    copy.points[pointIndex] = (Point2D.Float) points[pointIndex].clone();
                copy.clearCachedPath();
                return copy;
            } catch (CloneNotSupportedException e) {
                throw new InternalError();
            }
        }

        @Override
        public Rectangle2D getBounds2D() {
            float minX = java.lang.Float.POSITIVE_INFINITY;
            float maxX = java.lang.Float.NEGATIVE_INFINITY;
            float minY = java.lang.Float.POSITIVE_INFINITY;
            float maxY = java.lang.Float.NEGATIVE_INFINITY;
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                Point2D.Float point = points[pointIndex];
                minX = Math.min(minX, point.x);
                maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y);
                maxY = Math.max(maxY, point.y);
            }
            return new Rectangle2D.Float(minX, minY, maxX - minX, maxY - minY);
        }

        @Override
        public int getNumberOfPoints() {
            return pointCount;
        }

        @Override
        public Point2D getPoint(int index) {
            return points[index];
        }

        @Override
        public Point2D[] getPoints() {
            return points;
        }

        @Override
        public int hashCode() {
            return 31 * getClass().hashCode() + Arrays.hashCode(points);
        }
    }

    private transient GeneralPath pathCache;

    protected Polygon2D() {
    }

    private static void requireNonEmpty(int pointCount, String message) {
        if (pointCount == 0)
            throw new IllegalArgumentException(message);
    }

    private GeneralPath getPathCache() {
        if (pathCache == null) {
            int pointCount = getNumberOfPoints();
            GeneralPath path = new GeneralPath(PathIterator.WIND_EVEN_ODD, pointCount);
            Point2D firstPoint = getPoint(0);
            path.moveTo((float) firstPoint.getX(), (float) firstPoint.getY());
            for (int pointIndex = 1; pointIndex < pointCount; pointIndex++) {
                Point2D point = getPoint(pointIndex);
                path.lineTo((float) point.getX(), (float) point.getY());
            }
            path.closePath();
            pathCache = path;
        }
        return pathCache;
    }

    final void clearCachedPath() {
        pathCache = null;
    }

    private static boolean isPointOnSegment(double startX, double startY, double endX, double endY, double x, double y) {
        double crossProduct = (x - startX) * (endY - startY) - (y - startY) * (endX - startX);
        if (crossProduct != 0.0)
            return false;
        return x >= Math.min(startX, endX)
                && x <= Math.max(startX, endX)
                && y >= Math.min(startY, endY)
                && y <= Math.max(startY, endY);
    }

    /// Tests whether the point lies strictly inside the polygon interior.
    ///
    /// Boundary points return `false`.
    @Override
    public boolean contains(double x, double y) {
        return containsInInterior(x, y);
    }

    @Override
    public boolean contains(double x, double y, double width, double height) {
        return getPathCache().contains(x, y, width, height);
    }

    @Override
    public boolean contains(Point2D p) {
        return contains(p.getX(), p.getY());
    }

    @Override
    public boolean contains(Rectangle2D r) {
        return contains(r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    /// Tests whether the point lies strictly inside the polygon interior.
    ///
    /// Boundary points return `false`.
    public boolean containsInInterior(double x, double y) {
        Rectangle2D bounds = getBounds2D();
        if (x < bounds.getMinX() || x > bounds.getMaxX() || y < bounds.getMinY() || y > bounds.getMaxY())
            return false;

        boolean inside = false;
        int pointCount = getNumberOfPoints();
        Point2D previousPoint = getPoint(pointCount - 1);
        double previousX = previousPoint.getX();
        double previousY = previousPoint.getY();
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            Point2D currentPoint = getPoint(pointIndex);
            double currentX = currentPoint.getX();
            double currentY = currentPoint.getY();

            if (isPointOnSegment(previousX, previousY, currentX, currentY, x, y))
                return false;

            boolean crossesRay = (previousY > y) != (currentY > y)
                    && x < (currentX - previousX) * (y - previousY) / (currentY - previousY) + previousX;
            if (crossesRay)
                inside = !inside;

            previousX = currentX;
            previousY = currentY;
        }
        return inside;
    }

    /// Compares the concrete polygon subtype and exact vertex sequence.
    @Override
    public boolean equals(Object obj) {
        return obj == this || obj instanceof Polygon2D other
                && getClass() == other.getClass()
                && Arrays.equals(getPoints(), other.getPoints());
    }

    /// Returns the signed area enclosed by the polygon.
    ///
    /// Counter-clockwise vertex order yields a positive result and clockwise order yields a
    /// negative result.
    public double getArea() {
        int pointCount = getNumberOfPoints();
        if (pointCount <= 2)
            return 0.0;

        double twiceArea = 0.0;
        Point2D previousPoint = getPoint(pointCount - 1);
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            Point2D currentPoint = getPoint(pointIndex);
            twiceArea += previousPoint.getX() * currentPoint.getY() - currentPoint.getX() * previousPoint.getY();
            previousPoint = currentPoint;
        }
        return 0.5 * twiceArea;
    }

    @Override
    public Rectangle getBounds() {
        Rectangle2D bounds = getBounds2D();
        int minX = (int) Math.floor(bounds.getMinX());
        int maxX = (int) Math.ceil(bounds.getMaxX());
        int minY = (int) Math.floor(bounds.getMinY());
        int maxY = (int) Math.ceil(bounds.getMaxY());
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    @Override
    public abstract Rectangle2D getBounds2D();

    /// Returns the number of stored vertices.
    public abstract int getNumberOfPoints();

    @Override
    public PathIterator getPathIterator(AffineTransform tx) {
        return new Polygon2DPathIterator(this, tx);
    }

    @Override
    public PathIterator getPathIterator(AffineTransform tx, double flatness) {
        return getPathIterator(tx);
    }

    /// Returns the live stored point at `index`.
    ///
    /// Mutating the returned point mutates the polygon.
    public abstract Point2D getPoint(int index);

    /// Returns the live backing point array.
    ///
    /// The returned array is not a defensive copy.
    public abstract Point2D[] getPoints();

    @Override
    public boolean intersects(double x, double y, double width, double height) {
        return getPathCache().intersects(x, y, width, height);
    }

    @Override
    public boolean intersects(Rectangle2D r) {
        return intersects(r.getX(), r.getY(), r.getWidth(), r.getHeight());
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder(getClass().getName()).append('[');
        int pointCount = getNumberOfPoints();
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            if (pointIndex > 0)
                text.append(" -> ");
            Point2D point = getPoint(pointIndex);
            text.append('(')
                    .append(point.getX())
                    .append(',')
                    .append(point.getY())
                    .append(')');
        }
        return text.append(']').toString();
    }
}
