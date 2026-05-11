package one.chartsy.charting.renderers;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartDataPicker;
import one.chartsy.charting.ChartProjector;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.CoordinateSystem;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.DoublePoint;
import one.chartsy.charting.LegendEntry;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DataPoints;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSetPoint;
import one.chartsy.charting.graphic.DataRenderingHint;
import one.chartsy.charting.graphic.Marker;
import one.chartsy.charting.renderers.internal.DataSetRendererProperty;
import one.chartsy.charting.renderers.internal.VirtualDataSet;
import one.chartsy.charting.util.GraphicUtil;
import one.chartsy.charting.util.PointsClipper;
import one.chartsy.charting.util.java2d.AdaptablePaint;

/// Single-series renderer that draws one dataset as contiguous polyline runs and can optionally
/// overlay per-point markers.
///
/// Undefined samples, whether represented as `Double.NaN` or as the dataset-specific undefined
/// sentinel, split the dataset into separate logical runs. Painting and bounds collection process
/// each run as one item, while nearest-item picking breaks a run into adjacent segments so
/// distance checks stay local and the reported source index remains stable.
///
/// Marker painting is delegated to an internal [SingleScatterRenderer] that shares the same
/// dataset selection, rendering hints, and default-style derivation as the line itself. When no
/// explicit marker style is configured, markers inherit the polyline fill style or, for stroke-only
/// lines, derive a fill from the line stroke so the glyph stays visible.
///
/// Radar charts treat the first and last defined samples as neighbors. To keep the seam visually
/// continuous, this renderer requests one extra sample on each side of the visible x-window and
/// temporarily splices the wrapped endpoint into the active batch when needed.
///
/// Filled subclasses such as [SingleAreaRenderer] and [SingleStairRenderer] reuse the same
/// traversal hooks, item buffer, and marker overlay logic but reinterpret the emitted polyline
/// runs as polygons or stepped outlines.
public class SinglePolylineRenderer extends SingleChartRenderer {

    /// Internal scatter helper used to paint and pick the optional marker overlay.
    ///
    /// The helper shares dataset selection, per-point rendering hints, and fallback style
    /// derivation with the enclosing polyline so marker behavior stays aligned with the line run it
    /// annotates.
    class MarkerRenderer extends SingleScatterRenderer {

        MarkerRenderer() {
            super.setMarker(null);
        }

        @Override
        DataRenderingHint getRenderingHint(DataSetPoint point) {
            return SinglePolylineRenderer.this.getRenderingHint(point);
        }

        @Override
        DataSet getRenderedDataSet() {
            return SinglePolylineRenderer.this.getRenderedDataSet();
        }

        @Override
        public ChartRenderer getParent() {
            return SinglePolylineRenderer.this;
        }

        @Override
        public PlotStyle getStyle() {
            PlotStyle style = super.getStyle();
            return (style != null) ? style : makeDefaultStyle();
        }

        @Override
        boolean hasRenderingHints() {
            return SinglePolylineRenderer.this.hasRenderingHints();
        }

        @Override
        protected PlotStyle makeDefaultStyle() {
            PlotStyle lineStyle = SinglePolylineRenderer.this.getStyle();
            if (lineStyle == null)
                return super.makeDefaultStyle();
            return lineStyle.isFillOn() ? lineStyle : new PlotStyle(lineStyle.getStrokePaint());
        }

        @Override
        SingleScatterRenderer.ScatterItem createScatterItem() {
            return new SingleScatterRenderer.ScatterItem() {
                final DataSet dataSet = MarkerRenderer.this.getRenderedDataSet();
                final DataInterval visibleRange = SinglePolylineRenderer.this.getChart().getXAxis().getVisibleRange();

                @Override
                public void draw(Graphics g, PlotStyle style) {
                    if (!visibleRange.isInside(dataSet.getXData(super.dataIndex)))
                        return;
                    super.draw(g, style);
                }
            };
        }
    }

    /// Bounds collector that expands polyline geometry to include the optional marker overlay.
    ///
    /// The base item bounds already cover the stroke or polygon run itself. This collector adds the
    /// marker radius and any marker-style stroke expansion so the resulting rectangle matches the
    /// full painted extent.
    class PolyBoundsAction extends SingleChartRenderer.DefaultBoundsItemAction {

        @Override
        public Rectangle2D getBounds() {
            Rectangle2D bounds = super.getBounds();
            if (!hasNoMarker(getMarker())) {
                GraphicUtil.grow(bounds, getMarkerSize() + 1, getMarkerSize() + 1);
                getMarkerRenderer().getStyle().expand(false, bounds);
            }
            return bounds;
        }
    }

    /// Mutable buffer for one contiguous polyline run.
    ///
    /// The same container is reused for plain lines, filled polygons, and outline-only passes. The
    /// flags exposed through the setter methods let filled subclasses tell whether a run touches a
    /// series or dataset boundary before they close the shape.
    class PolyItem extends SingleChartRenderer.DefaultItem {
        static final int DRAW_MODE_POLYLINE = 1;
        static final int DRAW_MODE_OUTLINE = 2;
        static final int DRAW_MODE_POLYGON = 3;

        /// Number of source samples in the run before subclasses append closure or stair vertices.
        int sourcePointCount;
        /// Internal mode that tells the shared item whether it represents a line, outline, or polygon pass.
        int drawMode;
        boolean firstInSeries;
        boolean lastInSeries;
        boolean firstInDataSet;
        boolean lastInDataSet;
        boolean skipFirstRayStroke;
        boolean skipLastRayStroke;

        /// Creates a polyline item with space for up to `pointCapacity` vertices.
        ///
        /// @param pointCapacity initial capacity of the shared coordinate buffers
        /// @param drawMode      one of this item type's internal line or polygon modes
        public PolyItem(int pointCapacity, int drawMode) {
            super(pointCapacity);
            this.drawMode = drawMode;
        }

        /// {@inheritDoc}
        ///
        /// Plain line runs use the base polyline distance logic. Filled subclasses reuse the same
        /// item type for outline-only and polygon passes, so the draw mode decides whether distance
        /// should be measured against the line or against the filled polygon.
        @Override
        public double distance(PlotStyle style, double x, double y, boolean outlineOnly) {
            if (drawMode == DRAW_MODE_POLYLINE)
                return super.distance(style, x, y, outlineOnly);
            if (drawMode == DRAW_MODE_OUTLINE)
                return style.distanceToPolyline(super.getXValues(), super.getYValues(), super.size(), x, y, outlineOnly);
            if (drawMode != DRAW_MODE_POLYGON)
                return Double.POSITIVE_INFINITY;
            return style.distanceToPolygon(super.getXValues(), super.getYValues(), super.size(), x, y, outlineOnly);
        }

        @Override
        public void draw(Graphics g, PlotStyle style) {
            if (style.isStrokeOn())
                style.drawPolyline(g, super.getXValues(), super.getYValues(), super.size());
        }

        public void setFirstInDataSet(boolean firstInDataSet) {
            this.firstInDataSet = firstInDataSet;
        }

        public void setFirstInSeries(boolean firstInSeries) {
            this.firstInSeries = firstInSeries;
        }

        public void setLastInDataSet(boolean lastInDataSet) {
            this.lastInDataSet = lastInDataSet;
        }

        public void setLastInSeries(boolean lastInSeries) {
            this.lastInSeries = lastInSeries;
        }

        public void setSkipFirstRayStroke(boolean skipFirstRayStroke) {
            this.skipFirstRayStroke = skipFirstRayStroke;
        }

        public void setSkipLastRayStroke(boolean skipLastRayStroke) {
            this.skipLastRayStroke = skipLastRayStroke;
        }

        /// Receives the leading source index for this run before any subclass post-processing.
        ///
        /// Base polylines do not retain the value, but subclasses can override when they need the
        /// starting source index while reshaping the run.
        public void setStartIndex(@SuppressWarnings("unused") int startIndex) {
        }
    }

    /// Adapter that prepares emitted polyline runs for another [SingleChartRenderer.ItemAction].
    ///
    /// The delegate sees items only after x-range clipping, data-to-display projection, and any
    /// subclass-specific reshaping. Filled renderers reuse this hook point to close polygons or
    /// convert the run into a different display-space outline before bounds collection, painting,
    /// or picking continues.
    class PolyItemAction implements SingleChartRenderer.ItemAction {
        private final SingleChartRenderer.ItemAction delegate;
        private final Rectangle plotRect;
        private final CoordinateSystem coordinateSystem;
        private final ChartProjector projector;

        /// Creates a run adapter around `delegate`.
        ///
        /// @param delegate downstream consumer that receives processed polyline items
        PolyItemAction(SingleChartRenderer.ItemAction delegate) {
            this.delegate = delegate;
            plotRect = getPlotRect();
            coordinateSystem = getCoordinateSystem();
            projector = getChart().getLocalProjector2D(plotRect, coordinateSystem);
        }

        /// Clips the run to the current visible x interval before projection.
        void clipToVisibleRange(SinglePolylineRenderer.PolyItem item) {
            PointsClipper.clipX(item, getXAxis().getVisibleMin(), getXAxis().getVisibleMax());
        }

        @Override
        public void endProcessItems() {
            delegate.endProcessItems();
        }

        @Override
        public int minPolylineItemPoints() {
            return delegate.minPolylineItemPoints();
        }

        /// Hook for subclasses that need to append closing points or otherwise reshape the run
        /// after projection but before the delegate consumes it.
        protected void postProcessPolyItem(SingleChartRenderer.Points points, SinglePolylineRenderer.PolyItem item,
                                           PlotStyle style) {
        }

        /// Projects one still-connected run into display space and forwards it to the delegate.
        protected void processConnectedPolyItem(SingleChartRenderer.Points points, int pointIndex,
                                                SinglePolylineRenderer.PolyItem item, PlotStyle style) {
            clipToVisibleRange(item);
            SinglePolylineRenderer.this.toDisplay(item, projector, plotRect, coordinateSystem);
            postProcessPolyItem(points, item, style);
            delegate.processItem(points, pointIndex, item, style);
        }

        @Override
        public void processItem(SingleChartRenderer.Points points, int pointIndex, SingleChartRenderer.Item item,
                                PlotStyle style) {
            SinglePolylineRenderer.PolyItem polyItem = (SinglePolylineRenderer.PolyItem) item;
            if (polyItem.size() > 0) {
                processPolyItem(points, pointIndex, polyItem, style);
            }
        }

        /// Processes one emitted run before it reaches the delegate.
        ///
        /// The default implementation forwards the run unchanged after clipping and projection.
        protected void processPolyItem(SingleChartRenderer.Points points, int pointIndex,
                                       SinglePolylineRenderer.PolyItem item, PlotStyle style) {
            processConnectedPolyItem(points, pointIndex, item, style);
        }

        @Override
        public void startProcessItems() {
            delegate.startProcessItems();
        }
    }

    static {
        ChartRenderer.register("SinglePolyline", SinglePolylineRenderer.class);
    }

    private PlotStyle c;
    private MarkerRenderer d;

    private static boolean hasNoMarker(Marker marker) {
        return marker == null || marker == Marker.NONE;
    }

    /// Creates a polyline renderer that derives its style from the owning chart when attached.
    public SinglePolylineRenderer() {
    }

    /// Creates a polyline renderer with an explicit base line style.
    public SinglePolylineRenderer(PlotStyle style) {
        super(style);
    }

    /// Creates the logical polyline item used for one emitted run.
    ///
    /// Filled subclasses override this to install a more specialized item type while preserving the
    /// same run traversal contract.
    SinglePolylineRenderer.PolyItem createPolyItem(int pointCapacity, int drawMode) {
        return new SinglePolylineRenderer.PolyItem(pointCapacity, drawMode);
    }

    PlotStyle resolveMarkerStyle(PlotStyle style) {
        PlotStyle markerStyle = getMarkerStyle();
        if (markerStyle == null)
            markerStyle = style.isFillOn() ? style : new PlotStyle(style.getStrokePaint());
        return markerStyle;
    }

    /// Draws the legend marker glyph centered inside the legend swatch when markers are enabled.
    final void drawLegendMarkerOverlay(PlotStyle style, Graphics g, int x, int y, int w, int h) {
        if (!hasNoMarker(getMarker()))
            getMarker().draw(g, x + w / 2, y + h / 2,
                    Math.min(getMarkerSize(), Math.min(w / 2, h / 2)),
                    this.resolveMarkerStyle(style));
    }

    /// {@inheritDoc}
    ///
    /// Radar slices ignore the narrow display-space query rectangle because a wrapped run can need
    /// context on both sides of the seam to stay continuous.
    @Override
    DataPoints getVisibleData(Rectangle sliceRect) {
        if (super.getChart().getType() == Chart.RADAR)
            return super.getVisibleData(null);
        return super.getVisibleData(sliceRect);
    }

    SinglePolylineRenderer.PolyItemAction createPolyItemAction(SingleChartRenderer.ItemAction action) {
        return new SinglePolylineRenderer.PolyItemAction(action);
    }

    private boolean isUndefinedPoint(SingleChartRenderer.Points points, boolean resolveRenderingHints, int pointIndex) {
        int[] indices = points.getIndices();
        PlotStyle style = super.getStyle();
        Double undefinedValue = points.getDataSet().getUndefValue();
        double undefinedSentinel = (undefinedValue == null) ? 0.0 : undefinedValue.doubleValue();
        if (resolveRenderingHints) {
            DisplayPoint point = new DisplayPoint(this, points.getDataSet(), indices[pointIndex], 0.0, 0.0);
            DataRenderingHint renderingHint = super.getRenderingHint(point);
            if (renderingHint != null)
                style = renderingHint.getStyle(point, style);
        }

        double y = points.getYData()[pointIndex];
        if (style == null)
            return true;
        if (undefinedValue != null && y == undefinedSentinel)
            return true;
        return Double.isNaN(y);
    }

    /// {@inheritDoc}
    ///
    /// Marker picking gets the first chance when markers are globally enabled. Any winning marker
    /// hit is then wrapped back into a [DisplayPoint] owned by this polyline renderer.
    @Override
    DisplayPoint pickItem(SingleChartRenderer.Points points, ChartDataPicker dataPicker, boolean measureDistance,
                          double[] distanceOut) {
        DisplayPoint markerPoint = null;
        if (!hasNoMarker(getMarker()))
            markerPoint = getMarkerRenderer().pickItem(points, dataPicker, measureDistance, distanceOut);
        if (markerPoint == null)
            return super.pickItem(points, dataPicker, measureDistance, distanceOut);
        return new DisplayPoint(this, markerPoint.getDataSet(), markerPoint.getIndex(),
                markerPoint.getXCoord(), markerPoint.getYCoord());
    }

    /// {@inheritDoc}
    ///
    /// The traversal groups consecutive defined samples into one run, except during nearest-item
    /// picking where each emitted item spans only adjacent samples so segment distance checks remain
    /// local. Radar charts may splice one wrapped endpoint into the batch before traversal starts.
    @Override
    void forEachItem(SingleChartRenderer.Points points, SingleChartRenderer.ItemAction action) {
        boolean splitIntoSegments = action instanceof SingleChartRenderer.DistanceItemAction;
        SinglePolylineRenderer.PolyItemAction polyItemAction = this.createPolyItemAction(action);
        boolean resolveRenderingHints = super.hasRenderingHints();
        boolean skipFirstRayStroke = false;
        boolean skipLastRayStroke = false;

        if (super.getChart().getType() == Chart.RADAR && points.size() >= 1) {
            DataSet dataSet = points.getDataSet();
            int dataSetSize = dataSet.size();
            if (points.getDataIndex(0) == 0
                    && !this.isUndefinedPoint(points, resolveRenderingHints, points.size() - 1))
                skipFirstRayStroke = true;
            if (points.getDataIndex(points.size() - 1) == dataSetSize - 1
                    && !this.isUndefinedPoint(points, resolveRenderingHints, 0))
                skipLastRayStroke = true;

            if (points.getDataIndex(points.size() - 1) == dataSetSize - 1)
                points.addData(dataSet.getXData(0), dataSet.getYData(0), 0);
            else if (points.getDataIndex(0) == 0)
                points.addData(0, dataSet.getXData(dataSetSize - 1), dataSet.getYData(dataSetSize - 1), dataSetSize - 1);
        }

        if (!isFilled())
            this.emitPolylineRuns(points, polyItemAction, resolveRenderingHints, skipFirstRayStroke, skipLastRayStroke, PolyItem.DRAW_MODE_POLYLINE,
                    splitIntoSegments);
        else {
            this.emitPolylineRuns(points, polyItemAction, resolveRenderingHints, skipFirstRayStroke, skipLastRayStroke, PolyItem.DRAW_MODE_POLYGON,
                    splitIntoSegments);
            this.emitPolylineRuns(points, polyItemAction, resolveRenderingHints, skipFirstRayStroke, skipLastRayStroke, PolyItem.DRAW_MODE_OUTLINE,
                    false);
        }
    }

    /// Emits contiguous polyline runs to `action`.
    ///
    /// Runs split when the y-value becomes undefined, when the resolved style changes, or when the
    /// caller requests segment-by-segment emission for distance calculations.
    void emitPolylineRuns(SingleChartRenderer.Points points, SinglePolylineRenderer.PolyItemAction action,
                          boolean resolveRenderingHints, boolean skipFirstRayStroke, boolean skipLastRayStroke,
                          int drawMode, boolean splitIntoSegments) {
        double[] xData = points.getXData();
        double[] yData = points.getYData();
        int[] indices = points.getIndices();
        int pointCount = points.size();
        DataSet dataSet = points.getDataSet();
        DisplayPoint displayPoint = new DisplayPoint(this, dataSet);
        PlotStyle baseStyle = super.getStyle();
        Double undefinedValue = dataSet.getUndefValue();
        double undefinedSentinel = (undefinedValue == null) ? 0.0 : undefinedValue.doubleValue();

        SinglePolylineRenderer.PolyItem item = this.createPolyItem(pointCount, drawMode);
        item.setSkipFirstRayStroke(skipFirstRayStroke);
        item.setSkipLastRayStroke(skipLastRayStroke);

        PlotStyle runStyle = baseStyle;
        int runStart = 0;
        while (runStart < pointCount) {
            if (resolveRenderingHints) {
                displayPoint.dataSet = dataSet;
                displayPoint.set(indices[runStart], 0.0, 0.0);
                DataRenderingHint renderingHint = super.getRenderingHint(displayPoint);
                runStyle = (renderingHint == null) ? baseStyle : renderingHint.getStyle(displayPoint, baseStyle);
            }

            double y = yData[runStart];
            if (runStyle != null && (undefinedValue == null || y != undefinedSentinel) && !Double.isNaN(y))
                break;
            runStart++;
        }

        item.setFirstInSeries(true);
        PlotStyle nextStyle = runStyle;
        boolean styleChanged = false;
        for (int index = runStart + 1; index < pointCount; index++) {
            if (resolveRenderingHints) {
                displayPoint.dataSet = dataSet;
                displayPoint.set(indices[index], 0.0, 0.0);
                DataRenderingHint renderingHint = super.getRenderingHint(displayPoint);
                if (renderingHint != null) {
                    nextStyle = renderingHint.getStyle(displayPoint, baseStyle);
                    styleChanged = nextStyle == null || !nextStyle.equals(runStyle);
                } else if (runStyle == baseStyle)
                    styleChanged = false;
                else {
                    nextStyle = baseStyle;
                    styleChanged = true;
                }
            }

            double y = yData[index];
            boolean undefinedPoint = nextStyle == null
                    || (undefinedValue != null && y == undefinedSentinel)
                    || Double.isNaN(y);
            if (!splitIntoSegments && !styleChanged && !undefinedPoint)
                continue;

            item.setLastInSeries(undefinedPoint);
            item.setSize(undefinedPoint ? index - runStart : index - runStart + 1);
            item.sourcePointCount = item.size();
            item.setStartIndex(runStart);
            if (item.size() >= action.minPolylineItemPoints() && runStyle != null) {
                System.arraycopy(xData, runStart, item.getXValues(), 0, item.size());
                System.arraycopy(yData, runStart, item.getYValues(), 0, item.size());
                item.setFirstInDataSet(runStart == 0 && indices[0] == 0);
                item.setLastInDataSet(false);
                action.processItem(points, runStart, item, runStyle);
            }

            if (styleChanged)
                runStyle = nextStyle;
            runStart = undefinedPoint ? index + 1 : index;
            item.setFirstInSeries(undefinedPoint);
        }

        item.setLastInSeries(true);
        item.setSize(pointCount - runStart);
        item.setStartIndex(runStart);
        item.sourcePointCount = item.size();
        if (item.size() >= action.minPolylineItemPoints() && runStyle != null) {
            System.arraycopy(xData, runStart, item.getXValues(), 0, item.size());
            System.arraycopy(yData, runStart, item.getYValues(), 0, item.size());
            item.setFirstInDataSet(runStart == 0 && indices[0] == 0);
            item.setLastInDataSet(true);
            action.processItem(points, runStart, item, runStyle);
        }
        item.dispose();
    }

    /// {@inheritDoc}
    ///
    /// Markers are painted after the line pass so they sit on top of the stroke. Per-point
    /// rendering hints may still enable markers even when the renderer-level marker is disabled.
    @Override
    void drawItems(Graphics g, SingleChartRenderer.Points points) {
        super.drawItems(g, points);
        if (hasNoMarker(getMarker()) && !super.hasRenderingHints())
            return;

        Rectangle clip = g.getClipBounds();
        clip.grow(getMarkerSize(), getMarkerSize());
        g.setClip(clip.x, clip.y, clip.width, clip.height);
        getMarkerRenderer().drawItems(g, points);
    }

    @Override
    Rectangle prepareSliceRect(Rectangle clipRect) {
        if (!hasNoMarker(getMarker()))
            clipRect.grow(getMarkerSize() + 1, getMarkerSize() + 1);
        return clipRect;
    }

    /// {@inheritDoc}
    ///
    /// When label layout mode `2` is active, labels are offset away from the next visible point so
    /// rising segments place the label below the marker and falling or flat segments keep it above.
    @Override
    public Point computeDataLabelLocation(DisplayPoint point, Dimension labelSize) {
        DisplayPoint mappedPoint = point;
        DisplayPoint originalPoint = mappedPoint;
        DataSet dataSet = mappedPoint.getDataSet();
        VirtualDataSet virtualDataSet = DataSetRendererProperty.getVirtualDataSet(this, dataSet);
        if (virtualDataSet != null) {
            mappedPoint = (DisplayPoint) mappedPoint.clone();
            virtualDataSet.map(mappedPoint);
            dataSet = virtualDataSet;
        }

        if (super.getDataLabelLayout() != 2)
            return super.computeDataLabelLocation(originalPoint, labelSize);

        double labelOffset = hasNoMarker(getMarker()) ? 3.0 : 3 + getMarkerSize();
        int index = mappedPoint.getIndex();
        if (index > 0 && index < dataSet.size() - 1 && dataSet.getYData(index) < dataSet.getYData(index + 1))
            labelOffset = -labelOffset;

        DoublePoint labelAnchor = new DoublePoint(mappedPoint.getXCoord(), mappedPoint.getYCoord());
        return super.computeShiftedLabelLocation(labelAnchor, labelSize, labelOffset, true);
    }

    /// {@inheritDoc}
    ///
    /// The legend swatch is a horizontal line segment. When markers are enabled, the corresponding
    /// marker glyph is painted at the center of the swatch.
    @Override
    public void drawLegendMarker(LegendEntry legend, Graphics g, int x, int y, int w, int h) {
        PlotStyle legendStyle = super.getLegendStyle();
        Paint strokePaint = legendStyle.getStrokePaint();
        if (strokePaint instanceof AdaptablePaint adaptablePaint && adaptablePaint.isAdapting()) {
            Rectangle userBounds = new Rectangle(x, y, w, h);
            try {
                ((Graphics2D) g).setRenderingHint(AdaptablePaint.KEY_USER_BOUNDS, userBounds);
                legendStyle.drawLine(g, x, y + h / 2, x + w, y + h / 2);
            } finally {
                ((Graphics2D) g).setRenderingHint(AdaptablePaint.KEY_USER_BOUNDS, null);
            }
        } else
            legendStyle.drawLine(g, x, y + h / 2, x + w, y + h / 2);

        this.drawLegendMarkerOverlay(legendStyle, g, x, y, w, h);
    }

    /// Requests one extra data sample on each side of the visible x-window so runs that cross the
    /// slice boundary can stay connected.
    @Override
    int getDisplayQueryPadding() {
        return 1;
    }

    /// Returns the marker template used for the overlay renderer.
    ///
    /// `null` and `Marker.NONE` both mean that the line has no renderer-level marker, although
    /// per-point rendering hints may still introduce markers.
    public final Marker getMarker() {
        return getMarkerRenderer().getMarker();
    }

    /// Returns the marker size used by the overlay renderer.
    public final int getMarkerSize() {
        return getMarkerRenderer().getMarkerSize();
    }

    /// Returns the explicit marker style, or `null` when markers should derive their style from
    /// the line itself.
    public PlotStyle getMarkerStyle() {
        return c;
    }

    /// {@inheritDoc}
    ///
    /// Stacked parent renderers suppress z-annotation text because one text label would no longer
    /// identify one source series. Standalone and superimposed polylines reuse the default legend
    /// text.
    @Override
    public String getZAnnotationText() {
        PolylineChartRenderer parentRenderer = getParentPolylineRenderer();
        if (parentRenderer != null && parentRenderer.getMode() != PolylineChartRenderer.SUPERIMPOSED)
            return null;
        return super.getDefaultLegendText();
    }

    @Override
    public boolean isFilled() {
        return false;
    }

    /// Returns the bounds collector that expands polyline bounds to include marker outsets.
    @Override
    SingleChartRenderer.BoundsItemAction createBoundsItemAction() {
        return new SinglePolylineRenderer.PolyBoundsAction();
    }

    /// Lazily creates the helper renderer used for marker painting and picking.
    final MarkerRenderer getMarkerRenderer() {
        if (d == null)
            d = new SinglePolylineRenderer.MarkerRenderer();
        return d;
    }

    /// Updates the renderer-level marker template.
    ///
    /// Switching between two "no marker" states does not trigger a repaint, but any change that
    /// toggles visible marker geometry does.
    public void setMarker(Marker marker) {
        Marker currentMarker = getMarkerRenderer().getMarker();
        if (marker != currentMarker) {
            getMarkerRenderer().setMarker(marker);
            if (hasNoMarker(marker) && hasNoMarker(currentMarker))
                return;
            super.triggerChange(4);
        }
    }

    /// Updates the renderer-level marker size.
    ///
    /// Size changes repaint only when markers are currently visible at the renderer level.
    public void setMarkerSize(int markerSize) {
        if (markerSize != getMarkerRenderer().getMarkerSize()) {
            getMarkerRenderer().setMarkerSize(markerSize);
            if (!hasNoMarker(getMarker()))
                super.triggerChange(4);
        }
    }

    /// Sets the explicit marker style.
    ///
    /// Passing `null` clears the override and restores the marker-style derivation from the line
    /// style.
    public void setMarkerStyle(PlotStyle markerStyle) {
        if (markerStyle == c && markerStyle == getMarkerRenderer().getStyle())
            return;
        c = markerStyle;
        getMarkerRenderer().setStyle(markerStyle);
        super.triggerChange(4);
    }

    /// Returns the owning polyline-family renderer when this instance is used as one child of a
    /// [PolylineChartRenderer].
    PolylineChartRenderer getParentPolylineRenderer() {
        return (super.getParent() instanceof PolylineChartRenderer parentRenderer) ? parentRenderer : null;
    }
}
