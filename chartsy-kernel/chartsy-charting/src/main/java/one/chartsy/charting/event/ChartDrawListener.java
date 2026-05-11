package one.chartsy.charting.event;

import java.util.EventListener;

/// Receives callbacks immediately before and after one chart paint pass.
///
/// Both callbacks run synchronously on the chart's painting path and see the same
/// [ChartDrawEvent] instance. `beforeDraw` is the last hook before chart contents render, while
/// `afterDraw` runs once the chart has finished painting and the graphics context is still valid
/// for that pass.
public interface ChartDrawListener extends EventListener {

    /// Called immediately before the chart paints its contents for the current pass.
    void beforeDraw(ChartDrawEvent event);

    /// Called immediately after the chart paints its contents for the current pass.
    void afterDraw(ChartDrawEvent event);
}
