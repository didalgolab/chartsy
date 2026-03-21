package one.chartsy.charting;

import java.io.Serializable;

import one.chartsy.charting.util.GraphicUtil;

/// Represents a mutable two-coordinate point reused across chart layout, projection, and
/// interaction code.
///
/// The charting module uses this type as a lightweight scratch holder instead of `Point2D` in
/// hot UI paths such as label placement, projector origin lookup, and display-to-data conversion.
/// The public [#x] and [#y] fields are intentional so callers can reuse one instance and update it
/// in place without wrapper allocation.
///
/// Equality uses exact `double` comparison rather than tolerance-based geometry checks. The
/// `floor`, [#xFloor()], and [#yFloor()] helpers snap coordinates with [GraphicUtil], which applies
/// the same device-pixel conversion logic used elsewhere in the rendering pipeline.
public final class DoublePoint implements Serializable {
    public double x;
    public double y;

    /// Creates a point at the origin.
    public DoublePoint() {
        x = 0.0;
        y = 0.0;
    }

    /// Creates a point with explicit coordinates.
    ///
    /// @param x the x coordinate
    /// @param y the y coordinate
    public DoublePoint(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /// Creates a copy of `point`.
    ///
    /// @param point the point to copy
    public DoublePoint(DoublePoint point) {
        x = point.x;
        y = point.y;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof DoublePoint point)) {
            return false;
        }
        return x == point.x && y == point.y;
    }

    /// Snaps both coordinates in place to the charting integer pixel grid.
    ///
    /// The conversion uses [GraphicUtil]'s shared rounding rules rather than `Math.floor`.
    public void floor() {
        x = GraphicUtil.toInt(x);
        y = GraphicUtil.toInt(y);
    }

    @Override
    public int hashCode() {
        long xBits = Double.doubleToLongBits(normalizeZero(x));
        long yBits = Double.doubleToLongBits(normalizeZero(y));
        long hash = xBits ^ (yBits * 31L);
        return (int) hash ^ (int) (hash >> 32);
    }

    private static double normalizeZero(double value) {
        return (value == 0.0) ? 0.0 : value;
    }

    /// Replaces both coordinates.
    ///
    /// @param x the new x coordinate
    /// @param y the new y coordinate
    public void setLocation(double x, double y) {
        this.x = x;
        this.y = y;
    }

    /// Replaces both coordinates with those of `point`.
    ///
    /// @param point the source point
    public void setLocation(DoublePoint point) {
        x = point.x;
        y = point.y;
    }

    @Override
    public String toString() {
        return x + " " + y;
    }

    /// Adds the supplied deltas to the current coordinates.
    ///
    /// @param deltaX the x offset
    /// @param deltaY the y offset
    public void translate(double deltaX, double deltaY) {
        x += deltaX;
        y += deltaY;
    }

    /// Returns the x coordinate snapped with [GraphicUtil]'s pixel conversion logic.
    public int xFloor() {
        return GraphicUtil.toInt(x);
    }

    /// Returns the y coordinate snapped with [GraphicUtil]'s pixel conversion logic.
    public int yFloor() {
        return GraphicUtil.toInt(y);
    }
}
