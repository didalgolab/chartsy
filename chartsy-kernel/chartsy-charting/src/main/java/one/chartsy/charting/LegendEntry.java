package one.chartsy.charting;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.io.Serial;
import java.util.Objects;

import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.border.Border;

import one.chartsy.charting.util.text.BidiUtil;

/// Represents one marker-plus-label row owned by a [Legend].
///
/// `LegendEntry` is the common base class for legend rows contributed by renderers and overlays.
/// The owning legend supplies marker-box size, marker-to-text spacing, foreground fallback, and
/// the effective base text direction used to normalize mixed-direction labels.
///
/// Detached entries intentionally report an empty preferred size, so they become layout-active
/// only after [Legend#addLegendItem(LegendEntry)] attaches them. Public label measurement and
/// painting methods still work while detached by falling back to this component's own Swing state.
///
/// ### Subclassing
///
/// Override [#drawMarker(Graphics, Rectangle)] to render the row's marker. Subclasses whose label
/// depends on renderer state may also update that label immediately before delegating to
/// [#paintComponent(Graphics)].
public class LegendEntry extends JComponent {

    @Serial
    private static final long serialVersionUID = 214800846233449063L;

    private Legend legend;
    private String label;
    private transient String resolvedLabelText;
    private final LabelRenderer labelRenderer;
    private transient Rectangle dynamicBounds;

    /// Creates a detached legend row with the supplied raw label text.
    ///
    /// The entry remains layout-neutral until a [Legend] attaches it through
    /// [Legend#addLegendItem(LegendEntry)].
    ///
    /// @param label the raw label text, or `null` for a marker-only row
    public LegendEntry(String label) {
        enableEvents(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
        labelRenderer = createLabelRenderer();
        setLabel(label);
    }

    /// Paints only the row content used by [Legend]'s export-time child painting path.
    ///
    /// The owning legend calls this after translating into temporary child bounds and before it
    /// paints the entry border separately.
    ///
    /// @param g the target graphics context
    final void paintEntry(Graphics g) {
        paintComponent(g);
    }

    /// Installs a temporary bounds override for export-time painting.
    ///
    /// [Legend] uses this hook when server-side layout predicts child bounds without mutating the
    /// live Swing hierarchy. Callers must clear a previous override with `null` before installing a
    /// second non-`null` rectangle.
    ///
    /// @param bounds export-time bounds override, or `null` to clear it
    /// @throws UnsupportedOperationException if a second non-`null` override is installed before
    ///     the first is cleared
    final void setDynamicBounds(Rectangle bounds) {
        if (bounds != null && dynamicBounds != null)
            throw new UnsupportedOperationException("setDynamicBounds is not reentrant");
        dynamicBounds = bounds;
    }

    /// Invalidates cached bidi-resolved text after the effective base direction changes.
    ///
    /// Subclasses may override this when they cache additional direction-dependent presentation
    /// state, but should still delegate to the base implementation.
    protected void baseTextDirectionChanged() {
        resolvedLabelText = null;
    }

    /// Reacts to a component-orientation change that can alter rendered label text.
    ///
    /// When the effective base direction is inherited or otherwise orientation-sensitive, the
    /// cached bidi-normalized label is discarded so the next measurement or paint recomputes it.
    ///
    /// @param previousOrientation the orientation in effect before the change
    /// @param newOrientation the orientation in effect after the change
    protected void componentOrientationChanged(
            ComponentOrientation previousOrientation,
            ComponentOrientation newOrientation) {
        if (newOrientation.isLeftToRight() == previousOrientation.isLeftToRight())
            return;
        if (getConfiguredBaseTextDirection() != 514 && getResolvedBaseTextDirection() != 527)
            return;
        baseTextDirectionChanged();
    }

    private Rectangle getMarkerBounds() {
        var markerBounds = new Rectangle();
        if (legend == null)
            return markerBounds;

        Insets insets = getInsets();
        Rectangle bounds = dynamicBounds;
        int width = (bounds != null) ? bounds.width : getWidth();
        int height = (bounds != null) ? bounds.height : getHeight();
        int contentHeight = height - insets.top - insets.bottom;
        int markerWidth = legend.getMarkerSize().width;
        int markerHeight = legend.getMarkerSize().height;

        markerBounds.x = getComponentOrientation().isLeftToRight()
                ? insets.left
                : width - insets.right - markerWidth;
        markerBounds.y = insets.top + (contentHeight - markerHeight) / 2;
        markerBounds.width = markerWidth;
        markerBounds.height = markerHeight;
        return markerBounds;
    }

    /// Paints the bidi-resolved label centered on `anchor`.
    ///
    /// Foreground resolution prefers this component's explicit foreground, then the owning legend's
    /// foreground, then Swing's `Label.foreground` default.
    ///
    /// @param g the target graphics context
    /// @param anchor the label center point
    public void drawLabel(Graphics g, Point2D anchor) {
        String text = getResolvedLabelText();
        if (text == null)
            return;

        labelRenderer.setColor(resolveLabelForeground());
        JComponent labelContext = getLabelContext();
        labelRenderer.paintLabel(labelContext, g, text, anchor.getX(), anchor.getY());
    }

    /// Paints the marker glyph inside `bounds`.
    ///
    /// The default implementation draws nothing.
    ///
    /// @param g the target graphics context
    /// @param bounds the marker slot reserved by the owning legend
    public void drawMarker(Graphics g, Rectangle bounds) {
    }

    private LabelRenderer createLabelRenderer() {
        return new LabelRenderer() {
            @Override
            Font getFallbackFont() {
                return UIManager.getFont("Panel.font");
            }
        };
    }

    private int getConfiguredBaseTextDirection() {
        return (legend != null) ? legend.getResolvedBaseTextDirection() : 514;
    }

    private JComponent getLabelContext() {
        return (legend != null) ? legend : this;
    }

    private String getResolvedLabelText() {
        if (label == null)
            return null;
        if (resolvedLabelText == null) {
            resolvedLabelText = BidiUtil.getCombinedString(
                    label,
                    getResolvedBaseTextDirection(),
                    getComponentOrientation(),
                    false);
        }
        return resolvedLabelText;
    }

    private Color resolveLabelForeground() {
        Color foreground = getForeground();
        if (foreground == null && legend != null)
            foreground = legend.getForeground();
        return (foreground != null) ? foreground : UIManager.getColor("Label.foreground");
    }

    private static Insets getBorderInsets(Border border, JComponent c) {
        return (border != null) ? border.getBorderInsets(c) : null;
    }

    private static boolean isZeroInsets(Insets insets) {
        return insets == null
                || (insets.left == 0 && insets.right == 0 && insets.top == 0 && insets.bottom == 0);
    }

    private static boolean affectsLabelLayout(Border previousBorder, Border newBorder, JComponent c) {
        Insets previousInsets = getBorderInsets(previousBorder, c);
        Insets newInsets = getBorderInsets(newBorder, c);
        return !Objects.equals(previousInsets, newInsets)
                && (!isZeroInsets(previousInsets) || !isZeroInsets(newInsets));
    }

    /// Returns the raw, unresolved label text supplied for this row.
    ///
    /// The returned value does not include bidi control marks added during paint or measurement.
    ///
    /// @return the logical label text, or `null`
    public final String getLabel() {
        return label;
    }

    /// Returns the measured size of the text block alone.
    ///
    /// The result includes any border configured through [#setTextBorder(Border)], but excludes the
    /// legend-managed marker slot, marker-to-text spacing, and this component's own insets.
    ///
    /// @return measured label size
    public Dimension getLabelDimension() {
        return labelRenderer.getSize(getLabelContext(), getResolvedLabelText(), true, true);
    }

    /// Returns the legend that currently owns this row.
    ///
    /// @return the owning legend, or `null` while detached
    public final Legend getLegend() {
        return legend;
    }

    /// Returns [#getPreferredSize()] so legend layouts treat each row as fixed-size content.
    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    /// Returns [#getPreferredSize()] so legend layouts treat each row as fixed-size content.
    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    /// Returns the size needed for the marker slot, spacing, label text, and component insets.
    ///
    /// Detached entries intentionally return an empty size until a [Legend] owns them.
    ///
    /// @return preferred row size for legend layout
    @Override
    public Dimension getPreferredSize() {
        if (legend == null)
            return new Dimension();

        Dimension size = getLabelDimension();
        Insets insets = getInsets();
        size.width += legend.getMarkerSize().width + legend.getSymbolTextSpacing() + insets.left + insets.right;
        size.height = Math.max(legend.getMarkerSize().height, size.height) + insets.top + insets.bottom;
        return size;
    }

    /// Returns the effective base text direction currently used to normalize [#getLabel()].
    ///
    /// Attached entries mirror their owning legend. Detached entries fall back to the current
    /// [ComponentOrientation].
    ///
    /// @return the resolved base text-direction constant
    public int getResolvedBaseTextDirection() {
        int configuredBaseTextDirection = getConfiguredBaseTextDirection();
        if (configuredBaseTextDirection != 514)
            return configuredBaseTextDirection;
        return getComponentOrientation().isLeftToRight() ? 516 : 520;
    }

    /// Returns the border painted around the text block by the internal [LabelRenderer].
    ///
    /// This is separate from the component border painted around the whole legend row.
    ///
    /// @return the text border, or `null`
    public Border getTextBorder() {
        return labelRenderer.getBorder();
    }

    /// Returns whether the internal [LabelRenderer] paints an opaque text background.
    ///
    /// This flag is independent of [#isOpaque()], which controls the whole row background.
    ///
    /// @return `true` when text painting fills a label background rectangle
    public boolean isOpaqueText() {
        return labelRenderer.isOpaque();
    }

    /// Paints the current marker and label using legend-provided row metrics.
    ///
    /// Detached entries paint nothing because marker size and spacing come from the owning legend.
    ///
    /// @param g the target graphics context
    @Override
    protected void paintComponent(Graphics g) {
        if (legend == null)
            return;

        Rectangle markerBounds = getMarkerBounds();
        drawMarker(g, markerBounds);

        Dimension labelSize = getLabelDimension();
        int labelCenterX = getComponentOrientation().isLeftToRight()
                ? markerBounds.x + markerBounds.width + legend.getSymbolTextSpacing() + labelSize.width / 2
                : markerBounds.x - legend.getSymbolTextSpacing() - labelSize.width / 2;
        int labelCenterY = markerBounds.y + (legend.getMarkerSize().height + 1) / 2;
        drawLabel(g, new Point2D.Double(labelCenterX, labelCenterY));
    }

    /// Updates component orientation and invalidates cached bidi text when needed.
    @Override
    public void setComponentOrientation(ComponentOrientation orientation) {
        ComponentOrientation previousOrientation = getComponentOrientation();
        super.setComponentOrientation(orientation);
        if (orientation != previousOrientation)
            componentOrientationChanged(previousOrientation, orientation);
    }

    /// Updates both this component and its internal [LabelRenderer] font.
    @Override
    public void setFont(Font font) {
        labelRenderer.setFont(font);
        super.setFont(font);
    }

    /// Updates both this component and its internal [LabelRenderer] foreground color.
    @Override
    public void setForeground(Color foreground) {
        labelRenderer.setColor(foreground);
        super.setForeground(foreground);
    }

    /// Replaces the raw label text and invalidates cached measurement and bidi state.
    ///
    /// @param label the new logical label text, or `null`
    public void setLabel(String label) {
        if (Objects.equals(this.label, label))
            return;

        this.label = label;
        resolvedLabelText = null;
        super.revalidate();
        super.repaint();
    }

    /// Attaches or detaches the owning legend for this row.
    ///
    /// [Legend] calls this while adding or removing the entry. The cached bidi-normalized label is
    /// cleared only when the ownership change alters the effective base text direction.
    ///
    /// @param legend the owning legend, or `null` to detach the row
    void setLegend(Legend legend) {
        if (legend == this.legend)
            return;

        int previousResolvedBaseTextDirection = getResolvedBaseTextDirection();
        this.legend = legend;
        if (getResolvedBaseTextDirection() != previousResolvedBaseTextDirection)
            baseTextDirectionChanged();
    }

    /// Enables or disables opaque background painting for the text block only.
    ///
    /// @param opaqueText `true` to paint a label background rectangle
    public void setOpaqueText(boolean opaqueText) {
        labelRenderer.setOpaque(opaqueText);
    }

    /// Replaces the border painted around the text block.
    ///
    /// Changing only border colors keeps the current layout. A revalidation happens only when the
    /// old and new borders differ in effective insets.
    ///
    /// @param border the new text border, or `null`
    public void setTextBorder(Border border) {
        Border previousBorder = labelRenderer.getBorder();
        if (border == previousBorder)
            return;

        labelRenderer.setBorder(border);
        if (affectsLabelLayout(previousBorder, border, this))
            super.revalidate();
        super.repaint();
    }
}
