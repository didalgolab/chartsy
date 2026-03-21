package one.chartsy.charting.interactors;

import java.awt.AWTEvent;
import java.awt.Cursor;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;

import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.event.ChartInteractionEvent;

/// Publishes one [ChartInteractionEvent] for a point picked from a mouse gesture.
///
/// On mouse press the interactor resolves one [DisplayPoint] through the picking strategy inherited
/// from [ChartDataInteractor]. If a point is found, the gesture stays armed until
/// [#isPickingEvent(MouseEvent)] returns `true`, at which point the interactor emits a cloned
/// snapshot of that point and ends the operation. The default implementation therefore behaves as a
/// click-and-release picker: resolution happens on press and publication happens on release.
///
/// This class switches the inherited picking mode to [ChartDataInteractor#ITEM_PICKING], which
/// favors the first acceptable display item reported by the chart instead of the nearest point.
/// Subclasses can override [#isPickingEvent(MouseEvent)] to fire on a different event boundary or
/// [#pick(DisplayPoint)] to publish a different semantic result.
public class ChartPickInteractor extends ChartDataInteractor {
    private transient DisplayPoint pendingPick;

    /// Creates a pick interactor for the primary y-axis slot using the primary mouse button.
    public ChartPickInteractor() {
        this(0, MouseEvent.BUTTON1_DOWN_MASK);
    }

    /// Creates a pick interactor for one y-axis slot and modifier combination.
    ///
    /// @param yAxisIndex the y-axis slot whose renderers should participate in picking
    /// @param eventMask the mouse modifier mask required to arm the gesture
    public ChartPickInteractor(int yAxisIndex, int eventMask) {
        super(yAxisIndex, eventMask);
        enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK);
        setPickingMode(ITEM_PICKING);
    }

    @Override
    protected void abort() {
        super.abort();
        pendingPick = null;
        setCursor(null);
    }

    @Override
    protected void endOperation(MouseEvent event) {
        super.endOperation(event);
        pendingPick = null;
        setCursor(null);
    }

    /// Returns the cursor shown while a pick gesture is armed.
    protected Cursor getCursor() {
        return Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
    }

    /// Returns whether `event` should complete the current pick gesture.
    ///
    /// The base implementation fires on mouse release.
    ///
    /// @param event the mouse event being processed
    /// @return `true` when the pending pick should be published now
    protected boolean isPickingEvent(MouseEvent event) {
        return event.getID() == MouseEvent.MOUSE_RELEASED;
    }

    /// Publishes one semantic pick result.
    ///
    /// The emitted event receives a clone of `displayPoint` so listeners can retain it without
    /// depending on later renderer reuse of the same mutable handle.
    ///
    /// @param displayPoint the point to publish
    protected void pick(DisplayPoint displayPoint) {
        fireChartInteractionEvent(new ChartInteractionEvent(this, displayPoint.clone()));
    }

    @Override
    public void processKeyEvent(KeyEvent event) {
        if (event.getID() == KeyEvent.KEY_PRESSED && event.getKeyCode() == KeyEvent.VK_ESCAPE) {
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

    private void handleMousePressed(MouseEvent event) {
        if ((event.getModifiersEx() & getEventMaskEx()) != getEventMaskEx()) {
            return;
        }
        if ((event.getModifiersEx() & ~getEventMaskEx()) != 0) {
            return;
        }

        pendingPick = pickData(createDataPicker(event));
        if (pendingPick == null) {
            return;
        }

        startOperation(event);
        if (isPickingEvent(event)) {
            pick(pendingPick);
            endOperation(event);
        }
        if (isConsumeEvents()) {
            event.consume();
        }
    }

    private void handleMouseReleased(MouseEvent event) {
        if (pendingPick == null || !isPickingEvent(event)) {
            return;
        }

        pick(pendingPick);
        endOperation(event);
        if (isConsumeEvents()) {
            event.consume();
        }
    }

    @Override
    protected void startOperation(MouseEvent event) {
        super.startOperation(event);
        setCursor(getCursor());
    }
}
