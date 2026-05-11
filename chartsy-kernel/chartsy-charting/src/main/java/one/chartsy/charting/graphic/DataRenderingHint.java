package one.chartsy.charting.graphic;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.PlotStyle;

/// Resolves the effective [PlotStyle] for one renderer-visible [DisplayPoint].
///
/// Built-in renderers choose a baseline style from their own configuration first, then pass that
/// style together with the current point to [#getStyle(DisplayPoint, PlotStyle)]. The returned
/// style is used for that point's paint pass, so hints can either preserve the baseline, replace
/// it, or derive a related variant from point data.
///
/// Charting code can attach hints to a [ChartRenderer] itself and, for renderers that support it,
/// to narrower dataset- or point-specific modifier slots before painting.
///
/// ### API Note
///
/// Several built-in point renderers skip painting for that point when this method returns `null`
/// instead of a style. Code that depends on that suppression behavior should still verify the
/// concrete renderer in use.
@FunctionalInterface
public interface DataRenderingHint {
    /// Returns the style to use for `point`.
    ///
    /// `style` is the renderer-selected baseline style for that point. Implementations may return
    /// it unchanged, return a different style, or return `null` when the concrete renderer treats
    /// that as "do not paint this point".
    ///
    /// @param point renderer-visible point being styled
    /// @param style renderer-selected baseline style for that point
    /// @return style to use for `point`, or `null` when the concrete renderer interprets that as
    ///     suppression
    PlotStyle getStyle(DisplayPoint point, PlotStyle style);
}
