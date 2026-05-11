package one.chartsy.charting.interactors;

import java.awt.AWTEvent;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartInteractor;
import one.chartsy.charting.DoublePoints;

/// Pans the chart's visible window by dragging in data space.
///
/// Dragging starts only when the pressed mouse event matches the configured modifier mask exactly
/// and [#isValidStartPoint(double, double)] accepts the converted data-space anchor. Each drag
/// step compares the previous and current data-space pointer locations and forwards that delta to
/// [Chart#scroll(double, double, int)], so the gesture tracks the chart's
/// current projection and y-axis slot instead of raw pixel motion.
///
/// The default configuration binds the gesture to the secondary mouse button and enables only
/// horizontal scrolling. [#setXPanAllowed(boolean)] and [#setYPanAllowed(boolean)] act as
/// independent axis locks. Subclasses can tighten the valid-start rule or reinterpret the drag
/// delta while reusing the same lifecycle and cursor management.
///
/// Instances are mutable UI objects and are not thread-safe.
public class ChartPanInteractor extends ChartInteractor {
    static {
        ChartInteractor.register("Pan", ChartPanInteractor.class);
    }

    private boolean xPanAllowed;
    private boolean yPanAllowed;
    private transient DoublePoints previousDataPoint;
    private transient DoublePoints currentDataPoint;

    /// Creates a pan interactor for the primary y-axis slot using the secondary mouse button.
    public ChartPanInteractor() {
        this(0, MouseEvent.BUTTON3_DOWN_MASK);
    }

    /// Creates a pan interactor for the primary y-axis slot.
    ///
    /// @param eventMask the mouse modifier mask required to start panning
    public ChartPanInteractor(int eventMask) {
        this(0, eventMask);
    }

    /// Creates a pan interactor for one y-axis slot and modifier combination.
    ///
    /// Horizontal panning is enabled by default and vertical panning is disabled by default.
    ///
    /// @param yAxisIndex the y-axis slot whose visible range should be translated
    /// @param eventMask the mouse modifier mask required to start panning
    public ChartPanInteractor(int yAxisIndex, int eventMask) {
        super(yAxisIndex, eventMask);
        xPanAllowed = true;
        yPanAllowed = false;
        initTransientState();
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        setXORGhost(false);
    }

    private void initTransientState() {
        previousDataPoint = new DoublePoints(0.0, 0.0);
        currentDataPoint = new DoublePoints(0.0, 0.0);
    }

    @Override
    protected void abort() {
        super.abort();
        setCursor(null);
        getXAxis().setAdjusting(false);
        getYAxis().setAdjusting(false);
    }

    @Override
    protected void endOperation(MouseEvent event) {
        super.endOperation(event);
        setCursor(null);
        getXAxis().setAdjusting(false);
        getYAxis().setAdjusting(false);
    }

    /// Returns the cursor shown while a pan drag is active.
    protected Cursor getCursor() {
        return Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR);
    }

    /// Returns whether a pan drag may start from the supplied data-space location.
    ///
    /// The base implementation accepts every point inside the chart's handling region.
    ///
    /// @param x data-space x coordinate of the pointer
    /// @param y data-space y coordinate of the pointer
    /// @return `true` when the drag may start
    protected boolean isValidStartPoint(double x, double y) {
        return true;
    }

    /// Returns whether horizontal panning is currently enabled.
    public boolean isXPanAllowed() {
        return xPanAllowed;
    }

    /// Returns whether vertical panning is currently enabled.
    public boolean isYPanAllowed() {
        return yPanAllowed;
    }

    /// Translates the owning chart by one drag step.
    ///
    /// The two point buffers are expected to hold the previous and current pointer locations in
    /// data coordinates for this interactor's y-axis slot.
    ///
    /// @param previousDataPoint the previous data-space pointer location
    /// @param currentDataPoint the current data-space pointer location
    protected void pan(DoublePoints previousDataPoint, DoublePoints currentDataPoint) {
        double xOffset = xPanAllowed ? previousDataPoint.getX(0) - currentDataPoint.getX(0) : 0.0;
        double yOffset = yPanAllowed ? previousDataPoint.getY(0) - currentDataPoint.getY(0) : 0.0;
        getChart().scroll(xOffset, yOffset, getYAxisIndex());
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
        if (!isInOperation()) {
            return;
        }
        if (event.getID() == MouseEvent.MOUSE_DRAGGED) {
            currentDataPoint.set(0, event.getX(), event.getY());
            getChart().toData(currentDataPoint, getYAxisIndex());
            pan(previousDataPoint, currentDataPoint);
            previousDataPoint.set(0, event.getX(), event.getY());
            getChart().toData(previousDataPoint, getYAxisIndex());
            if (isConsumeEvents()) {
                event.consume();
            }
        }
    }

    private void handleMousePressed(MouseEvent event) {
        if ((event.getModifiersEx() & getEventMaskEx()) != getEventMaskEx()) {
            return;
        }
        if ((event.getModifiersEx() & ~getEventMaskEx()) != 0) {
            return;
        }

        previousDataPoint.set(0, event.getX(), event.getY());
        getChart().toData(previousDataPoint, getYAxisIndex());
        if (!isValidStartPoint(previousDataPoint.getX(0), previousDataPoint.getY(0))) {
            return;
        }

        startOperation(event);
        if (isConsumeEvents()) {
            event.consume();
        }
    }

    private void handleMouseReleased(MouseEvent event) {
        if (!isInOperation()) {
            return;
        }
        if ((event.getModifiersEx() & getEventMaskEx()) == getEventMaskEx()) {
            return;
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

    /// Enables or disables horizontal panning.
    ///
    /// @param xPanAllowed `true` to allow x-axis scrolling
    public void setXPanAllowed(boolean xPanAllowed) {
        this.xPanAllowed = xPanAllowed;
    }

    /// Enables or disables vertical panning.
    ///
    /// @param yPanAllowed `true` to allow y-axis scrolling
    public void setYPanAllowed(boolean yPanAllowed) {
        this.yPanAllowed = yPanAllowed;
    }

    @Override
    protected void startOperation(MouseEvent event) {
        super.startOperation(event);
        setCursor(getCursor());
        getXAxis().setAdjusting(true);
        getYAxis().setAdjusting(true);
    }
}
