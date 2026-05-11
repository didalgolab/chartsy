package one.chartsy.charting.event;

import java.util.EventObject;

import one.chartsy.charting.ChartInteractor;
import one.chartsy.charting.DisplayPoint;

/// Reports one semantic interaction result emitted by a [ChartInteractor].
///
/// The event source is the interactor that recognized the gesture. [#getDisplayPoint()] carries
/// the resolved display-space point associated with that gesture, and subclasses such as
/// [ChartHighlightInteractionEvent] can attach additional state without changing the basic payload
/// shape.
///
/// ### API Note
///
/// Current built-in interactors publish cloned [DisplayPoint] snapshots before dispatch so
/// listeners can retain the point without depending on renderer-owned mutable instances.
public class ChartInteractionEvent extends EventObject {
    private DisplayPoint displayPoint;

    /// Creates an interaction event for `interactor`.
    ///
    /// @param displayPoint the display-space point resolved for the interaction
    public ChartInteractionEvent(ChartInteractor interactor, DisplayPoint displayPoint) {
        super(interactor);
        this.displayPoint = displayPoint;
    }

    /// Returns the display-space point associated with this interaction.
    public final DisplayPoint getDisplayPoint() {
        return displayPoint;
    }

    /// Returns the interactor that emitted this interaction event.
    public final ChartInteractor getInteractor() {
        return (ChartInteractor) super.getSource();
    }

    /// Updates the display-space point carried by this event.
    ///
    /// @param displayPoint the new display-space point payload
    public final void setDisplayPoint(DisplayPoint displayPoint) {
        this.displayPoint = displayPoint;
    }

    /// Updates the interactor recorded as this event's source.
    ///
    /// @param interactor the interactor to expose through [#getInteractor()]
    public final void setInteractor(ChartInteractor interactor) {
        super.source = interactor;
    }
}
