package one.chartsy.charting;

import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.Objects;

import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.util.java2d.ShapeUtil;

/// Shape-backed display item for renderer output that occupies an area rather than a single point.
///
/// `DisplayArea` keeps the same logical dataset entry and owning renderer metadata as
/// [DisplayPoint] while also exposing the area geometry used for hit testing or annotation
/// placement. The inherited display coordinates act as a representative anchor for that geometry
/// and are updated to the center of the shape's tight bounds whenever [#setShape(Shape)] installs
/// a new non-null shape.
///
/// The assigned shape is retained and returned by reference rather than copied. Mutating a mutable
/// shape after assignment therefore changes the geometry seen through [#getShape()], but does not
/// recalculate the cached center coordinates until [#setShape(Shape)] is called again with a
/// different shape reference.
///
/// Equality extends [DisplayPoint]'s renderer, dataset, index, and coordinate checks by also
/// requiring the two stored shapes to compare equal via `Shape.equals(Object)`.
public class DisplayArea extends DisplayPoint {
    private Shape shape;

    /// Creates a detached area handle for later reuse.
    public DisplayArea() {
    }

    /// Creates an area handle for one renderer-visible dataset entry.
    ///
    /// The inherited display coordinates start at `0,0` and are typically positioned by
    /// [#setShape(Shape)] once the renderer has computed the item's geometry.
    ///
    /// @param renderer the renderer that owns this display-space view
    /// @param dataSet the logical source dataset currently addressed by this handle
    /// @param index the logical point index within `dataSet`
    public DisplayArea(ChartRenderer renderer, DataSet dataSet, int index) {
        super(renderer, dataSet, index, 0.0, 0.0);
    }

    /// Returns whether `obj` describes the same dataset area, renderer, cached center, and stored
    /// shape.
    ///
    /// Two geometrically identical shapes may still compare unequal when their `Shape.equals`
    /// implementations do not consider them equal.
    @Override
    public boolean equals(Object obj) {
        return obj instanceof DisplayArea area
                && super.equals(area)
                && Objects.equals(shape, area.shape);
    }

    /// Returns the current area geometry, or `null` when none has been assigned.
    ///
    /// The returned shape is the live stored reference, not a defensive copy.
    public Shape getShape() {
        return shape;
    }

    /// Replaces the area geometry represented by this handle.
    ///
    /// When `shape` is non-null, the inherited display coordinates are updated to the center of the
    /// shape's tight bounds as reported by [ShapeUtil#getTightBounds2D(Shape)]. Passing the same
    /// shape reference is a no-op. Passing `null` clears the stored geometry but leaves the current
    /// display coordinates unchanged.
    ///
    /// @param shape the new area geometry, or `null` to clear it
    public void setShape(Shape shape) {
        if (shape != this.shape) {
            this.shape = shape;
            if (shape != null) {
                Rectangle2D bounds = ShapeUtil.getTightBounds2D(shape);
                super.xCoord = bounds.getCenterX();
                super.yCoord = bounds.getCenterY();
            }
        }
    }

    @Override
    public String toString() {
        return "Point#" + super.index + ((shape == null) ? "" : " (" + shape + ")");
    }
}
