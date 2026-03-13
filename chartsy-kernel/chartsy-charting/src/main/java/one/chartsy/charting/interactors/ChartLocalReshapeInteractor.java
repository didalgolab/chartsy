package one.chartsy.charting.interactors;

import java.awt.AWTEvent;
import java.awt.Cursor;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;

import one.chartsy.charting.AxisTransformer;
import one.chartsy.charting.AxisTransformerException;
import one.chartsy.charting.ChartInteractor;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DoublePoints;
import one.chartsy.charting.LocalZoomAxisTransformer;

/// Drags one edge of a [LocalZoomAxisTransformer]'s zoom range in place.
///
/// This interactor becomes active only when the targeted x or y axis currently uses
/// [LocalZoomAxisTransformer]. It first looks for a pointer close to one transformed zoom bound in
/// data space and, when that would be too hard to hit on screen, falls back to a two-pixel
/// display-space proximity test. During the drag it rewrites only the selected bound while keeping
/// the opposite bound fixed.
///
/// The active axis is marked as adjusting for the duration of the drag. Subclasses can extend
/// [#startOperation(MouseEvent)] and [#endOperation(MouseEvent)] when related axes or peer charts
/// must mirror that adjusting state.
public class ChartLocalReshapeInteractor extends ChartInteractor {
    private static final double DATA_PROXIMITY_RATIO = 0.05;
    private static final double DISPLAY_PROXIMITY_PIXELS = 2.0;

    /// No zoom-range edge is currently targeted.
    public static final int NONE = 0;

    /// The lower x bound of the local zoom range is active.
    public static final int MIN_X_BOUND = 1;

    /// The upper x bound of the local zoom range is active.
    public static final int MAX_X_BOUND = 2;

    /// The lower y bound of the local zoom range is active.
    public static final int MIN_Y_BOUND = 3;

    /// The upper y bound of the local zoom range is active.
    public static final int MAX_Y_BOUND = 4;

    private transient int activeBound;
    private transient DoublePoints pointerPoint;
    private transient boolean hoverCursorActive;

    /// Creates a reshape interactor for the primary y axis using the left mouse button.
    public ChartLocalReshapeInteractor() {
        this(0, MouseEvent.BUTTON1_DOWN_MASK);
    }

    /// Creates a reshape interactor for one y-axis slot and modifier combination.
    ///
    /// @param yAxisIndex the y-axis slot whose local zoom handles should be reshaped
    /// @param eventMask  the mouse modifier mask required to start the drag
    public ChartLocalReshapeInteractor(int yAxisIndex, int eventMask) {
        super(yAxisIndex, eventMask);
        initTransientState();
        enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }

    private void initTransientState() {
        activeBound = NONE;
        pointerPoint = new DoublePoints(0.0, 0.0);
        hoverCursorActive = false;
    }

    private static boolean isXAxisBound(int bound) {
        return bound == MIN_X_BOUND || bound == MAX_X_BOUND;
    }

    private static boolean isYAxisBound(int bound) {
        return bound == MIN_Y_BOUND || bound == MAX_Y_BOUND;
    }

    private void setAdjustingState(boolean adjusting) {
        if (isXAxisBound(activeBound)) {
            getXAxis().setAdjusting(adjusting);
        } else if (isYAxisBound(activeBound)) {
            getYAxis().setAdjusting(adjusting);
        }
    }

    private boolean isFinitePointer(DoublePoints point) {
        return Double.isFinite(point.getX(0)) && Double.isFinite(point.getY(0));
    }

    private boolean isWithinDisplayProximity(double x, double y, double boundX, double boundY) {
        DoublePoints pointer = new DoublePoints(x, y);
        getChart().toDisplay(pointer, getYAxisIndex());

        DoublePoints boundPoint = new DoublePoints(boundX, boundY);
        getChart().toDisplay(boundPoint, getYAxisIndex());

        return Math.hypot(
                Math.floor(boundPoint.getX(0)) - Math.floor(pointer.getX(0)),
                Math.floor(boundPoint.getY(0)) - Math.floor(pointer.getY(0)))
                <= DISPLAY_PROXIMITY_PIXELS;
    }

    private int resolveXBound(double x, double y, LocalZoomAxisTransformer transformer) {
        DataInterval zoomRange = transformer.getZoomRange();
        double tolerance = zoomRange.getLength() * DATA_PROXIMITY_RATIO;
        if (x > zoomRange.getMin() - tolerance && x < zoomRange.getMin() + tolerance) {
            return MIN_X_BOUND;
        }
        if (x > zoomRange.getMax() - tolerance && x < zoomRange.getMax() + tolerance) {
            return MAX_X_BOUND;
        }

        if (x > zoomRange.getMiddle()) {
            return isWithinDisplayProximity(x, y, zoomRange.getMax(), y) ? MAX_X_BOUND : NONE;
        }
        return isWithinDisplayProximity(x, y, zoomRange.getMin(), y) ? MIN_X_BOUND : NONE;
    }

    private int resolveYBound(double x, double y, LocalZoomAxisTransformer transformer) {
        DataInterval zoomRange = transformer.getZoomRange();
        double tolerance = zoomRange.getLength() * DATA_PROXIMITY_RATIO;
        if (y > zoomRange.getMin() - tolerance && y < zoomRange.getMin() + tolerance) {
            return MIN_Y_BOUND;
        }
        if (y > zoomRange.getMax() - tolerance && y < zoomRange.getMax() + tolerance) {
            return MAX_Y_BOUND;
        }

        if (y > zoomRange.getMiddle()) {
            return isWithinDisplayProximity(x, y, x, zoomRange.getMax()) ? MAX_Y_BOUND : NONE;
        }
        return isWithinDisplayProximity(x, y, x, zoomRange.getMin()) ? MIN_Y_BOUND : NONE;
    }

    @Override
    protected void abort() {
        super.abort();
        setAdjustingState(false);
        activeBound = NONE;
        setCursor(null);
        hoverCursorActive = false;
    }

    @Override
    protected void endOperation(MouseEvent event) {
        super.endOperation(event);
        if (hoverCursorActive) {
            setCursor(null);
        }
        hoverCursorActive = false;
        setAdjustingState(false);
        activeBound = NONE;
    }

    /// Returns the cursor shown while the pointer is over a draggable zoom handle.
    protected Cursor getCursor() {
        return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    }

    /// Returns which local-zoom edge is currently targeted.
    ///
    /// The value is meaningful while the pointer is over a resize handle or while a drag is in
    /// progress.
    ///
    /// @return one of [#NONE], [#MIN_X_BOUND], [#MAX_X_BOUND], [#MIN_Y_BOUND], or [#MAX_Y_BOUND]
    public int getActiveBound() {
        return activeBound;
    }

    /// Returns the current x-axis transformer when it supports local zoom.
    ///
    /// @return the active x-axis local zoom transformer, or `null` when the x axis does not use
    ///     one
    protected LocalZoomAxisTransformer getXTransformer() {
        AxisTransformer transformer = getXAxis().getTransformer();
        return (transformer instanceof LocalZoomAxisTransformer localZoomTransformer)
                ? localZoomTransformer
                : null;
    }

    /// Returns the current y-axis transformer when it supports local zoom.
    ///
    /// @return the active y-axis local zoom transformer, or `null` when the y axis does not use
    ///     one
    protected LocalZoomAxisTransformer getYTransformer() {
        AxisTransformer transformer = getYAxis().getTransformer();
        return (transformer instanceof LocalZoomAxisTransformer localZoomTransformer)
                ? localZoomTransformer
                : null;
    }

    @Override
    public boolean isHandling(int x, int y) {
        return true;
    }

    /// Returns whether a reshape drag can start from the supplied data-space location.
    ///
    /// The method also records the selected bound in [#getActiveBound()].
    ///
    /// @param x data-space x coordinate of the pointer
    /// @param y data-space y coordinate of the pointer
    /// @return `true` when the pointer is close enough to one local-zoom edge to start dragging
    protected boolean isValidStartPoint(double x, double y) {
        activeBound = NONE;

        LocalZoomAxisTransformer xTransformer = getXTransformer();
        if (xTransformer != null) {
            activeBound = resolveXBound(x, y, xTransformer);
        }
        if (activeBound == NONE) {
            LocalZoomAxisTransformer yTransformer = getYTransformer();
            if (yTransformer != null) {
                activeBound = resolveYBound(x, y, yTransformer);
            }
        }
        return activeBound != NONE;
    }

    @Override
    public void processMouseEvent(MouseEvent event) {
        switch (event.getID()) {
            case MouseEvent.MOUSE_PRESSED -> {
                if ((event.getModifiersEx() & getEventMaskEx()) != getEventMaskEx()) {
                    return;
                }
                if ((event.getModifiersEx() & ~getEventMaskEx()) != 0) {
                    return;
                }

                pointerPoint.set(0, event.getX(), event.getY());
                getChart().toData(pointerPoint, getYAxisIndex());
                if (!isValidStartPoint(pointerPoint.getX(0), pointerPoint.getY(0))) {
                    return;
                }

                startOperation(event);
                applyActiveTransformer(pointerPoint);
                if (isConsumeEvents()) {
                    event.consume();
                }
            }
            case MouseEvent.MOUSE_RELEASED -> {
                if (!isInOperation()) {
                    return;
                }
                if ((event.getModifiersEx() & getEventMaskEx()) == getEventMaskEx()) {
                    return;
                }

                pointerPoint.set(0, event.getX(), event.getY());
                getChart().toData(pointerPoint, getYAxisIndex());
                applyActiveTransformer(pointerPoint);
                endOperation(event);
                if (isConsumeEvents()) {
                    event.consume();
                }
            }
            case MouseEvent.MOUSE_ENTERED -> {
                if (event.getButton() == MouseEvent.NOBUTTON) {
                    processMouseMovedEvent(event);
                }
            }
            case MouseEvent.MOUSE_EXITED -> {
                if (hoverCursorActive) {
                    setCursor(null);
                    hoverCursorActive = false;
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void processMouseMotionEvent(MouseEvent event) {
        switch (event.getID()) {
            case MouseEvent.MOUSE_MOVED -> processMouseMovedEvent(event);
            case MouseEvent.MOUSE_DRAGGED -> {
                if (!isInOperation()) {
                    return;
                }

                pointerPoint.set(0, event.getX(), event.getY());
                getChart().toData(pointerPoint, getYAxisIndex());
                if (!isFinitePointer(pointerPoint)) {
                    return;
                }

                applyActiveTransformer(pointerPoint);
                reshape(pointerPoint.getX(0), pointerPoint.getY(0));
                if (isConsumeEvents()) {
                    event.consume();
                }
            }
            default -> {
            }
        }
    }

    /// Updates the hover cursor for the current pointer location.
    ///
    /// @param event the mouse-move style event to inspect
    public void processMouseMovedEvent(MouseEvent event) {
        pointerPoint.set(0, event.getX(), event.getY());
        boolean insidePlot = getChart().getChartArea().getPlotRect().contains(event.getX(), event.getY());
        if (insidePlot) {
            getChart().toData(pointerPoint, getYAxisIndex());
            insidePlot = isValidStartPoint(pointerPoint.getX(0), pointerPoint.getY(0));
        }

        if (insidePlot) {
            if (!hoverCursorActive) {
                setCursor(getCursor());
                hoverCursorActive = true;
            }
        } else if (hoverCursorActive) {
            setCursor(null);
            hoverCursorActive = false;
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initTransientState();
    }

    /// Rewrites the active local-zoom bound using transformed pointer coordinates.
    ///
    /// @param x transformed x coordinate of the pointer
    /// @param y transformed y coordinate of the pointer
    protected void reshape(double x, double y) {
        switch (activeBound) {
            case MIN_X_BOUND -> {
                LocalZoomAxisTransformer transformer = getXTransformer();
                DataInterval transformedRange = transformer.getTransformedRange();
                double delta = (x - transformedRange.getMin()) / transformer.getZoomFactor();
                transformer.setZoomRange(transformer.getZoomRange().getMin() + delta,
                        transformer.getZoomRange().getMax());
            }
            case MAX_X_BOUND -> {
                LocalZoomAxisTransformer transformer = getXTransformer();
                DataInterval transformedRange = transformer.getTransformedRange();
                double delta = (x - transformedRange.getMax()) / transformer.getZoomFactor();
                transformer.setZoomRange(transformer.getZoomRange().getMin(),
                        transformer.getZoomRange().getMax() + delta);
            }
            case MIN_Y_BOUND -> {
                LocalZoomAxisTransformer transformer = getYTransformer();
                DataInterval transformedRange = transformer.getTransformedRange();
                double delta = (y - transformedRange.getMin()) / transformer.getZoomFactor();
                transformer.setZoomRange(transformer.getZoomRange().getMin() + delta,
                        transformer.getZoomRange().getMax());
            }
            case MAX_Y_BOUND -> {
                LocalZoomAxisTransformer transformer = getYTransformer();
                DataInterval transformedRange = transformer.getTransformedRange();
                double delta = (y - transformedRange.getMax()) / transformer.getZoomFactor();
                transformer.setZoomRange(transformer.getZoomRange().getMin(),
                        transformer.getZoomRange().getMax() + delta);
            }
            default -> {
            }
        }
    }

    @Override
    protected void startOperation(MouseEvent event) {
        super.startOperation(event);
        if (!hoverCursorActive) {
            setCursor(getCursor());
            hoverCursorActive = true;
        }
        setAdjustingState(true);
    }

    /// Maps `point` through the currently active local-zoom transformer in place.
    ///
    /// The reshape logic operates in transformed coordinates so the pointer position must be
    /// converted before [#reshape(double, double)] runs.
    ///
    /// @param point the mutable point buffer to transform
    protected void applyActiveTransformer(DoublePoints point) {
        try {
            switch (activeBound) {
                case MIN_X_BOUND, MAX_X_BOUND -> {
                    LocalZoomAxisTransformer transformer = getXTransformer();
                    if (transformer != null) {
                        point.setX(0, transformer.apply(point.getX(0)));
                        point.setY(0, transformer.apply(point.getY(0)));
                    }
                }
                case MIN_Y_BOUND, MAX_Y_BOUND -> {
                    LocalZoomAxisTransformer transformer = getYTransformer();
                    if (transformer != null) {
                        point.setX(0, transformer.apply(point.getX(0)));
                        point.setY(0, transformer.apply(point.getY(0)));
                    }
                }
                default -> {
                }
            }
        } catch (AxisTransformerException exception) {
            exception.printStackTrace();
        }
    }
}
