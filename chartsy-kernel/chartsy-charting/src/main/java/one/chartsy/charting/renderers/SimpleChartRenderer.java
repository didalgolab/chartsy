package one.chartsy.charting.renderers;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.util.Collections;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.ColorData;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DataSet;

/// Base class for renderers that own exactly one renderer-wide [PlotStyle].
///
/// Unlike [SingleChartRenderer], this type does not model per-dataset or per-point style lookup.
/// Callers either assign one explicit style through [#setStyle(PlotStyle)] or let the renderer
/// synthesize one default style from the owning [Chart]'s palette and the optional shared stroke
/// override stored in `defaultStroke`.
///
/// Passing `null` to [#setStyle(PlotStyle)] clears the explicit style choice. When the renderer is
/// chart-owned, that immediately recreates the default style and marks it as auto-generated so
/// later chart-level color changes can be replayed through [#refreshAutoStyle()]. Composite parents
/// use that hook to keep child default styles distinct after palette changes without overwriting
/// explicitly assigned styles.
public abstract class SimpleChartRenderer extends ChartRenderer {
    transient Stroke defaultStroke;
    private PlotStyle style;
    private transient boolean autoStyle;

    /// Creates a renderer with no explicit style.
    ///
    /// The first call to [#chartConnected(Chart, Chart)] or [#setStyle(PlotStyle)] determines
    /// whether the live style becomes auto-generated or explicit.
    protected SimpleChartRenderer() {
    }

    /// Creates a [PlotStyle] using the shared fallback stroke and a black fallback stroke paint.
    ///
    /// Subclasses reuse this helper when they need to synthesize a default style from chart-derived
    /// paints.
    PlotStyle createPlotStyle(Paint fillPaint, Paint strokePaint, Stroke stroke) {
        if (stroke == null)
            stroke = PlotStyle.DEFAULT_STROKE;
        if (strokePaint == null)
            strokePaint = Color.black;
        return new PlotStyle(stroke, strokePaint, fillPaint);
    }

    private void applyStyle(PlotStyle style) {
        PlotStyle resolvedStyle = style;
        if (resolvedStyle == null && super.getChart() != null) {
            resolvedStyle = makeDefaultStyle();
            autoStyle = true;
            this.style = resolvedStyle;
            super.triggerChange(4);
            return;
        }

        autoStyle = false;
        if (resolvedStyle != this.style) {
            this.style = resolvedStyle;
            super.triggerChange(4);
        }
    }

    /// {@inheritDoc}
    ///
    /// Connecting to a chart materializes the default style only when no explicit style was already
    /// set. Detaching leaves the last resolved style in place.
    @Override
    protected void chartConnected(Chart previousChart, Chart chart) {
        if (chart != null && getStyle() == null)
            setStyle(null);
    }

    /// Returns the chart-area bounds that should be used by the current paint pass.
    ///
    /// Normal interactive painting uses the live [Chart.Area] bounds. Image rendering may supply a
    /// paint-context override, and that override takes precedence when present so off-screen renders
    /// use the requested export geometry instead of the onscreen component size.
    ///
    /// Subclasses may use this helper only while the renderer is attached to a chart.
    @SuppressWarnings("unused")
    final Rectangle getEffectiveChartAreaBounds() {
        Chart chart = super.getChart();
        if (chart == null)
            throw new IllegalStateException("Renderer is not attached to a chart");
        Chart.Area chartArea = chart.getChartArea();
        if (chart.isPaintingImage() && chart.getPaintContext().getBounds() != null)
            return chart.getPaintContext().getBounds().get(chartArea);
        return chartArea.getBounds();
    }

    /// Paints the data range from `fromIndex` through `toIndex`.
    ///
    /// This base type cannot infer geometry from the dataset on its own, so subclasses that expose
    /// indexed drawing must override this method.
    ///
    /// @throws UnsupportedOperationException because [SimpleChartRenderer] provides no default
    ///         dataset-slice drawing behavior
    @SuppressWarnings("unused")
    public void draw(Graphics g, DataSet dataSet, int fromIndex, int toIndex) {
        throw new UnsupportedOperationException();
    }

    /// Recreates the chart-derived default style after chart context changes.
    ///
    /// This has an effect only while the current style was auto-generated rather than explicitly
    /// assigned. Explicit styles are left untouched.
    void refreshAutoStyle() {
        if (autoStyle) {
            style = null;
            applyStyle(null);
        }
    }

    private Color resolveDefaultColor() {
        return (super.getChart() != null)
                ? super.findAppropriateColor(Collections.emptySet())
                : ColorData.getDefaultColors().getFirst();
    }

    /// {@inheritDoc}
    ///
    /// The base implementation reports an empty bounds rectangle because it has no geometry model
    /// of its own. Concrete subclasses that participate in range clipping, export sizing, or hit
    /// heuristics are expected to override this method.
    @Override
    public Rectangle2D getBounds(DataSet dataSet, int fromIndex, int toIndex, Rectangle2D bounds, boolean includeStroke) {
        Rectangle2D resolvedBounds = bounds;
        if (resolvedBounds == null)
            resolvedBounds = new Rectangle2D.Double();
        else
            resolvedBounds.setRect(0.0, 0.0, 0.0, 0.0);
        return resolvedBounds;
    }

    /// Returns the renderer-wide style currently in effect.
    ///
    /// The value may be explicit or auto-generated from the owning chart.
    public PlotStyle getStyle() {
        return style;
    }

    /// {@inheritDoc}
    ///
    /// [SimpleChartRenderer] has no per-dataset style table, so callers should use [#getStyle()]
    /// instead.
    ///
    /// @throws UnsupportedOperationException because this renderer manages only one renderer-wide
    ///         style
    @Override
    public PlotStyle getStyle(DataSet dataSet, int index) {
        throw new UnsupportedOperationException();
    }

    /// {@inheritDoc}
    ///
    /// The returned array is always a snapshot containing either zero elements when no style is
    /// currently resolved or one element representing the renderer-wide style.
    @Override
    public PlotStyle[] getStyles() {
        if (style == null)
            return new PlotStyle[0];
        return new PlotStyle[] { style };
    }

    /// Creates the default style used when no explicit style is configured.
    ///
    /// The base implementation chooses an appropriate chart color and combines it with the shared
    /// stroke override.
    protected PlotStyle makeDefaultStyle() {
        return createPlotStyle(resolveDefaultColor(), null, defaultStroke);
    }

    /// Assigns the renderer-wide style.
    ///
    /// Passing `null` clears any explicit style and, when attached to a chart, regenerates the
    /// default style immediately.
    public void setStyle(PlotStyle style) {
        applyStyle(style);
    }

    /// {@inheritDoc}
    ///
    /// Only the first array element is meaningful for this base type because it owns a single style
    /// slot. Passing `null` clears the explicit style.
    @Override
    public void setStyles(PlotStyle[] styles) {
        setStyle((styles == null) ? null : styles[0]);
    }
}
