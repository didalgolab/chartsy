package one.chartsy.charting.renderers;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartProjector;
import one.chartsy.charting.CoordinateSystem;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.LegendEntry;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DataPoints;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.graphic.DataRenderingHint;
import one.chartsy.charting.renderers.internal.HiLoDataSet;
import one.chartsy.charting.util.java2d.ShapeUtil;

/// Single-series renderer that paints one logical series from paired dataset values.
///
/// The renderer expects exactly two source datasets. It wraps them in a [HiLoDataSet] so each
/// logical sample becomes one consecutive point pair `[2*n, 2*n + 1]` sharing the same source
/// index. Each pair is then rendered as one [Type] glyph such as a stick, body, arrow, or
/// open/close marker.
///
/// The relative ordering of the pair also selects the style. Pairs where the first value is lower
/// than the second use [#getRiseStyle()]; all other pairs use [#getFallStyle()]. That lets the
/// same renderer draw candle bodies, open/close ticks, or generic paired-value series without
/// changing the traversal code.
///
/// Width is expressed as a percentage of one category slot. Callers may optionally preserve half
/// of the category spacing at plot borders, and [#setPixelBodyWidthHint(int)] can force thin bar
/// bodies to snap to a stable odd device-pixel width so bullish and bearish candles remain
/// visible under narrow or high-DPI layouts.
public class SingleHiLoRenderer extends SingleChartRenderer implements VariableWidthRenderer {

    /// Reusable logical item that stores the projected geometry for one paired sample.
    ///
    /// Cartesian bar bodies can switch into a pixel-aligned rectangle mode so drawing and hit
    /// testing use a snapped body instead of the generic polygon approximation.
    class HiLoItem extends SingleChartRenderer.DefaultItem {
        boolean pixelAlignedBody;
        double dataCenterX;

        HiLoItem(int pointCapacity) {
            super(pointCapacity);
            Chart chart = getChart();
            pixelAlignedBody = chart != null && chart.getType() == Chart.CARTESIAN && getType() == Type.BAR;
        }

        /// Rebuilds this item's geometry from the paired values at `pointIndex`.
        void updateGeometry(SingleChartRenderer.Points points, int pointIndex,
                            double firstY, double secondY, double halfWidth) {
            double xShift = getXShift();
            double firstX = points.getXData(pointIndex) + xShift;
            double secondX = points.getXData(pointIndex + 1) + xShift;
            dataCenterX = (points.getXData(pointIndex) + points.getXData(pointIndex + 1)) / 2.0 + xShift;
            if (pixelAlignedBody) {
                super.set(0, firstX - halfWidth, firstY);
                super.set(1, secondX + halfWidth, secondY);
                super.setSize(2);
                return;
            }

            double[] xValues = super.getXValues();
            double[] yValues = super.getYValues();
            int pointCount = switch (getType()) {
                case BAR -> {
                    xValues[0] = firstX - halfWidth;
                    yValues[0] = firstY;
                    xValues[1] = secondX - halfWidth;
                    yValues[1] = secondY;
                    xValues[2] = secondX + halfWidth;
                    yValues[2] = secondY;
                    xValues[3] = firstX + halfWidth;
                    yValues[3] = firstY;
                    xValues[4] = xValues[0];
                    yValues[4] = yValues[0];
                    yield 5;
                }
                case STICK -> {
                    xValues[0] = firstX;
                    yValues[0] = firstY;
                    xValues[1] = secondX;
                    yValues[1] = secondY;
                    yield 2;
                }
                case MARKED -> {
                    xValues[0] = firstX - halfWidth;
                    yValues[0] = firstY;
                    xValues[1] = firstX;
                    yValues[1] = firstY;
                    xValues[2] = secondX;
                    yValues[2] = secondY;
                    xValues[3] = secondX + halfWidth;
                    yValues[3] = secondY;
                    yield 4;
                }
                case ARROW -> {
                    double yDelta = secondY - firstY;
                    xValues[0] = firstX - halfWidth / 2.0;
                    yValues[0] = firstY;
                    xValues[1] = secondX - halfWidth / 2.0;
                    yValues[1] = firstY + yDelta * 0.8;
                    xValues[2] = xValues[1] - halfWidth / 2.0;
                    yValues[2] = yValues[1];
                    xValues[3] = xValues[1] + halfWidth / 2.0;
                    yValues[3] = secondY;
                    xValues[4] = firstX + halfWidth;
                    yValues[4] = yValues[2];
                    xValues[5] = firstX + halfWidth / 2.0;
                    yValues[5] = yValues[4];
                    xValues[6] = xValues[5];
                    yValues[6] = yValues[0];
                    xValues[7] = xValues[0];
                    yValues[7] = yValues[0];
                    yield 8;
                }
            };
            super.setSize(pointCount);
        }

        @Override
        public double distance(PlotStyle style, double x, double y, boolean outlineOnly) {
            if (!pixelAlignedBody)
                return super.distance(style, x, y, outlineOnly);
            Rectangle2D bodyBounds = super.getBounds(style, true, null);
            return outlineOnly
                    ? ShapeUtil.distanceTo(bodyBounds, x, y, null)
                    : (bodyBounds.contains(x, y) ? 0.0 : Double.POSITIVE_INFINITY);
        }

        @Override
        public void draw(Graphics g, PlotStyle style) {
            Object[] sharpState = (getType() == Type.STICK || pixelAlignedBody) ? beginSharpRendering(g) : null;
            try {
                switch (getType()) {
                    case MARKED -> drawMarkers(g, style);
                    case STICK -> {
                        if (g instanceof Graphics2D g2)
                            drawWick(g2, style);
                        else
                            super.draw(g, style);
                    }
                    default -> {
                        if (pixelAlignedBody && g instanceof Graphics2D g2)
                            drawBody(g2, style);
                        else
                            super.draw(g, style);
                    }
                }
            } finally {
                endSharpRendering(g, sharpState);
            }
        }

        private void drawMarkers(Graphics g, PlotStyle style) {
            style.drawLine(g, super.getX(0), super.getY(0), super.getX(1), super.getY(1));
            style.drawLine(g, super.getX(2), super.getY(2), super.getX(3), super.getY(3));
        }

        private Object[] beginSharpRendering(Graphics g) {
            if (!(g instanceof Graphics2D g2))
                return null;
            Object[] state = new Object[]{
                    g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING),
                    g2.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL)
            };
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);
            return state;
        }

        private void endSharpRendering(Graphics g, Object[] state) {
            if (!(g instanceof Graphics2D g2) || state == null)
                return;
            Object antialiasing = (state[0] != null) ? state[0] : RenderingHints.VALUE_ANTIALIAS_DEFAULT;
            Object strokeControl = (state[1] != null) ? state[1] : RenderingHints.VALUE_STROKE_DEFAULT;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antialiasing);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, strokeControl);
        }

        private void drawWick(Graphics2D g2, PlotStyle style) {
            DevicePixelSnapper devicePixels = new DevicePixelSnapper(g2);
            int x = snapCenterX(devicePixels);
            int top = Math.min(devicePixels.snapY(super.getY(0)), devicePixels.snapY(super.getY(1)));
            int bottom = Math.max(devicePixels.snapY(super.getY(0)), devicePixels.snapY(super.getY(1)));
            drawSnappedRect(g2, devicePixels, style.getStrokePaint(), x, top, 1, bottom - top + 1);
        }

        private void drawBody(Graphics2D g2, PlotStyle style) {
            DevicePixelSnapper devicePixels = new DevicePixelSnapper(g2);
            double y1 = super.getY(0);
            double y2 = super.getY(1);
            double x1 = super.getX(0);
            double x2 = super.getX(1);
            int centerX = snapCenterX(devicePixels);
            int width = (pixelBodyWidthHint > 0)
                    ? normalizedBodyWidth(pixelBodyWidthHint)
                    : normalizedBodyWidth(devicePixels.deviceWidth(Math.abs(x2 - x1)));
            int left = centerX - width / 2;
            int top = Math.min(devicePixels.snapY(y1), devicePixels.snapY(y2));
            int bottom = Math.max(devicePixels.snapY(y1), devicePixels.snapY(y2));
            int height = bottom - top + 1;

            if (style.isFillOn()) {
                if (style.isStrokeOn()) {
                    drawSnappedRect(g2, devicePixels, style.getStrokePaint(), left, top, width, height);
                    if (width > 2 && height > 2)
                        drawSnappedRect(g2, devicePixels, style.getFillPaint(), left + 1, top + 1, width - 2, height - 2);
                } else {
                    drawSnappedRect(g2, devicePixels, style.getFillPaint(), left, top, width, height);
                }
            } else if (style.isStrokeOn()) {
                drawSnappedRect(g2, devicePixels, style.getStrokePaint(), left, top, width, 1);
                if (height > 1)
                    drawSnappedRect(g2, devicePixels, style.getStrokePaint(), left, bottom, width, 1);
                drawSnappedRect(g2, devicePixels, style.getStrokePaint(), left, top, 1, height);
                if (width > 1)
                    drawSnappedRect(g2, devicePixels, style.getStrokePaint(), left + width - 1, top, 1, height);
            }
        }

        private int snapCenterX(DevicePixelSnapper devicePixels) {
            Rectangle plotRect = SingleHiLoRenderer.this.getPlotRect();
            CoordinateSystem coordinateSystem = SingleHiLoRenderer.this.getCoordinateSystem();
            if (plotRect == null || coordinateSystem == null || plotRect.width <= 1)
                return devicePixels.snapX((super.getX(0) + super.getX(1)) / 2.0);

            DataInterval visibleRange = coordinateSystem.getXAxis().getVisibleRange();
            double visibleLength = visibleRange.getLength();
            if (!Double.isFinite(dataCenterX) || !Double.isFinite(visibleLength) || visibleLength <= 0.0)
                return devicePixels.snapX((super.getX(0) + super.getX(1)) / 2.0);

            int left = devicePixels.snapX(plotRect.x);
            int right = devicePixels.snapX(plotRect.x + plotRect.width - 1.0);
            int axisLength = right - left;
            if (axisLength <= 0)
                return devicePixels.snapX((super.getX(0) + super.getX(1)) / 2.0);

            double relative = (dataCenterX - visibleRange.getMin()) / visibleLength;
            return left + (int) Math.round(relative * axisLength);
        }

        private void drawSnappedRect(Graphics2D g2, DevicePixelSnapper devicePixels, Paint paint,
                                     int x, int y, int width, int height) {
            if (paint == null || width <= 0 || height <= 0)
                return;
            Paint previousPaint = g2.getPaint();
            try {
                g2.setPaint(paint);
                g2.fill(devicePixels.toUserRect(x, y, width, height));
            } finally {
                g2.setPaint(previousPaint);
            }
        }

        private int normalizedBodyWidth(double width) {
            int pixels = Math.max(1, (int) Math.round(width));
            if (pixels > 1 && (pixels & 1) == 0) {
                int lowerOdd = pixels - 1;
                int upperOdd = pixels + 1;
                pixels = (Math.abs(width - lowerOdd) <= Math.abs(upperOdd - width)) ? lowerOdd : upperOdd;
            }
            return pixels;
        }
    }

    private static final class DevicePixelSnapper {
        private final double scaleX;
        private final double scaleY;
        private final double translateX;
        private final double translateY;

        private DevicePixelSnapper(Graphics2D g2) {
            var transform = g2.getTransform();
            GraphicsConfiguration configuration = g2.getDeviceConfiguration();
            double graphicsScaleX = 1.0;
            double graphicsScaleY = 1.0;
            if (configuration != null) {
                var defaultTransform = configuration.getDefaultTransform();
                graphicsScaleX = Math.abs(defaultTransform.getScaleX());
                graphicsScaleY = Math.abs(defaultTransform.getScaleY());
            }
            double transformScaleX = Math.abs(transform.getScaleX());
            double transformScaleY = Math.abs(transform.getScaleY());
            scaleX = normalizeScale(Math.max(transformScaleX, graphicsScaleX));
            scaleY = normalizeScale(Math.max(transformScaleY, graphicsScaleY));
            translateX = transform.getTranslateX();
            translateY = transform.getTranslateY();
        }

        private static double normalizeScale(double scale) {
            if (!Double.isFinite(scale) || scale <= 0.0)
                return 1.0;
            return scale;
        }

        private int snapX(double userX) {
            return (int) Math.round(userX * scaleX + translateX);
        }

        private int snapY(double userY) {
            return (int) Math.round(userY * scaleY + translateY);
        }

        private double deviceWidth(double userWidth) {
            return userWidth * scaleX;
        }

        private Rectangle2D toUserRect(int deviceX, int deviceY, int deviceWidth, int deviceHeight) {
            return new Rectangle2D.Double(
                    (deviceX - translateX) / scaleX,
                    (deviceY - translateY) / scaleY,
                    deviceWidth / scaleX,
                    deviceHeight / scaleY);
        }
    }

    /// Declares how each paired sample should be painted.
    public enum Type {
        /// Draws a one-pixel wick between the paired values.
        STICK,
        /// Draws a body spanning the configured width between the paired values.
        BAR,
        /// Draws an arrow-shaped body pointing from the first value toward the second.
        ARROW,
        /// Draws open/close-style side markers at the paired values.
        MARKED;

        boolean hasFaceForm() {
            return (this == BAR || this == ARROW);
        }
    }

    private static int strokeThreshold = 6;

    /// Returns the device-width threshold used when deciding whether thin filled bodies may drop
    /// their stroke.
    public static int getStrokeThreshold() {
        return strokeThreshold;
    }

    /// Sets the device-width threshold consulted for thin filled bodies.
    public static void setStrokeThreshold(int strokeThreshold) {
        SingleHiLoRenderer.strokeThreshold = strokeThreshold;
    }

    private PlotStyle fallStyle;
    private double widthPercent;
    private int pixelBodyWidthHint = -1;

    private boolean useCategorySpacingAtBorders;

    private Type type;

    /// Creates a hi/lo renderer with auto styles, bar glyphs, and an `80%` width budget.
    public SingleHiLoRenderer() {
        this(null, null);
    }

    /// Creates a hi/lo renderer with explicit rise/fall styles, bar glyphs, and an `80%` width
    /// budget.
    public SingleHiLoRenderer(PlotStyle riseStyle, PlotStyle fallStyle) {
        this(riseStyle, fallStyle, Type.BAR, 80.0);
    }

    /// Creates a hi/lo renderer with explicit styles, glyph type, and width budget.
    ///
    /// @param riseStyle    style applied when the first paired value is lower than the second
    /// @param fallStyle    style applied when the first paired value is greater than or equal to the
    ///                                                                                                                                          second
    /// @param type         glyph used for each paired sample
    /// @param widthPercent percentage of one category slot reserved for each glyph
    public SingleHiLoRenderer(PlotStyle riseStyle, PlotStyle fallStyle, Type type, double widthPercent) {
        super(riseStyle);
        useCategorySpacingAtBorders = false;
        setFallStyle(fallStyle);
        setType(type);
        setWidthPercent(widthPercent);
    }

    private static int alignPairStart(int index) {
        return (index >= 0) ? index - index % 2 : index;
    }

    private static int alignPairEnd(int index) {
        return (index >= 0) ? index + (1 - index % 2) : index;
    }

    private static boolean isDefinedPair(Double undefinedMarker, double undefinedValue, double firstY, double secondY) {
        return !Double.isNaN(firstY)
                && !Double.isNaN(secondY)
                && (undefinedMarker == null || (firstY != undefinedValue && secondY != undefinedValue));
    }

    @Override
    DataPoints getDataSlice(DataSet dataSet, int fromIndex, int toIndex, int contextPairs) {
        int alignedFromIndex = fromIndex;
        int alignedToIndex = toIndex;
        if (!(dataSet instanceof HiLoDataSet))
            return super.getDataSlice(dataSet, alignedFromIndex, alignedToIndex, contextPairs);

        alignedFromIndex = alignPairStart(alignedFromIndex);
        alignedToIndex = alignPairEnd(alignedToIndex);
        if (contextPairs > 0) {
            alignedFromIndex = Math.max(0, alignedFromIndex - 2 * contextPairs);
            alignedToIndex = Math.min(dataSet.size() - 1, alignedToIndex + 2 * contextPairs);
        }
        return super.getDataSlice(dataSet, alignedFromIndex, alignedToIndex, 0);
    }

    @Override
    DataWindow adjustVisibleWindow(DataWindow window) {
        double halfWidth = getWidth() / 2.0;
        if (useCategorySpacingAtBorders) {
            double categoryHalfWidth = super.getCategoryWidth() / 2.0;
            halfWidth = Math.max(halfWidth, categoryHalfWidth);
        }
        if (halfWidth > 0.0)
            window.xRange.expand(halfWidth);
        return window;
    }

    @Override
    DataPoints getVisibleData(Rectangle plotClip) {
        DataPoints visiblePoints = super.getVisibleData(plotClip);
        if (visiblePoints == null)
            return null;

        DataSet dataSet = super.getRenderedDataSet();
        DataInterval visibleYRange = super.getYAxis().getVisibleRange();
        if (visibleYRange.contains(dataSet.getYRange(null)))
            return visiblePoints;

        int startIndex = 0;
        int limit = visiblePoints.size();
        if (limit < 2)
            return visiblePoints;
        if ((visiblePoints.getIndex(0) & 1) != 0)
            startIndex++;
        if (((limit - startIndex) & 1) != 0)
            limit--;
        if (limit - startIndex <= 0)
            return visiblePoints;

        int[] indices = visiblePoints.getIndices();
        double[] xValues = visiblePoints.getXValues();
        double[] yValues = visiblePoints.getYValues();
        DataPoints filteredPoints = new DataPoints(dataSet, limit - startIndex);
        DataInterval pairYRange = new DataInterval();
        for (int pointIndex = startIndex; pointIndex < limit; pointIndex += 2) {
            double firstY = yValues[pointIndex];
            double secondY = yValues[pointIndex + 1];
            pairYRange.setMin(Math.min(firstY, secondY));
            pairYRange.setMax(Math.max(firstY, secondY));
            if (!pairYRange.intersects(visibleYRange))
                continue;
            filteredPoints.add(xValues[pointIndex], firstY, indices[pointIndex]);
            filteredPoints.add(xValues[pointIndex + 1], secondY, indices[pointIndex + 1]);
        }
        visiblePoints.dispose();
        return filteredPoints;
    }

    @Override
    void forEachItem(SingleChartRenderer.Points points, SingleChartRenderer.ItemAction callback) {
        int startIndex = 0;
        int limit = points.size();
        if (limit < 2)
            return;
        if ((points.getDataIndex(0) & 1) != 0)
            startIndex++;
        if (((limit - startIndex) & 1) != 0)
            limit--;
        if (limit - startIndex <= 0)
            return;

        DataSet dataSet = points.getDataSet();
        DataInterval visibleYRange = super.getYAxis().getVisibleRange();
        boolean clampToVisibleRange = !visibleYRange.contains(dataSet.getYRange(null));
        CoordinateSystem coordinateSystem = super.getCoordinateSystem();
        Rectangle plotRect = super.getPlotRect();
        ChartProjector projector = super.getChart().getLocalProjector2D(plotRect, coordinateSystem);
        Double undefinedMarker = dataSet.getUndefValue();
        double undefinedValue = (undefinedMarker == null) ? 0.0 : undefinedMarker.doubleValue();
        double halfWidth = getWidth() / 2.0;
        PlotStyle riseStyle = getRiseStyle();
        PlotStyle fallStyle = getFallStyle();
        if (getType() == Type.BAR || getType() == Type.ARROW) {
            double axisLength = projector.getAxisLength(plotRect, super.getXAxis());
            double visibleLength = super.getXAxis().getVisibleRange().getLength();
            boolean thickEnoughForStroke = !Double.isFinite(visibleLength)
                    || visibleLength <= 0.0
                    || 2.0 * axisLength * halfWidth / visibleLength > SingleHiLoRenderer.getStrokeThreshold();
            // Pixel-perfect candle bodies must keep their outline. Otherwise setStacked100Percent bullish
            // candle becomes white-on-white under narrower layouts and appears to
            // disappear even though overlays and wicks still render correctly.
            boolean preserveThinBodyStroke = getType() == Type.BAR
                    && (pixelBodyWidthHint > 0 || getChart().getType() == Chart.CARTESIAN);
            if (riseStyle.isFillOn() && !thickEnoughForStroke && !preserveThinBodyStroke)
                riseStyle = riseStyle.setStrokeOn(false);
            if (fallStyle.isFillOn() && !thickEnoughForStroke && !preserveThinBodyStroke)
                fallStyle = fallStyle.setStrokeOn(false);
        }

        HiLoItem item = new HiLoItem(32);
        if (!hasRenderingHints()) {
            for (int pointIndex = startIndex; pointIndex < limit; pointIndex += 2) {
                double firstY = points.getYData(pointIndex);
                double secondY = points.getYData(pointIndex + 1);
                if (!isDefinedPair(undefinedMarker, undefinedValue, firstY, secondY))
                    continue;

                PlotStyle style = (firstY >= secondY) ? fallStyle : riseStyle;
                if (clampToVisibleRange) {
                    firstY = visibleYRange.clamp(firstY);
                    secondY = visibleYRange.clamp(secondY);
                }
                item.updateGeometry(points, pointIndex, firstY, secondY, halfWidth);
                projector.toDisplay(item, plotRect, coordinateSystem);
                callback.processItem(points, pointIndex, item, style);
            }
            return;
        }

        int[] indices = points.getIndices();
        DisplayPoint displayPoint = new DisplayPoint(this, dataSet);
        displayPoint.dataSet = dataSet;
        for (int pointIndex = startIndex; pointIndex < limit; pointIndex += 2) {
            double firstY = points.getYData(pointIndex);
            double secondY = points.getYData(pointIndex + 1);
            if (!isDefinedPair(undefinedMarker, undefinedValue, firstY, secondY))
                continue;

            PlotStyle style;
            if (firstY < secondY) {
                style = riseStyle;
                displayPoint.set(indices[pointIndex], 0.0, 0.0);
            } else {
                style = fallStyle;
                displayPoint.set(indices[pointIndex + 1], 0.0, 0.0);
            }
            DataRenderingHint renderingHint = super.getRenderingHint(displayPoint);
            if (renderingHint != null) {
                style = renderingHint.getStyle(displayPoint, style);
                if (style == null)
                    continue;
            }
            if (clampToVisibleRange) {
                firstY = visibleYRange.clamp(firstY);
                secondY = visibleYRange.clamp(secondY);
            }
            item.updateGeometry(points, pointIndex, firstY, secondY, halfWidth);
            projector.toDisplay(item, plotRect, coordinateSystem);
            callback.processItem(points, pointIndex, item, style);
        }
    }

    @Override
    PlotStyle getBaseStyle(DataSet dataSet, int dataSetIndex) {
        if (dataSet == super.getDataSource().get(0))
            return getRiseStyle();
        return getFallStyle();
    }

    @Override
    protected void chartConnected(Chart previousChart, Chart chart) {
        super.chartConnected(previousChart, chart);
        if (chart != null && getFallStyle() == null)
            setFallStyle(super.makeDefaultStyle());
    }

    @Override
    protected void dataSetsAdded(int fromIndex, int toIndex, DataSet[] dataSets) {
        super.dataSetsAdded(fromIndex, toIndex, dataSets);
        refreshHiLoDataSetBindings();
    }

    @Override
    protected void dataSetsRemoved(int fromIndex, int toIndex, DataSet[] dataSets) {
        super.dataSetsRemoved(fromIndex, toIndex, dataSets);
        refreshHiLoDataSetBindings();
    }

    /// Paints a centered sample glyph inside the provided legend slot.
    ///
    /// Filled glyphs show both rise and fall variants so the legend still communicates the
    /// directional styling even when the current data only exercises one branch.
    @Override
    public void drawLegendMarker(LegendEntry legend, Graphics g, int x, int y, int w, int h) {
        int markerSize = Math.min(w, h);
        int markerX = x + (w - markerSize) / 2;
        int markerY = y + (h - markerSize) / 2;

        switch (getType()) {
            case BAR -> {
                getRiseStyle().plotRect(g, markerX, (int) (markerY + 0.2 * h), markerSize, (int) (h * 0.4));
                getFallStyle().plotRect(g, markerX, (int) (markerY + h * 0.6 - 1.0), markerSize, (int) (h * 0.3));
            }
            case ARROW -> {
                int[] xPoints = {
                        markerX + markerSize / 4,
                        markerX + markerSize / 4,
                        markerX,
                        markerX + markerSize / 2,
                        markerX + markerSize,
                        markerX + 3 * markerSize / 4,
                        markerX + 3 * markerSize / 4,
                        markerX + markerSize / 4
                };
                int[] riseYPoints = {
                        markerY + markerSize / 2,
                        markerY + markerSize / 3,
                        markerY + markerSize / 3,
                        markerY,
                        markerY + markerSize / 3,
                        markerY + markerSize / 3,
                        markerY + markerSize / 2,
                        markerY + markerSize / 2
                };
                int[] fallYPoints = {
                        markerY + markerSize / 2,
                        markerY + 2 * markerSize / 3,
                        markerY + 2 * markerSize / 3,
                        markerY + markerSize,
                        markerY + 2 * markerSize / 3,
                        markerY + 2 * markerSize / 3,
                        markerY + markerSize / 2,
                        markerY + markerSize / 2
                };
                getRiseStyle().plotPoints(g, xPoints, riseYPoints, xPoints.length);
                getFallStyle().plotPoints(g, xPoints, fallYPoints, xPoints.length);
            }
            case MARKED -> {
                getLegendStyle().drawLine(g, markerX, markerY + 2 * markerSize / 3, markerX + markerSize / 2, markerY + 2 * markerSize / 3);
                getLegendStyle().drawLine(g, markerX + markerSize / 2, markerY + markerSize / 3, markerX + markerSize, markerY + markerSize / 3);
            }
            case STICK ->
                    getLegendStyle().drawLine(g, markerX + markerSize / 2, markerY, markerX + markerSize / 2, markerY + markerSize);
        }
    }

    @Override
    int getDisplayQueryPadding() {
        return 1;
    }

    /// Returns bounds expanded to whole paired samples before delegating to the base renderer.
    ///
    /// When `dataSet` is a [HiLoDataSet], odd start or end indices are widened so callers never
    /// clip just one side of a logical hi/lo pair.
    @Override
    public Rectangle2D getBounds(DataSet dataSet, int fromIndex, int toIndex, Rectangle2D bounds) {
        if (dataSet instanceof HiLoDataSet) {
            fromIndex = alignPairStart(fromIndex);
            toIndex = alignPairEnd(toIndex);
        }
        return super.getBounds(dataSet, fromIndex, toIndex, bounds);
    }

    /// Returns bounds expanded to whole paired samples before delegating to the base renderer.
    ///
    /// This overload applies the same pair widening as [#getBounds(DataSet, int, int, Rectangle2D)]
    /// before optional style outsets are considered.
    @Override
    public Rectangle2D getBounds(DataSet dataSet, int fromIndex, int toIndex, Rectangle2D bounds, boolean expand) {
        if (dataSet instanceof HiLoDataSet) {
            fromIndex = alignPairStart(fromIndex);
            toIndex = alignPairEnd(toIndex);
        }
        return super.getBounds(dataSet, fromIndex, toIndex, bounds, expand);
    }

    /// Returns the style used when the first paired value is greater than or equal to the second.
    public PlotStyle getFallStyle() {
        return fallStyle;
    }

    /// Requires exactly two source datasets so each logical sample can be rendered from one pair of
    /// values.
    @Override
    public int getMinDataSetCount() {
        return 2;
    }

    /// Returns the style used when the first paired value is lower than the second.
    public PlotStyle getRiseStyle() {
        return getStyle();
    }

    /// Returns the effective rise/fall style pair.
    ///
    /// The returned array contains either zero entries or exactly two entries. When only one
    /// direction has an explicit style, that style is duplicated so generic style-management code
    /// still sees a complete pair.
    @Override
    public PlotStyle[] getStyles() {
        PlotStyle riseStyle = getRiseStyle();
        PlotStyle fallStyle = getFallStyle();
        if (riseStyle == null && fallStyle == null)
            return new PlotStyle[0];
        if (fallStyle == null)
            return new PlotStyle[]{riseStyle, riseStyle};
        if (riseStyle == null)
            return new PlotStyle[]{fallStyle, fallStyle};
        return new PlotStyle[]{riseStyle, fallStyle};
    }

    /// Returns the glyph type used for each paired sample.
    public final Type getType() {
        return type;
    }

    /// Returns the optional fixed device-pixel body width, or `-1` when no hint is active.
    public int getPixelBodyWidthHint() {
        return pixelBodyWidthHint;
    }

    /// Returns the resolved glyph width in the renderer's x-axis units.
    ///
    /// The value tracks the current category span, so changes to clustering or x-axis scaling can
    /// change the result even when [#getWidthPercent()] is unchanged.
    @Override
    public double getWidth() {
        return widthPercent * super.getCategoryWidth();
    }

    /// Returns the configured width as a percentage of one category slot.
    @Override
    public double getWidthPercent() {
        return widthPercent * 100.0;
    }

    @Override
    int getBoundsQueryPadding() {
        return 0;
    }

    /// Returns whether this glyph family paints closed faces.
    ///
    /// Only bar and arrow glyphs are treated as filled; wick and marked variants remain edge-only.
    @Override
    public boolean isFilled() {
        return getType().hasFaceForm();
    }

    /// Returns whether half of the category spacing is preserved at plot borders when that would
    /// exceed half of the configured body width.
    @Override
    public boolean isUseCategorySpacingAtBorders() {
        return useCategorySpacingAtBorders;
    }

    /// Rebuilds the internal [HiLoDataSet] view when both source datasets are present.
    ///
    /// The virtual dataset is installed for both source datasets so picking and bounds queries can
    /// still be addressed through either original dataset while rendering traverses paired samples.
    void refreshHiLoDataSetBindings() {
        int dataSetCount = super.getDataSource().size();
        if (dataSetCount == 2) {
            HiLoDataSet hiLoDataSet = new HiLoDataSet(super.getDataSource().get(0), super.getDataSource().get(1));
            boolean changed = super.setVirtualDataSet(super.getDataSource().get(0), hiLoDataSet, false);
            changed |= super.setVirtualDataSet(super.getDataSource().get(1), hiLoDataSet, true);
            if (changed && super.getChart() != null)
                super.getChart().updateDataRange();
        }
    }

    /// Sets the style used when the first paired value is greater than or equal to the second.
    public void setFallStyle(PlotStyle fallStyle) {
        if (fallStyle != this.fallStyle) {
            this.fallStyle = fallStyle;
            super.triggerChange(4);
        }
    }

    /// Sets the style used when the first paired value is lower than the second.
    public void setRiseStyle(PlotStyle style) {
        setStyle(style);
    }

    /// Sets an optional fixed device-pixel width hint for bar bodies.
    ///
    /// Non-positive values disable the hint after normalization to `-1`.
    public void setPixelBodyWidthHint(int pixelBodyWidthHint) {
        int normalizedHint = (pixelBodyWidthHint > 0) ? pixelBodyWidthHint : -1;
        if (this.pixelBodyWidthHint != normalizedHint) {
            this.pixelBodyWidthHint = normalizedHint;
            super.triggerChange(4);
        }
    }

    /// Replaces the rise/fall style pair.
    ///
    /// Supplying a single style applies it to both directions. Passing `null` clears both slots.
    @Override
    public void setStyles(PlotStyle[] styles) {
        PlotStyle riseStyle = (styles == null) ? null : styles[0];
        PlotStyle fallStyle = (styles == null || styles.length < 2) ? riseStyle : styles[1];
        setRiseStyle(riseStyle);
        setFallStyle(fallStyle);
    }

    /// Sets the glyph type used for each paired sample.
    public void setType(Type type) {
        if (type != this.type) {
            this.type = type;
            super.triggerChange(4);
        }
    }

    /// Enables or disables preserving half of the category spacing at plot borders.
    @Override
    public void setUseCategorySpacingAtBorders(boolean useCategorySpacingAtBorders) {
        if (useCategorySpacingAtBorders != this.useCategorySpacingAtBorders) {
            this.useCategorySpacingAtBorders = useCategorySpacingAtBorders;
            if (super.getChart() != null)
                super.getChart().updateDataRangeAndRepaint();
        }
    }

    /// Sets the glyph width as a percentage of one category slot.
    ///
    /// @throws IllegalArgumentException if `widthPercent` is outside the inclusive `0..100` range
    @Override
    public void setWidthPercent(double widthPercent) {
        if (widthPercent < 0.0 || widthPercent > 100.0)
            throw new IllegalArgumentException("Percentage must be in [0..100]");
        double normalizedWidth = widthPercent / 100.0;
        if (normalizedWidth != this.widthPercent) {
            this.widthPercent = normalizedWidth;
            super.triggerChange(4);
        }
    }
}
