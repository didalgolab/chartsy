package one.chartsy.charting.graphic;

import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.PlotStyle;

/// Extends [DataRenderingHint] with marker selection for point renderers that draw discrete
/// symbols.
///
/// Implementations still participate in ordinary style resolution through
/// [DataRenderingHint#getStyle(DisplayPoint, PlotStyle)]. Renderers that recognize this
/// subinterface can then ask [#getMarker(DisplayPoint, Marker)] for the glyph to use for the same
/// point.
///
/// Renderers that do not recognize this subinterface still see it as an ordinary
/// [DataRenderingHint] and can use only its style contribution.
///
/// ### API Note
///
/// The current built-in scatter renderer interprets `null` and [Marker#NONE] from
/// [#getMarker(DisplayPoint, Marker)] as "do not draw a marker" for that point.
public interface DataRenderingMarkerHint extends DataRenderingHint {
    
    /// Returns the marker to use for `point`.
    ///
    /// @param point renderer-visible point whose marker is being resolved
    /// @param defaultMarker renderer-selected fallback marker
    /// @return marker to draw for `point`
    Marker getMarker(DisplayPoint point, Marker defaultMarker);
    
}
