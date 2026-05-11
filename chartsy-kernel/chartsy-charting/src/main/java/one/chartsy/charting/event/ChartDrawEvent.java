package one.chartsy.charting.event;

import java.awt.Graphics;

import one.chartsy.charting.Chart;

/// Brackets one chart paint pass and exposes the live graphics context for that pass.
///
/// `Chart` creates one event per repaint and delivers the same instance to
/// [ChartDrawListener#beforeDraw(ChartDrawEvent)] and
/// [ChartDrawListener#afterDraw(ChartDrawEvent)]. Listeners use it for transient overlays,
/// one-shot synchronization, or paint-time instrumentation that depends on the same `Graphics`
/// object as the chart itself.
///
/// ### API Note
///
/// The returned graphics context is owned by the active paint operation. Listeners should neither
/// dispose it nor retain it after the callback returns.
public class ChartDrawEvent extends ChartEvent {
    private final Graphics graphics;

    /// Creates a draw event for `chart`.
    ///
    /// @param graphics the live graphics context used for the current paint pass
    public ChartDrawEvent(Chart chart, Graphics graphics) {
        super(chart);
        this.graphics = graphics;
    }

    /// Returns the graphics context currently painting the chart.
    ///
    /// The same object is shared with the chart's own rendering code for this paint pass only.
    public Graphics getGraphics() {
        return graphics;
    }
}
