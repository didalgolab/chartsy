package one.chartsy.charting;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;

import one.chartsy.charting.util.DoubleArray;

/// Draws major and minor reference lines for a chart axis.
///
/// A `Grid` is attached to a single axis slot through [Chart#setXGrid(Grid)] or
/// [Chart#setYGrid(int, Grid)]. The attached axis normally supplies the step positions, while the
/// current chart type decides whether those positions become straight lines, radar polygons, or
/// projector-defined shapes.
///
/// New grids start with draw order `-1`, which places them before chart renderers in the chart's
/// drawable pipeline.
///
/// ### API Note
///
/// Subclasses can override [#draw(Graphics)] to source positions from something other than the
/// attached axis scale and still reuse [#draw(Graphics, DoubleArray, boolean)] for geometry and
/// styling. The financial module's shared-grid implementation uses that pattern to mirror another
/// scale's tick positions.
public class Grid extends ChartOwnedDrawable implements Serializable {
    private static final Shape[] NO_SHAPES = new Shape[0];
    private static Color defaultGridColor = Color.lightGray;

    private Chart.AxisElement axisElement;
    private boolean majorLineVisible = true;
    private boolean minorLineVisible = true;
    private PlotStyle majorStyle;
    private PlotStyle minorStyle;
    private int drawOrder = -1;

    /// Creates a grid that uses the current default grid color.
    ///
    /// Major lines are enabled and minor lines start hidden.
    public Grid() {
        this(defaultGridColor);
    }

    /// Creates a grid that uses the same paint for both major and minor lines.
    ///
    /// Minor lines start hidden so the grid behaves like a single-level reference grid until
    /// [#setMinorLineVisible(boolean)] is enabled.
    ///
    /// @param paint the paint applied to both line levels
    public Grid(Paint paint) {
        this(paint, paint);
        setMinorLineVisible(false);
    }

    /// Creates a grid with independent paints for major and minor lines.
    ///
    /// Both line levels are enabled initially.
    ///
    /// @param majorPaint the paint applied to major lines
    /// @param minorPaint the paint applied to minor lines
    public Grid(Paint majorPaint, Paint minorPaint) {
        setMajorPaint(majorPaint);
        setMinorPaint(minorPaint);
    }

    /// Returns the chart axis slot to which `grid` is attached.
    ///
    /// The x axis is reported as `-1`. Y axes are reported using the zero-based index accepted by
    /// [Chart#setYGrid(int, Grid)].
    ///
    /// @param grid the grid whose attached axis slot should be returned
    /// @return the attached axis slot index
    /// @throws IllegalArgumentException if `grid` is not currently attached to a chart
    public static int getAxisIndex(Grid grid) {
        if (grid.axisElement != null)
            return grid.axisElement.getAxisIndex();
        throw new IllegalArgumentException("grid not connected to a chart.");
    }

    /// Returns the color used by [#Grid()].
    ///
    /// Changing the default affects only grids created afterward.
    ///
    /// @return the current default color for no-arg grid instances
    public static Color getDefaultGridColor() {
        return defaultGridColor;
    }

    /// Updates the color used by future [#Grid()] instances.
    ///
    /// Existing grids keep their current paints.
    ///
    /// @param color the new default color
    public static void setDefaultGridColor(Color color) {
        defaultGridColor = color;
    }

    /// Rebinds this grid to an axis slot.
    ///
    /// [Chart] uses this internal hook when the grid is attached, detached, or moved between axis
    /// slots. [#chartConnected(Chart, Chart)] is notified only when the owning chart changes.
    ///
    /// @param axisElement the new owning axis slot, or `null` to detach the grid
    void setAxisElement(Chart.AxisElement axisElement) {
        Chart previousChart = getChart();
        this.axisElement = axisElement;
        Chart chart = getChart();
        if (chart != previousChart)
            chartConnected(previousChart, chart);
    }

    private int getProjectionAxisIndex() {
        return isXAxisGrid() ? 0 : axisElement.getAxisIndex();
    }

    private boolean isXAxisGrid() {
        return getAxis().isXAxis();
    }

    private DoublePoints createLineEndpoints(DoubleArray values) {
        Chart chart = getChart();
        CoordinateSystem coordinateSystem = getCoordinateSystem();
        int fromIndex = 0;
        int valueCount = values.size();

        if (chart.getType() == 1) {
            PlotStyle plotStyle = chart.getChartArea().getPlotStyle();
            if (plotStyle != null && plotStyle.isStrokeOn()) {
                Axis axis = isXAxisGrid() ? coordinateSystem.getXAxis() : coordinateSystem.getYAxis();
                int skippedValues = 0;

                // Avoid duplicating the plot outline when the plot itself already draws that edge.
                if (values.get(0) == axis.getVisibleMin()) {
                    fromIndex++;
                    skippedValues++;
                }
                if (valueCount > skippedValues && values.get(valueCount - 1) == axis.getVisibleMax())
                    skippedValues++;

                valueCount -= skippedValues;
            }
        }

        int pointCount = valueCount << 1;
        DoublePoints points = new DoublePoints(pointCount);

        if (isXAxisGrid()) {
            System.arraycopy(values.data(), fromIndex, points.getXValues(), 0, valueCount);
            System.arraycopy(values.data(), fromIndex, points.getXValues(), valueCount, valueCount);

            double[] yValues = points.getYValues();
            double minY = coordinateSystem.getYAxis().getVisibleMin();
            double maxY = coordinateSystem.getYAxis().getVisibleMax();
            for (int i = 0; i < valueCount; i++)
                yValues[i] = minY;
            for (int i = valueCount; i < pointCount; i++)
                yValues[i] = maxY;
        } else {
            System.arraycopy(values.data(), fromIndex, points.getYValues(), 0, valueCount);
            System.arraycopy(values.data(), fromIndex, points.getYValues(), valueCount, valueCount);

            double[] xValues = points.getXValues();
            double minX = coordinateSystem.getXAxis().getVisibleMin();
            double maxX = coordinateSystem.getXAxis().getVisibleMax();
            for (int i = 0; i < valueCount; i++)
                xValues[i] = minX;
            for (int i = valueCount; i < pointCount; i++)
                xValues[i] = maxX;
        }

        points.setSize(pointCount);
        chart.toDisplay(points, getProjectionAxisIndex());
        return points;
    }

    private Shape[] createRectangularGridShapes(DoubleArray values) {
        DoublePoints points = createLineEndpoints(values);
        try {
            double[] xValues = points.getXValues();
            double[] yValues = points.getYValues();
            int lineCount = points.size() >> 1;
            Shape[] shapes = new Shape[lineCount];

            for (int i = 0, opposite = lineCount; i < lineCount; i++, opposite++)
                shapes[i] = new Line2D.Double(xValues[i], yValues[i], xValues[opposite], yValues[opposite]);

            return shapes;
        } finally {
            points.dispose();
        }
    }

    private Shape[] createRadarGridShapes(DoubleArray values) {
        Scale xScale = getChart().getXScale();
        if (xScale == null)
            return NO_SHAPES;

        DoubleArray xStepValues = xScale.getStepValues();
        int polygonPointCount = xStepValues.size();
        double[] polygonXValues = xStepValues.data();
        Shape[] shapes = new Shape[values.size()];
        DoublePoints polygonPoints = new DoublePoints(polygonPointCount + 1);
        polygonPoints.setSize(polygonPointCount + 1);

        try {
            for (int i = 0; i < values.size(); i++) {
                System.arraycopy(polygonXValues, 0, polygonPoints.getXValues(), 0, polygonPointCount);
                polygonPoints.setX(polygonPointCount, polygonXValues[0]);

                for (int pointIndex = 0; pointIndex <= polygonPointCount; pointIndex++)
                    polygonPoints.setY(pointIndex, values.get(i));

                getChart().toDisplay(polygonPoints, getProjectionAxisIndex());

                GeneralPath shape = new GeneralPath();
                shape.moveTo((float) polygonPoints.getX(0), (float) polygonPoints.getY(0));
                for (int pointIndex = 1; pointIndex < polygonPoints.size(); pointIndex++)
                    shape.lineTo((float) polygonPoints.getX(pointIndex), (float) polygonPoints.getY(pointIndex));
                shape.closePath();
                shapes[i] = shape;
            }

            return shapes;
        } finally {
            polygonPoints.dispose();
        }
    }

    private Shape[] createProjectedGridShapes(DoubleArray values) {
        ChartProjector projector = getChart().getProjector();
        CoordinateSystem coordinateSystem = getCoordinateSystem();
        Rectangle plotRect = getChart().getChartArea().getPlotRect();
        int axisType = getAxis().getType();
        Shape[] shapes = new Shape[values.size()];

        for (int i = 0; i < values.size(); i++)
            shapes[i] = projector.getShape(values.get(i), axisType, plotRect, coordinateSystem);

        return shapes;
    }

    @Override
    protected void chartConnected(Chart previousChart, Chart chart) {
    }

    /// Paints all currently visible grid lines using the attached axis scale.
    ///
    /// This method does nothing while the grid is detached or while the attached axis has no
    /// scale.
    ///
    /// @param g the graphics context for the current chart paint pass
    @Override
    public void draw(Graphics g) {
        if (getChart() == null)
            return;

        Scale scale = axisElement.getScale();
        if (scale == null)
            return;

        if (isMajorLineVisible()) {
            DoubleArray values = scale.getStepValues();
            if (values.size() > 0)
                draw(g, values, true);
        }
        if (isMinorLineVisible()) {
            DoubleArray values = scale.getSubStepValues();
            if (values.size() > 0)
                draw(g, values, false);
        }
    }

    /// Draws grid lines for the supplied axis-space values.
    ///
    /// The values are interpreted in the data space of the currently attached axis and are
    /// rendered using the chart's current projector. Subclasses overriding [#draw(Graphics)] can
    /// call this helper after obtaining values from an external scale or custom policy.
    ///
    /// @param g the graphics context for the current chart paint pass
    /// @param values axis-space positions to draw
    /// @param major `true` to use the major-line style, `false` to use the minor-line style
    protected void draw(Graphics g, DoubleArray values, boolean major) {
        for (Shape shape : getGridShapes(values))
            drawGridLine(g, shape, major);
    }

    /// Paints one display-space grid shape.
    ///
    /// The supplied shape is expected to come from [#getGridShapes(DoubleArray)] or an equivalent
    /// custom projection step.
    ///
    /// @param g the graphics context for the current chart paint pass
    /// @param shape the display-space geometry to paint
    /// @param major `true` to use the major-line style, `false` to use the minor-line style
    protected void drawGridLine(Graphics g, Shape shape, boolean major) {
        PlotStyle style = major ? getMajorStyle() : getMinorStyle();

        if (getChart().getType() == 1) {
            Line2D.Double line = (Line2D.Double) shape;
            style.drawLine(g, line.x1, line.y1, line.x2, line.y2);
            return;
        }
        style.draw(g, shape);
    }

    /// Returns the axis that currently owns this grid.
    ///
    /// @return the attached axis, or `null` when the grid is detached
    public final Axis getAxis() {
        return (axisElement == null) ? null : axisElement.getAxis();
    }

    /// {@inheritDoc}
    ///
    /// An attached grid reports the chart area's plot rectangle. A detached grid reports an empty
    /// rectangle.
    @Override
    public Rectangle2D getBounds(Rectangle2D bounds) {
        Chart chart = getChart();
        if (chart != null) {
            Rectangle plotRect = chart.getChartArea().getPlotRect();
            if (bounds != null) {
                bounds.setRect(plotRect);
                return bounds;
            }
            return (Rectangle) plotRect.clone();
        }

        if (bounds == null)
            return new Rectangle();

        bounds.setRect(0.0, 0.0, 0.0, 0.0);
        return bounds;
    }

    /// {@inheritDoc}
    @Override
    public final Chart getChart() {
        return (axisElement == null) ? null : axisElement.getChart();
    }

    /// Returns the coordinate system paired with the attached axis.
    ///
    /// @return the attached coordinate system, or `null` when the grid is detached
    public final CoordinateSystem getCoordinateSystem() {
        return (axisElement == null) ? null : axisElement.getCoordinateSystem();
    }

    /// {@inheritDoc}
    ///
    /// New grids default to `-1`.
    @Override
    public final int getDrawOrder() {
        return drawOrder;
    }

    /// Creates the display-space shapes for the supplied axis-space values.
    ///
    /// Rectangular charts return line segments, radar y-axis grids return closed polygons, and
    /// other chart types defer to the active [ChartProjector].
    ///
    /// @param values axis-space positions to project
    /// @return newly created shapes in display coordinates
    public final Shape[] getGridShapes(DoubleArray values) {
        return switch (getChart().getType()) {
            case 1 -> createRectangularGridShapes(values);
            case 4 -> isXAxisGrid() ? createRectangularGridShapes(values) : createRadarGridShapes(values);
            default -> createProjectedGridShapes(values);
        };
    }

    /// Returns the stroke paint used for major lines.
    ///
    /// @return the current paint for major grid lines
    public final Paint getMajorPaint() {
        return getMajorStyle().getStrokePaint();
    }

    /// Returns the stroke used for major lines.
    ///
    /// @return the current stroke for major grid lines
    public final Stroke getMajorStroke() {
        return getMajorStyle().getStroke();
    }

    /// Returns the style consulted when major lines are rendered.
    ///
    /// Grid painting uses the style's stroke settings.
    ///
    /// @return the style currently used for major-line rendering
    public final PlotStyle getMajorStyle() {
        if (majorStyle == null)
            majorStyle = PlotStyle.createStroked(Color.black);
        return majorStyle;
    }

    /// Returns the stroke paint used for minor lines.
    ///
    /// @return the current paint for minor grid lines
    public final Paint getMinorPaint() {
        return getMinorStyle().getStrokePaint();
    }

    /// Returns the stroke used for minor lines.
    ///
    /// @return the current stroke for minor grid lines
    public final Stroke getMinorStroke() {
        return getMinorStyle().getStroke();
    }

    /// Returns the style consulted when minor lines are rendered.
    ///
    /// Grid painting uses the style's stroke settings.
    ///
    /// @return the style currently used for minor-line rendering
    public final PlotStyle getMinorStyle() {
        if (minorStyle == null)
            minorStyle = PlotStyle.createStroked(Color.black);
        return minorStyle;
    }

    /// Returns whether major lines are currently drawn.
    ///
    /// @return `true` when major grid lines are enabled
    public final boolean isMajorLineVisible() {
        return majorLineVisible;
    }

    /// Returns whether minor lines are currently drawn.
    ///
    /// @return `true` when minor grid lines are enabled
    public final boolean isMinorLineVisible() {
        return minorLineVisible;
    }

    /// {@inheritDoc}
    ///
    /// A grid is visible when either its major or minor line level is enabled.
    @Override
    public boolean isVisible() {
        return majorLineVisible || minorLineVisible;
    }

    /// Updates this grid's draw order inside the owning chart.
    ///
    /// When the grid is already attached, the chart is resorted and the plot area is repainted
    /// immediately.
    ///
    /// @param drawOrder the new chart draw order
    public void setDrawOrder(int drawOrder) {
        if (this.drawOrder == drawOrder)
            return;

        int previousDrawOrder = this.drawOrder;
        this.drawOrder = drawOrder;

        Chart chart = getChart();
        if (chart != null) {
            chart.handleDrawableDrawOrderChanged(this, previousDrawOrder, drawOrder);
            chart.getChartArea().repaint2D(getBounds(null));
        }
    }

    /// Enables or disables major-line painting.
    ///
    /// @param visible `true` to paint major lines
    public void setMajorLineVisible(boolean visible) {
        majorLineVisible = visible;
    }

    /// Updates the paint used for major lines.
    ///
    /// @param paint the new major-line paint
    public void setMajorPaint(Paint paint) {
        majorStyle = getMajorStyle().setStrokePaint(paint);
        if (axisElement != null)
            assert paint == getMajorPaint();
    }

    /// Updates the stroke used for major lines.
    ///
    /// @param stroke the new major-line stroke
    public void setMajorStroke(Stroke stroke) {
        majorStyle = getMajorStyle().setStroke(stroke);
    }

    /// Enables or disables minor-line painting.
    ///
    /// @param visible `true` to paint minor lines
    public void setMinorLineVisible(boolean visible) {
        minorLineVisible = visible;
    }

    /// Updates the paint used for minor lines.
    ///
    /// @param paint the new minor-line paint
    public void setMinorPaint(Paint paint) {
        minorStyle = getMinorStyle().setStrokePaint(paint);
        if (axisElement != null)
            assert paint == getMinorPaint();
    }

    /// Updates the stroke used for minor lines.
    ///
    /// @param stroke the new minor-line stroke
    public void setMinorStroke(Stroke stroke) {
        minorStyle = getMinorStyle().setStroke(stroke);
    }

    /// Enables or disables both major and minor lines together.
    ///
    /// @param visible `true` to paint both line levels, `false` to hide both
    public final void setVisible(boolean visible) {
        majorLineVisible = visible;
        minorLineVisible = visible;
    }
}
