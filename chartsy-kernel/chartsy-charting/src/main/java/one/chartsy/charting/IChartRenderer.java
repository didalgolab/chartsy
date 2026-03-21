package one.chartsy.charting;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;

import one.chartsy.charting.data.DataSetPoint;
import one.chartsy.charting.graphic.DataLabelAnnotation;

/// Minimal renderer contract shared by chart painting, data-label annotations, and legend entries.
///
/// The interface binds together three related responsibilities:
/// - [Chart] calls [#draw(Graphics)] for the renderer's main plot pass and then
///   [#drawAnnotations(Graphics)] for any annotation overlay pass.
/// - [DataLabelAnnotation] asks [#computeDataLabel(DataSetPoint)] and
///   [#computeDataLabelLocation(DisplayPoint, Dimension)] for the text and placement of
///   point-driven labels.
/// - [ChartRendererLegendItem] asks [#drawLegendMarker(LegendEntry, Graphics, int, int, int, int)]
///   for the marker that visually represents one legend row.
///
/// ### API Note
///
/// [ChartRenderer] is the standard base implementation in this module. Implementing this interface
/// directly only makes sense when the chart-ownership, data-source, and viewable-state behavior
/// supplied by [ChartRenderer] is intentionally not needed.
public interface IChartRenderer {

    /// Returns the text for a data-label annotation anchored at `dataPoint`.
    ///
    /// [DataLabelAnnotation] treats a `null` result as an instruction to skip label painting for
    /// that point.
    ///
    /// @param dataPoint the logical dataset point being labeled
    /// @return label text for `dataPoint`, or `null` to omit the label
    String computeDataLabel(DataSetPoint dataPoint);

    /// Computes the display-space center point for the label associated with `displayPoint`.
    ///
    /// [DataLabelAnnotation] measures the label first and passes that size here so renderers can
    /// offset labels away from bars, markers, or slices without duplicating label-layout logic. The
    /// returned point is reused for both painting and bounds calculation.
    ///
    /// @param displayPoint the display-space point being annotated
    /// @param labelSize the measured label size
    /// @return the display-space label center
    Point computeDataLabelLocation(DisplayPoint displayPoint, Dimension labelSize);

    /// Paints this renderer's primary plot contribution.
    ///
    /// Owning charts invoke this pass before [#drawAnnotations(Graphics)] for the same renderer.
    ///
    /// @param g the target graphics context
    void draw(Graphics g);

    /// Paints annotation overlays associated with this renderer's main plot content.
    ///
    /// This pass runs separately from [#draw(Graphics)] so charts can manage annotation layout and
    /// visibility independently of the main series geometry. Implementations that expose no
    /// annotations may do nothing.
    ///
    /// @param g the target graphics context
    void drawAnnotations(Graphics g);

    /// Paints the marker glyph for one legend row inside the supplied bounds.
    ///
    /// [ChartRendererLegendItem] clips the graphics context to the marker slot before invoking this
    /// method. `legend` may be a subtype carrying extra row-specific context when one renderer
    /// contributes multiple legend entries.
    ///
    /// @param legend the legend row requesting a marker
    /// @param g the target graphics context
    /// @param x the marker slot x coordinate
    /// @param y the marker slot y coordinate
    /// @param w the marker slot width
    /// @param h the marker slot height
    void drawLegendMarker(LegendEntry legend, Graphics g, int x, int y, int w, int h);
}
