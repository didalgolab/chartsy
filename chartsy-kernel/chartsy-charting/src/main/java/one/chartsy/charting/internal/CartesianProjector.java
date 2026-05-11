package one.chartsy.charting.internal;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;

import one.chartsy.charting.Axis;
import one.chartsy.charting.AxisTransformer;
import one.chartsy.charting.AxisTransformerException;
import one.chartsy.charting.CoordinateSystem;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.DoublePoint;
import one.chartsy.charting.DoublePoints;
import one.chartsy.charting.data.DataPoints;
import one.chartsy.charting.util.MathUtil;

/// Rectangular [one.chartsy.charting.ChartProjector] used by ordinary x/y charts.
///
/// The projector maps each axis independently onto one edge of the plot rectangle and then combines
/// those linear axis mappings into standard Cartesian geometry for renderers, scales, grids, and
/// interactions.
///
/// The optional reversed flag inherited from [AbstractProjector] does not reverse axis values by
/// itself. Instead it swaps which chart axis is laid out horizontally versus vertically. Each
/// [Axis] still applies its own [Axis#isReversed()] direction on top of that layout choice.
public class CartesianProjector extends AbstractProjector {
    private static final boolean PROJECTION_FILTER_ENABLED = false;

    /// Minimum transformed-axis spacing used by the disabled point-decimation fast path.
    private double projectionFilterSpacing = 1.0;

    /// Creates a projector in its default orientation.
    public CartesianProjector() {
    }

    /// Creates a projector with an explicitly selected axis-layout orientation.
    ///
    /// @param reversed `true` to swap which chart axis is laid out horizontally
    public CartesianProjector(boolean reversed) {
        super.setReversed(reversed);
    }

    /// Returns the spacing threshold consulted by the disabled projection-filter branch.
    double getProjectionFilterSpacing() {
        return projectionFilterSpacing;
    }

    /// Updates the spacing threshold consulted by the disabled projection-filter branch.
    ///
    /// @param projectionFilterSpacing the new transformed-axis spacing threshold
    void setProjectionFilterSpacing(double projectionFilterSpacing) {
        this.projectionFilterSpacing = projectionFilterSpacing;
    }

    private boolean isHorizontalAxis(Axis axis) {
        return axis.isXAxis() != super.isReversed();
    }

    @Override
    void applyCoefficients(DoublePoints points, Rectangle rect, Coefficient xCoefficient,
                           Coefficient yCoefficient) {
        int pointCount = points.size();
        if (PROJECTION_FILTER_ENABLED && pointCount > 1000 && projectionFilterSpacing > 0.0) {
            double xScale = xCoefficient.scale;
            double xOffset = xCoefficient.offset;
            double yScale = yCoefficient.scale;
            double yOffset = yCoefficient.offset;
            double minXDistance = Math.abs(projectionFilterSpacing / xScale);
            double minYDistance = Math.abs(projectionFilterSpacing / yScale);
            double[] xValues = points.getXValues();
            double[] yValues = points.getYValues();
            int[] indices = (points instanceof DataPoints dataPoints) ? dataPoints.getIndices() : null;

            double nextAcceptedX = xValues[0] + minXDistance;
            double nextAcceptedMinY = yValues[0] - minYDistance;
            double nextAcceptedMaxY = yValues[0] + minYDistance;
            xValues[0] = xValues[0] * xScale + xOffset;
            yValues[0] = yValues[0] * yScale + yOffset;

            int acceptedCount = 1;
            for (int pointIndex = 1; pointIndex < pointCount; pointIndex++) {
                double x = xValues[pointIndex];
                double y = yValues[pointIndex];
                if (x <= nextAcceptedX && y <= nextAcceptedMaxY && y >= nextAcceptedMinY)
                    continue;

                xValues[acceptedCount] = x * xScale + xOffset;
                yValues[acceptedCount] = y * yScale + yOffset;
                if (indices != null)
                    indices[acceptedCount] = indices[pointIndex];
                nextAcceptedX = x + minXDistance;
                nextAcceptedMinY = y - minYDistance;
                nextAcceptedMaxY = y + minYDistance;
                acceptedCount++;
            }
            points.setSize(acceptedCount);
            return;
        }

        super.applyCoefficients(points, rect, xCoefficient, yCoefficient);
    }

    @Override
    void resolveAxisDisplayPositions(Rectangle rect, Axis axis, DataPos dataPos) {
        if (isHorizontalAxis(axis)) {
            dataPos.start = rect.x;
            dataPos.end = rect.x + rect.width - 1;
        } else {
            dataPos.start = rect.y + rect.height - 1;
            dataPos.end = rect.y;
        }
    }

    @Override
    void computeDisplayCoefficient(Rectangle rect, Axis axis, DataPos dataPos,
                                   Coefficient coefficient) {
        DataInterval transformedVisibleRange = axis.getTVisibleRange();
        double transformedLength = transformedVisibleRange.getLength();
        if (MathUtil.isNearZero(transformedLength)) {
            coefficient.scale = 0.0;
            coefficient.offset = dataPos.start;
            return;
        }

        coefficient.scale = (dataPos.end - dataPos.start) / transformedLength;
        coefficient.offset = -transformedVisibleRange.getMin() * coefficient.scale + dataPos.start;
    }

    /// Computes the source-space visible range that preserves the current on-screen anchoring of
    /// `axis` after a cartesian plot-rectangle resize.
    ///
    /// The calculation is performed in transformed-axis space and converted back through the
    /// current [AxisTransformer] when necessary. Bounded axes are clamped back into their data
    /// range by sliding the opposite bound so the resized span is preserved.
    ///
    /// @param previousRect the old plot rectangle
    /// @param currentRect the new plot rectangle
    /// @param axis the axis whose manual visible range should be adjusted
    /// @return the adjusted visible range, or `null` when the relevant display span did not change
    ///     or the transformed range cannot be inverted
    DataInterval computeVisibleRangeAfterResize(Rectangle previousRect, Rectangle currentRect,
                                                Axis axis) {
        if (previousRect.isEmpty() || currentRect.isEmpty())
            return null;

        boolean horizontalAxis = isHorizontalAxis(axis);
        if (horizontalAxis) {
            if (previousRect.width == currentRect.width)
                return null;
        } else if (previousRect.height == currentRect.height) {
            return null;
        }

        DataInterval transformedVisibleRange = axis.getTVisibleRange();
        DataPos dataPos = new DataPos();
        resolveAxisDisplayPositions(previousRect, axis, dataPos);
        double previousDisplayLength = dataPos.end - dataPos.start;
        resolveAxisDisplayPositions(currentRect, axis, dataPos);
        double currentDisplayLength = dataPos.end - dataPos.start;
        double transformedLengthDelta = (currentDisplayLength - previousDisplayLength)
                * transformedVisibleRange.getLength() / previousDisplayLength;
        boolean growsTowardMax = horizontalAxis != axis.isReversed();

        if (growsTowardMax)
            transformedVisibleRange.setMax(transformedVisibleRange.getMax() + transformedLengthDelta);
        else
            transformedVisibleRange.setMin(transformedVisibleRange.getMin() - transformedLengthDelta);

        AxisTransformer transformer = axis.getTransformer();
        if (transformer != null)
            try {
                transformedVisibleRange = transformer.inverse(transformedVisibleRange);
            } catch (AxisTransformerException e) {
                e.printStackTrace();
                return null;
            }

        if (axis.isBounded()) {
            if (growsTowardMax) {
                double maxOverflow = axis.getDataMax() - transformedVisibleRange.getMax();
                if (maxOverflow < 0.0)
                    transformedVisibleRange.setMin(transformedVisibleRange.getMin() + maxOverflow);
            } else {
                double minOverflow = axis.getDataMin() - transformedVisibleRange.getMin();
                if (minOverflow > 0.0)
                    transformedVisibleRange.setMax(transformedVisibleRange.getMax() + minOverflow);
            }
        }

        return transformedVisibleRange;
    }

    @Override
    void computeDataCoefficient(Rectangle rect, Axis axis, DataPos dataPos,
                                Coefficient coefficient) {
        DataInterval transformedVisibleRange = axis.getTVisibleRange();
        double displayLength = dataPos.end - dataPos.start;
        if (MathUtil.isNearZero(displayLength)) {
            coefficient.scale = 0.0;
            coefficient.offset = transformedVisibleRange.getMin();
            return;
        }

        coefficient.scale = transformedVisibleRange.getLength() / displayLength;
        coefficient.offset = transformedVisibleRange.getMin() - dataPos.start * coefficient.scale;
    }

    /// Returns the display-space heading used by scale and label layout for `axis`.
    ///
    /// Horizontal axes report `0` when values increase to the right and `180` when they increase
    /// to the left. Vertical axes report `90` when values increase upward on screen and `270` when
    /// they increase downward.
    ///
    /// @param axis the axis whose display heading should be described
    /// @return the axis heading in degrees
    public double getAxisAngle(Axis axis) {
        if (isHorizontalAxis(axis))
            return axis.isReversed() ? 180.0 : 0.0;
        return axis.isReversed() ? 270.0 : 90.0;
    }

    @Override
    public double getAxisLength(Rectangle rect, Axis axis) {
        return isHorizontalAxis(axis) ? rect.width : rect.height;
    }

    @Override
    public void getOrigin(Rectangle rect, DoublePoint point) {
        point.x = rect.x;
        point.y = rect.y;
    }

    @Override
    public Shape getShape(DataWindow window, Rectangle rect, CoordinateSystem coords) {
        return toRectangle(window, rect, coords);
    }

    @Override
    public Shape getShape(double value, DataInterval interval, int axisType, Rectangle rect,
                          CoordinateSystem coords) {
        DoublePoints points = new DoublePoints(2);
        if (axisType != Axis.Y_AXIS) {
            points.add(interval.getMin(), value);
            points.add(interval.getMax(), value);
        } else {
            points.add(value, interval.getMin());
            points.add(value, interval.getMax());
        }
        toDisplay(points, rect, coords);

        Line2D.Double line = new Line2D.Double(
                points.getX(0), points.getY(0),
                points.getX(1), points.getY(1));
        points.dispose();
        return line;
    }

    @Override
    public void shiftAlongAxis(Rectangle rect, Axis axis, DoublePoint point, double distance) {
        double adjustedDistance = axis.isReversed() ? -distance : distance;
        if (isHorizontalAxis(axis))
            point.x += adjustedDistance;
        else
            point.y -= adjustedDistance;
    }

    @Override
    public void toData(DoublePoints points, Rectangle rect, CoordinateSystem coords) {
        if (super.isReversed())
            points.swapXYValues();
        super.toData(points, rect, coords);
    }

    /// {@inheritDoc}
    ///
    /// When this projector swaps the horizontal and vertical chart axes, the base affine transform
    /// is transposed to keep the display axes aligned with the swapped layout.
    @Override
    public AffineTransform toDataAffineTransform(Rectangle rect, CoordinateSystem coords) {
        AffineTransform transform = super.toDataAffineTransform(rect, coords);
        if (transform != null && super.isReversed())
            transform = new AffineTransform(
                    transform.getShearX(), transform.getScaleY(),
                    transform.getScaleX(), transform.getShearY(),
                    transform.getTranslateX(), transform.getTranslateY());
        return transform;
    }

    @Override
    public void toDisplay(DoublePoints points, Rectangle rect, CoordinateSystem coords) {
        super.toDisplay(points, rect, coords);
        if (super.isReversed())
            points.swapXYValues();
    }

    /// {@inheritDoc}
    ///
    /// The full currently visible window is returned directly as the pixel-aligned plot rectangle
    /// to avoid round-trip projection drift at the chart edges.
    @Override
    public Rectangle toRectangle(DataWindow window, Rectangle rect, CoordinateSystem coords) {
        if (window.xRange.equals(coords.getXAxis().getVisibleRange())
                && window.yRange.equals(coords.getYAxis().getVisibleRange()))
            return new Rectangle(rect.x, rect.y, rect.width - 1, rect.height - 1);
        return super.toRectangle(window, rect, coords);
    }
}
