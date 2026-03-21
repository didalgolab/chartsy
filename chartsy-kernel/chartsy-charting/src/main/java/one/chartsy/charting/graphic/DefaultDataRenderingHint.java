package one.chartsy.charting.graphic;

import java.io.Serializable;

import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.PlotStyle;

/// Adapts either a fixed [PlotStyle] or a relative [PlotStyle.Change] into a reusable
/// [DataRenderingHint].
///
/// This helper covers the two common renderer-override cases:
/// - replace the renderer-selected style for every addressed point with one retained style
/// - start from the renderer-selected style and apply one retained style change to it
///
/// The supplied `PlotStyle` or `PlotStyle.Change` is retained by reference rather than copied.
/// Later mutations to those objects therefore affect subsequent rendering passes driven by this
/// hint.
///
/// `DisplayPoint` is intentionally not inspected. Code that needs point-dependent styling should
/// implement [DataRenderingHint] directly instead of using this wrapper.
public class DefaultDataRenderingHint implements DataRenderingHint, Serializable {
    private final PlotStyle style;
    private final PlotStyle.Change styleChange;

    /// Creates a hint that always replaces the renderer-selected style with `style`.
    ///
    /// @param style the retained replacement style, or `null` to suppress painting at call sites
    ///                  that treat a `null` style as "do not render"
    public DefaultDataRenderingHint(PlotStyle style) {
        this.style = style;
        styleChange = null;
    }

    /// Creates a hint that derives the effective style by transforming the renderer-selected
    /// baseline style.
    ///
    /// @param styleChange the retained transformation applied to the renderer-selected baseline
    ///                        style
    public DefaultDataRenderingHint(PlotStyle.Change styleChange) {
        style = null;
        this.styleChange = styleChange;
    }

    /// Returns the fixed replacement style retained by this hint.
    ///
    /// @return the retained replacement style, or `null` when this instance operates in
    ///     change-based mode
    public final PlotStyle getStyle() {
        return style;
    }

    /// Resolves the style for `point` using this hint's configured mode.
    ///
    /// `point` is ignored. When a style change is present, the method delegates to
    /// [PlotStyle.Change#change(PlotStyle)] with the renderer-selected baseline style. Otherwise it
    /// returns the retained fixed style directly.
    ///
    /// @param defaultStyle the renderer-selected baseline style used only in change-based mode
    /// @return the retained fixed style, or the transformed baseline style when a change is
    ///     present
    @Override
    public PlotStyle getStyle(DisplayPoint point, PlotStyle defaultStyle) {
        if (styleChange == null)
            return style;
        return styleChange.change(defaultStyle);
    }

    /// Returns the retained style transformation used by this hint.
    ///
    /// @return the retained style transformation, or `null` when this instance operates in
    ///     fixed-style mode
    public final PlotStyle.Change getStyleChange() {
        return styleChange;
    }
}
