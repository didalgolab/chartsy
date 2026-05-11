package one.chartsy.charting.internal;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.io.Serializable;

import one.chartsy.charting.AffineAxisTransformer;
import one.chartsy.charting.Axis;
import one.chartsy.charting.AxisTransformer;
import one.chartsy.charting.AxisTransformerException;
import one.chartsy.charting.ChartProjector;
import one.chartsy.charting.CoordinateSystem;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.DoublePoint;
import one.chartsy.charting.DoublePoints;

/// Base [ChartProjector] for projector implementations that can treat each axis mapping as a
/// linear transform between transformed data space and one projector-specific display span.
///
/// This class centralizes the work shared by cartesian, polar, and radar projections:
/// - applying and inverting per-axis [AxisTransformer] instances
/// - converting between [DataWindow] rectangles and projected display geometry
/// - caching precomputed display coefficients for [#getLocalProjector(Rectangle, CoordinateSystem)]
///   so repeated paint-time transforms against one plot rectangle can skip coefficient
///   recomputation
///
/// Subclasses provide the geometry-specific pieces through the package-private template methods
/// that describe where each axis lives inside `rect` and how to derive the forward and inverse
/// linear coefficients for that axis.
public abstract class AbstractProjector implements ChartProjector, Cloneable, Serializable {

    /// Mutable linear-transform coefficients for one axis projection step.
    ///
    /// Depending on the factory method that filled this instance, the pair represents either a
    /// transformed-data-to-display mapping or the inverse display-to-transformed-data mapping. The
    /// stored formula is always `mapped = source * scale + offset`.
    static class Coefficient {
        double scale;
        double offset;
    }

    /// Mutable holder for the two display-space endpoints assigned to one axis.
    ///
    /// Subclasses fill [#start] and [#end] before coefficient computation. Reversing an axis swaps
    /// those endpoints so the later linear mapping runs in the opposite direction.
    static class DataPos {
        double start;
        double end;

        /// Swaps [#start] and [#end] in place.
        public final void reverse() {
            double currentStart = start;
            start = end;
            end = currentStart;
        }
    }

    /// Whether this projector should use its subclass-defined reversed orientation.
    private boolean reversed;

    /// Cached forward coefficient for the x axis on one local-projector clone.
    private transient Coefficient cachedXDisplayCoefficient;

    /// Cached forward coefficient for the y axis on one local-projector clone.
    private transient Coefficient cachedYDisplayCoefficient;

    /// Creates a projector with no cached local coefficients.
    public AbstractProjector() {
    }

    /// Applies the supplied axis coefficients to `points` in place.
    ///
    /// The base implementation performs one independent affine transform for x and y coordinates.
    /// Subclasses may override this hook when additional projection-specific filtering or axis
    /// swapping is required.
    void applyCoefficients(DoublePoints points, Rectangle rect, Coefficient xCoefficient,
                           Coefficient yCoefficient) {
        double xScale = xCoefficient.scale;
        double xOffset = xCoefficient.offset;
        double yScale = yCoefficient.scale;
        double yOffset = yCoefficient.offset;
        double[] xValues = points.getXValues();
        double[] yValues = points.getYValues();
        for (int index = points.size() - 1; index >= 0; index--) {
            xValues[index] = xValues[index] * xScale + xOffset;
            yValues[index] = yValues[index] * yScale + yOffset;
        }
    }

    /// Resolves the display-space endpoints occupied by `axis` inside `rect`.
    ///
    /// Implementations should fill `dataPos` for the projector's natural axis direction before any
    /// [Axis#isReversed()] adjustment is applied by this base class.
    abstract void resolveAxisDisplayPositions(Rectangle rect, Axis axis, DataPos dataPos);

    /// Computes the transformed-data-to-display coefficient for `axis`.
    abstract void computeDisplayCoefficient(Rectangle rect, Axis axis, DataPos dataPos,
                                            Coefficient coefficient);

    private void cacheDisplayCoefficients(Rectangle rect, CoordinateSystem coords) {
        cachedXDisplayCoefficient = new Coefficient();
        cachedYDisplayCoefficient = new Coefficient();
        computeDisplayCoefficients(rect, coords, cachedXDisplayCoefficient, cachedYDisplayCoefficient);
    }

    private void computeDisplayCoefficients(Rectangle rect, CoordinateSystem coords,
                                            Coefficient xCoefficient, Coefficient yCoefficient) {
        DataPos dataPos = new DataPos();
        Axis xAxis = coords.getXAxis();
        resolveAxisDisplayPositions(rect, xAxis, dataPos);
        if (xAxis.isReversed())
            dataPos.reverse();
        computeDisplayCoefficient(rect, xAxis, dataPos, xCoefficient);

        Axis yAxis = coords.getYAxis();
        resolveAxisDisplayPositions(rect, yAxis, dataPos);
        if (yAxis.isReversed())
            dataPos.reverse();
        computeDisplayCoefficient(rect, yAxis, dataPos, yCoefficient);
    }

    /// Computes the display-to-transformed-data coefficient for `axis`.
    abstract void computeDataCoefficient(Rectangle rect, Axis axis, DataPos dataPos,
                                         Coefficient coefficient);

    /// Returns a shallow projector clone.
    @Override
    public AbstractProjector clone() {
        try {
            return (AbstractProjector) super.clone();
        } catch (CloneNotSupportedException e1) {
            throw new InternalError();
        }
    }

    /// Returns a shallow clone with cached display coefficients specialized for `rect` and
    /// `coords`.
    ///
    /// Callers use the returned instance for repeated paint-time projections against one fixed plot
    /// rectangle. The clone shares all ordinary object state with this projector except for the
    /// cached coefficient scratch objects initialized for that one display context.
    ///
    /// @param rect   plot rectangle the local projector should target
    /// @param coords coordinate system the local projector should target
    /// @return a projector clone with cached forward coefficients for the supplied context
    public AbstractProjector getLocalProjector(Rectangle rect, CoordinateSystem coords) {
        AbstractProjector localProjector = clone();
        localProjector.cacheDisplayCoefficients(rect, coords);
        return localProjector;
    }

    /// Stores the display-space origin used by this projector in `point`.
    ///
    /// @param rect  projector bounds in display coordinates
    /// @param point mutable output point that receives the origin coordinates
    public abstract void getOrigin(Rectangle rect, DoublePoint point);

    /// Returns the projector rectangle to use for an auxiliary operation near (`x`, `y`).
    ///
    /// The base implementation returns `rect` unchanged. Subclasses can override when the effective
    /// projector bounds depend on the queried point or distance.
    ///
    /// @param rect default projector rectangle
    /// @param x x-coordinate of the query point in display space
    /// @param y y-coordinate of the query point in display space
    /// @param distance display-space distance associated with the query
    /// @return the rectangle that should be used for the operation
    public Rectangle getProjectorRect(Rectangle rect, double x, double y, double distance) {
        return rect;
    }

    @Override
    public abstract Shape getShape(double value, DataInterval interval, int axisType, Rectangle rect,
                                   CoordinateSystem coords);

    @Override
    public Shape getShape(double value, int axisType, Rectangle rect, CoordinateSystem coords) {
        DataInterval interval = (axisType != Axis.X_AXIS)
                ? coords.getXAxis().getVisibleRange()
                : coords.getYAxis().getVisibleRange();
        return getShape(value, interval, axisType, rect, coords);
    }

    /// Returns whether this projector's primary orientation is reversed relative to its default
    /// geometry.
    ///
    /// Concrete meanings vary by subclass. For example, cartesian projectors use the flag to swap
    /// whether x or y is treated as the horizontal axis.
    ///
    /// @return `true` when the subclass-defined reversed orientation is active
    public final boolean isReversed() {
        return reversed;
    }

    /// Replaces the projector's primary reversed-state flag.
    ///
    /// @param reversed `true` to use the subclass-defined reversed orientation
    public void setReversed(boolean reversed) {
        this.reversed = reversed;
    }

    @Override
    public void toData(DoublePoints points, Rectangle rect, CoordinateSystem coords) {
        Axis xAxis = coords.getXAxis();
        Axis yAxis = coords.getYAxis();
        Coefficient xCoefficient = new Coefficient();
        Coefficient yCoefficient = new Coefficient();
        DataPos dataPos = new DataPos();

        resolveAxisDisplayPositions(rect, xAxis, dataPos);
        if (xAxis.isReversed())
            dataPos.reverse();
        computeDataCoefficient(rect, xAxis, dataPos, xCoefficient);

        resolveAxisDisplayPositions(rect, yAxis, dataPos);
        if (yAxis.isReversed())
            dataPos.reverse();
        computeDataCoefficient(rect, yAxis, dataPos, yCoefficient);

        applyCoefficients(points, rect, xCoefficient, yCoefficient);
        try {
            AxisTransformer xTransformer = xAxis.getTransformer();
            if (xTransformer != null)
                xTransformer.inverse(points.getXValues(), points.size());
            AxisTransformer yTransformer = yAxis.getTransformer();
            if (yTransformer != null)
                yTransformer.inverse(points.getYValues(), points.size());
        } catch (AxisTransformerException e1) {
            e1.printStackTrace();
        }
    }

    /// Returns one affine display-to-data transform when both axis transformers are affine or
    /// absent.
    ///
    /// The returned transform folds this projector's inverse coefficients together with the inverse
    /// of the exact runtime [AffineAxisTransformer] instances installed on the axes. Non-affine
    /// projectors or non-affine axis transformers report `null`.
    ///
    /// @param rect   projector bounds in display coordinates
    /// @param coords coordinate system whose axes define the mapping
    /// @return an affine display-to-data transform, or `null` when the mapping is not affine
    public AffineTransform toDataAffineTransform(Rectangle rect, CoordinateSystem coords) {
        Axis xAxis = coords.getXAxis();
        Axis yAxis = coords.getYAxis();
        AxisTransformer xTransformer = xAxis.getTransformer();
        AxisTransformer yTransformer = yAxis.getTransformer();
        if (xTransformer != null && xTransformer.getClass() != AffineAxisTransformer.class)
            return null;
        if (yTransformer != null && yTransformer.getClass() != AffineAxisTransformer.class)
            return null;

        Coefficient xCoefficient = new Coefficient();
        Coefficient yCoefficient = new Coefficient();
        DataPos dataPos = new DataPos();

        resolveAxisDisplayPositions(rect, xAxis, dataPos);
        if (xAxis.isReversed())
            dataPos.reverse();
        computeDataCoefficient(rect, xAxis, dataPos, xCoefficient);

        resolveAxisDisplayPositions(rect, yAxis, dataPos);
        if (yAxis.isReversed())
            dataPos.reverse();
        computeDataCoefficient(rect, yAxis, dataPos, yCoefficient);

        double xScale = xCoefficient.scale;
        double xOffset = xCoefficient.offset;
        double yScale = yCoefficient.scale;
        double yOffset = yCoefficient.offset;

        if (xTransformer != null) {
            AffineAxisTransformer affineXTransformer = (AffineAxisTransformer) xTransformer;
            xOffset = (xOffset - affineXTransformer.getConstant()) / affineXTransformer.getScaling();
            xScale /= affineXTransformer.getScaling();
        }
        if (yTransformer != null) {
            AffineAxisTransformer affineYTransformer = (AffineAxisTransformer) yTransformer;
            yOffset = (yOffset - affineYTransformer.getConstant()) / affineYTransformer.getScaling();
            yScale /= affineYTransformer.getScaling();
        }

        return new AffineTransform(xScale, 0.0, 0.0, yScale, xOffset, yOffset);
    }

    @Override
    public DataWindow toDataWindow(Rectangle selection, Rectangle rect, CoordinateSystem coords) {
        DataWindow dataWindow = new DataWindow();
        DoublePoints points = new DoublePoints(2);
        points.add(selection.x, selection.y);
        points.add(selection.x + selection.width, selection.y + selection.height);
        toData(points, rect, coords);

        if (points.getX(0) >= points.getX(1))
            dataWindow.xRange.set(points.getX(1), points.getX(0));
        else
            dataWindow.xRange.set(points.getX(0), points.getX(1));

        if (points.getY(0) >= points.getY(1))
            dataWindow.yRange.set(points.getY(1), points.getY(0));
        else
            dataWindow.yRange.set(points.getY(0), points.getY(1));

        points.dispose();
        return dataWindow;
    }

    @Override
    public void toDisplay(DoublePoints points, Rectangle rect, CoordinateSystem coords) {
        Axis xAxis = coords.getXAxis();
        Axis yAxis = coords.getYAxis();
        try {
            AxisTransformer xTransformer = xAxis.getTransformer();
            if (xTransformer != null)
                xTransformer.apply(points.getXValues(), points.size());

            AxisTransformer yTransformer = yAxis.getTransformer();
            if (yTransformer != null)
                yTransformer.apply(points.getYValues(), points.size());
        } catch (AxisTransformerException e1) {
            e1.printStackTrace();
        }

        Coefficient xCoefficient = cachedXDisplayCoefficient;
        Coefficient yCoefficient = cachedYDisplayCoefficient;
        if (xCoefficient == null || yCoefficient == null) {
            xCoefficient = new Coefficient();
            yCoefficient = new Coefficient();
            computeDisplayCoefficients(rect, coords, xCoefficient, yCoefficient);
        }

        applyCoefficients(points, rect, xCoefficient, yCoefficient);
    }

    @Override
    public Rectangle toRectangle(DataWindow window, Rectangle rect, CoordinateSystem coords) {
        Rectangle projectedRect = new Rectangle();
        DoublePoints points = new DoublePoints(2);
        points.add(window.getXMin(), window.getYMin());
        points.add(window.getXMax(), window.getYMax());
        toDisplay(points, rect, coords);

        if (points.getX(0) <= points.getX(1)) {
            projectedRect.x = (int) Math.floor(points.getX(0));
            projectedRect.width = (int) Math.floor(points.getX(1)) - projectedRect.x;
        } else {
            projectedRect.x = (int) Math.floor(points.getX(1));
            projectedRect.width = (int) Math.floor(points.getX(0)) - projectedRect.x;
        }

        if (points.getY(0) <= points.getY(1)) {
            projectedRect.y = (int) Math.floor(points.getY(0));
            projectedRect.height = (int) Math.floor(points.getY(1)) - projectedRect.y;
        } else {
            projectedRect.y = (int) Math.floor(points.getY(1));
            projectedRect.height = (int) Math.floor(points.getY(0)) - projectedRect.y;
        }

        points.dispose();
        return projectedRect;
    }
}
