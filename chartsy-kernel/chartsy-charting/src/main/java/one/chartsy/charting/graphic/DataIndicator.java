package one.chartsy.charting.graphic;

import java.awt.ComponentOrientation;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import one.chartsy.charting.Axis;
import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartDecoration;
import one.chartsy.charting.CoordinateSystem;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.DoublePoint;
import one.chartsy.charting.DoublePoints;
import one.chartsy.charting.LabelRenderer;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.event.ChartAreaEvent;
import one.chartsy.charting.event.ChartListener;
import one.chartsy.charting.event.LabelRendererPropertyEvent;
import one.chartsy.charting.util.GraphicUtil;
import one.chartsy.charting.util.text.BidiUtil;

/// Highlights one data value or one axis-aligned data region on top of a chart.
///
/// A `DataIndicator` always operates in exactly one of five modes:
/// - [#X_VALUE]: vertical line bound to the chart x axis
/// - [#Y_VALUE]: horizontal line bound to one y-axis slot
/// - [#X_RANGE]: vertical band spanning an x-axis interval
/// - [#Y_RANGE]: horizontal band spanning a y-axis interval
/// - [#WINDOW]: rectangular highlight spanning both axes of one coordinate system
///
/// `axisIndex == -1` always means "use the chart x axis". Non-negative indices select the
/// corresponding y-axis slot and coordinate system.
///
/// ### API Note
///
/// Only the accessors that match the current mode carry meaningful geometry:
/// - [#getValue()] and [#setValue(double)] for value indicators
/// - [#getRange()] and [#setRange(DataInterval)] for range indicators
/// - [#getDataWindow()] and [#setDataWindow(DataWindow)] for window indicators
///
/// The mismatched getters return `0.0` or `null`, while the mismatched mutators throw
/// `UnsupportedOperationException`.
///
/// ### Implementation Note
///
/// The current implementation paints filled regions in [#beforeDraw(Graphics)] and their outlines
/// and labels in [#afterDraw(Graphics)] so translucent bands can sit behind plotted data while
/// their edges and labels remain readable above it.
public class DataIndicator extends ChartDecoration {
    private static final double UNBOUNDED_MIN = -Double.MAX_VALUE;
    private static final double UNBOUNDED_MAX = Double.MAX_VALUE;
    private static final int PROJECTOR_X_AXIS = 1;
    private static final int PROJECTOR_Y_AXIS = 2;

    /// Keeps cached label repaint bounds aligned with chart-area layout changes.
    ///
    /// [ChartAreaEvent] is fired after the chart installs a new plot rectangle and before that
    /// layout change is repainted. The listener therefore only refreshes this indicator's cached
    /// label bounds instead of issuing its own repaint request.
    private final class AreaListener implements ChartListener, Serializable {
        /// Refreshes the cached label bounds after the chart plot rectangle changes.
        ///
        /// The enclosing [DataIndicator] relies on the chart's own repaint triggered by the layout
        /// update.
        ///
        /// @param event event describing the chart-area update
        @Override
        public void chartAreaChanged(ChartAreaEvent event) {
            if (isAttachedAndVisible()) {
                updateBoundsCache();
            }
        }
    }

    /// Keeps label-only repaint regions in sync with [LabelRenderer] configuration changes.
    ///
    /// The listener is registered only while the enclosing indicator is both attached to a chart
    /// and visible. Size-affecting updates repaint the union of the previous and current cached
    /// label bounds so stale glyphs are cleared without invalidating the indicator line or region.
    private final class LabelRendererChangeListener implements PropertyChangeListener, Serializable {
        /// Creates a listener bound to the enclosing indicator instance.
        private LabelRendererChangeListener() {
        }

        /// Repaints the cached label footprint after a retained [LabelRenderer] property change.
        ///
        /// Drawing-only changes repaint the current cached bounds in place. Size-affecting changes
        /// recompute the cache first and repaint the union of the old and new label rectangles.
        /// Other property events are ignored.
        ///
        /// @param event property event emitted by the retained label renderer
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (!isAttachedAndVisible() || !(event instanceof LabelRendererPropertyEvent labelEvent)) {
                return;
            }

            if (labelEvent.affectsSizes()) {
                Rectangle2D previousBounds = (Rectangle2D) boundsCache.clone();
                updateBoundsCache();
                Rectangle2D repaintBounds = mergeLabelBounds(previousBounds, boundsCache);
                if (repaintBounds != null) {
                    getChart().getChartArea().repaint2D(repaintBounds);
                }
            } else if (labelEvent.affectsDrawing() && !boundsCache.isEmpty()) {
                getChart().getChartArea().repaint2D(boundsCache);
            }
        }
    }

    /// Indicator type for a vertical line anchored to the chart x axis.
    public static final int X_VALUE = 0;
    /// Indicator type for a horizontal line anchored to one y-axis slot.
    public static final int Y_VALUE = 1;
    /// Indicator type for a vertical band spanning an x-axis interval.
    public static final int X_RANGE = 2;
    /// Indicator type for a horizontal band spanning a y-axis interval.
    public static final int Y_RANGE = 3;
    /// Indicator type for a rectangular highlight spanning both axes of one coordinate system.
    public static final int WINDOW = 4;

    private int type;
    private double value;
    private DataWindow dataWindow;
    private PlotStyle style;
    private int axisIndex;
    private String text;
    private LabelRenderer labelRenderer;
    private final PropertyChangeListener labelRendererChangeListener;
    private final ChartListener areaListener;

    private transient Rectangle2D boundsCache;

    /// Creates an axis-aligned range indicator.
    ///
    /// `axisIndex == -1` creates an x-axis band with type [#X_RANGE]. Non-negative indices create
    /// a y-axis band with type [#Y_RANGE].
    ///
    /// The supplied interval is copied into the indicator. Later mutations of `range` are not
    /// observed.
    ///
    /// @param axisIndex `-1` for the chart x axis, or the target y-axis slot
    /// @param range     bounded interval to highlight
    /// @param text      optional label painted at the center of the currently visible portion of the band
    public DataIndicator(int axisIndex, DataInterval range, String text) {
        labelRenderer = new LabelRenderer();
        labelRendererChangeListener = new LabelRendererChangeListener();
        areaListener = new AreaListener();
        this.text = text;
        configureRangeIndicator(axisIndex, range);
        initializeTransientState();
    }

    /// Creates a rectangular window indicator for one y-axis coordinate system.
    ///
    /// The supplied window bounds are copied into the indicator. The `DataWindow` instance itself
    /// is not retained.
    ///
    /// @param axisIndex  target y-axis slot
    /// @param dataWindow window to highlight
    /// @param text       optional label painted at the center of the currently visible portion of the
    ///                                                     window
    /// @throws IllegalArgumentException if `axisIndex` is negative
    public DataIndicator(int axisIndex, DataWindow dataWindow, String text) {
        labelRenderer = new LabelRenderer();
        labelRendererChangeListener = new LabelRendererChangeListener();
        areaListener = new AreaListener();
        this.text = text;
        configureWindowIndicator(axisIndex, dataWindow);
        initializeTransientState();
    }

    /// Creates a single-axis value indicator.
    ///
    /// `axisIndex == -1` creates a vertical x-value line with type [#X_VALUE]. Non-negative
    /// indices create horizontal y-value lines with type [#Y_VALUE].
    ///
    /// @param axisIndex `-1` for the chart x axis, or the target y-axis slot
    /// @param value     data-space value to highlight
    /// @param text      optional label painted at the center of the currently visible span
    public DataIndicator(int axisIndex, double value, String text) {
        labelRenderer = new LabelRenderer();
        labelRendererChangeListener = new LabelRendererChangeListener();
        areaListener = new AreaListener();
        this.text = text;
        configureValueIndicator(axisIndex, value);
        initializeTransientState();
    }

    private void initializeTransientState() {
        boundsCache = new Rectangle2D.Double();
    }

    private boolean isAttachedAndVisible() {
        return getChart() != null && isVisible();
    }

    private boolean isValueIndicator() {
        return type == X_VALUE || type == Y_VALUE;
    }

    private int getCoordinateSystemIndex() {
        return Math.max(axisIndex, 0);
    }

    private int getProjectorAxis() {
        return (axisIndex == -1) ? PROJECTOR_X_AXIS : PROJECTOR_Y_AXIS;
    }

    private Rectangle2D captureBoundsIfAttachedAndVisible() {
        return isAttachedAndVisible() ? getBounds(null) : null;
    }

    private Rectangle2D repaintBounds(Rectangle2D previousBounds, boolean includeCurrentBounds) {
        if (previousBounds == null) {
            return includeCurrentBounds ? getBounds(null) : null;
        }
        return includeCurrentBounds ? GraphicUtil.addToRect(previousBounds, getBounds(null)) : previousBounds;
    }

    private static Rectangle2D mergeLabelBounds(Rectangle2D previousBounds, Rectangle2D currentBounds) {
        if (previousBounds.isEmpty()) {
            return currentBounds.isEmpty() ? null : currentBounds;
        }
        if (!currentBounds.isEmpty()) {
            previousBounds.add(currentBounds);
        }
        return previousBounds;
    }

    private void repaintAfterGeometryChange(Rectangle2D previousBounds, boolean includeCurrentBounds) {
        if (!isAttachedAndVisible()) {
            return;
        }

        updateBoundsCache();
        Rectangle2D repaintBounds = repaintBounds(previousBounds, includeCurrentBounds);
        if (repaintBounds != null) {
            getChart().getChartArea().repaint2D(repaintBounds);
        }
    }

    private Rectangle2D computeBounds(boolean includeIndicatorShape) {
        Chart chart = getChart();
        if (chart == null) {
            return new Rectangle();
        }

        Rectangle2D result;
        CoordinateSystem coordinateSystem = chart.getCoordinateSystem(getCoordinateSystemIndex());
        DoublePoint labelLocation = null;

        if (isValueIndicator()) {
            Axis axis = (axisIndex == -1) ? chart.getXAxis() : chart.getYAxis(axisIndex);
            if (!axis.getVisibleRange().isInside(value)) {
                return new Rectangle();
            }

            Shape shape = chart.getProjector().getShape(
                    value,
                    getProjectorAxis(),
                    chart.getChartArea().getPlotRect(),
                    coordinateSystem);
            result = getDrawStyle().getShapeBounds(shape);
            if (text != null) {
                labelLocation = computeLabelLocation(coordinateSystem.getVisibleWindow());
            }
        } else {
            DataWindow visibleWindow = new DataWindow(dataWindow);
            visibleWindow.intersection(coordinateSystem.getVisibleWindow());
            if (visibleWindow.isEmpty()) {
                return new Rectangle();
            }

            Shape shape = chart.getProjector().getShape(
                    visibleWindow,
                    chart.getChartArea().getPlotRect(),
                    coordinateSystem);
            result = getDrawStyle().getShapeBounds(shape);
            if (text != null) {
                labelLocation = computeLabelLocation(visibleWindow);
            }
        }

        if (labelLocation == null) {
            return includeIndicatorShape ? result : new Rectangle();
        }

        Rectangle2D labelBounds = labelRenderer.getBounds(
                chart.getChartArea(),
                labelLocation.xFloor(),
                labelLocation.yFloor(),
                resolveLabelText(),
                null);
        return includeIndicatorShape ? GraphicUtil.addToRect(result, labelBounds) : labelBounds;
    }

    private void updateDataWindow(double xMin, double xMax, double yMin, double yMax) {
        if (dataWindow != null
                && xMin == dataWindow.xRange.getMin()
                && xMax == dataWindow.xRange.getMax()
                && yMin == dataWindow.yRange.getMin()
                && yMax == dataWindow.yRange.getMax()) {
            return;
        }

        Rectangle2D previousBounds = captureBoundsIfAttachedAndVisible();
        if (dataWindow == null) {
            dataWindow = new DataWindow(xMin, xMax, yMin, yMax);
        } else {
            dataWindow.xRange.set(xMin, xMax);
            dataWindow.yRange.set(yMin, yMax);
        }

        if (previousBounds != null) {
            repaintAfterGeometryChange(previousBounds, true);
        }
    }

    private void paintValueIndicator(Graphics g) {
        Axis axis = (axisIndex == -1) ? getChart().getXAxis() : getChart().getYAxis(axisIndex);
        if (!axis.getVisibleRange().isInside(value)) {
            return;
        }

        Chart chart = getChart();
        CoordinateSystem coordinateSystem = chart.getCoordinateSystem(getCoordinateSystemIndex());
        Shape shape = chart.getProjector().getShape(
                value,
                getProjectorAxis(),
                chart.getChartArea().getPlotRect(),
                coordinateSystem);

        getDrawStyle().draw(g, shape);
        if (text != null) {
            DoublePoint labelLocation = computeLabelLocation(coordinateSystem.getVisibleWindow());
            labelRenderer.paintLabel(
                    chart.getChartArea(),
                    g,
                    resolveLabelText(),
                    labelLocation.xFloor(),
                    labelLocation.yFloor());
        }
    }

    private void paintRegionIndicator(Graphics g, boolean beforeDataPass) {
        Chart chart = getChart();
        CoordinateSystem coordinateSystem = chart.getCoordinateSystem(getCoordinateSystemIndex());
        DataWindow visibleWindow = new DataWindow(dataWindow);
        visibleWindow.intersection(coordinateSystem.getVisibleWindow());
        if (visibleWindow.isEmpty()) {
            return;
        }

        Rectangle plotRect = chart.getChartArea().getPlotRect();
        boolean intersectsClip = true;
        Rectangle clipBounds = g.getClipBounds();
        if (clipBounds != null) {
            Rectangle expandedClipBounds = (Rectangle) clipBounds.clone();
            getDrawStyle().expand(true, expandedClipBounds);
            intersectsClip = chart.getProjector()
                    .toDataWindow(expandedClipBounds, plotRect, coordinateSystem)
                    .intersects(visibleWindow);
        }

        if (beforeDataPass) {
            if (intersectsClip) {
                Shape shape = chart.getProjector().getShape(visibleWindow, plotRect, coordinateSystem);
                getDrawStyle().setStrokeOn(false).plotShape(g, shape);
            }
            return;
        }

        if (intersectsClip) {
            Shape shape = chart.getProjector().getShape(visibleWindow, plotRect, coordinateSystem);
            getDrawStyle().setFillOn(false).plotShape(g, shape);
        }
        if (text != null) {
            DoublePoint labelLocation = computeLabelLocation(visibleWindow);
            labelRenderer.paintLabel(
                    chart.getChartArea(),
                    g,
                    resolveLabelText(),
                    labelLocation.xFloor(),
                    labelLocation.yFloor());
        }
    }

    private void setType(int type) {
        this.type = type;
    }

    private void configureRangeIndicator(int axisIndex, DataInterval range) {
        this.axisIndex = axisIndex;
        if (axisIndex != -1) {
            setType(Y_RANGE);
            updateDataWindow(UNBOUNDED_MIN, UNBOUNDED_MAX, range.getMin(), range.getMax());
        } else {
            setType(X_RANGE);
            updateDataWindow(range.getMin(), range.getMax(), UNBOUNDED_MIN, UNBOUNDED_MAX);
        }
    }

    private void configureWindowIndicator(int axisIndex, DataWindow dataWindow) {
        if (axisIndex < 0) {
            throw new IllegalArgumentException("Y axis index must be positive");
        }

        setType(WINDOW);
        this.axisIndex = axisIndex;
        updateDataWindow(
                dataWindow.getXMin(),
                dataWindow.getXMax(),
                dataWindow.getYMin(),
                dataWindow.getYMax());
    }

    private void configureValueIndicator(int axisIndex, double value) {
        setType((axisIndex != -1) ? Y_VALUE : X_VALUE);
        this.axisIndex = axisIndex;
        dataWindow = null;
        this.value = value;
    }

    private Rectangle2D computeLabelBounds() {
        return computeBounds(false);
    }

    private String resolveLabelText() {
        return BidiUtil.getCombinedString(
                getText(),
                getChart().getResolvedBaseTextDirection(),
                getChart().getComponentOrientation(),
                false);
    }

    @Override
    public void afterDraw(Graphics g) {
        if (getChart() == null) {
            return;
        }

        if (isValueIndicator()) {
            paintValueIndicator(g);
        } else {
            paintRegionIndicator(g, false);
        }
    }

    @Override
    protected void baseTextDirectionChanged() {
        if (isAttachedAndVisible()) {
            updateBoundsCache();
        }
    }

    @Override
    public void beforeDraw(Graphics g) {
        if (getChart() == null) {
            return;
        }

        if (!isValueIndicator()) {
            paintRegionIndicator(g, true);
        }
    }

    @Override
    protected void chartConnected(Chart previousChart, Chart chart) {
        if (axisIndex >= 0 && chart != null && axisIndex >= chart.getYAxisCount()) {
            throw new IndexOutOfBoundsException("Cannot add indicator: invalid y-axis index");
        }

        super.chartConnected(previousChart, chart);

        if (previousChart != null) {
            previousChart.removeChartListener(areaListener);
        }
        if (chart != null) {
            chart.addChartListener(areaListener);
            if (isVisible()) {
                updateBoundsCache();
            }
        }

        if (isVisible()) {
            if (previousChart != null && chart == null) {
                labelRenderer.removePropertyChangeListener(labelRendererChangeListener);
            } else if (previousChart == null && chart != null) {
                labelRenderer.addPropertyChangeListener(labelRendererChangeListener);
            }
        }
    }

    @Override
    protected void componentOrientationChanged(ComponentOrientation oldOrientation, ComponentOrientation newOrientation) {
        if (newOrientation.isLeftToRight() != oldOrientation.isLeftToRight() && isAttachedAndVisible()) {
            updateBoundsCache();
        }
    }

    /// Computes the display-space anchor used for this indicator's label.
    ///
    /// Value indicators place the label halfway across the currently visible span on the
    /// perpendicular axis. Range and window indicators place it at the center of the supplied
    /// visible window.
    ///
    /// @param window visible data window currently used for painting
    /// @return label anchor in display coordinates
    protected DoublePoint computeLabelLocation(DataWindow window) {
        DoublePoints points = new DoublePoints(1);
        if (isValueIndicator()) {
            if (getProjectorAxis() == PROJECTOR_Y_AXIS) {
                points.add((window.getXMin() + window.getXMax()) / 2.0, value);
            } else {
                points.add(value, (window.getYMin() + window.getYMax()) / 2.0);
            }
        } else {
            points.add(
                    (window.getXMin() + window.getXMax()) / 2.0,
                    (window.getYMin() + window.getYMax()) / 2.0);
        }

        getChart().toDisplay(points, getCoordinateSystemIndex());
        DoublePoint labelLocation = new DoublePoint(points.getX(0), points.getY(0));
        points.dispose();
        return labelLocation;
    }

    /// Returns whether the rendered indicator shape contains the given display-space point.
    ///
    /// The optional label is not part of the hit area.
    ///
    /// @param x display-space x coordinate
    /// @param y display-space y coordinate
    /// @return `true` when the indicator shape contains the point
    public boolean contains(int x, int y) {
        Shape shape = getShape();
        return shape != null && getDrawStyle().shapeContains(shape, x, y);
    }

    @Override
    public void draw(Graphics g) {
    }

    /// Returns the axis binding for this indicator.
    ///
    /// `-1` means the chart x axis. Non-negative values select the corresponding y-axis slot.
    ///
    /// @return axis binding used by this indicator
    public final int getAxisIndex() {
        return axisIndex;
    }

    @Override
    public Rectangle2D getBounds(Rectangle2D bounds) {
        return computeBounds(true);
    }

    /// Returns a defensive copy of the stored window geometry.
    ///
    /// Value indicators return `null`. Range indicators return the internal two-axis window used
    /// for projection; use [#getRange()] when only the bounded axis interval matters.
    ///
    /// @return copied window geometry, or `null` for value indicators
    public final DataWindow getDataWindow() {
        return isValueIndicator() ? null : (dataWindow == null ? null : new DataWindow(dataWindow));
    }

    /// Returns the effective style used for painting.
    ///
    /// When no explicit [#setStyle(PlotStyle)] has been installed, value indicators default to a
    /// stroked version of the chart area's foreground color and range/window indicators default to
    /// foreground-stroke/background-fill style derived from the chart area. Resolving that fallback
    /// requires the indicator to be attached to a chart.
    ///
    /// @return effective painting style
    public PlotStyle getDrawStyle() {
        if (style != null) {
            return style;
        }

        Chart chart = Objects.requireNonNull(
                getChart(),
                "Cannot resolve the default draw style while detached from a chart");
        if (isValueIndicator()) {
            return PlotStyle.createStroked(chart.getChartArea().getForeground());
        }
        return new PlotStyle(chart.getChartArea().getForeground(), chart.getChartArea().getBackground());
    }

    /// Returns the live label renderer retained by this indicator.
    ///
    /// Changes to the returned renderer are observed immediately while the indicator is attached and
    /// visible.
    ///
    /// @return live label renderer
    public final LabelRenderer getLabelRenderer() {
        return labelRenderer;
    }

    /// Returns a defensive copy of the bounded axis interval for range indicators.
    ///
    /// Value and window indicators return `null`.
    ///
    /// @return copied range geometry, or `null` when this indicator is not a range indicator
    public final DataInterval getRange() {
        if (type == X_RANGE) {
            return new DataInterval(dataWindow.xRange);
        }
        if (type == Y_RANGE) {
            return new DataInterval(dataWindow.yRange);
        }
        return null;
    }

    /// Returns the current projected indicator shape.
    ///
    /// Range and window indicators are clipped to the current visible data window before
    /// projection.
    ///
    /// @return projected shape, or `null` while detached or when the visible portion is empty
    public Shape getShape() {
        Chart chart = getChart();
        if (chart == null) {
            return null;
        }

        CoordinateSystem coordinateSystem = chart.getCoordinateSystem(getCoordinateSystemIndex());
        if (isValueIndicator()) {
            return chart.getProjector().getShape(
                    value,
                    getProjectorAxis(),
                    chart.getChartArea().getPlotRect(),
                    coordinateSystem);
        }

        DataWindow visibleWindow = new DataWindow(dataWindow);
        visibleWindow.intersection(coordinateSystem.getVisibleWindow());
        return visibleWindow.isEmpty()
                ? null
                : chart.getProjector().getShape(visibleWindow, chart.getChartArea().getPlotRect(), coordinateSystem);
    }

    /// Returns the explicit style configured through [#setStyle(PlotStyle)].
    ///
    /// A `null` result means this indicator is currently using its chart-derived fallback style.
    ///
    /// @return explicit style, or `null`
    public final PlotStyle getStyle() {
        return style;
    }

    /// Returns the optional label text.
    ///
    /// @return label text, or `null` when this indicator paints no label
    public String getText() {
        return text;
    }

    /// Returns the current indicator mode.
    ///
    /// @return one of [#X_VALUE], [#Y_VALUE], [#X_RANGE], [#Y_RANGE], or [#WINDOW]
    public final int getType() {
        return type;
    }

    /// Returns the highlighted data value for value indicators.
    ///
    /// Range and window indicators return `0.0`.
    ///
    /// @return highlighted data value, or `0.0` when this indicator is not a value indicator
    public final double getValue() {
        return isValueIndicator() ? value : 0.0;
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initializeTransientState();
    }

    /// Replaces the highlighted window geometry.
    ///
    /// The supplied bounds are copied into this indicator. The `DataWindow` instance itself is not
    /// retained.
    ///
    /// @param dataWindow new window geometry
    /// @throws UnsupportedOperationException if this indicator is not of type [#WINDOW]
    public void setDataWindow(DataWindow dataWindow) {
        setDataWindow(dataWindow.getXMin(), dataWindow.getXMax(), dataWindow.getYMin(), dataWindow.getYMax());
    }

    /// Replaces the highlighted window geometry.
    ///
    /// @param xMin lower x bound
    /// @param xMax upper x bound
    /// @param yMin lower y bound
    /// @param yMax upper y bound
    /// @throws UnsupportedOperationException if this indicator is not of type [#WINDOW]
    public void setDataWindow(double xMin, double xMax, double yMin, double yMax) {
        if (type != WINDOW) {
            throw new UnsupportedOperationException("Invalid indicator type");
        }
        updateDataWindow(xMin, xMax, yMin, yMax);
    }

    /// Replaces the label renderer retained by this indicator.
    ///
    /// The supplied renderer is kept by reference. While the indicator is attached and visible, it
    /// is registered for [LabelRendererPropertyEvent] updates so label-only repaint requests stay
    /// precise.
    ///
    /// @param labelRenderer new label renderer to retain
    /// @throws IllegalArgumentException if `labelRenderer` is `null`
    public void setLabelRenderer(LabelRenderer labelRenderer) {
        if (labelRenderer == null) {
            throw new IllegalArgumentException("LabelRenderer cannot be null.");
        }
        if (labelRenderer == this.labelRenderer) {
            return;
        }

        Rectangle2D previousBounds = captureBoundsIfAttachedAndVisible();
        if (previousBounds != null) {
            this.labelRenderer.removePropertyChangeListener(labelRendererChangeListener);
        }

        this.labelRenderer = labelRenderer;

        if (previousBounds != null) {
            this.labelRenderer.addPropertyChangeListener(labelRendererChangeListener);
            repaintAfterGeometryChange(previousBounds, true);
        }
    }

    /// Replaces the bounded axis interval for a range indicator.
    ///
    /// The supplied interval is copied into this indicator.
    ///
    /// @param range new bounded interval
    /// @throws UnsupportedOperationException if this indicator is not of type [#X_RANGE] or
    ///                                                                                                                       [#Y_RANGE]
    public void setRange(DataInterval range) {
        if (type == X_RANGE) {
            if (!dataWindow.xRange.equals(range)) {
                updateDataWindow(range.getMin(), range.getMax(), UNBOUNDED_MIN, UNBOUNDED_MAX);
            }
            return;
        }
        if (type == Y_RANGE) {
            if (!dataWindow.yRange.equals(range)) {
                updateDataWindow(UNBOUNDED_MIN, UNBOUNDED_MAX, range.getMin(), range.getMax());
            }
            return;
        }
        throw new UnsupportedOperationException("Invalid indicator type");
    }

    /// Sets the explicit painting style for this indicator.
    ///
    /// Passing `null` restores the chart-derived fallback returned by [#getDrawStyle()].
    ///
    /// @param style explicit style to retain, or `null` to use the fallback style
    public void setStyle(PlotStyle style) {
        if (style == this.style) {
            return;
        }

        Rectangle2D previousBounds = captureBoundsIfAttachedAndVisible();
        this.style = style;
        if (previousBounds != null) {
            repaintAfterGeometryChange(previousBounds, true);
        }
    }

    /// Sets the optional label text.
    ///
    /// Passing `null` removes the label.
    ///
    /// @param text label text, or `null`
    public void setText(String text) {
        if (Objects.equals(this.text, text)) {
            return;
        }

        Rectangle2D previousBounds = null;
        if (isAttachedAndVisible() && this.text != null) {
            previousBounds = getBounds(null);
        }

        this.text = text;
        if (previousBounds != null || isAttachedAndVisible()) {
            repaintAfterGeometryChange(previousBounds, text != null);
        }
    }

    /// Replaces the highlighted data value for a value indicator.
    ///
    /// @param value new highlighted data value
    /// @throws UnsupportedOperationException if this indicator is not of type [#X_VALUE] or
    ///                                                                                                                       [#Y_VALUE]
    public void setValue(double value) {
        if (!isValueIndicator()) {
            throw new UnsupportedOperationException("Invalid indicator type");
        }
        if (value == this.value) {
            return;
        }

        Rectangle2D previousBounds = captureBoundsIfAttachedAndVisible();
        this.value = value;
        if (previousBounds != null) {
            repaintAfterGeometryChange(previousBounds, true);
        }
    }

    @Override
    public void setVisible(boolean visible) {
        boolean wasVisible = isVisible();
        super.setVisible(visible);

        if (getChart() == null) {
            return;
        }
        if (wasVisible && !isVisible()) {
            labelRenderer.removePropertyChangeListener(labelRendererChangeListener);
        } else if (!wasVisible && isVisible()) {
            labelRenderer.addPropertyChangeListener(labelRendererChangeListener);
        }
    }

    @Override
    protected void updateBoundsCache() {
        super.updateBoundsCache();
        boundsCache = computeLabelBounds();
    }
}
