package one.chartsy.charting.internal;

import java.awt.AWTEvent;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Vector;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartInteractor;

/// Routes keyboard and mouse input from one [Chart] to its installed [ChartInteractor] instances.
///
/// The manager keeps two interactor collections:
/// - ordinary interactors, exposed through [#getInteractors()] and replaced by
///   [#setInteractors(ChartInteractor[])]
/// - reserved-priority interactors whose priority is exactly [ChartInteractor#INTERNAL_HIGH] or
///   [ChartInteractor#INTERNAL_LOW]
///
/// Only event categories requested through [ChartInteractor#enableEvents(long)] are dispatched.
/// Mouse and motion dispatch additionally require the interactor to report
/// [ChartInteractor#isHandling(int, int)] unless it is already in an operation. Once a mouse
/// press selects an interactor that stays in operation, later events and expose repaints remain
/// routed to that interactor until it leaves operation state.
///
/// Instances are mutable chart-side UI state and are not thread-safe. The interactor lists and the
/// owning chart are serialized, while the active-operation state and dispatch strategies are
/// rebuilt after deserialization.
public final class ChartInteractorManager implements Serializable {
    private static final String CURSORS_REPOSITORY_PROPERTY = "___Cursors_Repository";
    private static final ChartInteractor[] EMPTY_INTERACTORS = new ChartInteractor[0];
    private static final Comparator<ChartInteractor> PRIORITY_COMPARATOR =
            new InteractorComparator();

    private final ArrayList<ChartInteractor> ordinaryInteractors;
    private final ArrayList<ChartInteractor> reservedPriorityInteractors;
    private final Chart chart;
    private transient ChartInteractor activeInteractor;
    private int keyEventInterestCount;
    private int mouseEventInterestCount;
    private int mouseMotionEventInterestCount;
    private transient InputEventDispatcher mouseEventDispatcher;
    private transient InputEventDispatcher mouseMotionEventDispatcher;
    private transient InputEventDispatcher keyEventDispatcher;

    /// Orders interactors by descending [ChartInteractor#getPriority()] during dispatch.
    ///
    /// [ChartInteractorManager] merges ordinary and reserved-priority interactors into one
    /// temporary dispatch array and sorts it with this comparator before offering an input event.
    /// Higher-priority interactors therefore see the event first.
    private static class InteractorComparator implements Comparator<ChartInteractor> {

        private InteractorComparator() {
        }

        @Override
        public int compare(ChartInteractor left, ChartInteractor right) {
            return Integer.compare(right.getPriority(), left.getPriority());
        }
    }

    /// Strategy used to route one already-filtered [InputEvent] to one interactor.
    ///
    /// The manager chooses whether an interactor should be offered the event. Implementations of
    /// this strategy then perform the event-type specific call into [ChartInteractor].
    private interface InputEventDispatcher {

        /// Dispatches `event` to `interactor`.
        ///
        /// @param event      the already-filtered AWT event
        /// @param interactor the interactor that should receive the event
        void dispatch(InputEvent event, ChartInteractor interactor);
    }

    /// Creates a manager bound to one chart.
    ///
    /// @param chart the owning chart
    public ChartInteractorManager(Chart chart) {
        ordinaryInteractors = new ArrayList<>(5);
        reservedPriorityInteractors = new ArrayList<>(1);
        this.chart = chart;
        initializeDispatchers();
    }

    private void initializeDispatchers() {
        mouseEventDispatcher = (event, interactor) -> {
            MouseEvent mouseEvent = (MouseEvent) event;
            if (interactor.isHandling(mouseEvent.getX(), mouseEvent.getY())
                    || interactor.isInOperation())
                interactor.processMouseEvent(mouseEvent);
        };
        mouseMotionEventDispatcher = (event, interactor) -> {
            MouseEvent mouseEvent = (MouseEvent) event;
            if (interactor.isHandling(mouseEvent.getX(), mouseEvent.getY())
                    || interactor.isInOperation())
                interactor.processMouseMotionEvent(mouseEvent);
        };
        keyEventDispatcher = (event, interactor) ->
                interactor.processKeyEvent((KeyEvent) event);
    }

    /// Returns the chart that owns this manager.
    public Chart getChart() {
        return chart;
    }

    /// Returns the number of ordinary interactors currently installed.
    ///
    /// Reserved-priority interactors are not included in this count.
    public synchronized int getInteractorCount() {
        return ordinaryInteractors.size();
    }

    private boolean hasReservedPriority(ChartInteractor interactor) {
        int priority = interactor.getPriority();
        return priority == ChartInteractor.INTERNAL_HIGH
                || priority == ChartInteractor.INTERNAL_LOW;
    }

    /// Attaches one interactor instance to this chart.
    ///
    /// Interactors already attached to this chart are left unchanged. Interactors attached to a
    /// different chart are rejected.
    ///
    /// @param interactor the interactor to attach
    /// @throws IllegalArgumentException if `interactor` is `null` or is already connected to a
    ///                                      different chart
    public synchronized void addInteractor(ChartInteractor interactor) {
        if (interactor == null)
            throw new IllegalArgumentException();
        if (interactor.getChart() == chart)
            return;
        if (interactor.getChart() != null)
            throw new IllegalArgumentException("Interactor already connected to another chart");

        ArrayList<ChartInteractor> targetList =
                hasReservedPriority(interactor) ? reservedPriorityInteractors : ordinaryInteractors;
        for (ChartInteractor currentInteractor : targetList)
            if (currentInteractor == interactor)
                throw new IllegalArgumentException(interactor + " has already been added");

        targetList.add(interactor);
        interactor.setChart(chart);
        updateEventMaskCounts(interactor.getAWTEventMask(), 1);
    }

    /// Creates an interactor by name and attaches it to this chart.
    ///
    /// @param interactorName the name understood by [ChartInteractor#create(String)]
    /// @throws IllegalArgumentException if the interactor cannot be created or attached
    public void addInteractor(String interactorName) {
        ChartInteractor interactor = ChartInteractor.create(interactorName);
        if (interactor != null) {
            addInteractor(interactor);
            return;
        }
        throw new IllegalArgumentException(interactorName + " cannot be found");
    }

    /// Removes one interactor from this chart.
    ///
    /// @param interactor the interactor to detach
    /// @return `true` if the interactor was present in the expected manager list
    /// @throws IllegalArgumentException if `interactor` is `null`
    public synchronized boolean removeInteractor(ChartInteractor interactor) {
        if (interactor == null)
            throw new IllegalArgumentException();

        ArrayList<ChartInteractor> sourceList =
                hasReservedPriority(interactor) ? reservedPriorityInteractors : ordinaryInteractors;
        if (sourceList.isEmpty())
            return false;

        boolean removed = sourceList.remove(interactor);
        if (removed) {
            interactor.setChart(null);
            updateEventMaskCounts(interactor.getAWTEventMask(), -1);
        }
        return removed;
    }

    /// Removes all ordinary interactors.
    ///
    /// Reserved-priority interactors remain installed.
    public synchronized void removeAllInteractors() {
        while (!ordinaryInteractors.isEmpty()) {
            ChartInteractor interactor = ordinaryInteractors.remove(ordinaryInteractors.size() - 1);
            interactor.setChart(null);
            updateEventMaskCounts(interactor.getAWTEventMask(), -1);
        }
    }

    /// Returns a snapshot of the currently installed ordinary interactors.
    ///
    /// Reserved-priority interactors are not included.
    public synchronized ChartInteractor[] getInteractors() {
        ChartInteractor[] result = new ChartInteractor[ordinaryInteractors.size()];
        ordinaryInteractors.toArray(result);
        return result;
    }

    /// Replaces the ordinary interactor set with `interactors`.
    ///
    /// Reserved-priority interactors remain installed. The method compares the supplied array by
    /// element identity and returns `false` without changing anything when the ordinary interactor
    /// sequence is already the same.
    ///
    /// @param interactors the new ordinary interactor sequence
    /// @return `true` if the ordinary interactor list changed
    public synchronized boolean setInteractors(ChartInteractor[] interactors) {
        int interactorCount = interactors.length;
        if (interactorCount == this.ordinaryInteractors.size()) {
            boolean unchanged = true;
            for (int i = 0; i < interactorCount; i++) {
                if (this.ordinaryInteractors.get(i) != interactors[i]) {
                    unchanged = false;
                    break;
                }
            }
            if (unchanged)
                return false;
        }

        removeAllInteractors();
        for (ChartInteractor interactor : interactors)
            addInteractor(interactor);
        return true;
    }

    /// Routes one mouse-motion event to interested interactors.
    ///
    /// @param event the event to dispatch
    public void processMouseMotionEvent(MouseEvent event) {
        if (mouseMotionEventInterestCount != 0)
            dispatchEvent(event, mouseMotionEventDispatcher);
    }

    /// Routes one mouse-button event to interested interactors.
    ///
    /// @param event the event to dispatch
    public void processMouseEvent(MouseEvent event) {
        if (mouseEventInterestCount != 0)
            dispatchEvent(event, mouseEventDispatcher);
    }

    /// Routes one key event to interested interactors.
    ///
    /// @param event the event to dispatch
    public void processKeyEvent(KeyEvent event) {
        if (keyEventInterestCount != 0)
            dispatchEvent(event, keyEventDispatcher);
    }

    /// Delegates expose repaint handling to the currently active interactor, if any.
    ///
    /// @param g the chart-area graphics context
    public void handleExpose(Graphics g) {
        if (activeInteractor != null)
            activeInteractor.handleExpose(g);
    }

    /// Notifies peer ordinary interactors that `interactor` has started an operation.
    ///
    /// @param interactor the interactor that just entered an operation
    /// @param event      the mouse event that started the operation
    public void interactionStarted(ChartInteractor interactor, MouseEvent event) {
        ChartInteractor[] peers;
        synchronized (this) {
            peers = ordinaryInteractors.toArray(EMPTY_INTERACTORS);
        }

        for (ChartInteractor peer : peers) {
            if (peer != interactor)
                peer.interactionStarted(interactor, event);
        }
    }

    private void dispatchEvent(InputEvent event, InputEventDispatcher dispatcher) {
        ChartInteractor currentActiveInteractor = activeInteractor;
        if (currentActiveInteractor != null) {
            dispatcher.dispatch(event, currentActiveInteractor);
            if (!currentActiveInteractor.isInOperation())
                activeInteractor = null;
            return;
        }

        int interactorCount = ordinaryInteractors.size();
        int internalInteractorCount = reservedPriorityInteractors.size();
        if (interactorCount + internalInteractorCount == 0)
            return;

        ChartInteractor[] dispatchTargets =
                new ChartInteractor[interactorCount + internalInteractorCount];
        int targetCount = 0;
        for (int i = 0; i < interactorCount; i++)
            dispatchTargets[targetCount++] = ordinaryInteractors.get(i);
        for (int i = 0; i < internalInteractorCount; i++)
            dispatchTargets[targetCount++] = reservedPriorityInteractors.get(i);

        Arrays.sort(dispatchTargets, 0, targetCount, PRIORITY_COMPARATOR);

        ChartInteractor lastInteractor = null;
        for (int i = 0; i < targetCount; i++) {
            lastInteractor = dispatchTargets[i];
            dispatcher.dispatch(event, lastInteractor);
            if (event.isConsumed())
                break;
        }

        if (event.getID() == MouseEvent.MOUSE_PRESSED)
            activeInteractor = lastInteractor;
    }

    private void updateEventMaskCounts(long awtEventMask, int delta) {
        if ((awtEventMask & AWTEvent.MOUSE_EVENT_MASK) != 0L)
            mouseEventInterestCount += delta;
        if ((awtEventMask & AWTEvent.MOUSE_MOTION_EVENT_MASK) != 0L)
            mouseMotionEventInterestCount += delta;
        if ((awtEventMask & AWTEvent.KEY_EVENT_MASK) != 0L)
            keyEventInterestCount += delta;
    }

    /// Pushes `cursor` onto the chart's interactor cursor stack.
    ///
    /// The previous cursor, including `null`, is stored so nested cursor changes can be unwound by
    /// [#popCursor(Chart)].
    ///
    /// @param chart  the chart whose cursor should change
    /// @param cursor the cursor to install, or `null` to query the current cursor without pushing
    /// @return the cursor that was active before the change
    public static Cursor pushCursor(Chart chart, Cursor cursor) {
        Cursor previousCursor = chart.isCursorSet() ? chart.getCursor() : null;
        if (cursor == null)
            return previousCursor;

        @SuppressWarnings("unchecked")
        Vector<Cursor> cursorStack = (Vector<Cursor>) chart.getClientProperty(
                CURSORS_REPOSITORY_PROPERTY);
        if (cursorStack == null) {
            cursorStack = new Vector<>(5, 5);
            chart.putClientProperty(CURSORS_REPOSITORY_PROPERTY, cursorStack);
        }

        cursorStack.addElement(previousCursor);
        chart.setCursor(cursor);
        return previousCursor;
    }

    /// Restores the most recently pushed cursor for `chart`.
    ///
    /// @param chart the chart whose cursor stack should be popped
    /// @return the cursor that was active immediately before the restoration, or `null` when the
    ///     stack is empty
    public static Cursor popCursor(Chart chart) {
        @SuppressWarnings("unchecked")
        Vector<Cursor> cursorStack = (Vector<Cursor>) chart.getClientProperty(
                CURSORS_REPOSITORY_PROPERTY);
        if (cursorStack == null || cursorStack.isEmpty())
            return null;

        Cursor currentCursor = chart.isCursorSet() ? chart.getCursor() : null;
        Cursor previousCursor = cursorStack.lastElement();
        cursorStack.removeElementAt(cursorStack.size() - 1);
        chart.setCursor(previousCursor);
        return currentCursor;
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initializeDispatchers();
    }
}
