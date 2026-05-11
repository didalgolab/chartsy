package one.chartsy.charting;

import java.awt.Color;
import java.awt.Paint;
import java.awt.PaintContext;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.ColorModel;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;

import one.chartsy.charting.event.AxisChangeEvent;
import one.chartsy.charting.event.AxisListener;
import one.chartsy.charting.event.AxisRangeEvent;
import one.chartsy.charting.event.ChartAreaEvent;
import one.chartsy.charting.event.ChartListener;
import one.chartsy.charting.util.java2d.GradientUtil;
import one.chartsy.charting.util.java2d.LinearGradientPaint;
import one.chartsy.charting.util.java2d.MultipleGradientPaintConstants;
import one.chartsy.charting.util.java2d.RadialGradientPaint;

/// Paint whose stop coordinates live in chart data space instead of fixed user-space pixels.
///
/// The paint interprets each entry in `values` as a coordinate on one chart axis and pairs it with
/// the color at the same index. X-axis constructors anchor those stops on the chart x axis. Y-axis
/// constructors anchor them on one selected y-axis slot. The resulting gradient is then projected
/// through the chart's current coordinate system, so the color bands follow scrolling, zooming,
/// transformer changes, and chart-area relayout.
///
/// Cartesian charts resolve to a [LinearGradientPaint]. Non-cartesian chart types resolve to a
/// [RadialGradientPaint] centered on the current plot rectangle. The stop order is therefore
/// significant: callers should supply values in axis order so interpolation stays monotonic.
///
/// Constructor arguments are cloned, and [#getValues()] plus [#getColors()] return defensive
/// copies. Instances remain mutable through the protected [#setValues(double[])] and
/// [#setColors(Color[])] hooks so subclasses can regenerate their definition before each paint
/// rebuild.
///
/// ### Lifecycle
///
/// Listener registration is lazy. The first paint request attaches listeners to the owning chart
/// and to both axes that influence the final geometry. Call [#dispose()] when the paint will no
/// longer be reused so those listeners can be removed and the chart can be released.
public class ValueGradientPaint implements Paint, Serializable, MultipleGradientPaintConstants {

    /// Serializable axis listener that invalidates the cached paint after axis-level changes.
    ///
    /// The next paint pass rebuilds the gradient from the axis's current state, so the listener
    /// only needs to drop the cached geometry instead of recomputing it eagerly.
    private final class SerAxisListener implements AxisListener, Serializable {

        @Override
        public void axisChanged(AxisChangeEvent event) {
            if (event.getType() != AxisChangeEvent.ADJUSTMENT_CHANGE)
                invalidatePaint();
        }

        @Override
        public void axisRangeChanged(AxisRangeEvent event) {
            if (event.isAboutToChangeEvent() && event.isVisibleRangeEvent())
                invalidatePaint();
        }
    }

    /// Serializable chart listener that invalidates the cached paint after plot-area relayout.
    private final class SerChartListener implements ChartListener, Serializable {

        @Override
        public void chartAreaChanged(ChartAreaEvent event) {
            invalidatePaint();
        }
    }

    private final Chart chart;
    private final int gradientAxisType;
    private final int yAxisIndex;
    private double[] xCoordinates;
    private double[] yCoordinates;
    private Color[] colors;
    private final short colorSpace;
    private final boolean clampToDataRange;
    private ChartListener chartListener;
    private AxisListener axisListener;
    private transient boolean paintValid;
    private transient Paint resolvedPaint;
    private int chartType;

    /// Creates a gradient whose stop values are expressed on the chart x axis.
    ///
    /// The paint uses y-axis slot `0` for the perpendicular coordinate system. `values` and
    /// `colors` are consumed positionally and should therefore have matching lengths.
    public ValueGradientPaint(Chart chart, double[] values, Color[] colors) {
        this(chart, values, colors, false);
    }

    /// Creates an x-axis gradient and optionally clamps generated sample points to the chart's data
    /// ranges before projection.
    public ValueGradientPaint(Chart chart, double[] values, Color[] colors, boolean clampToDataRange) {
        this(chart, values, colors, SRGB, clampToDataRange);
    }

    /// Creates an x-axis gradient with an explicit interpolation color space.
    ///
    /// @param colorSpace one of [#SRGB] or [#LINEAR_RGB]
    public ValueGradientPaint(Chart chart, double[] values, Color[] colors, short colorSpace, boolean clampToDataRange) {
        this(chart, 0, values.clone(), newZeroArray(values.length), colors, colorSpace, Axis.X_AXIS,
                clampToDataRange);
    }

    /// Creates a gradient whose stop values are expressed on one y axis.
    ///
    /// @param axisIndex chart y-axis slot whose coordinate system should be used for projection
    public ValueGradientPaint(Chart chart, int axisIndex, double[] values, Color[] colors) {
        this(chart, axisIndex, values, colors, false);
    }

    /// Creates a y-axis gradient and optionally clamps generated sample points to the chart's data
    /// ranges before projection.
    ///
    /// @param axisIndex chart y-axis slot whose coordinate system should be used for projection
    public ValueGradientPaint(Chart chart, int axisIndex, double[] values, Color[] colors, boolean clampToDataRange) {
        this(chart, axisIndex, values, colors, SRGB, clampToDataRange);
    }

    /// Creates a y-axis gradient with an explicit interpolation color space.
    ///
    /// @param axisIndex chart y-axis slot whose coordinate system should be used for projection
    /// @param colorSpace one of [#SRGB] or [#LINEAR_RGB]
    public ValueGradientPaint(Chart chart, int axisIndex, double[] values, Color[] colors, short colorSpace,
            boolean clampToDataRange) {
        this(chart, axisIndex, newZeroArray(values.length), values.clone(), colors, colorSpace, Axis.Y_AXIS,
                clampToDataRange);
    }

    private ValueGradientPaint(Chart chart, int yAxisIndex, double[] xCoordinates, double[] yCoordinates,
            Color[] colors, short colorSpace, int gradientAxisType, boolean clampToDataRange) {
        paintValid = false;
        this.chart = chart;
        this.yAxisIndex = yAxisIndex;
        this.xCoordinates = xCoordinates;
        this.yCoordinates = yCoordinates;
        this.colors = colors.clone();
        this.colorSpace = colorSpace;
        this.gradientAxisType = gradientAxisType;
        this.clampToDataRange = clampToDataRange;
        chartType = chart.getType();
    }

    private static double[] newZeroArray(int length) {
        double[] values = new double[length];
        Arrays.fill(values, 0.0);
        return values;
    }

    private static boolean variesAlongX(DoublePoints points) {
        for (int index = 1; index < points.size(); index++) {
            if (points.getX(index - 1) != points.getX(index))
                return true;
        }
        return false;
    }

    private Color fallbackColor() {
        return (colors == null) ? Color.black : colors[0];
    }

    final void invalidatePaint() {
        paintValid = false;
    }

    private void clampToDataRanges(DoublePoints points) {
        DataInterval xRange = chart.getXAxis().getDataRange();
        DataInterval yRange = chart.getYAxis(yAxisIndex).getDataRange();
        double[] xValues = points.getXValues();
        double[] yValues = points.getYValues();
        for (int index = 0; index < points.size(); index++) {
            xValues[index] = xRange.clamp(xValues[index]);
            yValues[index] = yRange.clamp(yValues[index]);
        }
    }

    /// Returns the axis whose coordinates define this gradient's stop values.
    public final Axis getAxis() {
        return (gradientAxisType == Axis.X_AXIS) ? chart.getXAxis() : chart.getYAxis(yAxisIndex);
    }

    private Axis getOrthogonalAxis() {
        return (gradientAxisType != Axis.X_AXIS) ? chart.getXAxis() : chart.getYAxis(yAxisIndex);
    }

    private void ensurePaintUpToDate() {
        if (paintValid && chartType == chart.getType())
            return;

        chartType = chart.getType();
        if (chartListener == null) {
            chartListener = new SerChartListener();
            chart.addChartListener(chartListener);
        }
        if (axisListener == null) {
            axisListener = new SerAxisListener();
            getAxis().addAxisListener(axisListener);
            getOrthogonalAxis().addAxisListener(axisListener);
        }

        update();
        paintValid = true;
        rebuildPaint();
    }

    private void updateLinearPaint(DoublePoints points) {
        Point2D.Double startPoint = new Point2D.Double(points.getX(0), points.getY(0));
        Point2D.Double endPoint = new Point2D.Double();
        int stopCount = points.size();
        float[] stops = new float[stopCount];
        stops[0] = 0.0f;

        if (variesAlongX(points)) {
            endPoint.setLocation(points.getX(stopCount - 1), points.getY(0));
            double span = endPoint.getX() - startPoint.getX();
            for (int index = 1; index < stopCount; index++) {
                stops[index] = (float) ((points.getX(index) - startPoint.getX()) / span);
            }
        } else {
            endPoint.setLocation(points.getX(0), points.getY(stopCount - 1));
            double span = endPoint.getY() - startPoint.getY();
            if (span == 0.0) {
                resolvedPaint = fallbackColor();
                return;
            }
            for (int index = 1; index < stopCount; index++) {
                stops[index] = (float) ((points.getY(index) - startPoint.getY()) / span);
            }
        }

        resolvedPaint = new LinearGradientPaint(startPoint, endPoint, stops, colors, SPREAD_PAD, colorSpace, null,
                false);
    }

    private void rebuildPaint() {
        try {
            DoublePoints displayPoints = createDisplayPoints();
            try {
                if (chart.getType() != Chart.CARTESIAN)
                    updateRadialPaint(displayPoints);
                else
                    updateLinearPaint(displayPoints);
            } finally {
                displayPoints.dispose();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            resolvedPaint = fallbackColor();
        }
    }

    private void updateRadialPaint(DoublePoints points) {
        Rectangle plotRect = chart.getChartArea().getPlotRect();
        double centerX = plotRect.getCenterX();
        double centerY = plotRect.getCenterY();
        int stopCount = points.size();
        double[] distances = new double[stopCount];
        for (int index = 0; index < stopCount; index++) {
            distances[index] = Math.hypot(points.getX(index) - centerX, points.getY(index) - centerY);
        }

        float radius;
        float[] stops = new float[stopCount];
        Color[] gradientColors = colors;
        if (distances[stopCount - 1] >= distances[0]) {
            radius = (float) distances[stopCount - 1];
            if (radius == 0.0f) {
                resolvedPaint = fallbackColor();
                return;
            }
            stops[0] = (float) (distances[0] / radius);
            for (int index = 1; index < stopCount; index++) {
                if (distances[index] < distances[index - 1]) {
                    resolvedPaint = fallbackColor();
                    return;
                }
                stops[index] = (float) (distances[index] / radius);
            }
        } else {
            radius = (float) distances[0];
            if (radius == 0.0f) {
                resolvedPaint = fallbackColor();
                return;
            }
            stops[stopCount - 1] = 1.0f;
            for (int index = 1; index < stopCount; index++) {
                if (distances[index] > distances[index - 1]) {
                    resolvedPaint = fallbackColor();
                    return;
                }
                stops[stopCount - index - 1] = (float) (distances[index] / radius);
            }
            gradientColors = gradientColors.clone();
            Collections.reverse(Arrays.asList(gradientColors));
        }

        Point2D.Double center = new Point2D.Double(centerX, centerY);
        resolvedPaint = new RadialGradientPaint(center, radius, stops, gradientColors, center, SPREAD_PAD, colorSpace,
                null, false);
    }

    /// Releases listeners and cached paint state held by this instance.
    ///
    /// After disposal the object may still be reused, but the next paint request will attach fresh
    /// listeners and rebuild the gradient from scratch.
    public void dispose() {
        if (chartListener != null) {
            chart.removeChartListener(chartListener);
            chartListener = null;
        }
        if (axisListener != null) {
            getAxis().removeAxisListener(axisListener);
            getOrthogonalAxis().removeAxisListener(axisListener);
            axisListener = null;
        }
        resolvedPaint = null;
        invalidatePaint();
    }

    private DoublePoints createDisplayPoints() {
        double orthogonalValue = getOrthogonalAxis().getVisibleMin();
        double[] orthogonalCoordinates = (gradientAxisType != Axis.X_AXIS) ? xCoordinates : yCoordinates;
        Arrays.fill(orthogonalCoordinates, orthogonalValue);

        DoublePoints points = new DoublePoints();
        points.add(xCoordinates, yCoordinates, xCoordinates.length);
        if (clampToDataRange)
            clampToDataRanges(points);
        chart.toDisplay(points, yAxisIndex);
        return points;
    }

    /// Returns a defensive copy of the gradient colors.
    public Color[] getColors() {
        return colors.clone();
    }

    /// Samples the resolved gradient at one display-space point.
    ///
    /// The current implementation projects `displayX` and `displayY` back into chart data space
    /// and ignores the remaining compatibility parameters.
    public int getPixelARGB(int ignored1, int ignored2, double ignored3, double ignored4, double ignored5,
            double displayX, double displayY, double ignored8) {
        DoublePoints point = new DoublePoints(displayX, displayY);
        chart.getProjector2D().toData(point, chart.getProjectorRect(), chart.getCoordinateSystem(yAxisIndex));
        double xValue = point.getX(0);
        double yValue = point.getY(0);
        point.dispose();

        double[] gradientValues = (gradientAxisType != Axis.X_AXIS) ? yCoordinates : xCoordinates;
        double sampleValue = (gradientAxisType != Axis.X_AXIS) ? yValue : xValue;
        if (Double.isNaN(sampleValue))
            return 0;
        if (sampleValue <= gradientValues[0])
            return colors[0].getRGB() | 0xff000000;

        int stopCount = gradientValues.length;
        if (sampleValue >= gradientValues[stopCount - 1])
            return colors[stopCount - 1].getRGB() | 0xff000000;

        int low = 0;
        int high = stopCount - 1;
        while (high - low > 1) {
            int middle = (high + low) >> 1;
            if (sampleValue == gradientValues[middle])
                return colors[middle].getRGB() | 0xff000000;
            if (sampleValue >= gradientValues[middle])
                low = middle;
            else
                high = middle;
        }

        double lowerValue = gradientValues[low];
        double upperValue = gradientValues[low + 1];
        float fraction = (float) ((sampleValue - lowerValue) / (upperValue - lowerValue));
        int lowerColor = colors[low].getRGB();
        int upperColor = colors[low + 1].getRGB();
        if (colorSpace != LINEAR_RGB)
            return GradientUtil.interpolate(lowerColor, upperColor, fraction);

        lowerColor = GradientUtil.convertEntireColorSRGBtoLinearRGB(lowerColor);
        upperColor = GradientUtil.convertEntireColorSRGBtoLinearRGB(upperColor);
        int interpolatedColor = GradientUtil.interpolate(lowerColor, upperColor, fraction);
        return GradientUtil.convertEntireColorLinearRGBtoSRGB(interpolatedColor);
    }

    /// Creates an AWT [PaintContext] for the chart's current projection and plot geometry.
    ///
    /// The paint definition is rebuilt lazily on demand. If gradient resolution fails, the context
    /// falls back to a solid fill based on the first configured color.
    @Override
    public PaintContext createContext(ColorModel colorModel, Rectangle deviceBounds, Rectangle2D userBounds,
            AffineTransform transform, RenderingHints hints) {
        ensurePaintUpToDate();
        if (resolvedPaint != null) {
            try {
                return resolvedPaint.createContext(colorModel, deviceBounds, userBounds, transform, hints);
            } catch (Exception ignored) {
                // Fall through to the solid-color fallback below.
            }
        }
        return fallbackColor().createContext(colorModel, deviceBounds, userBounds, transform, hints);
    }

    @Override
    public int getTransparency() {
        ensurePaintUpToDate();
        return (resolvedPaint == null) ? Color.black.getTransparency() : resolvedPaint.getTransparency();
    }

    /// Returns a defensive copy of the stop values on the axis returned by [#getAxis()].
    public double[] getValues() {
        return ((gradientAxisType != Axis.X_AXIS) ? yCoordinates : xCoordinates).clone();
    }

    /// Replaces the configured gradient colors and invalidates the cached paint.
    ///
    /// Subclasses use this when [#update()] derives a new color ramp from current chart state.
    protected void setColors(Color[] colors) {
        this.colors = colors.clone();
        invalidatePaint();
    }

    /// Replaces the stop values on the axis returned by [#getAxis()].
    ///
    /// The supplied values are cloned. The perpendicular coordinate array is reset and will be
    /// repopulated from the orthogonal axis's current visible minimum during the next rebuild.
    protected void setValues(double[] values) {
        if (gradientAxisType != Axis.X_AXIS) {
            xCoordinates = newZeroArray(values.length);
            yCoordinates = values.clone();
        } else {
            xCoordinates = values.clone();
            yCoordinates = newZeroArray(values.length);
        }
        invalidatePaint();
    }

    /// Hook invoked immediately before the paint geometry is rebuilt.
    ///
    /// Subclasses can override this to refresh values or colors from the current chart state.
    protected void update() {
    }
}
