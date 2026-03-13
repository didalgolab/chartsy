package one.chartsy.charting.event;

import java.util.EventListener;

import one.chartsy.charting.Legend;

/// Listener for [LegendEvent] notifications from an interactive [Legend].
///
/// Register implementations through [Legend#addLegendListener(LegendListener)] to observe clicks
/// recognized by the legend's built-in entry interactor. Non-interactive legends suppress these
/// callbacks even when entries receive mouse input.
public interface LegendListener extends EventListener {
    
    /// Handles one legend-entry click.
    ///
    /// @param event event describing the owning legend together with the clicked entry
    void itemClicked(LegendEvent event);
}
