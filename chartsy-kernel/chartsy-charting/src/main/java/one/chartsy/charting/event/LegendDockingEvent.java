package one.chartsy.charting.event;

import java.io.Serial;
import java.util.EventObject;

import one.chartsy.charting.Chart;
import one.chartsy.charting.Legend;

/// Event published when an interactive [Legend] changes chart position or switches to floating
/// mode.
///
/// The event stores only the previous position. [#getNewPosition()] always reads the legend's
/// current [Legend#getPosition()] when queried, which keeps the event lightweight but means the
/// "new" position is not a snapshot if the legend moves again before the event is inspected.
///
/// [Chart] creates and dispatches this event both when a legend is repositioned programmatically
/// through chart APIs and when the user finishes a drag-based docking gesture.
public class LegendDockingEvent extends EventObject {

    @Serial
    private static final long serialVersionUID = 1991560371514658460L;

    /// Position captured before the legend move was applied.
    private final String oldPosition;

    /// Creates a docking event for one legend move.
    ///
    /// @param legend      legend whose position changed
    /// @param oldPosition position held before the change, or `null` when no previous position was
    ///                                           established
    public LegendDockingEvent(Legend legend, String oldPosition) {
        super(legend);
        this.oldPosition = oldPosition;
    }

    /// Returns the legend position observed at query time.
    ///
    /// @return current value of [Legend#getPosition()]
    public String getNewPosition() {
        return getSource().getPosition();
    }

    /// Returns the position captured before this change was applied.
    ///
    /// @return previous legend position, or `null` when none had been recorded yet
    public String getOldPosition() {
        return oldPosition;
    }

    /// Returns the legend that published this event.
    @Override
    public Legend getSource() {
        return (Legend) super.getSource();
    }
}
