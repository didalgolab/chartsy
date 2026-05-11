package one.chartsy.charting;

import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;
import java.io.Serial;
import java.util.Objects;

/// [LegendEntry] implementation that asks one [ChartRenderer] to supply both its marker and its
/// current label.
///
/// This type is the default bridge between the legend Swing component and renderer-specific legend
/// semantics. Marker painting is delegated to
/// [ChartRenderer#drawLegendMarker(LegendEntry, Graphics, int, int, int, int)], while
/// [#updateLabel()] resolves text through [ChartRenderer#getLegendText(LegendEntry)] just before
/// painting and again when the entry is attached to a [Legend].
///
/// Subclasses carry extra context when one renderer contributes more than one legend row, such as
/// pie-slice entries or combined hi/lo plus open/close entries.
public class ChartRendererLegendItem extends LegendEntry {
    
    @Serial
    private static final long serialVersionUID = -4385707374080591298L;
    
    private final ChartRenderer renderer;
    
    /// Creates a detached legend entry backed by `renderer`.
    ///
    /// The initial label is left unresolved until [#updateLabel()] runs, allowing the renderer to
    /// compute text lazily once the entry is measured or painted by a [Legend].
    ///
    /// @param renderer the renderer that supplies legend text and marker painting
    /// @throws NullPointerException if `renderer` is `null`
    public ChartRendererLegendItem(ChartRenderer renderer) {
        super(null);
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }
    
    /// Paints the renderer-supplied marker inside `rect`.
    ///
    /// The graphics clip is temporarily restricted to the marker slot before delegating to
    /// [ChartRenderer#drawLegendMarker(LegendEntry, Graphics, int, int, int, int)].
    @Override
    public void drawMarker(Graphics g, Rectangle rect) {
        Shape previousClip = g.getClip();
        try {
            g.clipRect(rect.x, rect.y, rect.width, rect.height);
            renderer.drawLegendMarker(this, g, rect.x, rect.y, rect.width - 1, rect.height - 1);
        } finally {
            g.setClip(previousClip);
        }
    }
    
    /// Returns the renderer consulted for label refresh and marker painting.
    ///
    /// @return the renderer that owns this legend row
    public final ChartRenderer getRenderer() {
        return renderer;
    }
    
    /// Refreshes the label immediately before [LegendEntry] paints the marker and text.
    ///
    /// This keeps the visible legend row aligned with renderer state that may change between paint
    /// passes, such as dynamically derived names.
    @Override
    protected void paintComponent(Graphics g) {
        updateLabel();
        super.paintComponent(g);
    }
    
    /// Updates the owning legend reference and refreshes the label when this entry becomes attached
    /// to a [Legend].
    ///
    /// Refreshing on attachment ensures layout can observe the current renderer-supplied text
    /// before the first on-screen paint of the new legend row.
    @Override
    void setLegend(Legend legend) {
        super.setLegend(legend);
        if (legend != null)
            updateLabel();
    }
    
    /// Resolves the current label from [#getRenderer()].
    ///
    /// The base implementation delegates to [ChartRenderer#getLegendText(LegendEntry)] with
    /// `this`, which lets renderers branch on the concrete legend-entry subtype when a single
    /// renderer contributes multiple legend rows.
    protected void updateLabel() {
        setLabel(renderer.getLegendText(this));
    }
}
