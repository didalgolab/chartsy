package one.chartsy.charting.event;

import java.util.EventListener;

import one.chartsy.charting.Chart;

/// Listener for chart-area layout updates published by [Chart].
///
/// Register implementations through [Chart#addChartListener(ChartListener)] to be notified after
/// the chart installs a new plot rectangle and refreshes any scale geometry derived from that
/// layout. Typical listeners update cached bounds for decorations, gradients, or chart-to-chart
/// layout synchronization.
public interface ChartListener extends EventListener {

    /// Handles one chart-area change notification.
    void chartAreaChanged(ChartAreaEvent event);
}
