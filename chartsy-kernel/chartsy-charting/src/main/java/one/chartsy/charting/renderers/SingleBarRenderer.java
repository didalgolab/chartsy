package one.chartsy.charting.renderers;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartProjector;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.CoordinateSystem;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.DoublePoint;
import one.chartsy.charting.DoublePoints;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.graphic.DataRenderingHint;
import one.chartsy.charting.renderers.internal.DataSetRendererProperty;
import one.chartsy.charting.renderers.internal.StackedDataSet;
import one.chartsy.charting.renderers.internal.VirtualDataSet;
import one.chartsy.charting.util.ColorUtil;
import one.chartsy.charting.util.GraphicUtil;
import one.chartsy.charting.util.java2d.ShapeUtil;

/// Single-series renderer that draws one bar per logical point in its primary dataset.
///
/// This renderer is the concrete child type used by [BarChartRenderer], but it can also be used
/// standalone. The configured width is expressed as a percentage of one category slot. Composite
/// parents may later narrow or offset that slot, while this renderer keeps each bar centered on
/// the current point's x position plus any inherited x shift.
///
/// Cartesian charts use a fast rectangle path. Other chart types may either approximate the bar
/// with a projected polygon or delegate the full geometry to the active [ChartProjector] through
/// [#SHAPE_EXACT]. When a parent [BarChartRenderer] is stacked, child renderers after the first
/// read their baseline from the current [StackedDataSet] instead of from the axis crossing.
public class SingleBarRenderer extends SingleChartRenderer implements VariableWidthRenderer {

    /// Reusable display item that stores the projected geometry for one bar.
    ///
    /// Cartesian charts keep only the rectangle corners and let [PlotStyle#plotRect(Graphics,
    /// double, double, double, double)] paint the bar directly. Other chart types either use the
    /// inherited point buffers as a polygon approximation or cache an exact projector-generated
    /// [Shape].
    class BarItem extends SingleChartRenderer.DefaultItem {
        private final boolean cartesianChart;
        private Shape projectedShape;

        BarItem(int pointCapacity) {
            super(pointCapacity);
            cartesianChart = getChart().getType() == Chart.CARTESIAN;
        }

        boolean clipToWindow(DataWindow window) {
            if (super.getX(0) < window.getXMin()) {
                if (super.getX(1) < window.getXMin()) {
                    return false;
                }
                super.setX(0, window.getXMin());
            } else if (super.getX(0) > window.getXMax()) {
                if (super.getX(1) > window.getXMax()) {
                    return false;
                }
                super.setX(0, window.getXMax());
            }

            if (super.getX(1) < window.getXMin()) {
                super.setX(1, window.getXMin());
            } else if (super.getX(1) > window.getXMax()) {
                super.setX(1, window.getXMax());
            }

            if (super.getY(0) < window.getYMin()) {
                if (super.getY(1) < window.getYMin()) {
                    return false;
                }
                super.setY(0, window.getYMin());
            } else if (super.getY(0) > window.getYMax()) {
                if (super.getY(1) > window.getYMax()) {
                    return false;
                }
                super.setY(0, window.getYMax());
            }

            if (super.getY(1) < window.getYMin()) {
                super.setY(1, window.getYMin());
            } else if (super.getY(1) > window.getYMax()) {
                super.setY(1, window.getYMax());
            }
            return true;
        }

        void updateGeometry(SingleChartRenderer.Points points, int pointIndex, double halfWidth) {
            double centerX = points.getXData(pointIndex) + getXShift();
            double baselineY = SingleBarRenderer.this.getBaselineY(points.getDataIndex(pointIndex));
            double valueY = points.getYData(pointIndex);

            if (cartesianChart && POLY_BAR_MODE) {
                super.set(0, centerX - halfWidth, baselineY);
                super.set(1, centerX + halfWidth, valueY);
                super.setSize(2);
                projectedShape = null;
                return;
            }

            if (barShape == SHAPE_EXACT) {
                DataWindow barWindow = new DataWindow(centerX - halfWidth, centerX + halfWidth, baselineY, valueY);
                projectedShape = getChart().getProjector().getShape(barWindow, getPlotRect(), getCoordinateSystem());
                return;
            }

            double[] xValues = super.getXValues();
            double[] yValues = super.getYValues();
            xValues[0] = centerX - halfWidth;
            yValues[0] = baselineY;
            xValues[1] = xValues[0];
            yValues[1] = valueY;

            if (barShape != SHAPE_QUADRILATERAL && !cartesianChart) {
                xValues[2] = centerX;
                yValues[2] = valueY;
                xValues[3] = centerX + halfWidth;
                yValues[3] = valueY;
                xValues[4] = xValues[3];
                yValues[4] = baselineY;
                xValues[5] = xValues[0];
                yValues[5] = baselineY;
                super.setSize(6);
            } else {
                xValues[2] = centerX + halfWidth;
                yValues[2] = valueY;
                xValues[3] = xValues[2];
                yValues[3] = baselineY;
                xValues[4] = xValues[0];
                yValues[4] = baselineY;
                super.setSize(5);
            }
            projectedShape = null;
        }

        @Override
        public double distance(PlotStyle style, double x, double y, boolean outlineOnly) {
            if (cartesianChart && POLY_BAR_MODE) {
                Rectangle2D bounds = getBounds(style, true, null);
                return outlineOnly
                        ? ShapeUtil.distanceTo(bounds, x, y, null)
                        : bounds.contains(x, y) ? 0.0 : Double.POSITIVE_INFINITY;
            }
            if (projectedShape == null) {
                return super.distance(style, x, y, outlineOnly);
            }
            return style.distanceToShape(projectedShape, x, y, outlineOnly);
        }

        @Override
        public void draw(Graphics g, PlotStyle style) {
            if (cartesianChart && POLY_BAR_MODE) {
                style.plotRect(g, super.getX(0), super.getY(0), super.getX(1), super.getY(1));
                return;
            }

            if (projectedShape == null) {
                super.draw(g, style);
            } else {
                style.plotShape(g, projectedShape);
                if (!style.isStrokeOn()) {
                    SingleBarRenderer.this.getStyle().draw(g, projectedShape);
                }
            }
        }

        @Override
        public Rectangle2D getBounds(PlotStyle style, boolean expand, Rectangle2D bounds) {
            if (!(cartesianChart && POLY_BAR_MODE) && projectedShape != null) {
                return style.getShapeBounds(projectedShape);
            }
            return super.getBounds(style, expand, bounds);
        }
    }

    private static final boolean POLY_BAR_MODE = true;
    private static int strokeThreshold = 6;

    /// Draws a projected four-corner bar when the chart projector cannot use a simple rectangle.
    public static final int SHAPE_QUADRILATERAL = 1;
    /// Draws a projected polygon that samples the bar top at the category center as well.
    public static final int SHAPE_POLYGON = 2;
    /// Delegates the complete bar window to the active [ChartProjector].
    public static final int SHAPE_EXACT = 3;

    private double widthPercent;
    private boolean useCategorySpacingAtBorders;
    private int barShape;
    private BarChartRenderer parentBarRenderer;
    private int childIndex;

    public SingleBarRenderer() {
        this(null);
    }

    /// Creates a bar renderer with an explicit base style.
    ///
    /// The initial width is `80%` of one category slot.
    public SingleBarRenderer(PlotStyle style) {
        this(style, 80.0);
    }

    /// Creates a bar renderer with an explicit base style and width budget.
    ///
    /// @param style        base plot style, or `null` to let the renderer resolve a default
    /// @param widthPercent percentage of one category slot reserved for each bar
    public SingleBarRenderer(PlotStyle style, double widthPercent) {
        super(style);
        useCategorySpacingAtBorders = false;
        barShape = SHAPE_POLYGON;
        setWidthPercent(widthPercent);
    }

    @Override
    DataWindow adjustVisibleWindow(DataWindow window) {
        double xShift = super.getXShift();
        if (xShift != 0.0) {
            window.xRange.translate(xShift);
        }

        double halfWidth = getWidth() / 2.0;
        if (halfWidth > 0.0) {
            window.xRange.expand(halfWidth);
        }
        return window;
    }

    @Override
    PlotStyle createStyle(Paint fillPaint, Paint strokePaint, Stroke stroke) {
        PlotStyle resolvedStyle = super.createStyle(fillPaint, strokePaint, stroke);
        ChartRenderer parent = super.getParent();
        if (resolvedStyle != null
                && super.isFilled()
                && parent instanceof SuperimposedRenderer superimposedParent
                && superimposedParent.isSuperimposed()
                && superimposedParent.isAutoTransparency()) {
            Color fillColor = resolvedStyle.getFillColor();
            if (fillColor != null) {
                resolvedStyle = resolvedStyle.setFillPaint(ColorUtil.setAlpha(fillColor, 0.5f));
            }
        }
        return resolvedStyle;
    }

    @Override
    void forEachItem(SingleChartRenderer.Points points, SingleChartRenderer.ItemAction callback) {
        double halfWidth = getWidth() / 2.0;
        if (halfWidth == 0.0) {
            return;
        }

        CoordinateSystem coordinateSystem = super.getCoordinateSystem();
        Rectangle plotRect = super.getPlotRect();
        ChartProjector localProjector = super.getChart().getLocalProjector2D(plotRect, coordinateSystem);
        BarItem item = new BarItem(7);
        int pointCount = points.size();
        PlotStyle defaultStyle = super.getStyle();
        DataSet dataSet = points.getDataSet();
        Double undefValue = dataSet.getUndefValue();
        double undefY = (undefValue == null) ? 0.0 : undefValue.doubleValue();
        double axisLength = super.getChart().getProjector().getAxisLength(plotRect, super.getXAxis());
        boolean strokeVisible = 2.0 * axisLength * halfWidth / super.getXAxis().getVisibleRange().getLength()
                > getStrokeThreshold();
        if (defaultStyle.isFillOn() && !strokeVisible) {
            defaultStyle = defaultStyle.setStrokeOn(false);
        }

        if (!super.hasRenderingHints()) {
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                double y = points.getYData(pointIndex);
                if ((undefValue != null && y == undefY) || Double.isNaN(y)) {
                    continue;
                }

                item.updateGeometry(points, pointIndex, halfWidth);
                localProjector.toDisplay(item, plotRect, coordinateSystem);
                callback.processItem(points, pointIndex, item, defaultStyle);
            }
            return;
        }

        int[] indices = points.getIndices();
        DisplayPoint displayPoint = new DisplayPoint(this, dataSet);
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            double y = points.getYData(pointIndex);
            if ((undefValue != null && y == undefY) || Double.isNaN(y)) {
                continue;
            }

            item.updateGeometry(points, pointIndex, halfWidth);
            localProjector.toDisplay(item, plotRect, coordinateSystem);
            displayPoint.dataSet = dataSet;
            displayPoint.set(indices[pointIndex], 0.0, 0.0);

            PlotStyle itemStyle = defaultStyle;
            DataRenderingHint renderingHint = super.getRenderingHint(displayPoint);
            if (renderingHint != null) {
                itemStyle = renderingHint.getStyle(displayPoint, defaultStyle);
                if (itemStyle == null) {
                    continue;
                }
            }
            callback.processItem(points, pointIndex, item, itemStyle);
        }
    }

    /// Positions centered data labels inside the bar body and outer labels just beyond the bar end.
    @Override
    public Point computeDataLabelLocation(DisplayPoint point, Dimension size) {
        DisplayPoint mappedPoint = point;
        DataSet dataSet = mappedPoint.getDataSet();
        VirtualDataSet virtualDataSet = DataSetRendererProperty.getVirtualDataSet(this, dataSet);
        if (virtualDataSet != null) {
            mappedPoint = (DisplayPoint) mappedPoint.clone();
            virtualDataSet.map(mappedPoint);
            dataSet = virtualDataSet;
        }

        if (super.getDataLabelLayout() == 2) {
            DoublePoint labelAnchor = new DoublePoint(mappedPoint.getXCoord(), mappedPoint.getYCoord());
            double y = dataSet.getYData(mappedPoint.getIndex());
            double yOffset = (y < getBaselineY(mappedPoint.getIndex())) ? -3.0 : 3.0;
            return super.computeShiftedLabelLocation(labelAnchor, size, yOffset, true);
        }

        DoublePoints labelAnchor = new DoublePoints(
                dataSet.getXData(mappedPoint.getIndex()),
                (getBaselineY(mappedPoint.getIndex()) + dataSet.getYData(mappedPoint.getIndex())) / 2.0
        );
        super.toDisplay(labelAnchor);
        double x = labelAnchor.getX(0);
        double y = labelAnchor.getY(0);
        labelAnchor.dispose();
        return new Point(GraphicUtil.toInt(x), GraphicUtil.toInt(y));
    }

    double getBaselineY(int dataIndex) {
        if (childIndex != 0 && parentBarRenderer != null && parentBarRenderer.getMode() == BarChartRenderer.STACKED) {
            double previousY = ((StackedDataSet) super.getRenderedDataSet()).getPreviousYData(dataIndex);
            if (!Double.isNaN(previousY)) {
                return Math.max(super.getYAxis().getVisibleMin(), previousY);
            }
        }
        return Math.max(super.getYAxis().getVisibleMin(), super.getCoordinateSystem().getXCrossingValue());
    }

    final void setChildIndex(int childIndex) {
        this.childIndex = childIndex;
    }

    @Override
    int getDisplayQueryPadding() {
        return 1;
    }

    /// Returns the projector shape mode used for non-Cartesian charts.
    public int getBarShape() {
        return barShape;
    }

    /// Returns the configured category footprint.
    ///
    /// This legacy alias currently returns the same value as [#getWidthPercent()].
    public double getSpecWidthPercent() {
        return widthPercent;
    }

    /// Returns the current bar width in source-space x units.
    @Override
    public double getWidth() {
        return getWidthPercent() * super.getCategoryWidth() / 100.0;
    }

    /// Returns the configured percentage of one category slot reserved for each bar.
    @Override
    public double getWidthPercent() {
        return widthPercent;
    }

    /// Returns the legend text only when this bar remains individually legended.
    ///
    /// Children of a grouped bar renderer suppress their z-annotation label outside
    /// `SUPERIMPOSED` mode because the parent renderer owns the combined legend semantics there.
    @Override
    public String getZAnnotationText() {
        if (parentBarRenderer != null && parentBarRenderer.getMode() != BarChartRenderer.SUPERIMPOSED) {
            return null;
        }
        return super.getDefaultLegendText();
    }

    @Override
    int getBoundsQueryPadding() {
        return 0;
    }

    @Override
    public boolean isUseCategorySpacingAtBorders() {
        return useCategorySpacingAtBorders;
    }

    /// Returns the pixel-width threshold below which filled bars suppress their stroke.
    public static int getStrokeThreshold() {
        return strokeThreshold;
    }

    /// Sets the pixel-width threshold below which filled bars suppress their stroke.
    public static void setStrokeThreshold(int strokeThreshold) {
        SingleBarRenderer.strokeThreshold = strokeThreshold;
    }

    /// Selects the projector geometry mode used for non-Cartesian bars.
    ///
    /// @param barShape one of [#SHAPE_QUADRILATERAL], [#SHAPE_POLYGON], or [#SHAPE_EXACT]
    public void setBarShape(int barShape) {
        switch (barShape) {
            case SHAPE_QUADRILATERAL, SHAPE_POLYGON, SHAPE_EXACT -> this.barShape = barShape;
            default -> throw new IllegalArgumentException("invalid barShape " + barShape);
        }
    }

    @Override
    public void setParent(ChartRenderer parent) {
        parentBarRenderer = (parent instanceof BarChartRenderer barChartRenderer) ? barChartRenderer : null;
        super.setParent(parent);
    }

    @Override
    public void setUseCategorySpacingAtBorders(boolean useCategorySpacingAtBorders) {
        if (useCategorySpacingAtBorders != this.useCategorySpacingAtBorders) {
            this.useCategorySpacingAtBorders = useCategorySpacingAtBorders;
            if (super.getChart() != null) {
                super.getChart().updateDataRangeAndRepaint();
            }
        }
    }

    /// Sets the percentage of one category slot reserved for each bar.
    ///
    /// @throws IllegalArgumentException if `widthPercent` is outside the inclusive `0..100` range
    @Override
    public void setWidthPercent(double widthPercent) {
        if (widthPercent < 0.0 || widthPercent > 100.0) {
            throw new IllegalArgumentException("Percentage must be in [0..100]");
        }
        if (widthPercent != this.widthPercent) {
            this.widthPercent = widthPercent;
            super.triggerChange(4);
        }
    }
}

