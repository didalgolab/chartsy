package one.chartsy.charting.interactors;

import java.awt.AWTEvent;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;

import javax.imageio.ImageIO;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartInteractor;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.DoublePoints;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.util.swing.CursorRegistry;
import one.chartsy.charting.util.swing.EventUtil;

/// Drag-box zoom interactor for one chart coordinate system.
///
/// By default the gesture uses the primary mouse button to zoom in and `Shift` plus the primary
/// mouse button to zoom out. The historic default configuration changes only the shared x axis;
/// callers may opt into vertical zoom through [#setYZoomAllowed(boolean)].
///
/// During a drag the interactor keeps a mutable [DataWindow] in data coordinates, paints it as an
/// XOR ghost, and on release either applies it directly or animates the transition through
/// [Chart#zoom(DataWindow, int)]. Subclasses can narrow the allowed start region through
/// [#isValidStartPoint(double, double)], clamp the drag box through [#validate(DataWindow)], or
/// reinterpret the final window through [#getZoomedDataWindow()] and [#doIt()].
///
/// Instances are mutable UI objects and are not thread-safe.
public class ChartZoomInteractor extends ChartInteractor {
    private static final int MIN_DRAG_DISTANCE = 5;
    private static final int DEFAULT_ZOOM_IN_EVENT_MASK = 16;
    private static final int DEFAULT_ZOOM_OUT_EVENT_MASK = 17;
    private static final int DEFAULT_ANIMATION_STEP = 10;
    private static final int SMALL_CURSOR_SIZE = 32;
    private static final int LARGE_CURSOR_SIZE = 64;

    private static Cursor zoomInCursor;
    private static Cursor zoomOutCursor;

    static {
        ChartInteractor.register("Zoom", ChartZoomInteractor.class);
    }

    private int zoomOutEventMask;
    private int zoomOutEventMaskEx;
    private int animationStep;
    private boolean xZoomAllowed;
    private boolean yZoomAllowed;
    private boolean zoomOutAllowed;
    private transient DoublePoints anchorDataPoint;
    private transient DoublePoints currentDataPoint;
    private transient int anchorX;
    private transient int anchorY;
    private transient boolean zoomingOut;
    private transient DataWindow dragWindow;

    /// Creates a zoom interactor for the primary y-axis slot with the default zoom bindings.
    ///
    /// The defaults correspond to zoom-in on the primary mouse button and zoom-out on
    /// `Shift` plus the primary mouse button.
    public ChartZoomInteractor() {
        this(0, DEFAULT_ZOOM_IN_EVENT_MASK, DEFAULT_ZOOM_OUT_EVENT_MASK);
    }

    /// Creates a zoom interactor for the primary y-axis slot with custom modifier masks.
    ///
    /// @param zoomInEventMask mouse modifier mask that should start zoom-in drags
    /// @param zoomOutEventMask mouse modifier mask that should start zoom-out drags
    public ChartZoomInteractor(int zoomInEventMask, int zoomOutEventMask) {
        this(0, zoomInEventMask, zoomOutEventMask);
    }

    /// Creates a zoom interactor for one y-axis slot and two drag gestures.
    ///
    /// X-axis zoom is enabled by default, y-axis zoom is disabled by default, and zoom-out
    /// gestures are allowed by default.
    ///
    /// @param yAxisIndex the y-axis slot whose coordinate system should receive the zoom
    /// @param zoomInEventMask mouse modifier mask that should start zoom-in drags
    /// @param zoomOutEventMask mouse modifier mask that should start zoom-out drags
    public ChartZoomInteractor(int yAxisIndex, int zoomInEventMask, int zoomOutEventMask) {
        super(yAxisIndex, zoomInEventMask);
        animationStep = DEFAULT_ANIMATION_STEP;
        xZoomAllowed = true;
        yZoomAllowed = false;
        zoomOutAllowed = true;
        initTransientState();
        enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        this.zoomOutEventMask = zoomOutEventMask;
        zoomOutEventMaskEx = EventUtil.convertModifiersMask(zoomOutEventMask);
        setXORGhost(true);
        ensureCursorsInitialized();
    }

    /// Creates a custom cursor and falls back to a predefined cursor when custom creation fails.
    ///
    /// Successful custom cursors are also registered in [CursorRegistry] under `name`.
    ///
    /// @param image image to use for the cursor, or `null` to skip custom creation
    /// @param hotSpot cursor hot spot in image coordinates
    /// @param name registry name for the custom cursor
    /// @param fallbackCursorType predefined cursor type used when custom creation fails
    /// @return the created custom cursor, or the predefined fallback cursor
    public static Cursor createCursor(Image image, Point hotSpot, String name, int fallbackCursorType) {
        Cursor cursor = null;
        if (image != null) {
            try {
                cursor = Toolkit.getDefaultToolkit().createCustomCursor(image, hotSpot, name);
            } catch (IndexOutOfBoundsException exception) {
                // Fall back to the predefined cursor below.
            }
            if (cursor != null) {
                CursorRegistry.registerCustomCursor(name, image, hotSpot);
            }
        }
        return (cursor != null) ? cursor : Cursor.getPredefinedCursor(fallbackCursorType);
    }

    private void ensureCursorsInitialized() {
        if (zoomInCursor != null && zoomOutCursor != null) {
            return;
        }

        Dimension bestCursorSize = null;
        try {
            bestCursorSize = Toolkit.getDefaultToolkit().getBestCursorSize(SMALL_CURSOR_SIZE, SMALL_CURSOR_SIZE);
        } catch (RuntimeException exception) {
            // Fall back to the predefined crosshair cursors below.
        }
        if (bestCursorSize != null) {
            int width = bestCursorSize.width;
            int height = bestCursorSize.height;
            if ((width == SMALL_CURSOR_SIZE && height == SMALL_CURSOR_SIZE)
                    || (width == LARGE_CURSOR_SIZE && height == LARGE_CURSOR_SIZE)) {
                zoomInCursor = loadCursor("zoomin_" + width + ".gif", "ZoomIn_");
                zoomOutCursor = loadCursor("zoomout_" + width + ".gif", "ZoomOut_");
                return;
            }
        }

        zoomInCursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        zoomOutCursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
    }

    private static Cursor loadCursor(String resourceName, String cursorName) {
        try {
            Image image = ImageIO.read(ChartZoomInteractor.class.getResource(resourceName));
            return createCursor(image, new Point(4, 5), cursorName, Cursor.CROSSHAIR_CURSOR);
        } catch (IOException | IllegalArgumentException exception) {
            return Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR);
        }
    }

    private void constrainDraggedWindow(DataWindow window) {
        DataWindow visibleWindow = getCoordinateSystem().getVisibleWindow();
        if (!yZoomAllowed && !visibleWindow.yRange.equals(window.yRange)) {
            window.yRange.min = visibleWindow.getYMin();
            window.yRange.max = visibleWindow.getYMax();
        }
        if (!xZoomAllowed && !visibleWindow.xRange.equals(window.xRange)) {
            window.xRange.min = visibleWindow.getXMin();
            window.xRange.max = visibleWindow.getXMax();
        }
        validate(window);
    }

    private boolean matchesZoomOutMask(int modifiersEx) {
        return matchesExactMask(modifiersEx, getZoomOutEventMaskEx());
    }

    private Shape getDraggedShape() {
        DataWindow visibleWindow = getCoordinateSystem().getVisibleWindow();
        dragWindow.intersection(visibleWindow);
        return getChart().getProjector().getShape(dragWindow, getChart().getProjectorRect(),
                getCoordinateSystem());
    }

    private boolean matchesZoomInMask(int modifiersEx) {
        return matchesExactMask(modifiersEx, getZoomInEventMaskEx());
    }

    private static boolean matchesExactMask(int modifiersEx, int expectedMaskEx) {
        return expectedMaskEx != 0
                && (modifiersEx & expectedMaskEx) == expectedMaskEx
                && (modifiersEx & ~expectedMaskEx) == 0;
    }

    private void updateDraggedWindow() {
        if (dragWindow == null) {
            dragWindow = new DataWindow();
        }

        double minX = Math.min(anchorDataPoint.getX(0), currentDataPoint.getX(0));
        double maxX = Math.max(anchorDataPoint.getX(0), currentDataPoint.getX(0));
        double minY = Math.min(anchorDataPoint.getY(0), currentDataPoint.getY(0));
        double maxY = Math.max(anchorDataPoint.getY(0), currentDataPoint.getY(0));
        dragWindow.xRange.set(minX, maxX);
        dragWindow.yRange.set(minY, maxY);
    }

    private void initTransientState() {
        anchorDataPoint = new DoublePoints(0.0, 0.0);
        currentDataPoint = new DoublePoints(0.0, 0.0);
        anchorX = 0;
        anchorY = 0;
        zoomingOut = false;
        dragWindow = null;
    }

    private void resetOperationState() {
        setGhostDrawingAllowed(false);
        zoomingOut = false;
        if (dragWindow != null) {
            dragWindow.xRange.empty();
            dragWindow.yRange.empty();
        }
        setCursor(null);
    }

    @Override
    protected void abort() {
        super.abort();
        resetOperationState();
    }

    /// Applies the current drag box to the chart.
    ///
    /// A zero animation step applies the target window immediately. Positive values add
    /// `animationStep + 1` intermediate windows before the final [Chart#zoom(DataWindow, int)]
    /// call. Use `0` to disable the animation.
    protected void doIt() {
        DataWindow zoomedWindow = getZoomedDataWindow();
        if (animationStep == 0) {
            getChart().zoom(zoomedWindow, getYAxisIndex());
            return;
        }

        DataInterval initialXRange = getXAxis().getVisibleRange();
        DataInterval initialYRange = getYAxis().getVisibleRange();
        Cursor previousCursor = setCursor(null);
        getChart().getChartArea().setDirectRedrawEnabled(true);
        getXAxis().setAdjusting(true);
        getYAxis().setAdjusting(true);
        setGhostDrawingAllowed(false);
        try {
            int animationFrames = animationStep + 1;
            double minXDelta = (zoomedWindow.xRange.min - initialXRange.min) / animationFrames;
            double maxXDelta = (zoomedWindow.xRange.max - initialXRange.max) / animationFrames;
            double minYDelta = (zoomedWindow.yRange.min - initialYRange.min) / animationFrames;
            double maxYDelta = (zoomedWindow.yRange.max - initialYRange.max) / animationFrames;
            DataWindow animatedWindow = new DataWindow(initialXRange, initialYRange);
            for (int frame = 0; frame < animationFrames; frame++) {
                animatedWindow.xRange.setMin(animatedWindow.getXMin() + minXDelta);
                animatedWindow.xRange.setMax(animatedWindow.getXMax() + maxXDelta);
                animatedWindow.yRange.setMin(animatedWindow.getYMin() + minYDelta);
                animatedWindow.yRange.setMax(animatedWindow.getYMax() + maxYDelta);
                getChart().zoom(animatedWindow, getYAxisIndex());
            }
            getChart().zoom(zoomedWindow, getYAxisIndex());
        } finally {
            setGhostDrawingAllowed(true);
            getChart().getChartArea().setDirectRedrawEnabled(false);
            getXAxis().setAdjusting(false);
            getYAxis().setAdjusting(false);
            setCursor(previousCursor);
        }
    }

    @Override
    protected void drawGhost(Graphics g) {
        if (dragWindow == null || dragWindow.isEmpty()) {
            return;
        }

        Shape draggedShape = getDraggedShape();
        PlotStyle style = !zoomingOut
                ? new PlotStyle(getGhostColor())
                : new PlotStyle(1.0f, getGhostColor());
        style.plotShape(g, draggedShape);
    }

    @Override
    protected void endOperation(MouseEvent event) {
        super.endOperation(event);
        resetOperationState();
    }

    /// Returns the number of intermediate animation steps applied before the final zoom.
    ///
    /// @return the configured animation step count, or `0` when zoom animation is disabled
    public final int getAnimationStep() {
        return animationStep;
    }

    /// Returns a defensive copy of the current drag window.
    ///
    /// @return the current drag box in data coordinates, or an empty window when no drag is active
    protected final DataWindow getDraggedWindow() {
        return (dragWindow != null) ? new DataWindow(dragWindow) : new DataWindow();
    }

    @Override
    protected Rectangle getGhostBounds() {
        return (dragWindow == null || dragWindow.isEmpty()) ? null : getDraggedShape().getBounds();
    }

    /// Returns the data window that the current gesture should apply.
    ///
    /// Zoom-in gestures return the dragged window directly. Zoom-out gestures interpret the dragged
    /// box as the region that should remain visible after the view expands, then derive the larger
    /// enclosing window that would produce that result and clamp it to the current axis data
    /// ranges.
    ///
    /// @return the data window that [#doIt()] should apply
    protected DataWindow getZoomedDataWindow() {
        DataWindow zoomedWindow = getDraggedWindow();
        if (zoomingOut) {
            DataInterval xVisibleRange = getXAxis().getVisibleRange();
            DataInterval yVisibleRange = getYAxis().getVisibleRange();
            double minX = dragWindow.getXMin();
            double maxX = dragWindow.getXMax();
            double minY = dragWindow.getYMin();
            double maxY = dragWindow.getYMax();
            DataInterval xRange = zoomedWindow.xRange;
            DataInterval yRange = zoomedWindow.yRange;

            double scale = xVisibleRange.getLength() / (maxX - minX);
            double scaledMin = xVisibleRange.max - (maxX - xVisibleRange.min) * scale;
            double scaledMax = xVisibleRange.min + (xVisibleRange.max - minX) * scale;
            xRange.set(scaledMin, scaledMax);
            xRange.intersection(getXAxis().getDataRange());

            scale = yVisibleRange.getLength() / (maxY - minY);
            scaledMin = yVisibleRange.max - (maxY - yVisibleRange.min) * scale;
            scaledMax = yVisibleRange.min + (yVisibleRange.max - minY) * scale;
            yRange.set(scaledMin, scaledMax);
            yRange.intersection(getYAxis().getDataRange());
        }
        return zoomedWindow;
    }

    /// Returns the cursor shown while a zoom-in drag is active.
    protected Cursor getZoomInCursor() {
        return zoomInCursor;
    }

    /// Returns the modifier mask that starts zoom-in drags.
    ///
    /// The mask is compared exactly against [MouseEvent#getModifiersEx()], so extra modifiers
    /// prevent the gesture from starting.
    public int getZoomInEventMask() {
        return getEventMask();
    }

    /// Returns the extended modifier mask used internally for zoom-in matching.
    public int getZoomInEventMaskEx() {
        return getEventMaskEx();
    }

    /// Returns the cursor shown while a zoom-out drag is active.
    protected Cursor getZoomOutCursor() {
        return zoomOutCursor;
    }

    /// Returns the modifier mask that starts zoom-out drags.
    ///
    /// The mask is compared exactly against [MouseEvent#getModifiersEx()], so extra modifiers
    /// prevent the gesture from starting.
    public final int getZoomOutEventMask() {
        return zoomOutEventMask;
    }

    /// Returns the extended modifier mask used internally for zoom-out matching.
    public final int getZoomOutEventMaskEx() {
        return zoomOutEventMaskEx;
    }

    /// Returns whether the supplied mouse event should start a zoom gesture.
    ///
    /// The event is accepted when it matches the zoom-in mask exactly or when it matches the
    /// zoom-out mask exactly and zoom-out gestures are still enabled.
    ///
    /// @param event the mouse event to test
    /// @return `true` when this interactor should handle the event
    protected boolean isValid(MouseEvent event) {
        int modifiersEx = event.getModifiersEx();
        return matchesZoomInMask(modifiersEx) || (isZoomOutAllowed() && matchesZoomOutMask(modifiersEx));
    }

    /// Returns whether a zoom drag may start from the supplied data-space location.
    ///
    /// The base implementation accepts every point in the handling region.
    ///
    /// @param x data-space x coordinate of the press location
    /// @param y data-space y coordinate of the press location
    /// @return `true` when the drag may start
    protected boolean isValidStartPoint(double x, double y) {
        return true;
    }

    /// Returns whether x-axis zoom is currently enabled for this interactor.
    public final boolean isXZoomAllowed() {
        return xZoomAllowed;
    }

    /// Returns whether y-axis zoom is currently enabled for this interactor.
    public final boolean isYZoomAllowed() {
        return yZoomAllowed;
    }

    /// Returns whether the current gesture is a zoom-out operation.
    protected boolean isZoomingOut() {
        return zoomingOut;
    }

    /// Returns whether zoom-out drags are currently allowed.
    public boolean isZoomOutAllowed() {
        return zoomOutAllowed;
    }

    @Override
    public void processKeyEvent(KeyEvent event) {
        if (event.getID() == KeyEvent.KEY_PRESSED && event.getKeyCode() == KeyEvent.VK_ESCAPE) {
            if (isInOperation()) {
                drawGhost();
            }
            abort();
            if (isConsumeEvents()) {
                event.consume();
            }
        }
    }

    @Override
    public void processMouseEvent(MouseEvent event) {
        switch (event.getID()) {
            case MouseEvent.MOUSE_PRESSED -> handleMousePressed(event);
            case MouseEvent.MOUSE_RELEASED -> handleMouseReleased(event);
            default -> {
            }
        }
    }

    @Override
    public void processMouseMotionEvent(MouseEvent event) {
        if (event.getID() == MouseEvent.MOUSE_DRAGGED && isInOperation()) {
            drawGhost();
            currentDataPoint.set(0, event.getX(), event.getY());
            getChart().toData(currentDataPoint, getYAxisIndex());
            updateDraggedWindow();
            constrainDraggedWindow(dragWindow);
            drawGhost();
            if (isConsumeEvents()) {
                event.consume();
            }
        }
    }

    private void handleMousePressed(MouseEvent event) {
        if (isInOperation() || !isValid(event)) {
            return;
        }

        zoomingOut = matchesZoomOutMask(event.getModifiersEx());
        anchorX = event.getX();
        anchorY = event.getY();
        anchorDataPoint.set(0, event.getX(), event.getY());
        getChart().toData(anchorDataPoint, getYAxisIndex());
        currentDataPoint.set(0, event.getX(), event.getY());
        getChart().toData(currentDataPoint, getYAxisIndex());
        if (!isValidStartPoint(anchorDataPoint.getX(0), anchorDataPoint.getY(0))) {
            zoomingOut = false;
            return;
        }

        startOperation(event);
        updateDraggedWindow();
        constrainDraggedWindow(dragWindow);
        drawGhost();
        if (isConsumeEvents()) {
            event.consume();
        }
    }

    private void handleMouseReleased(MouseEvent event) {
        if (!isInOperation()) {
            return;
        }

        currentDataPoint.set(0, event.getX(), event.getY());
        getChart().toData(currentDataPoint, getYAxisIndex());
        updateDraggedWindow();
        constrainDraggedWindow(dragWindow);
        drawGhost();
        if (Math.abs(event.getX() - anchorX) > MIN_DRAG_DISTANCE
                || Math.abs(event.getY() - anchorY) > MIN_DRAG_DISTANCE) {
            doIt();
        }
        endOperation(event);
        if (isConsumeEvents()) {
            event.consume();
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initTransientState();
    }

    /// Replaces the number of intermediate animation steps.
    ///
    /// Use `0` to disable the zoom animation.
    ///
    /// @param animationStep the new animation step count
    public void setAnimationStep(int animationStep) {
        this.animationStep = animationStep;
    }

    /// Enables or disables x-axis changes during the drag.
    ///
    /// Disabled axes keep their current visible range while the drag box is being constrained.
    ///
    /// @param xZoomAllowed `true` to let the drag change the x range
    public void setXZoomAllowed(boolean xZoomAllowed) {
        this.xZoomAllowed = xZoomAllowed;
    }

    /// Enables or disables y-axis changes during the drag.
    ///
    /// Disabled axes keep their current visible range while the drag box is being constrained.
    ///
    /// @param yZoomAllowed `true` to let the drag change the y range
    public void setYZoomAllowed(boolean yZoomAllowed) {
        this.yZoomAllowed = yZoomAllowed;
    }

    /// Replaces the modifier mask that starts zoom-in drags.
    ///
    /// The new mask is stored in the same form as [ChartInteractor#setEventMask(int)].
    ///
    /// @param zoomInEventMask new zoom-in modifier mask
    public void setZoomInEventMask(int zoomInEventMask) {
        setEventMask(zoomInEventMask);
    }

    /// Enables or disables zoom-out drags.
    ///
    /// @param zoomOutAllowed `true` to allow the zoom-out gesture
    public void setZoomOutAllowed(boolean zoomOutAllowed) {
        this.zoomOutAllowed = zoomOutAllowed;
    }

    /// Replaces the modifier mask that starts zoom-out drags.
    ///
    /// The extended-mask cache is recomputed immediately so future mouse events use the new exact
    /// match.
    ///
    /// @param zoomOutEventMask new zoom-out modifier mask
    public void setZoomOutEventMask(int zoomOutEventMask) {
        this.zoomOutEventMask = zoomOutEventMask;
        zoomOutEventMaskEx = EventUtil.convertModifiersMask(zoomOutEventMask);
    }

    @Override
    protected void startOperation(MouseEvent event) {
        super.startOperation(event);
        setGhostDrawingAllowed(true);
        setCursor(!zoomingOut ? getZoomInCursor() : getZoomOutCursor());
    }

    /// Hook that may clamp or normalize the current drag window before painting and application.
    ///
    /// The supplied window has already been locked to every axis whose zoom flag is currently
    /// disabled. Subclasses may mutate it in place to enforce local domains, minimum sizes, or
    /// provider-specific constraints.
    ///
    /// @param window the mutable drag window about to be painted or applied
    protected void validate(DataWindow window) {
    }
}
