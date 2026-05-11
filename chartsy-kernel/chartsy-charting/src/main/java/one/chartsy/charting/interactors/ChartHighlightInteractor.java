package one.chartsy.charting.interactors;

import java.awt.AWTEvent;
import java.awt.event.MouseEvent;

import one.chartsy.charting.ChartInteractor;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.event.ChartHighlightInteractionEvent;

/// Tracks the current hover highlight and emits semantic enter and leave events for it.
///
/// This interactor resolves one [DisplayPoint] from the current pointer location using the picking
/// rules inherited from [ChartDataInteractor]. It stores a cloned snapshot of the current
/// highlight because renderers commonly reuse `DisplayPoint` instances while scanning projected
/// data.
///
/// [#HIGHLIGHT_POINT] treats each picked point as a distinct target. [#HIGHLIGHT_SERIES] instead
/// coalesces all points from the same dataset into one logical target, which avoids redundant
/// leave and enter events while the pointer moves within one rendered series.
public class ChartHighlightInteractor extends ChartDataInteractor {
    /// Highlight mode that distinguishes individual picked points.
    public static final int HIGHLIGHT_POINT = 0;

    /// Highlight mode that treats every point from one dataset as the same target.
    public static final int HIGHLIGHT_SERIES = 1;

    static {
        ChartInteractor.register("Highlight", ChartHighlightInteractor.class);
    }

    private int highlightMode;
    private transient DisplayPoint highlightedPoint;

    /// Creates a highlight interactor for the primary y axis.
    ///
    /// The default mode is [#HIGHLIGHT_POINT].
    public ChartHighlightInteractor() {
        this(0);
    }

    /// Creates a highlight interactor for one y-axis slot.
    ///
    /// The gesture does not require any mouse modifiers and listens only for hover-oriented mouse
    /// events.
    ///
    /// @param yAxisIndex the y-axis slot whose renderers participate in highlighting
    public ChartHighlightInteractor(int yAxisIndex) {
        super(yAxisIndex, 0);
        highlightMode = HIGHLIGHT_POINT;
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        setXORGhost(false);
    }

    private DisplayPoint cloneDisplayPoint(DisplayPoint displayPoint) {
        return (displayPoint != null) ? displayPoint.clone() : null;
    }

    private boolean isSameHighlightTarget(DisplayPoint currentPoint, DisplayPoint previousPoint) {
        return switch (getHighlightMode()) {
            case HIGHLIGHT_POINT -> currentPoint.equals(previousPoint);
            case HIGHLIGHT_SERIES -> currentPoint.getDataSet() == previousPoint.getDataSet();
            default -> false;
        };
    }

    /// Publishes one highlight-state transition for `displayPoint`.
    ///
    /// Subclasses can override this hook to keep transient UI such as tooltips synchronized with
    /// the highlight lifecycle before delegating to `super`.
    ///
    /// @param displayPoint the point whose highlight state changed
    /// @param highlighted  `true` when `displayPoint` became highlighted, `false` when it was
    ///                         cleared
    /// @param event        the mouse event that triggered the transition
    protected void publishHighlightChange(DisplayPoint displayPoint, boolean highlighted,
                                          MouseEvent event) {
        fireChartInteractionEvent(
                new ChartHighlightInteractionEvent(this, cloneDisplayPoint(displayPoint), highlighted));
    }

    /// Returns a snapshot of the point currently considered highlighted.
    ///
    /// @return the current highlighted point, or `null` when nothing is highlighted
    public DisplayPoint getHighlightedPoint() {
        return cloneDisplayPoint(highlightedPoint);
    }

    /// Returns how this interactor deduplicates highlight transitions.
    ///
    /// @return either [#HIGHLIGHT_POINT] or [#HIGHLIGHT_SERIES]
    public int getHighlightMode() {
        return highlightMode;
    }

    @Override
    public boolean isHandling(int x, int y) {
        return true;
    }

    @Override
    public void processMouseEvent(MouseEvent event) {
        switch (event.getID()) {
            case MouseEvent.MOUSE_ENTERED -> {
                if (event.getButton() == MouseEvent.NOBUTTON) {
                    processMouseMovedEvent(event);
                }
            }
            case MouseEvent.MOUSE_EXITED -> {
                if (highlightedPoint != null) {
                    publishHighlightChange(highlightedPoint, false, event);
                }
                highlightedPoint = null;
            }
            default -> {
            }
        }
    }

    @Override
    public void processMouseMotionEvent(MouseEvent event) {
        if (event.getID() == MouseEvent.MOUSE_MOVED) {
            processMouseMovedEvent(event);
        }
    }

    /// Refreshes the highlight for the current pointer location.
    ///
    /// A highlight change produces a leave event for the previous target followed by an enter
    /// event for the new one. Moving onto empty chart space clears the current highlight.
    ///
    /// @param event the mouse-move style event that should be resolved
    public void processMouseMovedEvent(MouseEvent event) {
        DisplayPoint previousPoint = highlightedPoint;
        highlightedPoint = pickData(createDataPicker(event));

        if (highlightedPoint != null) {
            if (previousPoint == null) {
                publishHighlightChange(highlightedPoint, true, event);
            } else if (!isSameHighlightTarget(highlightedPoint, previousPoint)) {
                publishHighlightChange(previousPoint, false, event);
                publishHighlightChange(highlightedPoint, true, event);
            } else {
                return;
            }
            if (isConsumeEvents()) {
                event.consume();
            }
            return;
        }

        if (previousPoint != null) {
            publishHighlightChange(previousPoint, false, event);
        }
    }

    /// Replaces the stored highlight snapshot without emitting an interaction event.
    ///
    /// Higher-level coordination code uses this to mirror a highlight chosen by another chart or
    /// renderer.
    ///
    /// @param displayPoint the point that should become the current highlight, or `null` to clear
    ///                         it
    public void setHighlightedPoint(DisplayPoint displayPoint) {
        highlightedPoint = cloneDisplayPoint(displayPoint);
    }

    /// Selects whether highlight transitions are tracked per point or per dataset.
    ///
    /// @param highlightMode one of [#HIGHLIGHT_POINT] or [#HIGHLIGHT_SERIES]
    /// @throws IllegalArgumentException if `highlightMode` is not supported
    public void setHighlightMode(int highlightMode) {
        switch (highlightMode) {
            case HIGHLIGHT_POINT, HIGHLIGHT_SERIES -> this.highlightMode = highlightMode;
            default -> throw new IllegalArgumentException("invalid highlight mode: " + highlightMode);
        }
    }
}
