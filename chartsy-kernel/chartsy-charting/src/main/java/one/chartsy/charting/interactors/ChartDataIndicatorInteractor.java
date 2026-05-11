package one.chartsy.charting.interactors;

import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Objects;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartDecoration;
import one.chartsy.charting.ChartInteractor;
import one.chartsy.charting.CoordinateSystem;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.DoublePoint;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.graphic.DataIndicator;

/// Edits [DataIndicator] decorations by dragging their lines, bands, or window edges.
///
/// On mouse press, the interactor walks the chart's decoration list from frontmost to backmost and
/// captures the first [DataIndicator] whose rendered shape contains the pointer. Value indicators
/// always translate. Range and window indicators switch between translation and edge resizing when
/// the press lands within the outer 10% of the bounded span on the relevant axis.
///
/// When [#isOpaqueEdit()] is `false`, the live indicator stays unchanged during the drag and this
/// interactor paints a ghost preview instead. When it is `true`, every drag step is committed to
/// the indicator immediately. In both modes, the dragged data coordinates are clamped to the
/// current chart x-axis range and the active y-axis range before the delta is applied.
public class ChartDataIndicatorInteractor extends ChartInteractor {
    private static final double EDGE_HANDLE_RATIO = 0.1;
    private static final int RESIZE_MIN_X = 1;
    private static final int RESIZE_MAX_X = 2;
    private static final int RESIZE_MIN_Y = 3;
    private static final int RESIZE_MAX_Y = 4;
    private static final int PROJECTOR_X_AXIS = 1;
    private static final int PROJECTOR_Y_AXIS = 2;

    /// Strategy that turns one drag delta into indicator geometry updates.
    ///
    /// Implementations return the updated working state that the interactor should keep between
    /// drag events. Value indicators use a [Double]; range and window indicators use a mutable
    /// [DataWindow] copy.
    private interface InteractionHandler extends Serializable {
        /// Applies one drag delta to the active indicator.
        ///
        /// @param deltaX x-axis delta in data coordinates
        /// @param deltaY y-axis delta in data coordinates
        /// @param commit `true` to write the updated geometry back to the indicator immediately
        /// @return updated working state for the current drag operation
        Object applyDelta(double deltaX, double deltaY, boolean commit);

        /// Returns the cursor that should be shown while this handler is active.
        ///
        /// @return cursor for the current edit mode
        Cursor getCursor();
    }

    /// Resizes one bounded edge of the active range or window geometry.
    ///
    /// The handler keeps the opposite edge fixed and mutates the shared working [DataWindow] copy
    /// that the enclosing interactor carries across drag events. Range indicators write back only
    /// the bounded axis interval, while window indicators commit the whole window.
    private final class Resizer implements InteractionHandler {
        private final int resizeHandle;

        private Resizer(int resizeHandle) {
            this.resizeHandle = resizeHandle;
        }

        @Override
        public Object applyDelta(double deltaX, double deltaY, boolean commit) {
            DataWindow dataWindow = (workingState == null)
                    ? requireIndicatorDataWindow()
                    : (DataWindow) workingState;
            DataInterval resizedRange = null;

            switch (resizeHandle) {
                case RESIZE_MIN_X:
                    dataWindow.xRange.min += deltaX;
                    resizedRange = dataWindow.xRange;
                    break;
                case RESIZE_MAX_X:
                    dataWindow.xRange.max += deltaX;
                    resizedRange = dataWindow.xRange;
                    break;
                case RESIZE_MIN_Y:
                    dataWindow.yRange.min += deltaY;
                    resizedRange = dataWindow.yRange;
                    break;
                case RESIZE_MAX_Y:
                    dataWindow.yRange.max += deltaY;
                    resizedRange = dataWindow.yRange;
                    break;
                default:
                    break;
            }

            if (commit) {
                if (getIndicator().getType() == DataIndicator.WINDOW)
                    getIndicator().setDataWindow(dataWindow);
                else
                    getIndicator().setRange(resizedRange);
            }
            return dataWindow;
        }

        @Override
        public Cursor getCursor() {
            return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        }
    }

    /// Translates the active indicator without changing its size.
    ///
    /// Value indicators accumulate a single scalar value. Range and window indicators reuse the
    /// shared working [DataWindow] copy and move only the axes owned by the current indicator type.
    private final class Translator implements InteractionHandler {
        @Override
        public Object applyDelta(double deltaX, double deltaY, boolean commit) {
            return switch (getIndicator().getType()) {
                case DataIndicator.X_VALUE, DataIndicator.Y_VALUE -> {
                    double value = (workingState == null)
                            ? getIndicator().getValue()
                            : (Double) workingState;
                    value += (getIndicator().getType() == DataIndicator.X_VALUE) ? deltaX : deltaY;
                    if (commit)
                        getIndicator().setValue(value);
                    yield value;
                }
                default -> {
                    DataWindow dataWindow = (workingState == null)
                            ? requireIndicatorDataWindow()
                            : (DataWindow) workingState;
                    int type = getIndicator().getType();
                    if (type != DataIndicator.Y_RANGE)
                        dataWindow.xRange.translate(deltaX);
                    if (type != DataIndicator.X_RANGE)
                        dataWindow.yRange.translate(deltaY);

                    if (commit) {
                        if (type == DataIndicator.X_RANGE)
                            getIndicator().setRange(dataWindow.xRange);
                        else if (type == DataIndicator.Y_RANGE)
                            getIndicator().setRange(dataWindow.yRange);
                        else
                            getIndicator().setDataWindow(dataWindow);
                    }
                    yield dataWindow;
                }
            };
        }

        @Override
        public Cursor getCursor() {
            return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
        }
    }

    private transient DataIndicator indicator;
    private boolean opaqueEdit;
    private transient Object workingState;
    private final InteractionHandler translateHandler;
    private transient InteractionHandler activeHandler;
    private transient DoublePoint lastDataPoint;

    /// Creates an interactor for the primary y-axis slot with primary-button drag gestures.
    ///
    /// The default mode uses ghost previews instead of immediate commits.
    public ChartDataIndicatorInteractor() {
        this(0, 16, false);
    }

    /// Creates an interactor for one y-axis slot and one mouse-modifier combination.
    ///
    /// @param yAxisIndex y-axis slot whose coordinate system is used for preview projection
    /// @param eventMask  legacy mouse modifier mask required to start editing
    /// @param opaqueEdit `true` to commit every drag step immediately, `false` to use ghost
    ///                                         previews until mouse release
    public ChartDataIndicatorInteractor(int yAxisIndex, int eventMask, boolean opaqueEdit) {
        super(yAxisIndex, eventMask);
        translateHandler = new Translator();
        enableEvents(56L);
        this.opaqueEdit = opaqueEdit;
        clearInteractionState();
    }

    private void clearInteractionState() {
        indicator = null;
        workingState = null;
        activeHandler = null;
        lastDataPoint = null;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean pickIndicatorAt(int x, int y) {
        List<ChartDecoration> decorations = getChart().getDecorations();
        for (int i = decorations.size() - 1; i >= 0; i--) {
            ChartDecoration decoration = decorations.get(i);
            if (decoration instanceof DataIndicator dataIndicator && dataIndicator.contains(x, y)) {
                indicator = dataIndicator;
                return true;
            }
        }
        return false;
    }

    private DataWindow requireIndicatorDataWindow() {
        return Objects.requireNonNull(getIndicator().getDataWindow(), "Expected a range or window indicator");
    }

    private InteractionHandler createWindowHandler(MouseEvent event, DoublePoint point, DataWindow dataWindow) {
        double xMargin = dataWindow.xRange.getLength() * EDGE_HANDLE_RATIO;
        int xHandle;
        double xDistance;
        if (point.x < dataWindow.xRange.min + xMargin) {
            xHandle = RESIZE_MIN_X;
            DoublePoint edgePoint = toDisplay(new DoublePoint(
                    dataWindow.xRange.min,
                    clamp(point.y, dataWindow.yRange.min, dataWindow.yRange.max)));
            xDistance = Math.hypot(event.getX() - edgePoint.x, event.getY() - edgePoint.y);
        } else if (point.x <= dataWindow.xRange.max - xMargin) {
            xHandle = 0;
            xDistance = 0.0;
        } else {
            xHandle = RESIZE_MAX_X;
            DoublePoint edgePoint = toDisplay(new DoublePoint(
                    dataWindow.xRange.max,
                    clamp(point.y, dataWindow.yRange.min, dataWindow.yRange.max)));
            xDistance = Math.hypot(event.getX() - edgePoint.x, event.getY() - edgePoint.y);
        }

        double yMargin = dataWindow.yRange.getLength() * EDGE_HANDLE_RATIO;
        int yHandle;
        double yDistance;
        if (point.y < dataWindow.yRange.min + yMargin) {
            yHandle = RESIZE_MIN_Y;
            DoublePoint edgePoint = toDisplay(new DoublePoint(
                    clamp(point.x, dataWindow.xRange.min, dataWindow.xRange.max),
                    dataWindow.yRange.min));
            yDistance = Math.hypot(event.getX() - edgePoint.x, event.getY() - edgePoint.y);
        } else if (point.y <= dataWindow.yRange.max - yMargin) {
            yHandle = 0;
            yDistance = 0.0;
        } else {
            yHandle = RESIZE_MAX_Y;
            DoublePoint edgePoint = toDisplay(new DoublePoint(
                    clamp(point.x, dataWindow.xRange.min, dataWindow.xRange.max),
                    dataWindow.yRange.max));
            yDistance = Math.hypot(event.getX() - edgePoint.x, event.getY() - edgePoint.y);
        }

        if (xHandle != 0 && yHandle != 0) {
            if (xDistance >= yDistance)
                xHandle = 0;
            else
                yHandle = 0;
        }

        if (xHandle != 0)
            return new Resizer(xHandle);
        if (yHandle != 0)
            return new Resizer(yHandle);
        return translateHandler;
    }

    private InteractionHandler createRangeHandler(double value, DataInterval range, int minHandle, int maxHandle) {
        double margin = range.getLength() * EDGE_HANDLE_RATIO;
        if (value < range.min + margin)
            return new Resizer(minHandle);
        if (value > range.max - margin)
            return new Resizer(maxHandle);
        return translateHandler;
    }

    private InteractionHandler selectHandler(MouseEvent event) {
        DoublePoint point = getData(event);
        DataWindow dataWindow = requireIndicatorDataWindow();
        return switch (indicator.getType()) {
            case DataIndicator.X_VALUE, DataIndicator.Y_VALUE -> translateHandler;
            case DataIndicator.X_RANGE -> createRangeHandler(
                    point.x,
                    dataWindow.xRange,
                    RESIZE_MIN_X,
                    RESIZE_MAX_X);
            case DataIndicator.Y_RANGE -> createRangeHandler(
                    point.y,
                    dataWindow.yRange,
                    RESIZE_MIN_Y,
                    RESIZE_MAX_Y);
            case DataIndicator.WINDOW -> createWindowHandler(event, point, dataWindow);
            default -> throw new AssertionError("Unknown indicator type: " + indicator.getType());
        };
    }

    private void handleMouseDragged(MouseEvent event) {
        if (!isInOperation())
            return;

        DoublePoint point = getData(event);
        validate(point);
        double deltaX = point.x - lastDataPoint.x;
        double deltaY = point.y - lastDataPoint.y;

        if (opaqueEdit) {
            workingState = activeHandler.applyDelta(deltaX, deltaY, true);
        } else {
            drawGhost();
            workingState = activeHandler.applyDelta(deltaX, deltaY, false);
            drawGhost();
        }

        lastDataPoint = point;
        if (isConsumeEvents())
            event.consume();
    }

    private void handleMousePressed(MouseEvent event) {
        if ((event.getModifiersEx() & getEventMaskEx()) != getEventMaskEx())
            return;
        if ((event.getModifiersEx() & ~getEventMaskEx()) != 0)
            return;
        if (!pickIndicatorAt(event.getX(), event.getY()))
            return;

        lastDataPoint = getData(event);
        activeHandler = selectHandler(event);
        startOperation(event);
        workingState = activeHandler.applyDelta(0.0, 0.0, opaqueEdit);
        if (!opaqueEdit)
            drawGhost();
        if (isConsumeEvents())
            event.consume();
    }

    private void handleMouseReleased(MouseEvent event) {
        if ((event.getModifiersEx() & getEventMaskEx()) == getEventMaskEx())
            return;
        if (!isInOperation())
            return;

        DoublePoint point = getData(event);
        validate(point);
        if (!opaqueEdit)
            drawGhost();

        double deltaX = point.x - lastDataPoint.x;
        double deltaY = point.y - lastDataPoint.y;
        activeHandler.applyDelta(deltaX, deltaY, true);
        endOperation(event);
        if (isConsumeEvents())
            event.consume();
    }

    private Shape getGhostShape() {
        if (indicator == null || workingState == null)
            return null;

        Chart chart = getChart();
        CoordinateSystem coordinateSystem = chart.getCoordinateSystem(getYAxisIndex());
        return switch (indicator.getType()) {
            case DataIndicator.X_VALUE, DataIndicator.Y_VALUE -> chart.getProjector().getShape(
                    (Double) workingState,
                    (indicator.getAxisIndex() != -1) ? PROJECTOR_Y_AXIS : PROJECTOR_X_AXIS,
                    chart.getChartArea().getPlotRect(),
                    coordinateSystem);
            default -> {
                DataWindow visibleWindow = new DataWindow((DataWindow) workingState);
                visibleWindow.intersection(coordinateSystem.getVisibleWindow());
                yield visibleWindow.isEmpty()
                        ? null
                        : chart.getProjector().getShape(
                        visibleWindow,
                        chart.getChartArea().getPlotRect(),
                        coordinateSystem);
            }
        };
    }

    private PlotStyle resolveGhostStyle() {
        if (!isXORGhost() && indicator != null && indicator.getStyle() != null)
            return indicator.getStyle();
        return new PlotStyle(1.0f, getGhostColor());
    }

    @Override
    protected void abort() {
        super.abort();
        setGhostDrawingAllowed(false);
        setCursor(null);
        clearInteractionState();
    }

    @Override
    protected void drawGhost(Graphics g) {
        Shape ghostShape = getGhostShape();
        if (ghostShape != null)
            resolveGhostStyle().plotShape(g, ghostShape);
    }

    @Override
    protected void endOperation(MouseEvent event) {
        super.endOperation(event);
        setGhostDrawingAllowed(false);
        setCursor(null);
        clearInteractionState();
    }

    @Override
    protected Rectangle getGhostBounds() {
        Shape ghostShape = getGhostShape();
        return (ghostShape != null)
                ? resolveGhostStyle().getShapeBounds(ghostShape).getBounds()
                : new Rectangle();
    }

    /// Returns the indicator currently being edited, if any.
    ///
    /// @return active indicator, or `null` when no drag operation is in progress
    protected final DataIndicator getIndicator() {
        return indicator;
    }

    /// Returns whether drag updates are committed immediately.
    ///
    /// A `false` result means the interactor uses a ghost preview until mouse release.
    ///
    /// @return `true` for immediate commits, `false` for ghost previews
    public final boolean isOpaqueEdit() {
        return opaqueEdit;
    }

    @Override
    public void processKeyEvent(KeyEvent event) {
        if (event.getID() == KeyEvent.KEY_PRESSED && event.getKeyCode() == KeyEvent.VK_ESCAPE) {
            if (!opaqueEdit && lastDataPoint != null)
                drawGhost();
            abort();
            if (isConsumeEvents())
                event.consume();
        }
    }

    @Override
    public void processMouseEvent(MouseEvent event) {
        switch (event.getID()) {
            case MouseEvent.MOUSE_PRESSED:
                handleMousePressed(event);
                break;
            case MouseEvent.MOUSE_RELEASED:
                handleMouseReleased(event);
                break;
            default:
                break;
        }
    }

    @Override
    public void processMouseMotionEvent(MouseEvent event) {
        if (event.getID() == MouseEvent.MOUSE_DRAGGED)
            handleMouseDragged(event);
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        clearInteractionState();
    }

    /// Switches between immediate commits and ghost-preview editing.
    ///
    /// @param opaqueEdit `true` to commit every drag step immediately, `false` to defer until
    ///                                         mouse release
    public void setOpaqueEdit(boolean opaqueEdit) {
        this.opaqueEdit = opaqueEdit;
    }

    @Override
    protected void startOperation(MouseEvent event) {
        super.startOperation(event);
        setGhostDrawingAllowed(!opaqueEdit);
        if (activeHandler != null)
            setCursor(activeHandler.getCursor());
    }

    /// Clamps the dragged point into the current chart x-axis range and active y-axis range.
    ///
    /// Subclasses can override this to impose stricter editing constraints before deltas are
    /// applied.
    ///
    /// @param point mutable data-space point to clamp in place
    protected void validate(DoublePoint point) {
        DataInterval xRange = getChart().getXAxis().getDataRange();
        point.x = Math.max(xRange.min, Math.min(xRange.max, point.x));

        DataInterval yRange = getChart().getYAxis(indicator.getAxisIndex()).getDataRange();
        point.y = Math.max(yRange.min, Math.min(yRange.max, point.y));
    }
}
