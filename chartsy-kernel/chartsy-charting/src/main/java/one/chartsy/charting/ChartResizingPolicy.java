package one.chartsy.charting;

import java.awt.Rectangle;

import one.chartsy.charting.internal.ChartDefaultResizingPolicy;

/// Strategy that recomputes one axis' visible range when a chart's plot rectangle changes size.
///
/// [Chart] consults this policy only for axes that currently use a manual visible range. The
/// returned interval is then applied directly to that axis, allowing implementations to preserve a
/// particular anchor, maintain a fixed span in data space, or decline any special handling by
/// returning the current visible range unchanged.
///
/// The policy is axis-local: it is called once per eligible axis with the old and new plot
/// rectangles, not with the full chart state after every resize side effect has already been
/// applied.
public interface ChartResizingPolicy {

    /// Built-in policy that preserves manual cartesian windows during ordinary resize operations.
    ///
    /// This constant delegates to [ChartDefaultResizingPolicy], which adjusts visible ranges only
    /// when a cartesian projector can translate the plot-size delta into a new axis window.
    ChartResizingPolicy DEFAULT_POLICY = new ChartDefaultResizingPolicy();

    /// Computes the visible range that should be applied after the plot rectangle changes size.
    ///
    /// `previousDrawRect` and `drawRect` describe the same logical plot area before and after the
    /// resize. Implementations may inspect the chart, axis orientation, projector, or transformer
    /// state to preserve whichever on-screen anchoring they consider stable across the resize.
    ///
    /// @param chart            the chart being resized
    /// @param axis             the axis whose manual visible range should be recomputed
    /// @param previousDrawRect the plot rectangle before the resize
    /// @param drawRect         the plot rectangle after the resize
    /// @return non-`null` visible range to apply to `axis`
    DataInterval computeVisibleRange(Chart chart, Axis axis, Rectangle previousDrawRect,
                                     Rectangle drawRect);
}
