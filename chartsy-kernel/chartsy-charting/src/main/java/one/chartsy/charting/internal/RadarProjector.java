package one.chartsy.charting.internal;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.GeneralPath;

import one.chartsy.charting.Axis;
import one.chartsy.charting.CoordinateSystem;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.DoublePoints;

/// `PolarProjector` specialization for radar and spider-chart geometry.
///
/// Radar charts treat the visible x range as integer spoke positions. Constant y values therefore
/// render as closed polygons through those spokes rather than circular arcs, while constant x
/// values still behave like ordinary polar spokes.
public class RadarProjector extends PolarProjector {

    /// Creates a radar projector with outward-only radial mapping.
    public RadarProjector() {
        super.setSymmetric(false);
    }

    /// Returns the outer radar polygon when `window` matches the full visible chart window.
    ///
    /// Partial windows still use the base polar-sector geometry.
    @Override
    public Shape getShape(DataWindow window, Rectangle rect, CoordinateSystem coords) {
        if (!window.equals(coords.getVisibleWindow()))
            return super.getShape(window, rect, coords);

        double visibleRadius = (!coords.getYAxis().isReversed())
                ? window.getYMax()
                : window.getYMin();
        return getShape(visibleRadius, Axis.Y_AXIS, rect, coords);
    }

    /// Returns the radar geometry for one constant-axis line or band boundary.
    ///
    /// When `axisType` is [Axis#Y_AXIS] and `interval` spans the full visible x range, the result
    /// is a closed polygon with one vertex per integer spoke. All other cases fall back to the
    /// base polar implementation.
    @Override
    public Shape getShape(double value, DataInterval interval, int axisType, Rectangle rect,
                          CoordinateSystem coords) {
        if (axisType == Axis.Y_AXIS) {
            DataInterval visibleSpokes = coords.getXAxis().getVisibleRange();
            if (visibleSpokes.equals(interval)) {
                DoublePoints points = new DoublePoints((int) visibleSpokes.getLength());
                for (double spoke = Math.ceil(visibleSpokes.getMin());
                     spoke <= visibleSpokes.getMax();
                     spoke += 1.0) {
                    points.add(spoke, value);
                }
                if (points.size() == 0) {
                    points.dispose();
                    return new Rectangle();
                }

                super.toDisplay(points, rect, coords);
                double[] xValues = points.getXValues();
                double[] yValues = points.getYValues();
                int pointCount = points.size();

                GeneralPath polygon = new GeneralPath(GeneralPath.WIND_NON_ZERO, pointCount + 1);
                polygon.moveTo((float) xValues[0], (float) yValues[0]);
                for (int pointIndex = 1; pointIndex < pointCount; pointIndex++)
                    polygon.lineTo((float) xValues[pointIndex], (float) yValues[pointIndex]);
                polygon.closePath();
                points.dispose();
                return polygon;
            }
        }
        return super.getShape(value, interval, axisType, rect, coords);
    }

    /// Returns the constant-y radar polygon over the full visible spoke range.
    @Override
    public Shape getShape(double value, int axisType, Rectangle rect, CoordinateSystem coords) {
        if (axisType != Axis.Y_AXIS)
            return super.getShape(value, axisType, rect, coords);

        DataInterval visibleSpokes = coords.getXAxis().getVisibleRange();
        return getShape(value, visibleSpokes, axisType, rect, coords);
    }
}
