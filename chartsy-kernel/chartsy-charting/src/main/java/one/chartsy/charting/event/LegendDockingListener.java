package one.chartsy.charting.event;

import java.util.EventListener;

import one.chartsy.charting.Legend;

/// Listener for [LegendDockingEvent] notifications from an interactive [Legend].
///
/// Register implementations through [Legend#addLegendDockingListener(LegendDockingListener)] to
/// observe moves between docked chart slots and floating placement. Non-interactive legends keep
/// this listener silent even if their position changes programmatically.
public interface LegendDockingListener extends EventListener {

    /// Handles one legend docking change.
    ///
    /// @param event event describing the legend together with its previous position
    void dockingChanged(LegendDockingEvent event);
}
