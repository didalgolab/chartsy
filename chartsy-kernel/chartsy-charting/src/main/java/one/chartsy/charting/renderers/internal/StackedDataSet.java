package one.chartsy.charting.renderers.internal;

import one.chartsy.charting.data.DataSet;

/// Extends [DataSet] with the baseline information needed by stacked renderers.
///
/// Area and bar renderers use this contract to determine where the current series starts at a
/// logical point before they draw the visible segment represented by [#getYData(int)].
/// Implementations may derive that baseline from a preceding stacked dataset, from a cumulative
/// tower built across x-aligned points, or from percentage-normalized stack state; callers should
/// therefore treat it as "the y-value directly underneath this point" rather than "the raw y-value
/// from the previous dataset".
public interface StackedDataSet extends DataSet {

    /// Returns the stacked baseline directly below the point at the supplied logical index.
    ///
    /// Renderers typically combine this value with [#getYData(int)] to draw the vertical extent of
    /// the current series. `Double.NaN` signals that no prior stacked value exists at that index,
    /// letting the renderer fall back to its normal axis baseline.
    ///
    /// @param index logical point index in this stacked dataset view
    /// @return stacked y-baseline below the current point, or `Double.NaN` when the point starts a
    ///     stack
    double getPreviousYData(int index);
}
