package one.chartsy.charting.graphic;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;

import one.chartsy.charting.Chart;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.LabelRenderer;
import one.chartsy.charting.util.text.BidiUtil;

/// Paints a text label annotation for one renderer-visible [DisplayPoint].
///
/// When [#getText()] is `null`, the annotation asks the owning renderer to compute the label
/// content for each point. Before measurement or painting, that text is wrapped with bidi marks
/// derived from the owning [Chart]'s resolved base text direction and component orientation so the
/// measured bounds match the rendered glyph order.
///
/// Placement stays renderer-driven. The annotation measures the resolved text with its retained
/// [LabelRenderer] and then delegates to the point's renderer for the final display-space anchor,
/// letting bar, slice, marker, and polyline renderers apply their own label-offset rules without
/// duplicating text-layout logic here.
///
/// ### API Note
///
/// Subclasses can override [#computeText(DisplayPoint)] to change the label content or
/// [#computeLabelLocation(String, DisplayPoint)] to change the placement while keeping
/// [#draw(Graphics, DisplayPoint)] and [#getBounds(DisplayPoint, Rectangle2D)] consistent.
public class DataLabelAnnotation implements DataAnnotation, Serializable {
    /// Renderer used for both label measurement and label painting.
    private LabelRenderer labelRenderer;
    /// Optional fixed text override. When `null`, text is computed per point by the owning renderer.
    private String text;

    /// Creates an annotation with a white fill and black border/text label style.
    public DataLabelAnnotation() {
        labelRenderer = new LabelRenderer(Color.white, Color.black);
    }

    /// Creates an annotation that uses `labelRenderer` for all label measurement and painting.
    ///
    /// The renderer is retained by reference.
    ///
    /// @param labelRenderer renderer to use for future bounds and paint operations
    public DataLabelAnnotation(LabelRenderer labelRenderer) {
        this.labelRenderer = labelRenderer;
    }

    /// Returns the final label text to measure and paint for `displayPoint`.
    ///
    /// The returned string includes the bidi wrapper needed by the owning chart. A `null` result
    /// suppresses both painting and bounds.
    ///
    /// @param displayPoint point being annotated
    /// @return bidi-normalized label text, or `null`
    private String resolveDisplayText(DisplayPoint displayPoint) {
        String resolvedText = computeText(displayPoint);
        if (resolvedText != null) {
            Chart chart = displayPoint.getRenderer().getChart();
            resolvedText = BidiUtil.getCombinedString(
                    resolvedText,
                    chart.getResolvedBaseTextDirection(),
                    chart.getComponentOrientation(),
                    false);
        }
        return resolvedText;
    }

    /// Computes the display-space center point for `text`.
    ///
    /// The default implementation measures the current label first, including border and rotation,
    /// and then delegates to the owning renderer so renderer-specific offset rules stay in one
    /// place.
    ///
    /// @param text label text after bidi normalization
    /// @param displayPoint point being annotated
    /// @return display-space center for the label
    protected Point computeLabelLocation(String text, DisplayPoint displayPoint) {
        Dimension labelSize = getLabelRenderer().getSize(
                displayPoint.getRenderer().getChart().getChartArea(),
                text,
                true,
                true);
        return displayPoint.getRenderer().computeDataLabelLocation(displayPoint, labelSize);
    }

    /// Returns the base label text before bidi normalization.
    ///
    /// A non-`null` [#getText()] override wins. Otherwise the annotation delegates to the owning
    /// renderer's data-label hook for `displayPoint`.
    ///
    /// @param displayPoint point being annotated
    /// @return raw label text, or `null` when the renderer omits a label for this point
    protected String computeText(DisplayPoint displayPoint) {
        return (text != null) ? text : displayPoint.getRenderer().computeDataLabel(displayPoint);
    }

    /// Paints the annotation for `displayPoint`, if it resolves to a non-`null` label.
    ///
    /// Measurement and placement reuse the same helper path as
    /// [#getBounds(DisplayPoint, Rectangle2D)] so hit testing stays aligned with the painted label.
    @Override
    public void draw(Graphics g, DisplayPoint displayPoint) {
        String resolvedText = resolveDisplayText(displayPoint);
        if (resolvedText == null) {
            return;
        }

        Point labelLocation = computeLabelLocation(resolvedText, displayPoint);
        getLabelRenderer().paintLabel(
                displayPoint.getRenderer().getChart().getChartArea(),
                g,
                resolvedText,
                labelLocation.x,
                labelLocation.y);
    }

    /// Returns the axis-aligned bounds of the label that
    /// [#draw(Graphics, DisplayPoint)] would paint.
    ///
    /// A non-`null` `bounds` rectangle is reused in place. When the renderer supplies no label
    /// text, the result is an empty rectangle.
    @Override
    public Rectangle2D getBounds(DisplayPoint displayPoint, Rectangle2D bounds) {
        Rectangle2D result = bounds;
        String resolvedText = resolveDisplayText(displayPoint);
        if (resolvedText != null) {
            Point labelLocation = computeLabelLocation(resolvedText, displayPoint);
            Dimension2D labelSize = getLabelRenderer().getSize2D(
                    displayPoint.getRenderer().getChart().getChartArea(),
                    resolvedText,
                    true,
                    true);
            if (result != null) {
                result.setRect(
                        labelLocation.x - labelSize.getWidth() / 2.0,
                        labelLocation.y - labelSize.getHeight() / 2.0,
                        labelSize.getWidth(),
                        labelSize.getHeight());
            } else {
                result = new Rectangle2D.Double(
                        labelLocation.x - labelSize.getWidth() / 2.0,
                        labelLocation.y - labelSize.getHeight() / 2.0,
                        labelSize.getWidth(),
                        labelSize.getHeight());
            }
        } else if (result == null) {
            result = new Rectangle2D.Double();
        } else {
            result.setRect(0.0, 0.0, 0.0, 0.0);
        }
        return result;
    }

    /// Returns the live label renderer used for both measurement and painting.
    ///
    /// @return retained label renderer
    public final LabelRenderer getLabelRenderer() {
        return labelRenderer;
    }

    /// Returns the optional fixed text override.
    ///
    /// A `null` result means label content is computed by the owning renderer for each point.
    ///
    /// @return fixed text override, or `null`
    public String getText() {
        return text;
    }

    /// Replaces the label renderer used for future bounds and paint operations.
    ///
    /// The supplied renderer is retained by reference.
    ///
    /// @param labelRenderer renderer to use for future bounds and paint operations
    public void setLabelRenderer(LabelRenderer labelRenderer) {
        this.labelRenderer = labelRenderer;
    }

    /// Sets the optional fixed text override.
    ///
    /// Passing `null` restores per-point text computed by the owning renderer.
    ///
    /// @param text fixed label text, or `null`
    public void setText(String text) {
        this.text = text;
    }
}
