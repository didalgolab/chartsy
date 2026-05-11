package one.chartsy.charting.renderers.internal;

import java.io.Serializable;

import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.renderers.SinglePieRenderer;

/// Mutable slice-specific presentation overrides consumed by [SinglePieRenderer].
///
/// Each instance can carry an explicit [PlotStyle], an optional legend-text override, and an
/// explode ratio for one logical pie slice. Missing values leave `SinglePieRenderer` free to fall
/// back to renderer-wide defaults and dataset labels.
public class PieSliceInfo implements Serializable {
    String text;
    PlotStyle style;

    /// Outward offset of the slice, expressed as a percentage of that slice's radial thickness.
    public int explodeRatio;

    /// Returns the explicit style override for the slice.
    ///
    /// @return slice style override, or `null` when renderer defaults should be used
    public final PlotStyle getStyle() {
        return style;
    }

    /// Returns the stored legend-text override for the slice.
    ///
    /// @return explicit legend text, or `null` when the renderer should use the dataset label
    public final String getLegendText() {
        return text;
    }

    /// Sets the explicit style override for the slice.
    ///
    /// @param style slice style override, or `null` to fall back to renderer defaults
    public void setStyle(PlotStyle style) {
        this.style = style;
    }
}
