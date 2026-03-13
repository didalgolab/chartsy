package one.chartsy.charting.event;

import one.chartsy.charting.ChartInteractor;
import one.chartsy.charting.DisplayPoint;

/// Describes a highlight-state transition for one [DisplayPoint].
///
/// [one.chartsy.charting.interactors.ChartHighlightInteractor] emits this event when the current
/// highlight target changes or when highlighting is cleared because the pointer no longer resolves
/// to a pickable point. The inherited [#getDisplayPoint()] identifies the point whose state
/// changed.
///
/// A `true` [#isHighlighted()] value means listeners should treat that point as newly highlighted.
/// A `false` value means the same point should no longer be considered highlighted.
public class ChartHighlightInteractionEvent extends ChartInteractionEvent {
    private boolean highlighted;

    /// Creates an event describing whether one display point is entering or leaving highlighted
    /// state.
    ///
    /// @param displayPoint the point whose highlight state changed
    /// @param highlighted `true` when `displayPoint` should become highlighted, `false` when it
    ///     should be cleared
    public ChartHighlightInteractionEvent(ChartInteractor interactor, DisplayPoint displayPoint,
            boolean highlighted) {
        super(interactor, displayPoint);
        this.highlighted = highlighted;
    }

    /// Returns whether [#getDisplayPoint()] should be treated as highlighted after this event.
    public final boolean isHighlighted() {
        return highlighted;
    }

    /// Updates whether [#getDisplayPoint()] should be treated as highlighted after this event.
    ///
    /// @param highlighted the new highlight-state flag
    public final void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted;
    }
}
