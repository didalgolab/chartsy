package one.chartsy.charting.event;

import java.util.EventObject;

import one.chartsy.charting.Chart;

/// Base class for chart notifications whose event source is the owning [Chart].
///
/// This type carries no payload beyond the typed chart reference returned by [#getChart()].
/// Concrete subclasses such as [ChartAreaEvent] and [ChartDrawEvent] add the geometry or
/// paint-pass state specific to one chart callback.
public class ChartEvent extends EventObject {

    /// Creates an event sourced from `chart`.
    protected ChartEvent(Chart chart) {
        super(chart);
    }

    /// Returns the chart that emitted this event.
    public Chart getChart() {
        return (Chart) super.getSource();
    }
}
