package one.chartsy.charting.internal;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;

import one.chartsy.charting.Axis;
import one.chartsy.charting.CoordinateSystem;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.DoublePoint;
import one.chartsy.charting.DoublePoints;
import one.chartsy.charting.util.MathUtil;

/// Projects chart data into polar display geometry.
///
/// The x axis is interpreted as an angular domain measured in degrees and the y axis is
/// interpreted as radial distance from the plot-center origin. General polar charts use this
/// projector directly, pie rendering reuses it with symmetric radial mapping disabled and radius
/// edges forced visible, and [RadarProjector] overrides the constant-radius geometry to produce
/// polygon webs.
///
/// Symmetric mode centers the radial mapping on zero so positive and negative y magnitudes occupy
/// equal distance from the origin. Non-symmetric mode maps the full visible y range outward from
/// the center only.
///
/// The final display-space step between polar and cartesian coordinates is nonlinear, so
/// [#toDataAffineTransform(Rectangle, CoordinateSystem)] always returns `null`.
public class PolarProjector extends AbstractProjector {
    private double startingAngle;
    private double angleRange;
    private boolean symmetric;
    private boolean showingRadius;

    /// Creates a full-circle projector whose zero angle points right and whose radial mapping is
    /// symmetric around the origin.
    public PolarProjector() {
        startingAngle = 0.0;
        angleRange = 360.0;
        symmetric = true;
        showingRadius = false;
    }

    private void convertPolarDisplayToCartesian(DoublePoints points, Rectangle rect) {
        int pointCount = points.size();
        double[] xValues = points.getXValues();
        double[] yValues = points.getYValues();
        double centerX = rect.x + rect.width / 2.0;
        double centerY = rect.y + rect.height / 2.0;
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            double radius = yValues[pointIndex];
            double angle = Math.toRadians(xValues[pointIndex] + startingAngle);
            xValues[pointIndex] = centerX + radius * Math.cos(angle);
            yValues[pointIndex] = centerY - radius * Math.sin(angle);
        }
    }

    @Override
    void resolveAxisDisplayPositions(Rectangle rect, Axis axis, AbstractProjector.DataPos dataPos) {
        if (axis.isXAxis()) {
            dataPos.start = 0.0;
            dataPos.end = angleRange;
        } else {
            double radius = Math.min(rect.width, rect.height) / 2.0 - 1.0;
            if (!symmetric) {
                dataPos.start = 0.0;
                dataPos.end = radius;
            } else {
                dataPos.start = -radius;
                dataPos.end = radius;
            }
        }
    }

    @Override
    void computeDisplayCoefficient(Rectangle rect, Axis axis, AbstractProjector.DataPos dataPos,
                                   AbstractProjector.Coefficient coefficient) {
        DataInterval transformedVisibleRange = axis.getTVisibleRange();
        double transformedLength = transformedVisibleRange.getLength();
        if (MathUtil.isNearZero(transformedLength)) {
            coefficient.scale = 0.0;
            coefficient.offset = dataPos.start;
            return;
        }

        if (symmetric && !axis.isXAxis()) {
            double maxDistanceFromOrigin = Math.max(
                    Math.abs(transformedVisibleRange.getMin()),
                    Math.abs(transformedVisibleRange.getMax()));
            transformedVisibleRange.set(-maxDistanceFromOrigin, maxDistanceFromOrigin);
            transformedLength = 2.0 * maxDistanceFromOrigin;
        }

        coefficient.scale = (dataPos.end - dataPos.start) / transformedLength;
        coefficient.offset = -transformedVisibleRange.getMin() * coefficient.scale + dataPos.start;
    }

    private void convertCartesianToPolarDisplay(DoublePoints points, Rectangle rect) {
        int pointCount = points.size();
        double[] xValues = points.getXValues();
        double[] yValues = points.getYValues();
        double centerX = rect.x + rect.width / 2.0;
        double centerY = rect.y + rect.height / 2.0;
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            double translatedX = xValues[pointIndex] - centerX;
            double translatedY = centerY - yValues[pointIndex];
            double angle = Math.toDegrees(Math.atan2(translatedY, translatedX)) - startingAngle;
            if (angle < 0.0)
                angle += 360.0;
            xValues[pointIndex] = angle;
            yValues[pointIndex] = Math.hypot(translatedX, translatedY);
        }
    }

    @Override
    void computeDataCoefficient(Rectangle rect, Axis axis, AbstractProjector.DataPos dataPos,
                                AbstractProjector.Coefficient coefficient) {
        DataInterval transformedVisibleRange = axis.getTVisibleRange();
        double displayLength = dataPos.end - dataPos.start;
        if (MathUtil.isNearZero(displayLength)) {
            coefficient.scale = 0.0;
            coefficient.offset = transformedVisibleRange.getMin();
            return;
        }

        if (symmetric && !axis.isXAxis()) {
            double maxDistanceFromOrigin = Math.max(
                    Math.abs(transformedVisibleRange.getMin()),
                    Math.abs(transformedVisibleRange.getMax()));
            transformedVisibleRange.set(-maxDistanceFromOrigin, maxDistanceFromOrigin);
        }

        coefficient.scale = transformedVisibleRange.getLength() / displayLength;
        coefficient.offset = transformedVisibleRange.getMin() - dataPos.start * coefficient.scale;
    }

    /// Returns the display angle in degrees assigned to one x value.
    ///
    /// Circular scale layout uses this to orient labels and titles along the current polar x-axis
    /// geometry.
    ///
    /// @param value the x-axis value to project
    /// @param rect the projector bounds in display coordinates
    /// @param coords the coordinate system whose x axis defines the angular mapping
    /// @return the projected angle in degrees after the current starting-angle offset is applied
    public double getAngleDeg(double value, Rectangle rect, CoordinateSystem coords) {
        DoublePoints point = new DoublePoints(value, coords.getYAxis().getVisibleMax());
        super.toDisplay(point, rect, coords);
        double angle = point.getX(0);
        point.dispose();
        return angle + startingAngle;
    }

    @Override
    public double getAxisLength(Rectangle rect, Axis axis) {
        double diameter = Math.min(rect.width, rect.height);
        if (!axis.isXAxis())
            return diameter / 2.0;
        return diameter * 360.0 / angleRange;
    }

    @Override
    public void getOrigin(Rectangle rect, DoublePoint point) {
        point.x = rect.x + rect.width / 2.0;
        point.y = rect.y + rect.height / 2.0;
    }

    /// Returns the angular span in degrees currently assigned to the visible x-axis range.
    public double getRange() {
        return angleRange;
    }

    /// Returns the projected sector or annulus representing `window`.
    ///
    /// Symmetric projection collapses the inner radius to the polar origin, so the shape expands
    /// from the center outward. Non-symmetric projection preserves both radial bounds from
    /// `window`.
    @Override
    public Shape getShape(DataWindow window, Rectangle rect, CoordinateSystem coords) {
        if (rect.isEmpty())
            return new Rectangle();

        DoublePoints corners = new DoublePoints(2);
        corners.add(window.getXMin(), window.getYMin());
        corners.add(window.getXMax(), window.getYMax());
        super.toDisplay(corners, rect, coords);
        corners.setX(0, corners.getX(0) + startingAngle);
        corners.setX(1, corners.getX(1) + startingAngle);
        if (symmetric)
            corners.setY(0, 0.0);

        DoublePoint origin = new DoublePoint();
        getOrigin(rect, origin);

        double startAngle = corners.getX(0);
        double angleExtent = corners.getX(1) - corners.getX(0);
        boolean connectRadialEdge = showingRadius
                || angleExtent != 360.0 * Math.floor(angleExtent / 360.0);

        GeneralPath path = new GeneralPath();
        Arc2D.Double arc = new Arc2D.Double();
        arc.setArcByCenter(origin.x, origin.y, corners.getY(1), startAngle, angleExtent, Arc2D.OPEN);
        path.append(arc, false);

        arc.setArcByCenter(origin.x, origin.y, corners.getY(0), corners.getX(1), -angleExtent,
                Arc2D.OPEN);
        path.append(arc, connectRadialEdge);
        path.closePath();
        corners.dispose();
        return path;
    }

    /// Returns the projected shape for one constant-axis line or band boundary.
    ///
    /// Constant x values become radial lines. Constant y values become circular arcs centered on
    /// the plot origin.
    @Override
    public Shape getShape(double value, DataInterval interval, int axisType, Rectangle rect,
                          CoordinateSystem coords) {
        if (rect.isEmpty())
            return new Rectangle();

        DoublePoints points = new DoublePoints(2);
        if (axisType != Axis.Y_AXIS) {
            points.add(value, interval.getMin());
            points.add(value, interval.getMax());
            toDisplay(points, rect, coords);
            Line2D.Double radialLine = new Line2D.Double(
                    points.getX(0), points.getY(0), points.getX(1), points.getY(1));
            points.dispose();
            return radialLine;
        }

        points.add(interval.getMin(), value);
        points.add(interval.getMax(), value);
        super.toDisplay(points, rect, coords);
        points.setX(0, points.getX(0) + startingAngle);
        points.setX(1, points.getX(1) + startingAngle);

        DoublePoint origin = new DoublePoint();
        getOrigin(rect, origin);

        double startAngle = points.getX(0);
        double angleExtent = points.getX(1) - points.getX(0);
        Arc2D.Double arc = new Arc2D.Double();
        arc.setArcByCenter(origin.x, origin.y, points.getY(1), startAngle, angleExtent, Arc2D.OPEN);
        points.dispose();
        return arc;
    }

    /// Returns the projected shape for one constant-axis line over the current visible range.
    ///
    /// In symmetric mode, constant-x shapes use only the outward radial half of the visible y
    /// range so the same angular line is not duplicated through negative radii.
    @Override
    public Shape getShape(double value, int axisType, Rectangle rect, CoordinateSystem coords) {
        if (rect.isEmpty())
            return new Rectangle();
        if (axisType != Axis.Y_AXIS && symmetric) {
            DataInterval radialRange = coords.getYAxis().getVisibleRange();
            radialRange.setMin(0.0);
            return getShape(value, radialRange, axisType, rect, coords);
        }
        return super.getShape(value, axisType, rect, coords);
    }

    /// Returns the starting-angle offset in degrees applied before cartesian conversion.
    public double getStartingAngle() {
        return startingAngle;
    }

    /// Returns whether full-turn sector shapes keep an explicit radial seam.
    public boolean isShowingRadius() {
        return showingRadius;
    }

    /// Returns whether radial mapping is centered on zero.
    public boolean isSymmetric() {
        return symmetric;
    }

    /// Replaces the angular span used for the visible x-axis range.
    ///
    /// Values outside `[-360, 360]` are wrapped with [MathUtil#mod360(double)] so callers can
    /// supply whole-turn equivalents without first normalizing them.
    ///
    /// @param angleRange the new angular span in degrees
    public void setRange(double angleRange) {
        if (angleRange <= 360.0 && angleRange >= -360.0)
            this.angleRange = angleRange;
        else
            this.angleRange = MathUtil.mod360(angleRange);
    }

    /// Controls whether full-turn sector shapes keep their closing radial edge.
    ///
    /// @param showingRadius `true` to keep the radial seam visible
    public void setShowingRadius(boolean showingRadius) {
        this.showingRadius = showingRadius;
    }

    /// Replaces the starting-angle offset applied to projected x values.
    ///
    /// @param startingAngle the new angular offset in degrees
    public void setStartingAngle(double startingAngle) {
        this.startingAngle = startingAngle;
    }

    /// Controls whether radial mapping is centered on zero or starts at the origin.
    ///
    /// @param symmetric `true` to map positive and negative radii symmetrically around zero
    public void setSymmetric(boolean symmetric) {
        this.symmetric = symmetric;
    }

    /// Moves `point` along the projected direction of `axis`.
    ///
    /// Radial shifts move directly toward or away from the plot center. Angular shifts rotate the
    /// point around that center by an arc-length distance measured at the point's current radius.
    /// Reversed x axes invert the angular rotation direction.
    @Override
    public void shiftAlongAxis(Rectangle rect, Axis axis, DoublePoint point, double distance) {
        double adjustedDistance = distance;
        if (adjustedDistance == 0.0)
            return;

        double centerX = rect.x + rect.width / 2.0;
        double centerY = rect.y + rect.height / 2.0;
        double translatedX = point.x - centerX;
        double translatedY = point.y - centerY;
        double radius = Math.hypot(translatedX, translatedY);
        if (radius <= 1.0)
            return;

        if (!axis.isXAxis()) {
            double scale = adjustedDistance / radius;
            point.x += translatedX * scale;
            point.y += translatedY * scale;
        } else {
            if (axis.isReversed())
                adjustedDistance = -adjustedDistance;
            double angle = adjustedDistance / radius + Math.atan2(centerY - point.y, translatedX);
            point.x = centerX + radius * Math.cos(angle);
            point.y = centerY - radius * Math.sin(angle);
        }
    }

    @Override
    public void toData(DoublePoints points, Rectangle rect, CoordinateSystem coords) {
        convertCartesianToPolarDisplay(points, rect);
        super.toData(points, rect, coords);
    }

    /// Returns `null` because the polar-to-cartesian display step is nonlinear.
    @Override
    public AffineTransform toDataAffineTransform(Rectangle rect, CoordinateSystem coords) {
        return null;
    }

    /// Converts one display selection rectangle into the corresponding polar data window.
    ///
    /// Non-symmetric polar selections can become angularly ambiguous when they include the origin
    /// or cross the `0`/`360` seam. In those cases the returned x range falls back to the current
    /// visible x range while the y range still reflects the visible radial range.
    @Override
    public DataWindow toDataWindow(Rectangle selection, Rectangle rect, CoordinateSystem coords) {
        DataWindow dataWindow = new DataWindow();
        dataWindow.yRange.set(coords.getYAxis().getVisibleMin(), coords.getYAxis().getVisibleMax());
        if (symmetric) {
            dataWindow.xRange.set(coords.getXAxis().getVisibleMin(), coords.getXAxis().getVisibleMax());
            return dataWindow;
        }

        DoublePoint origin = new DoublePoint();
        getOrigin(rect, origin);
        boolean selectionContainsOrigin = selection.contains(origin.x, origin.y);

        DoublePoints selectionCorners = new DoublePoints(4);
        selectionCorners.add(selection.x, selection.y);
        selectionCorners.add(selection.x + selection.width, selection.y);
        selectionCorners.add(selection.x + selection.width, selection.y + selection.height);
        selectionCorners.add(selection.x, selection.y + selection.height);

        convertCartesianToPolarDisplay(selectionCorners, rect);
        double minDisplayAngle = Math.min(
                Math.min(selectionCorners.getX(0), selectionCorners.getX(1)),
                Math.min(selectionCorners.getX(2), selectionCorners.getX(3)));
        double maxDisplayAngle = Math.max(
                Math.max(selectionCorners.getX(0), selectionCorners.getX(1)),
                Math.max(selectionCorners.getX(2), selectionCorners.getX(3)));
        boolean wrapsAcrossAngleSeam = maxDisplayAngle - minDisplayAngle > 180.0;

        super.toData(selectionCorners, rect, coords);
        double minAngle = Math.min(
                Math.min(selectionCorners.getX(0), selectionCorners.getX(1)),
                Math.min(selectionCorners.getX(2), selectionCorners.getX(3)));
        double maxAngle = Math.max(
                Math.max(selectionCorners.getX(0), selectionCorners.getX(1)),
                Math.max(selectionCorners.getX(2), selectionCorners.getX(3)));

        if (!selectionContainsOrigin && !wrapsAcrossAngleSeam)
            dataWindow.xRange.set(minAngle, maxAngle);
        else
            dataWindow.xRange.set(coords.getXAxis().getVisibleMin(), coords.getXAxis().getVisibleMax());

        selectionCorners.dispose();
        return dataWindow;
    }

    @Override
    public void toDisplay(DoublePoints points, Rectangle rect, CoordinateSystem coords) {
        super.toDisplay(points, rect, coords);
        convertPolarDisplayToCartesian(points, rect);
    }
}
