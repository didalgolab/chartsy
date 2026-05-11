package one.chartsy.charting;

import java.awt.geom.Rectangle2D;
import java.io.Serializable;

/// Holds paired x-axis and y-axis ranges for chart queries, projections, and zoom operations.
///
/// `DataWindow` is the charting module's mutable two-dimensional range primitive. Projectors use it
/// to convert between screen rectangles and data coordinates, datasets use it as a filter for
/// `getDataInside(...)`, and interaction code reuses instances while dragging or animating zooms.
/// The public [#xRange] and [#yRange] fields are therefore intentional: callers frequently mutate
/// or replace the two axis ranges independently to avoid wrapper churn.
///
/// The constructor that accepts [DataInterval] instances retains those exact objects instead of
/// copying them. Use [#DataWindow(DataWindow)] or [#clone()] when the window must be isolated from
/// later mutations of the source ranges.
public final class DataWindow implements Serializable, Cloneable {
    public DataInterval xRange;
    public DataInterval yRange;

    /// Creates an empty window whose two ranges start in [DataInterval]'s canonical empty state.
    public DataWindow() {
        this(new DataInterval(), new DataInterval());
    }

    /// Creates a window that reuses the supplied range objects directly.
    ///
    /// Mutating either argument after construction also mutates this window.
    ///
    /// @param xRange the x-axis range to retain
    /// @param yRange the y-axis range to retain
    public DataWindow(DataInterval xRange, DataInterval yRange) {
        this.xRange = xRange;
        this.yRange = yRange;
    }

    /// Creates a deep copy of `window`.
    ///
    /// The cloned window receives new [DataInterval] instances for both axes.
    ///
    /// @param window the window to copy
    public DataWindow(DataWindow window) {
        this(new DataInterval(window.xRange), new DataInterval(window.yRange));
    }

    /// Creates a window from explicit x and y bounds.
    ///
    /// @param xMin the lower x bound
    /// @param xMax the upper x bound
    /// @param yMin the lower y bound
    /// @param yMax the upper y bound
    public DataWindow(double xMin, double xMax, double yMin, double yMax) {
        this(new DataInterval(xMin, xMax), new DataInterval(yMin, yMax));
    }

    /// Creates a window from a rectangle's left, right, top, and bottom edges.
    ///
    /// @param rectangle the rectangle to convert into x and y ranges
    public DataWindow(Rectangle2D rectangle) {
        this(
                rectangle.getX(),
                rectangle.getX() + rectangle.getWidth(),
                rectangle.getY(),
                rectangle.getY() + rectangle.getHeight()
        );
    }

    /// Expands this window so it also covers `window`.
    ///
    /// The operation is applied independently to [#xRange] and [#yRange].
    ///
    /// @param window the window to merge into this one
    public void add(DataWindow window) {
        xRange.add(window.xRange);
        yRange.add(window.yRange);
    }

    /// Expands this window so it includes the point (`x`, `y`).
    ///
    /// @param x the x coordinate to include
    /// @param y the y coordinate to include
    public void add(double x, double y) {
        xRange.add(x);
        yRange.add(y);
    }

    /// Returns a deep copy of this window.
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public DataWindow clone() {
        return new DataWindow(this);
    }

    /// Returns whether both axis ranges contain the supplied point.
    ///
    /// Endpoint handling follows `DataInterval.isInside(double)`, so both bounds are inclusive.
    ///
    /// @param x the x coordinate to test
    /// @param y the y coordinate to test
    /// @return `true` when the point lies inside both axis ranges
    public boolean contains(double x, double y) {
        return xRange.isInside(x) && yRange.isInside(y);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof DataWindow window)) {
            return false;
        }
        return xRange.equals(window.xRange) && yRange.equals(window.yRange);
    }

    public double getXMax() {
        return xRange.getMax();
    }

    public double getXMin() {
        return xRange.getMin();
    }

    public double getYMax() {
        return yRange.getMax();
    }

    public double getYMin() {
        return yRange.getMin();
    }

    /// Replaces this window with its overlap against the supplied axis ranges.
    ///
    /// @param xRange the x-axis range to intersect with
    /// @param yRange the y-axis range to intersect with
    public void intersection(DataInterval xRange, DataInterval yRange) {
        this.xRange.intersection(xRange);
        this.yRange.intersection(yRange);
    }

    /// Replaces this window with its overlap against `window`.
    ///
    /// @param window the window to intersect with
    public void intersection(DataWindow window) {
        intersection(window.xRange, window.yRange);
    }

    /// Returns whether this window overlaps `window` on both axes.
    ///
    /// @param window the other window to test
    /// @return `true` when both x ranges and y ranges intersect
    public boolean intersects(DataWindow window) {
        return xRange.intersects(window.xRange) && yRange.intersects(window.yRange);
    }

    /// Returns whether at least one axis range is empty.
    ///
    /// A `DataWindow` is usable only when both axes describe a non-empty span.
    ///
    /// @return `true` if either [#xRange] or [#yRange] is empty
    public boolean isEmpty() {
        return xRange.isEmpty() || yRange.isEmpty();
    }

    @Override
    public String toString() {
        return "xRange: [" + xRange.getMin() + "," + xRange.getMax()
                + "]   yRange : [" + yRange.getMin() + "," + yRange.getMax() + "]";
    }
}
