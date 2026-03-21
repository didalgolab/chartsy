package one.chartsy.charting.util;

import java.awt.geom.Dimension2D;

/// Provides a small mutable [Dimension2D] implementation backed by `double` fields.
///
/// `LabelRenderer` uses this type when it needs a concrete dimension result without rounding to the
/// integer-oriented semantics of [java.awt.Dimension]. The stored width and height are returned
/// exactly as supplied; no normalization or defensive copying is applied.
public final class Dimension2DExt extends Dimension2D {
    private double width;
    private double height;
    
    /// Creates a zero-sized dimension.
    public Dimension2DExt() {
        this(0.0, 0.0);
    }
    
    /// Creates a dimension with the supplied width and height.
    public Dimension2DExt(double width, double height) {
        this.width = width;
        this.height = height;
    }
    
    @Override
    public double getHeight() {
        return height;
    }
    
    @Override
    public double getWidth() {
        return width;
    }
    
    @Override
    public void setSize(double width, double height) {
        this.width = width;
        this.height = height;
    }
    
    /// Formats the current size as `(width,height)` for debugging output.
    @Override
    public String toString() {
        return "(" + getWidth() + "," + getHeight() + ")";
    }
}
