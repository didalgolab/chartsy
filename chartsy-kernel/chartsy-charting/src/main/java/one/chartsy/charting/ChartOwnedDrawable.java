package one.chartsy.charting;

/// Base class for [ChartDrawable] implementations that are owned by at most one [Chart].
///
/// [Chart] calls [#chartConnected(Chart, Chart)] whenever the drawable is attached to a chart,
/// detached from a chart, or reattached to a different chart instance. Implementations typically
/// use that callback to add or remove listeners, invalidate caches, or synchronize chart-derived
/// state.
public abstract class ChartOwnedDrawable implements ChartDrawable {

    /// Creates a drawable whose ownership will be supplied later by [Chart].
    public ChartOwnedDrawable() {
    }

    /// Reacts to a change in the owning chart reference.
    ///
    /// @param previousChart the previous owner, or `null` when the drawable was detached
    /// @param chart the new owner, or `null` when the drawable was detached
    protected abstract void chartConnected(Chart previousChart, Chart chart);

    /// Returns the chart that currently owns this drawable, if any.
    public abstract Chart getChart();
}
