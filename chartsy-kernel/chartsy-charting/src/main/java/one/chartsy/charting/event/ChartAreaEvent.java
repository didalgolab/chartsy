package one.chartsy.charting.event;

import java.awt.Rectangle;

import one.chartsy.charting.Chart;

/// Reports that a [Chart] installed a new plot rectangle.
///
/// The notification is fired after the chart updates its scales for the new layout, so listeners
/// can immediately query the chart using the current plot geometry. Typical consumers use this to
/// refresh cached annotation bounds, gradient anchors, or synchronized margins.
public class ChartAreaEvent extends ChartEvent {
    private final Rectangle plotRect;

    /// Creates a chart-area event for `chart`.
    ///
    /// @param plotRect the plot rectangle that has just been installed
    public ChartAreaEvent(Chart chart, Rectangle plotRect) {
        super(chart);
        this.plotRect = plotRect;
    }

    /// Returns the plot rectangle that triggered this notification.
    ///
    /// The rectangle describes the chart's current plot area after the relayout.
    public Rectangle getPlotRect() {
        return plotRect;
    }
}
