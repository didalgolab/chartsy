package one.chartsy.charting.event;

import java.util.EventListener;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartRenderer;

/// Listener for [ChartRendererEvent] notifications emitted by a [Chart].
///
/// Register implementations through [Chart#addChartRendererListener(ChartRendererListener)] to be
/// notified after the chart has already reacted to one renderer change. Each callback identifies
/// the affected [ChartRenderer] together with the change category chosen by the chart.
public interface ChartRendererListener extends EventListener {

    /// Handles one renderer change published by a chart.
    ///
    /// Listeners that need to coalesce multiple callbacks can also implement
    /// [ChartRendererListener2] to receive matching batch-boundary notifications.
    ///
    /// @param event change event describing the chart, renderer, and change category
    void rendererChanged(ChartRendererEvent event);
}
