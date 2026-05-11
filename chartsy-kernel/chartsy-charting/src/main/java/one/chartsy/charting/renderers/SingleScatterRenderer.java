package one.chartsy.charting.renderers;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.DoublePoint;
import one.chartsy.charting.LegendEntry;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.graphic.DataRenderingHint;
import one.chartsy.charting.graphic.DataRenderingMarkerHint;
import one.chartsy.charting.graphic.Marker;
import one.chartsy.charting.util.GraphicUtil;
import one.chartsy.charting.util.java2d.ShapeUtil;

/// Renders a single dataset as independent marker glyphs.
///
/// Every defined sample becomes one logical item positioned at that sample's projected x/y
/// coordinates. Undefined y-values, including the dataset-specific undefined sentinel when one is
/// configured, are skipped entirely.
///
/// Point-level [DataRenderingHint]s can override the base [PlotStyle] and, through
/// [DataRenderingMarkerHint], the marker used for a specific sample. A hint can also suppress a
/// sample by returning no style or `Marker.NONE`.
///
/// By default this renderer expands clip bounds and preferred margins by [#getMaxSize()] so
/// markers centered on the plot edge remain visible. Subclasses such as [SingleBubbleRenderer] can
/// opt into strict clipping and per-point marker sizing by overriding the dedicated hooks.
public class SingleScatterRenderer extends SingleChartRenderer {

    /// Reusable display-space marker item for one data point.
    ///
    /// The enclosing renderer mutates a single instance while traversing visible samples. The item
    /// therefore keeps only the projected coordinates, the source index, and the effective marker
    /// resolved for the current point.
    class ScatterItem implements SingleChartRenderer.Item {
        protected double x;
        protected double y;
        protected int dataIndex;
        protected Marker marker;

        ScatterItem() {
            marker = getMarker();
        }

        private int markerHalfSize() {
            return SingleScatterRenderer.this.getMarkerHalfSize(dataIndex);
        }

        @Override
        public double distance(PlotStyle style, double x, double y, boolean outlineOnly) {
            if (marker == null) {
                return Double.POSITIVE_INFINITY;
            }

            Rectangle2D bounds = getBounds(style, true, null);
            return outlineOnly ? ShapeUtil.distanceTo(bounds, x, y, null)
                    : bounds.contains(x, y) ? 0.0 : Double.POSITIVE_INFINITY;
        }

        @Override
        public void draw(Graphics g, PlotStyle style) {
            if (marker != null) {
                marker.draw(g, GraphicUtil.toInt(x), GraphicUtil.toInt(y), markerHalfSize(), style);
            }
        }

        @Override
        public Rectangle2D getBounds(PlotStyle style, boolean includeStroke, Rectangle2D bounds) {
            int markerHalfSize = markerHalfSize();
            if (bounds == null) {
                bounds = marker == null
                        ? new Rectangle2D.Double()
                        : new Rectangle2D.Double(x - markerHalfSize, y - markerHalfSize,
                        2 * markerHalfSize + 1, 2 * markerHalfSize + 1);
            } else if (marker == null) {
                bounds.setRect(0.0, 0.0, 0.0, 0.0);
            } else {
                bounds.setRect(x - markerHalfSize, y - markerHalfSize,
                        2 * markerHalfSize + 1, 2 * markerHalfSize + 1);
            }
            style.expand(includeStroke, bounds);
            return bounds;
        }

        public final void setDataIndex(int dataIndex) {
            this.dataIndex = dataIndex;
        }

        public final void setLocation(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    static final int DEFAULT_MARKER_SIZE = 3;
    static final Marker DEFAULT_MARKER = Marker.SQUARE;

    static {
        ChartRenderer.register("SingleScatter", SingleScatterRenderer.class);
    }

    private Marker marker;
    private int markerSize;

    private static boolean isUndefinedYValue(double y, Double undefinedY) {
        return Double.isNaN(y) || (undefinedY != null && y == undefinedY);
    }

    /// Creates a scatter renderer that uses square markers with half-size `3`.
    public SingleScatterRenderer() {
        this(DEFAULT_MARKER, DEFAULT_MARKER_SIZE, null);
    }

    /// Creates a scatter renderer with explicit marker geometry and base style.
    ///
    /// @param marker     marker template used for points that do not override it
    /// @param markerSize marker half-size used by the default scatter item
    /// @param style      base style, or `null` to let the renderer resolve a default
    public SingleScatterRenderer(Marker marker, int markerSize, PlotStyle style) {
        super(style);
        this.marker = marker;
        this.markerSize = markerSize;
    }

    /// Creates a scatter renderer with square markers and an explicit base style.
    public SingleScatterRenderer(PlotStyle style) {
        this(DEFAULT_MARKER, DEFAULT_MARKER_SIZE, style);
    }

    /// {@inheritDoc}
    ///
    /// Scatter traversal emits one item per defined sample. When rendering hints are active, each
    /// point resolves its own effective style and optional marker override before the item reaches
    /// the downstream action.
    @Override
    void forEachItem(SingleChartRenderer.Points points, SingleChartRenderer.ItemAction callback) {
        validateMarkerConfiguration();

        ScatterItem item = createScatterItem();
        double[] xCoords = points.getXCoords();
        double[] yCoords = points.getYCoords();
        int[] indices = points.getIndices();
        int pointCount = points.size();
        DataSet dataSet = points.getDataSet();
        PlotStyle baseStyle = getStyle();
        Double undefinedY = dataSet.getUndefValue();

        if (!hasRenderingHints()) {
            for (int index = 0; index < pointCount; index++) {
                if (isUndefinedYValue(points.getYData(index), undefinedY)) {
                    continue;
                }

                item.setLocation(xCoords[index], yCoords[index]);
                item.setDataIndex(indices[index]);
                item.marker = getMarker();
                callback.processItem(points, index, item, baseStyle);
            }
            return;
        }

        Marker defaultMarker = getMarker();
        DisplayPoint point = new DisplayPoint(this, dataSet);
        for (int index = 0; index < pointCount; index++) {
            if (isUndefinedYValue(points.getYData(index), undefinedY)) {
                continue;
            }

            point.dataSet = dataSet;
            point.set(indices[index], 0.0, 0.0);

            PlotStyle pointStyle = baseStyle;
            Marker pointMarker = defaultMarker;
            DataRenderingHint renderingHint = getRenderingHint(point);
            if (renderingHint != null) {
                pointStyle = renderingHint.getStyle(point, baseStyle);
                if (pointStyle == null) {
                    continue;
                }
                if (renderingHint instanceof DataRenderingMarkerHint markerHint) {
                    pointMarker = markerHint.getMarker(point, defaultMarker);
                    if (pointMarker == null || pointMarker == Marker.NONE) {
                        continue;
                    }
                }
            }

            item.marker = pointMarker;
            item.setLocation(xCoords[index], yCoords[index]);
            item.setDataIndex(indices[index]);
            callback.processItem(points, index, item, pointStyle);
        }
    }

    @Override
    Rectangle prepareSliceRect(Rectangle clipRect) {
        int markerHalfSize = getMaxSize() + 1;
        clipRect.grow(markerHalfSize, markerHalfSize);
        return clipRect;
    }

    /// {@inheritDoc}
    ///
    /// Data-label layout mode `2` offsets the shared label-placement helper by the current marker
    /// radius so the label clears the painted glyph.
    @Override
    public Point computeDataLabelLocation(DisplayPoint point, Dimension labelSize) {
        if (super.getDataLabelLayout() != 2) {
            return super.computeDataLabelLocation(point, labelSize);
        }

        double offset = 3 + getMarkerHalfSize(point.getIndex());
        DoublePoint anchor = new DoublePoint(point.getXCoord(), point.getYCoord());
        return super.computeShiftedLabelLocation(anchor, labelSize, offset, true);
    }

    @Override
    boolean keepOutsideYRangeWhenClipping() {
        return false;
    }

    /// Returns the marker half-size to use for `dataIndex`.
    ///
    /// The default implementation returns [#getMarkerSize()] for every point. Subclasses override
    /// this when marker geometry depends on per-point metadata.
    int getMarkerHalfSize(int dataIndex) {
        return getMarkerSize();
    }

    /// Draws the default marker glyph centered inside the legend swatch.
    ///
    /// When this renderer has no default marker, the legend entry stays empty unless an enclosing
    /// renderer paints its own legend representation.
    @Override
    public void drawLegendMarker(LegendEntry legend, Graphics g, int x, int y, int w, int h) {
        if (marker == null) {
            return;
        }
        marker.draw(g, x + w / 2, y + h / 2, Math.min(getMaxSize(), Math.min(w, h) / 2),
                super.getLegendStyle());
    }

    /// {@inheritDoc}
    ///
    /// Unclipped scatter renderers clone and expand the shared plot clip so markers can paint past
    /// the plot edge by up to their maximum radius.
    @Override
    public Rectangle getClipRect() {
        Rectangle clipRect = super.getClipRect();
        if (!isClipped()) {
            clipRect = (Rectangle) clipRect.clone();
            int markerHalfSize = getMaxSize();
            clipRect.grow(markerHalfSize, markerHalfSize);
        }
        return clipRect;
    }

    /// Returns the marker template currently used for points without a point-specific override.
    public final Marker getMarker() {
        return marker;
    }

    /// Returns the default marker half-size used by this renderer.
    public final int getMarkerSize() {
        return markerSize;
    }

    /// Returns the largest marker half-size this renderer can paint.
    ///
    /// Subclasses override this when marker size varies per point.
    public int getMaxSize() {
        return getMarkerSize();
    }

    /// {@inheritDoc}
    ///
    /// Unclipped scatter renderers reserve enough outer margin for the largest marker half-size on
    /// each side of the plot.
    @Override
    public Insets getPreferredMargins() {
        Insets margins = super.getPreferredMargins();
        if (!isClipped()) {
            int markerHalfSize = getMaxSize();
            if (margins.left < markerHalfSize) {
                margins.left = markerHalfSize;
            }
            if (margins.right < markerHalfSize) {
                margins.right = markerHalfSize;
            }
            if (margins.top < markerHalfSize) {
                margins.top = markerHalfSize;
            }
            if (margins.bottom < markerHalfSize) {
                margins.bottom = markerHalfSize;
            }
        }
        return margins;
    }

    /// Returns whether marker painting should stay inside the plot clip.
    ///
    /// The default is `false` so markers can remain visible when centered on the plot boundary.
    protected boolean isClipped() {
        return false;
    }

    /// Creates the reusable item instance used during scatter traversal.
    ///
    /// Subclasses override this to supply specialized items while keeping the one-point-per-sample
    /// traversal contract. The returned item is mutated and reused across the traversal, so
    /// callbacks must not retain it after the current [ItemAction#processItem(SingleChartRenderer.Points, int, SingleChartRenderer.Item, PlotStyle)]
    /// call returns.
    ScatterItem createScatterItem() {
        return new ScatterItem();
    }

    /// Updates the default marker template for future points and legend painting.
    ///
    /// `null` disables the renderer-wide fallback marker. Points can still paint if a
    /// [DataRenderingMarkerHint] supplies a per-point marker override.
    public void setMarker(Marker marker) {
        if (marker != this.marker) {
            this.marker = marker;
            super.triggerChange(4);
        }
    }

    /// Updates the default marker half-size used by this renderer.
    ///
    /// The value is interpreted as a half-size, matching the marker drawing API and the geometry
    /// returned by [ScatterItem#getBounds(PlotStyle, boolean, Rectangle2D)].
    public void setMarkerSize(int markerSize) {
        if (markerSize != this.markerSize) {
            this.markerSize = markerSize;
            super.triggerChange(4);
        }
    }

    /// Validates the current marker configuration before a traversal starts.
    ///
    /// The base implementation accepts every combination. Subclasses override this to reject
    /// inconsistent state before any items are emitted or geometry queries use the current marker
    /// configuration.
    void validateMarkerConfiguration() {
    }
}
