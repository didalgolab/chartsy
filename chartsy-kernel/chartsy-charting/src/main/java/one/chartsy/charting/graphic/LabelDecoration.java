package one.chartsy.charting.graphic;

import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

import one.chartsy.charting.Chart;
import one.chartsy.charting.LabelRenderer;
import one.chartsy.charting.event.LabelRendererPropertyEvent;
import one.chartsy.charting.util.text.BidiUtil;

/// Paints one anchored text label inside the chart plot rectangle.
///
/// The decoration measures its current text with a retained [LabelRenderer] and then uses the
/// anchor and offset rules from [PositionableDecoration] to place the label relative to the live
/// plot rectangle. Text is resolved lazily against the owning chart's base text direction and
/// component orientation, so bidi-sensitive labels stay aligned when the chart flips between
/// left-to-right and right-to-left presentation.
///
/// While attached and visible, the decoration listens to its renderer for
/// [LabelRendererPropertyEvent] updates. Size-affecting renderer changes recompute the anchor and
/// repaint the union of the previous and current label bounds; drawing-only changes repaint only
/// the cached label footprint.
///
/// The retained [LabelRenderer] is used directly rather than copied. Instances are mutable and not
/// thread-safe.
public class LabelDecoration extends PositionableDecoration {

    /// Repaints the cached label footprint after retained renderer changes.
    ///
    /// Size-affecting changes recompute the anchored location before the repaint so the label stays
    /// centered on the intended plot anchor.
    private final class LabelRendererChangeListener implements PropertyChangeListener, Serializable {
        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (!isAttachedAndVisible() || !(event instanceof LabelRendererPropertyEvent labelEvent))
                return;

            if (labelEvent.affectsSizes()) {
                Rectangle2D previousBounds = (Rectangle2D) boundsCache.clone();
                computeLocation();
                repaintAfterGeometryChange(previousBounds);
            } else if (labelEvent.affectsDrawing() && !boundsCache.isEmpty()) {
                getChart().getChartArea().repaint2D(boundsCache);
            }
        }
    }

    private String text;
    private LabelRenderer labelRenderer;
    private final PropertyChangeListener labelRendererChangeListener;

    private transient Rectangle2D boundsCache;

    /// Creates a centered label decoration with the module's default opaque label style.
    ///
    /// The default renderer uses a white fill with a black border and text so standalone labels
    /// remain legible over most plot backgrounds.
    ///
    /// @param text label text, or `null` for an empty decoration
    public LabelDecoration(String text) {
        this(text, new LabelRenderer(Color.white, Color.black), CENTER);
    }

    /// Creates a label decoration anchored to the plot rectangle.
    ///
    /// The supplied renderer is retained by reference and must not be `null`.
    ///
    /// @param text      label text, or `null` for an empty decoration
    /// @param labelRenderer renderer retained for future measurement and painting
    /// @param anchor        one of the compass-style [PositionableDecoration] anchors
    /// @throws NullPointerException if `labelRenderer` is `null`
    public LabelDecoration(String text, LabelRenderer labelRenderer, int anchor) {
        super(anchor);
        labelRendererChangeListener = new LabelRendererChangeListener();
        this.labelRenderer = Objects.requireNonNull(labelRenderer, "labelRenderer");
        this.text = text;
        initializeTransientState();
    }

    private Rectangle2D captureBoundsIfAttachedAndVisible() {
        return isAttachedAndVisible() ? (Rectangle2D) boundsCache.clone() : null;
    }

    private void initializeTransientState() {
        boundsCache = new Rectangle2D.Double();
    }

    private boolean isAttachedAndVisible() {
        return getChart() != null && isVisible();
    }

    private void repaintAfterGeometryChange(Rectangle2D previousBounds) {
        if (!isAttachedAndVisible())
            return;

        updateBoundsCache();
        Rectangle2D repaintBounds = (previousBounds != null) ? previousBounds : new Rectangle2D.Double();
        repaintBounds.add(boundsCache);
        getChart().getChartArea().repaint2D(repaintBounds);
    }

    private String resolveDisplayText() {
        return BidiUtil.getCombinedString(
                text,
                getChart().getResolvedBaseTextDirection(),
                getChart().getComponentOrientation(),
                false);
    }

    @Override
    protected void baseTextDirectionChanged() {
        computeLocation();
        if (isAttachedAndVisible())
            updateBoundsCache();
    }

    @Override
    protected void chartConnected(Chart previousChart, Chart chart) {
        super.chartConnected(previousChart, chart);
        if (!isVisible())
            return;

        if (previousChart == null && chart != null)
            labelRenderer.addPropertyChangeListener(labelRendererChangeListener);
        else if (previousChart != null && chart == null)
            labelRenderer.removePropertyChangeListener(labelRendererChangeListener);
    }

    @Override
    protected void componentOrientationChanged(ComponentOrientation oldOrientation,
                                               ComponentOrientation newOrientation) {
        if (newOrientation.isLeftToRight() != oldOrientation.isLeftToRight()) {
            computeLocation();
            if (isAttachedAndVisible())
                updateBoundsCache();
        }
    }

    /// Paints the resolved label text at the current anchored location.
    ///
    /// Detached decorations paint nothing.
    @Override
    public void draw(Graphics g) {
        if (getChart() == null)
            return;

        Point location = getLocation();
        labelRenderer.paintLabel(getChart().getChartArea(), g, resolveDisplayText(), location.x, location.y);
    }

    /// Returns the current device-space bounds of the rendered label.
    ///
    /// When detached, the decoration reports an empty rectangle.
    @Override
    public Rectangle2D getBounds(Rectangle2D bounds) {
        Rectangle2D result = (bounds != null) ? bounds : new Rectangle2D.Double();
        if (getChart() == null) {
            result.setRect(0.0, 0.0, 0.0, 0.0);
            return result;
        }

        Point location = getLocation();
        return labelRenderer.getBounds(
                getChart().getChartArea(),
                location.x,
                location.y,
                resolveDisplayText(),
                result);
    }

    /// Returns the live renderer retained by this decoration.
    ///
    /// Mutating the returned renderer while the decoration is attached and visible triggers the
    /// property-change listener documented on [LabelRendererChangeListener].
    ///
    /// @return retained renderer for this decoration
    public final LabelRenderer getLabelRenderer() {
        return labelRenderer;
    }

    /// Returns the raw label text before bidi normalization.
    ///
    /// @return retained label text, or `null`
    public final String getText() {
        return text;
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        initializeTransientState();
    }

    /// Replaces the renderer used for future measurement and painting.
    ///
    /// The supplied renderer is retained by reference and must not be `null`.
    ///
    /// @param labelRenderer renderer retained for future measurement and painting
    /// @throws NullPointerException if `labelRenderer` is `null`
    public void setLabelRenderer(LabelRenderer labelRenderer) {
        LabelRenderer newLabelRenderer = Objects.requireNonNull(labelRenderer, "labelRenderer");
        if (this.labelRenderer == newLabelRenderer)
            return;

        Rectangle2D previousBounds = captureBoundsIfAttachedAndVisible();
        if (previousBounds != null)
            this.labelRenderer.removePropertyChangeListener(labelRendererChangeListener);

        this.labelRenderer = newLabelRenderer;
        if (previousBounds != null)
            this.labelRenderer.addPropertyChangeListener(labelRendererChangeListener);

        computeLocation();
        repaintAfterGeometryChange(previousBounds);
    }

    /// Replaces the raw label text before bidi normalization.
    ///
    /// Passing `null` makes the decoration measure and paint as empty content.
    ///
    /// @param text new label text, or `null`
    public void setText(String text) {
        if (Objects.equals(this.text, text))
            return;

        Rectangle2D previousBounds = captureBoundsIfAttachedAndVisible();
        this.text = text;
        computeLocation();
        repaintAfterGeometryChange(previousBounds);
    }

    @Override
    public void setVisible(boolean visible) {
        boolean wasVisible = isVisible();
        super.setVisible(visible);
        if (getChart() == null)
            return;

        if (wasVisible && !isVisible())
            labelRenderer.removePropertyChangeListener(labelRendererChangeListener);
        else if (!wasVisible && isVisible())
            labelRenderer.addPropertyChangeListener(labelRendererChangeListener);
    }

    @Override
    protected void updateBoundsCache() {
        super.updateBoundsCache();
        boundsCache = getBounds(boundsCache);
    }
}
