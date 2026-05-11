package one.chartsy.charting;

import java.awt.Graphics;

import one.chartsy.charting.renderers.SinglePieRenderer;

/// [ChartRendererLegendItem] specialization that identifies one slice row contributed by a
/// [SinglePieRenderer].
///
/// Ordinary renderer-backed legend rows carry only their owning renderer because one renderer
/// usually contributes one legend entry. Pie renderers instead contribute one row per slice. This
/// subtype adds the slice index that [SinglePieRenderer] needs when it resolves the current label
/// and marker style through [SinglePieRenderer#getLegendText(LegendEntry)] and
/// [SinglePieRenderer#drawLegendMarker(LegendEntry, Graphics, int, int, int, int)].
///
/// The item intentionally stores only the slice ordinal, not a copied label or style snapshot.
/// Legend painting asks the renderer for current slice metadata each time the entry is measured or
/// painted, so legend content stays aligned with live pie-slice configuration.
public class PieRendererLegendItem extends ChartRendererLegendItem {

    private final int dataIndex;

    /// Creates a legend row for one slice of `renderer`.
    ///
    /// @param dataIndex the zero-based slice index understood by the owning renderer
    /// @param renderer the renderer that owns the slice and supplies its current label and marker
    public PieRendererLegendItem(int dataIndex, SinglePieRenderer renderer) {
        super(renderer);
        this.dataIndex = dataIndex;
    }

    /// Returns which slice of the owning [SinglePieRenderer] this legend row represents.
    ///
    /// [SinglePieRenderer] uses this index to select slice-specific text, style, and related
    /// metadata whenever it is asked to paint or refresh the entry.
    public final int getDataIndex() {
        return dataIndex;
    }
}
