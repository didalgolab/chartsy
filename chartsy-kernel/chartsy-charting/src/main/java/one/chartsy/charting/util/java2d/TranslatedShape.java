package one.chartsy.charting.util.java2d;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/// [Shape] view that applies a fixed translation to another shape.
///
/// This wrapper retains the wrapped shape instead of copying it. Point and rectangle queries are
/// evaluated by subtracting the stored offsets before delegating to the wrapped shape, while path
/// iteration adds the offsets back to every emitted coordinate. Later mutations of a mutable
/// wrapped shape are therefore observed immediately.
///
/// ### API Note
///
/// Use [ShapeUtil#getTranslatedShape(Shape, double, double)] when possible. That factory keeps
/// common Java2D shapes in their native concrete form and falls back to this wrapper only for
/// shapes without a cheaper specialized translation path.
public final class TranslatedShape implements Shape {
    private final Shape originalShape;
    private final double offsetX;
    private final double offsetY;
    
    TranslatedShape(Shape originalShape, double offsetX, double offsetY) {
        this.originalShape = originalShape;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }
    
    @Override
    public boolean contains(double x, double y) {
        return originalShape.contains(x - offsetX, y - offsetY);
    }
    
    @Override
    public boolean contains(double x, double y, double width, double height) {
        return originalShape.contains(x - offsetX, y - offsetY, width, height);
    }
    
    @Override
    public boolean contains(Point2D point) {
        return contains(point.getX(), point.getY());
    }
    
    @Override
    public boolean contains(Rectangle2D rectangle) {
        return contains(rectangle.getX(), rectangle.getY(), rectangle.getWidth(), rectangle.getHeight());
    }
    
    @Override
    public Rectangle getBounds() {
        return getBounds2D().getBounds();
    }
    
    /// Returns a translated snapshot of the wrapped shape's current bounds.
    ///
    /// Each call clones the wrapped bounds before shifting them, so mutating the returned
    /// rectangle does not affect this wrapper. If the wrapped shape later changes, subsequent calls
    /// reflect the new bounds.
    @Override
    public Rectangle2D getBounds2D() {
        Rectangle2D translatedBounds = (Rectangle2D) originalShape.getBounds2D().clone();
        translatedBounds.setRect(translatedBounds.getX() + offsetX, translatedBounds.getY() + offsetY,
                translatedBounds.getWidth(), translatedBounds.getHeight());
        return translatedBounds;
    }
    
    /// Returns the horizontal translation applied by this wrapper.
    ///
    /// The wrapped shape itself remains in its original coordinate system.
    public double getOffsetX() {
        return offsetX;
    }
    
    /// Returns the vertical translation applied by this wrapper.
    ///
    /// The wrapped shape itself remains in its original coordinate system.
    public double getOffsetY() {
        return offsetY;
    }
    
    /// Returns the live wrapped untranslated shape.
    ///
    /// Mutating a mutable returned shape affects future translated queries on this wrapper.
    public Shape getOriginalShape() {
        return originalShape;
    }
    
    /// Returns a path iterator whose emitted coordinates include this wrapper's translation.
    ///
    /// The supplied transform is applied first by the wrapped shape's iterator. The wrapper offset
    /// is then added to the emitted coordinates, so the offset itself is not transformed.
    @Override
    public PathIterator getPathIterator(AffineTransform transform) {
        return new TranslatedShapePathIterator(this, originalShape.getPathIterator(transform));
    }
    
    /// Returns a flattened path iterator whose emitted coordinates include this wrapper's
    /// translation.
    ///
    /// The flatness parameter is forwarded to the wrapped shape. The wrapper offset is then added
    /// after the wrapped iterator has already applied any supplied transform and flattening.
    @Override
    public PathIterator getPathIterator(AffineTransform transform, double flatness) {
        return new TranslatedShapePathIterator(this, originalShape.getPathIterator(transform, flatness));
    }
    
    @Override
    public boolean intersects(double x, double y, double width, double height) {
        return originalShape.intersects(x - offsetX, y - offsetY, width, height);
    }
    
    @Override
    public boolean intersects(Rectangle2D rectangle) {
        return intersects(rectangle.getX(), rectangle.getY(), rectangle.getWidth(), rectangle.getHeight());
    }
}
