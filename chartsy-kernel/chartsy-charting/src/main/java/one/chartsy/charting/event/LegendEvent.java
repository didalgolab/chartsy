package one.chartsy.charting.event;

import java.util.EventObject;

import one.chartsy.charting.Legend;
import one.chartsy.charting.LegendEntry;

/// Event published when an interactive [Legend] acknowledges a click on one [LegendEntry].
///
/// [Legend] creates this event in [Legend#itemClicked(LegendEntry)] and dispatches it through
/// [Legend#fireLegendEvent(LegendEvent)] only when legend interactivity is enabled and at least
/// one [LegendListener] is registered.
///
/// The legend source is fixed by [EventObject]. The clicked entry reference is stored separately
/// and remains mutable through [#setItem(LegendEntry)], which lets callers retarget or reuse the
/// same event instance before redispatch if they need to.
public class LegendEvent extends EventObject {
    /// Legend entry currently associated with this event.
    private LegendEntry item;
    
    /// Creates an event for one legend-entry click.
    ///
    /// @param legend legend that published the event
    /// @param item legend entry currently associated with the click
    public LegendEvent(Legend legend, LegendEntry item) {
        super(legend);
        this.item = item;
    }
    
    /// Returns the legend entry currently associated with this event.
    ///
    /// @return clicked legend entry, or the replacement set through [#setItem(LegendEntry)]
    public final LegendEntry getItem() {
        return item;
    }
    
    /// Returns the legend that published this event.
    ///
    /// @return typed event source
    public final Legend getLegend() {
        return (Legend) super.getSource();
    }
    
    /// Replaces the legend entry associated with this event.
    ///
    /// This does not affect the event source returned by [#getLegend()].
    ///
    /// @param item new legend entry payload
    public final void setItem(LegendEntry item) {
        this.item = item;
    }
}
