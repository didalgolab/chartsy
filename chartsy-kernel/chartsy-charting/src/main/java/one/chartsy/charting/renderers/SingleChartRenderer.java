package one.chartsy.charting.renderers;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import one.chartsy.charting.Axis;
import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartDataPicker;
import one.chartsy.charting.ChartProjector;
import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.ChartRendererLegendItem;
import one.chartsy.charting.ColorData;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.DoublePoint;
import one.chartsy.charting.DoublePoints;
import one.chartsy.charting.LegendEntry;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.AbstractDataSet;
import one.chartsy.charting.data.DataPoints;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSetPoint;
import one.chartsy.charting.data.DataSetProperty;
import one.chartsy.charting.data.DataSource;
import one.chartsy.charting.event.DataSetContentsEvent;
import one.chartsy.charting.event.DataSetListener;
import one.chartsy.charting.event.DataSetPropertyEvent;
import one.chartsy.charting.graphic.DataAnnotation;
import one.chartsy.charting.graphic.DataRenderingHint;
import one.chartsy.charting.renderers.internal.DataSetRendererProperty;
import one.chartsy.charting.renderers.internal.DefaultedRenderingModifierArray;
import one.chartsy.charting.renderers.internal.VirtualDataSet;
import one.chartsy.charting.util.GraphicUtil;
import one.chartsy.charting.util.IntInterval;
import one.chartsy.charting.util.IntIntervalSet;
import one.chartsy.charting.util.MathUtil;

/// Shared base for renderers that turn one logical dataset at a time into chart geometry.
///
/// The class centralizes the work that most single-series renderers share:
/// - loading the currently relevant [DataPoints] batch from the primary dataset
/// - exposing that batch through a temporary [Points] view that can keep both source-space data and
///   lazily projected display coordinates available at the same time
/// - enumerating renderer-specific logical items through
///   {@link #forEachItem(SingleChartRenderer.Points, SingleChartRenderer.ItemAction)} so drawing, bounds
///   calculation, and item picking can reuse one traversal
/// - layering dataset-wide and point-specific [DataRenderingHint] and [DataAnnotation] overrides on
///   top of the base [PlotStyle]
///
/// Source datasets may be wrapped in a [VirtualDataSet] for painting and editing, but picking and
/// point lookup translate results back to the caller-visible dataset and index before returning a
/// [DisplayPoint].
///
/// Concrete subclasses such as [SingleScatterRenderer], [SinglePolylineRenderer],
/// [SingleBarRenderer], [SingleHiLoRenderer], and [SinglePieRenderer] usually customize only the
/// logical item traversal and a few geometry-specific hooks.
///
/// Instances are mutable renderer models and are not thread-safe.
///
/// ### Implementation Requirements
///
/// Subclasses must make
/// {@link #forEachItem(SingleChartRenderer.Points, SingleChartRenderer.ItemAction)} enumerate items
/// consistently across painting, bounds collection, and picking. If one logical item represents
/// multiple source points, the callback index should still identify a stable leading point that
/// callers can use for follow-up lookups such as annotations and hit results.
public abstract class SingleChartRenderer extends ChartRenderer {

    /// Collects display bounds while logical items are being enumerated.
    ///
    /// [SingleChartRenderer] reuses the normal item traversal for bounds calculation by sending a
    /// `BoundsItemAction` through
    /// {@link #forEachItem(SingleChartRenderer.Points, SingleChartRenderer.ItemAction)}. Implementations keep
    /// any scratch state they need and merge each processed item's contribution into the rectangle
    /// later returned by [#getBounds()].
    abstract class BoundsItemAction implements SingleChartRenderer.ItemAction {

        BoundsItemAction() {
        }

        @Override
        public void endProcessItems() {
        }

        /// Returns the accumulated display-space bounds.
        public abstract Rectangle2D getBounds();

        /// Merges one processed item's contribution into the accumulated bounds.
        protected abstract void accumulateItemBounds(
                SingleChartRenderer.Points points,
                int itemIndex,
                SingleChartRenderer.Item item,
                PlotStyle style);

        @Override
        public int minPolylineItemPoints() {
            return 2;
        }

        @Override
        public void processItem(SingleChartRenderer.Points points, int itemIndex, SingleChartRenderer.Item item, PlotStyle style) {
            accumulateItemBounds(points, itemIndex, item, style);
        }

        @Override
        public void startProcessItems() {
        }
    }

    /// Default bounds collector that unions each item's own geometry bounds.
    ///
    /// This implementation delegates to [Item#getBounds(PlotStyle, boolean, Rectangle2D)] with
    /// expansion disabled and folds the returned rectangle into one mutable accumulator. It is
    /// appropriate when item bounds already cover the full painted extent. Subclasses can extend it
    /// when extra paint, such as markers, sits outside the raw item geometry.
    class DefaultBoundsItemAction extends SingleChartRenderer.BoundsItemAction {
        Rectangle2D bounds;
        Rectangle2D itemBounds;

        DefaultBoundsItemAction() {
            super();
            bounds = new Rectangle2D.Double();
            itemBounds = null;
        }

        @Override
        public Rectangle2D getBounds() {
            return bounds;
        }

        @Override
        protected void accumulateItemBounds(
                SingleChartRenderer.Points points,
                int itemIndex,
                SingleChartRenderer.Item item,
                PlotStyle style) {
            itemBounds = item.getBounds(style, false, itemBounds);
            bounds = GraphicUtil.addToRect(bounds, itemBounds);
        }
    }

    /// Default nearest-item accumulator for renderers whose picked location is the item's anchor
    /// point.
    ///
    /// Each processed item contributes its hit-test result through [Item#distance(PlotStyle,
    /// double, double, boolean)]. When the action is configured to measure distance, misses turn
    /// into positive distances and the nearest surviving item wins. Otherwise the action behaves
    /// like a pure hit test and keeps only items that contain the pick anchor.
    class DefaultDistanceItemAction extends SingleChartRenderer.DistanceItemAction {
        private final boolean measureDistance;
        private double distance;
        private DisplayPoint pickedPoint;

        public DefaultDistanceItemAction(ChartDataPicker dataPicker, boolean measureDistance) {
            super(dataPicker);
            this.measureDistance = measureDistance;
            distance = Double.POSITIVE_INFINITY;
            pickedPoint = null;
        }

        @Override
        public DisplayPoint getPickedPoint() {
            return pickedPoint;
        }

        @Override
        public double getDistance() {
            return distance;
        }

        @Override
        public void processItem(SingleChartRenderer.Points points, int itemIndex, SingleChartRenderer.Item item, PlotStyle style) {
            double itemDistance = item.distance(style, super.getPickX(), super.getPickY(), measureDistance);
            if (itemDistance < distance) {
                distance = itemDistance;
                pickedPoint = new DisplayPoint(SingleChartRenderer.this,
                        points.getDataSet(), points.getDataIndex(itemIndex), points.getXCoord(itemIndex), points.getYCoord(itemIndex));
            }
        }
    }

    /// Default draw action that lets each logical item paint itself with the resolved style.
    ///
    /// This is the normal draw path for item types whose traversal already prepares all geometry in
    /// display coordinates.
    class DefaultDrawItemAction extends SingleChartRenderer.DrawItemAction {

        DefaultDrawItemAction() {
            super();
        }

        @Override
        protected void drawItem(SingleChartRenderer.Points points, int pointIndex, SingleChartRenderer.Item item,
                                PlotStyle style) {
            item.draw(super.getGraphics(), style);
        }
    }

    /// Reusable {@link Item} implementation backed directly by this item's {@link DoublePoints}
    /// buffers.
    ///
    /// The default behavior treats the stored coordinates as a polyline when the owning renderer is
    /// not filled and as a point cloud otherwise. Subclasses reuse the same storage when they need
    /// custom drawing or hit-testing semantics without introducing another geometry container.
    class DefaultItem extends DoublePoints implements SingleChartRenderer.Item {

        /// Creates an empty item buffer.
        public DefaultItem() {
        }

        /// Creates an item buffer sized for the expected number of vertices.
        public DefaultItem(int pointCapacity) {
            super(pointCapacity);
        }

        /// Computes the display-space hit distance using the same point or polyline interpretation
        /// that {@link #draw(Graphics, PlotStyle)} uses.
        @Override
        public double distance(PlotStyle style, double x, double y, boolean outlineOnly) {
            return (!isFilled())
                    ? style.distanceToPolyline(super.getXValues(), super.getYValues(), super.size(), x, y, outlineOnly)
                    : style.distanceToPoints(super.getXValues(), super.getYValues(), super.size(), x, y, outlineOnly);
        }

        /// Paints the stored vertices as either a polyline or discrete points, matching the owning
        /// renderer's fill model.
        @Override
        public void draw(Graphics g, PlotStyle style) {
            if (!isFilled())
                style.drawPolyline(g, super.getXValues(), super.getYValues(), super.size());
            else
                style.plotPoints(g, super.getXValues(), super.getYValues(), super.size());
        }

        /// Returns the display-space bounds of the stored vertices.
        ///
        /// The supplied rectangle is reused when possible so repeated bounds queries can avoid
        /// allocating short-lived objects.
        @Override
        public Rectangle2D getBounds(PlotStyle style, boolean expand, Rectangle2D bounds) {
            return style.getBounds(super.getXValues(), super.getYValues(), super.size(), true, expand, bounds);
        }
    }

    /// Base callback for traversals that resolve a picked or nearest item.
    ///
    /// Each instance is tied to one {@link ChartDataPicker}. Implementations inspect every emitted
    /// {@link Item}, keep the best match for that picker, and later expose both the winning
    /// {@link DisplayPoint} and its distance from the pick anchor.
    abstract class DistanceItemAction implements SingleChartRenderer.ItemAction {
        private final ChartDataPicker dataPicker;

        /// Creates a picking callback bound to a single picker request.
        public DistanceItemAction(ChartDataPicker dataPicker) {
            this.dataPicker = dataPicker;
        }

        @Override
        public void endProcessItems() {
        }

        /// Returns the picker request that supplies the current pick anchor.
        public final ChartDataPicker getDataPicker() {
            return dataPicker;
        }

        /// Returns the best pick result accumulated so far.
        ///
        /// {@code null} indicates that no emitted item has matched yet.
        public abstract DisplayPoint getPickedPoint();

        /// Returns the distance for the current best match.
        ///
        /// Callers typically interpret {@link Double#POSITIVE_INFINITY} as "no match".
        public abstract double getDistance();

        /// Returns the pick x-coordinate in display space.
        public final int getPickX() {
            return dataPicker.getPickX();
        }

        /// Returns the pick y-coordinate in display space.
        public final int getPickY() {
            return dataPicker.getPickY();
        }

        @Override
        public int minPolylineItemPoints() {
            return 2;
        }

        /// Consumes one emitted item and updates the current best pick state when appropriate.
        @Override
        public abstract void processItem(SingleChartRenderer.Points points, int pointIndex,
                                         SingleChartRenderer.Item item, PlotStyle style);

        @Override
        public void startProcessItems() {
        }
    }

    /// Base callback for traversals that paint emitted items into a prepared graphics context.
    ///
    /// The renderer injects the active {@link Graphics} once before traversal starts. Subclasses
    /// normally implement {@link #drawItem(SingleChartRenderer.Points, int, SingleChartRenderer.Item, PlotStyle)}
    /// and keep the lifecycle methods unchanged.
    abstract class DrawItemAction implements SingleChartRenderer.ItemAction {
        Graphics graphics;

        DrawItemAction() {
        }

        /// Paints one emitted item using the current graphics context.
        protected abstract void drawItem(SingleChartRenderer.Points points, int pointIndex,
                                         SingleChartRenderer.Item item, PlotStyle style);

        @Override
        public void endProcessItems() {
        }

        /// Returns the graphics context assigned to this traversal.
        public final Graphics getGraphics() {
            return graphics;
        }

        @Override
        public int minPolylineItemPoints() {
            return 2;
        }

        @Override
        public void processItem(SingleChartRenderer.Points points, int pointIndex, SingleChartRenderer.Item item,
                                PlotStyle style) {
            drawItem(points, pointIndex, item, style);
        }

        /// Supplies the graphics context that subsequent {@link #drawItem(SingleChartRenderer.Points, int, SingleChartRenderer.Item, PlotStyle)}
        /// calls should paint into.
        public void setGraphics(Graphics g) {
            graphics = g;
        }

        @Override
        public void startProcessItems() {
        }
    }

    /// Represents one renderer-specific logical item after its geometry has been materialized in
    /// display space.
    ///
    /// An item may correspond to one source point, a contiguous run of points, or a generated
    /// shape such as a pie slice. Drawing, bounds, and hit-testing must remain consistent so
    /// painting, clipping, and picking all observe the same geometry.
    interface Item {

        /// Returns the display-space distance from this item to the supplied pick anchor.
        double distance(PlotStyle style, double x, double y, boolean outlineOnly);

        /// Paints this item with the already resolved style.
        void draw(Graphics g, PlotStyle style);

        /// Returns this item's display-space bounds.
        ///
        /// The supplied rectangle may be reused as a scratch object, and `expand` requests that the
        /// returned bounds include any style-specific outsets such as stroke thickness.
        Rectangle2D getBounds(PlotStyle style, boolean expand, Rectangle2D bounds);

    }

    /// Consumes logical items emitted by {@link SingleChartRenderer} traversal.
    ///
    /// Traversal calls {@link #startProcessItems()} once, then
    /// {@link #processItem(SingleChartRenderer.Points, int, SingleChartRenderer.Item, PlotStyle)}
    /// for each accepted item, and finally {@link #endProcessItems()}. Polyline-based renderers
    /// also consult {@link #minPolylineItemPoints()} before emitting a run so callbacks can reject
    /// degenerate fragments.
    interface ItemAction {

        /// Finishes the traversal after the last item has been emitted.
        void endProcessItems();

        /// Returns the minimum number of vertices a polyline-style item must contain before it
        /// should be emitted to this callback.
        int minPolylineItemPoints();

        /// Consumes one emitted item.
        ///
        /// `pointIndex` identifies the leading source point for the logical item, even when the
        /// item itself spans multiple source points.
        void processItem(SingleChartRenderer.Points points, int pointIndex, SingleChartRenderer.Item item,
                         PlotStyle style);

        /// Initializes any per-traversal state before the first item is emitted.
        void startProcessItems();
    }

    /// Iterates a [Points] batch while skipping samples that cannot produce drawable geometry.
    ///
    /// The iterator advances only over indices whose y-value is neither `Double.NaN` nor the
    /// dataset-specific undefined sentinel. It can also resolve the effective [PlotStyle] for the
    /// current point when per-point [DataRenderingHint] overrides are active.
    ///
    /// Current in-tree renderers mostly use explicit loops, but this helper remains the shared
    /// contract for subclasses that want one place to handle undefined-value filtering and style
    /// lookup.
    abstract class ItemIterator {
        private int currentIndex;
        private int nextIndex;
        private final SingleChartRenderer.Points points;
        private final boolean resolveRenderingHints;
        private final Double undefinedValue;
        private final double undefinedSentinel;

        /// Creates an iterator over `points`.
        public ItemIterator(SingleChartRenderer.Points points) {
            nextIndex = -1;
            this.points = points;
            resolveRenderingHints = hasRenderingHints();
            undefinedValue = points.getDataSet().getUndefValue();
            undefinedSentinel = (undefinedValue == null) ? 0.0 : undefinedValue.doubleValue();
        }

        /// Advances to the next point whose y-value should participate in renderer traversal.
        private boolean advanceToNextDefinedPoint() {
            for (nextIndex++; nextIndex < points.size(); nextIndex++) {
                double y = points.getYData(nextIndex);
                if (undefinedValue != null && y == undefinedSentinel)
                    continue;
                if (!Double.isNaN(y))
                    return true;
            }
            return false;
        }

        /// Returns the current point index after a successful [#next()] call.
        public int getCurrentIndex() {
            return currentIndex;
        }

        /// Returns the item instance backed by the current index.
        public abstract SingleChartRenderer.Item getItem();

        /// Returns the point batch being iterated.
        public SingleChartRenderer.Points getPoints() {
            return points;
        }

        /// Returns the effective style for the current point.
        ///
        /// When rendering hints are present, the returned style already includes any per-point
        /// override computed for the current [DisplayPoint].
        public PlotStyle getStyle() {
            PlotStyle style = SingleChartRenderer.this.getStyle();
            if (resolveRenderingHints) {
                DisplayPoint point = points.getDisplayPoint(getCurrentIndex());
                DataRenderingHint renderingHint = SingleChartRenderer.this.getRenderingHint(point);
                if (renderingHint != null)
                    return renderingHint.getStyle(point, style);
            }
            return style;
        }

        /// Returns whether another defined point is available.
        public boolean hasNext() {
            return advanceToNextDefinedPoint();
        }

        /// Commits the point found by the last successful [#hasNext()] call.
        public void next() {
            if (nextIndex >= points.size())
                throw new NoSuchElementException();
            currentIndex = nextIndex;
        }
    }

    /// Tracks palette changes on the owning chart while this renderer uses an auto-generated style.
    ///
    /// The listener only cares about the chart's `defaultColors` property. When that palette
    /// changes, the outer renderer regenerates its default style so sibling single-series renderers
    /// keep distinct automatic colors.
    private class MyChartListener implements PropertyChangeListener, Serializable {

        private MyChartListener() {
        }

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if (event.getSource() == getChart())
                if ("defaultColors".equals(event.getPropertyName()))
                    refreshAutoStyle();
        }
    }

    /// Forwards dataset events to the outer renderer and coalesces changes reported inside one
    /// dataset batch.
    ///
    /// The outer renderer still receives the original batch markers through
    /// [SingleChartRenderer#dataSetContentsChanged(DataSetContentsEvent)], but the deferred dynamic
    /// styling refreshes are held until the batch closes and then replayed either once as a full
    /// update or once per merged changed interval. Range-based changes are merged in an
    /// [IntIntervalSet], and any `FULL_UPDATE` escalates the entire batch to one full refresh.
    private class MyDataSetListener implements DataSetListener, Serializable {
        private DataSetContentsEvent batchBeginEvent;
        private boolean fullUpdatePending;
        private IntIntervalSet changedIntervals;

        private MyDataSetListener() {
        }

        /// Replays the deferred dynamic-styling refreshes collected for the current batch.
        void flushBatchedChanges() {
            if (batchBeginEvent != null) {
                DataSet dataSet = batchBeginEvent.getDataSet();
                boolean emitFullUpdate = fullUpdatePending;
                IntIntervalSet intervals = changedIntervals;
                batchBeginEvent = null;
                fullUpdatePending = false;
                changedIntervals = null;
                if (emitFullUpdate)
                    SingleChartRenderer.this.handleDynamicStylingChange(new DataSetContentsEvent(dataSet));
                else if (intervals != null) {
                    Iterator<IntInterval> iterator = intervals.intervalIterator();
                    while (iterator.hasNext()) {
                        IntInterval interval = iterator.next();
                        SingleChartRenderer.this.handleDynamicStylingChange(new DataSetContentsEvent(
                                dataSet,
                                DataSetContentsEvent.DATA_CHANGED,
                                interval.getFirst(),
                                interval.getLast()));
                    }
                }
            }
        }

        /// Adds one contents event to the current batch summary.
        void recordBatchedChange(DataSetContentsEvent event) {
            switch (event.getType()) {
                case DataSetContentsEvent.FULL_UPDATE:
                    fullUpdatePending = true;
                    break;

                default:
                    if (changedIntervals == null)
                        changedIntervals = new IntIntervalSet();
                    changedIntervals.add(event.getFirstIdx(), event.getLastIdx());
                    break;
            }
        }

        @Override
        public void dataSetContentsChanged(DataSetContentsEvent event) {
            try {
                activeDataSetListener = this;
                SingleChartRenderer.this.dataSetContentsChanged(event);
            } finally {
                activeDataSetListener = null;
            }
        }

        @Override
        public void dataSetPropertyChanged(DataSetPropertyEvent event) {
            SingleChartRenderer.this.dataSetPropertyChanged(event);
        }
    }

    /// Wraps one [DataPoints] batch and lazily exposes both source-space values and display-space
    /// coordinates.
    ///
    /// The wrapper can either project the supplied buffer in place or preserve the original data by
    /// projecting into a secondary [DoublePoints] copy. That lets renderer code mix drawing,
    /// picking, annotation lookup, and virtual-dataset unmapping without repeatedly reloading the
    /// same batch from the dataset.
    final class Points {
        private final DataPoints sourcePoints;
        private final DataSet dataSet;
        private final boolean preserveSourceData;
        private boolean projectionPrepared;
        private DoublePoints projectedPoints;

        /// Creates a wrapper around `dataPoints`.
        ///
        /// @param dataPoints         batch loaded from the current dataset
        /// @param preserveSourceData `true` to keep the original x/y values intact after projection
        public Points(DataPoints dataPoints, boolean preserveSourceData) {
            projectionPrepared = false;
            sourcePoints = dataPoints;
            dataSet = dataPoints.getDataSet();
            this.preserveSourceData = preserveSourceData;
        }

        /// Projects this batch into display space on first demand.
        void ensureProjected() {
            if (sourcePoints != null)
                if (!preserveSourceData) {
                    SingleChartRenderer.this.toDisplay(sourcePoints);
                    projectedPoints = sourcePoints;
                } else {
                    projectedPoints = new DoublePoints();
                    projectedPoints.add(sourcePoints);
                    SingleChartRenderer.this.toDisplay(projectedPoints);
                }
            projectionPrepared = true;
        }

        DataSetPoint getDataPoint(int pointIndex) {
            DataSetPoint point = new DataSetPoint(getDataSet(), getDataIndex(pointIndex));
            if (getDataSet() instanceof VirtualDataSet)
                ((VirtualDataSet) getDataSet()).unmap(point);
            return point;
        }

        /// Creates a [DisplayPoint] view for one logical point in this batch.
        private DisplayPoint createDisplayPoint(int pointIndex, boolean unmapVirtualDataSet,
                                                boolean includeDisplayCoordinates) {
            double xCoord = includeDisplayCoordinates ? getXCoord(pointIndex) : 0.0;
            double yCoord = includeDisplayCoordinates ? getYCoord(pointIndex) : 0.0;
            DisplayPoint point = new DisplayPoint(SingleChartRenderer.this, dataSet,
                    getDataIndex(pointIndex), xCoord, yCoord);
            if (unmapVirtualDataSet)
                if (dataSet instanceof VirtualDataSet)
                    ((VirtualDataSet) dataSet).unmap(point);
            return point;
        }

        public void addData(double x, double y, int dataIndex) {
            sourcePoints.add(x, y, dataIndex);
        }

        public void addData(double[] xValues, double[] yValues, int[] dataIndices, int length) {
            sourcePoints.add(xValues, yValues, dataIndices, length);
        }

        public void addData(int insertIndex, double x, double y, int dataIndex) {
            sourcePoints.add(insertIndex, new double[]{x}, new double[]{y}, new int[]{dataIndex}, 1);
        }

        public void addData(int insertIndex, double[] xValues, double[] yValues, int[] dataIndices, int length) {
            sourcePoints.add(insertIndex, xValues, yValues, dataIndices, length);
        }

        /// Disposes the wrapped batch and any detached projected copy.
        void dispose() {
            if (sourcePoints != null)
                sourcePoints.dispose();
            if (projectedPoints != null)
                if (projectedPoints != sourcePoints)
                    projectedPoints.dispose();
        }

        public int getDataIndex(int pointIndex) {
            return sourcePoints.getIndices()[pointIndex];
        }

        public DataSet getDataSet() {
            return dataSet;
        }

        /// Returns the current point as a display-space [DisplayPoint].
        public DisplayPoint getDisplayPoint(int pointIndex) {
            return createDisplayPoint(pointIndex, false, true);
        }

        public int[] getIndices() {
            return sourcePoints.getIndices();
        }

        public double getXCoord(int pointIndex) {
            return getXCoords()[pointIndex];
        }

        /// Returns the projected x-coordinate buffer, projecting the batch on first access.
        public double[] getXCoords() {
            if (!isProjected())
                ensureProjected();
            return projectedPoints.getXValues();
        }

        /// Returns source-space x-values for the batch.
        ///
        /// When this wrapper projected the original batch in place, values are reconstructed from
        /// the backing [DataSet] so callers still observe source coordinates rather than display
        /// coordinates.
        public double[] getXData() {
            if (!isProjected())
                return sourcePoints.getXValues();
            if (preserveSourceData)
                return sourcePoints.getXValues();
            int[] indices = getIndices();
            int count = size();
            double[] xValues = new double[count];
            for (int index = 0; index < count; index++)
                xValues[index] = dataSet.getXData(indices[index]);
            return xValues;
        }

        public double getXData(int pointIndex) {
            if (isProjected() && !preserveSourceData)
                return dataSet.getXData(getDataIndex(pointIndex));
            return sourcePoints.getX(pointIndex);
        }

        public double getYCoord(int pointIndex) {
            return getYCoords()[pointIndex];
        }

        /// Returns the projected y-coordinate buffer, projecting the batch on first access.
        public double[] getYCoords() {
            if (!isProjected())
                ensureProjected();
            return projectedPoints.getYValues();
        }

        /// Returns source-space y-values for the batch.
        ///
        /// After an in-place projection, values are reconstructed from the backing dataset for the
        /// same reason as [#getXData()].
        public double[] getYData() {
            if (!projectionPrepared)
                return sourcePoints.getYValues();
            if (preserveSourceData)
                return sourcePoints.getYValues();
            int[] indices = getIndices();
            int count = size();
            double[] yValues = new double[count];
            for (int index = 0; index < count; index++)
                yValues[index] = dataSet.getYData(indices[index]);
            return yValues;
        }

        public double getYData(int pointIndex) {
            if (isProjected() && !preserveSourceData)
                return dataSet.getYData(getDataIndex(pointIndex));
            return sourcePoints.getY(pointIndex);
        }

        /// Returns whether display coordinates have already been prepared.
        public boolean isProjected() {
            return projectedPoints != null;
        }

        public int size() {
            return (sourcePoints == null) ? 0 : sourcePoints.size();
        }

        @Override
        public String toString() {
            if (sourcePoints == null)
                return "(null)";
            return sourcePoints.toString();
        }
    }

    private static final boolean ASSERTIONS_DISABLED = !SingleChartRenderer.class.desiredAssertionStatus();

    /// Installs `virtualDataSet` on `renderer` when it supports `SingleChartRenderer` dataset views.
    ///
    /// Composite renderer configs use this bridge because some children expose the single-renderer
    /// virtual-dataset contract while others do not. Passing `null` restores the source dataset.
    /// The return value reports whether the effective rendered dataset changed for range purposes.
    static boolean setVirtualDataSetIfSupported(ChartRenderer renderer, DataSet dataSet, VirtualDataSet virtualDataSet) {
        if (!(renderer instanceof SingleChartRenderer singleRenderer))
            return false;
        return singleRenderer.setVirtualDataSet(dataSet, virtualDataSet);
    }

    private PlotStyle baseStyle;
    private Map<DataSet, DefaultedRenderingModifierArray> modifiersByDataSet;
    private final SingleChartRenderer.MyChartListener chartListener;
    private transient Stroke defaultStroke;

    private transient boolean autoStyle;

    private SingleChartRenderer.MyDataSetListener activeDataSetListener;

    protected SingleChartRenderer() {
        this(null);
    }

    /// Creates a detached renderer with an optional explicit base style.
    ///
    /// Passing `null` defers style resolution until the renderer is attached to a chart and can
    /// choose a default color that does not collide with sibling renderers.
    protected SingleChartRenderer(PlotStyle style) {
        setStyle(style);
        chartListener = new SingleChartRenderer.MyChartListener();
    }

    /// Creates the action used by item-based hit testing and nearest-item picking.
    ///
    /// `measureDistance` controls whether misses should be reported as distances or rejected
    /// immediately as
    /// non-hits.
    SingleChartRenderer.DistanceItemAction createDistanceItemAction(ChartDataPicker dataPicker, boolean measureDistance) {
        return new SingleChartRenderer.DefaultDistanceItemAction(dataPicker, measureDistance);
    }

    /// Wraps one raw dataset slice in the lazily projected [Points] view used across this class.
    SingleChartRenderer.Points createPointsView(DataPoints dataPoints) {
        return new SingleChartRenderer.Points(dataPoints, true);
    }

    /// Lets subclasses react to dataset-wide or per-point rendering-hint changes.
    ///
    /// The base implementation is a no-op. Subclasses override this only when cached geometry or
    /// auxiliary state depends on more than the repaint triggered by the public setter methods.
    void handleRenderingHintChange(DataRenderingHint previousRenderingHint, DataRenderingHint renderingHint) {
    }

    /// Resolves the dataset that this renderer currently traverses for `dataSet`.
    ///
    /// When the renderer has a [VirtualDataSet] installed for that source dataset, callers must use
    /// the virtual view for display-point lookup, picking, and visible-range slicing. `null`
    /// indicates that `dataSet` is not currently rendered by this renderer.
    DataSet getResolvedDataSet(DataSet dataSet) {
        if (!super.isDisplayingDataSet(dataSet))
            return null;
        VirtualDataSet virtualDataSet = DataSetRendererProperty.getVirtualDataSet(this, dataSet);
        return (virtualDataSet != null) ? virtualDataSet : dataSet;
    }

    /// Returns the modifier holder for `dataSet`.
    ///
    /// The holder stores dataset-wide and point-specific rendering hints and annotations for one
    /// source dataset. It is created lazily so untouched datasets do not pay for an empty modifier
    /// array.
    DefaultedRenderingModifierArray getModifiers(DataSet dataSet, boolean createIfMissing) {
        DefaultedRenderingModifierArray modifiers =
                (modifiersByDataSet == null) ? null : modifiersByDataSet.get(dataSet);
        if (modifiers == null && createIfMissing) {
            modifiers = new DefaultedRenderingModifierArray();
            setModifiers(dataSet, modifiers);
        }
        return modifiers;
    }

    /// Installs or clears the modifier holder associated with `dataSet`.
    void setModifiers(DataSet dataSet, DefaultedRenderingModifierArray modifiers) {
        if (modifiers != null) {
            if (modifiersByDataSet == null)
                modifiersByDataSet = new HashMap<>();
            modifiersByDataSet.put(dataSet, modifiers);
        } else if (modifiersByDataSet != null)
            modifiersByDataSet.remove(dataSet);
    }

    /// Returns the current annotation bounds for one logical item, or `null` when it has none.
    Rectangle2D getAnnotationBounds(DataSet dataSet, int index) {
        DisplayPoint displayPoint = getDisplayPoint(dataSet, index);
        if (displayPoint != null) {
            DataAnnotation annotation = getAnnotation(displayPoint);
            if (annotation != null)
                return annotation.getBounds(displayPoint, null);
        }
        return null;
    }

    /// Returns a dataset slice widened by `contextPadding` samples on both sides.
    ///
    /// Connected renderers use the widened slice so bounds, picking, and painting can still inspect
    /// neighboring samples that influence geometry at the requested endpoints.
    DataPoints getDataSlice(DataSet dataSet, int fromIndex, int toIndex, int contextPadding) {
        int effectiveFromIndex = fromIndex;
        int effectiveToIndex = toIndex;
        if (contextPadding > 0) {
            effectiveFromIndex = effectiveFromIndex - contextPadding;
            if (effectiveFromIndex < 0)
                effectiveFromIndex = 0;
            effectiveToIndex = effectiveToIndex + contextPadding;
            if (effectiveToIndex > dataSet.size() - 1)
                effectiveToIndex = dataSet.size() - 1;
        }
        return dataSet.getDataBetween(effectiveFromIndex, effectiveToIndex);
    }

    /// Installs `virtualDataSet` for `dataSet` and listens to it for contents changes.
    final boolean setVirtualDataSet(DataSet dataSet, VirtualDataSet virtualDataSet) {
        return setVirtualDataSet(dataSet, virtualDataSet, true);
    }

    /// Installs `virtualDataSet` for one source dataset view.
    ///
    /// Single renderers swap their listener between the source dataset and its virtual view. Shared
    /// virtual datasets can therefore bind multiple source datasets while only one binding owns the
    /// virtual-dataset listener, controlled by `attachVirtualListener`.
    boolean setVirtualDataSet(DataSet dataSet, VirtualDataSet virtualDataSet, boolean attachVirtualListener) {
        DataSetRendererProperty rendererProperty = DataSetRendererProperty.getDataSetRendererProperty(this, dataSet, false);
        if (rendererProperty == null || rendererProperty.listener == null)
            throw new IllegalStateException(this + " is not currently connected to " + dataSet);

        VirtualDataSet currentVirtualDataSet = rendererProperty.virtualDataSet;
        if (virtualDataSet == currentVirtualDataSet)
            return false;

        SingleChartRenderer.MyDataSetListener dataSetListener =
                (SingleChartRenderer.MyDataSetListener) rendererProperty.listener;
        if (currentVirtualDataSet != null) {
            if (attachVirtualListener) {
                dataSetListener.flushBatchedChanges();
                currentVirtualDataSet.removeDataSetListener(dataSetListener);
            }
            currentVirtualDataSet.dispose();
        }
        if (currentVirtualDataSet != null && virtualDataSet == null)
            dataSet.addDataSetListener(dataSetListener);

        rendererProperty.virtualDataSet = virtualDataSet;
        if (currentVirtualDataSet == null && virtualDataSet != null) {
            dataSetListener.flushBatchedChanges();
            dataSet.removeDataSetListener(dataSetListener);
        }
        if (virtualDataSet != null && attachVirtualListener)
            virtualDataSet.addDataSetListener(dataSetListener);
        return true;
    }

    /// Lets subclasses respond when dynamic styling depends on dataset contents.
    ///
    /// The base renderer does not derive its base style from live point values, so the default
    /// implementation is intentionally empty.
    void handleDynamicStylingChange(DataSetContentsEvent event) {
    }

    /// Resolves the annotation for one logical point after virtual-dataset unmapping.
    ///
    /// Point-specific overrides win over dataset-wide fallbacks, which in turn win over renderer or
    /// parent-level annotation inheritance.
    DataAnnotation getAnnotation(DataSetPoint point) {
        if (point.getDataSet() instanceof VirtualDataSet)
            ((VirtualDataSet) point.getDataSet()).unmap(point);

        DefaultedRenderingModifierArray modifiers = getModifiers(point.getDataSet(), false);
        if (modifiers != null) {
            DataAnnotation annotation = modifiers.getAnnotation(point.getIndex());
            if (annotation != null)
                return annotation;
        }
        return getAnnotationOrInherit();
    }

    /// Lets subclasses widen or otherwise adjust one visible data window before range clipping.
    DataWindow adjustVisibleWindow(DataWindow window) {
        return window;
    }

    /// Converts an anchor point and signed axis offset into the label's top-left display location.
    ///
    /// The anchor first moves along the requested axis in display space. The final location is then
    /// shifted by half the label size in the same direction so callers can place the full label
    /// just beyond the anchor rather than centering it on the anchor.
    Point computeShiftedLabelLocation(
            DoublePoint anchor,
            Dimension labelSize,
            double offset,
            boolean shiftAlongYAxis) {
        ChartProjector projector = super.getChart().getProjector();
        Rectangle plotRect = super.getPlotRect();
        DoublePoint shiftedAnchor = new DoublePoint(anchor.x, anchor.y);
        Axis axis = shiftAlongYAxis ? getYAxis() : getXAxis();
        projector.shiftAlongAxis(plotRect, axis, shiftedAnchor, offset);

        double angle = GraphicUtil.pointAngleDeg(anchor.x, anchor.y, shiftedAnchor.x, shiftedAnchor.y);
        double cosine = MathUtil.cosDeg(angle);
        double sine = MathUtil.sinDeg(angle);
        double halfWidthDistance = (cosine == 0.0) ? 100000.0 : Math.abs(labelSize.width / (2.0 * cosine));
        double halfHeightDistance = (sine == 0.0) ? 100000.0 : Math.abs(labelSize.height / (2.0 * sine));
        double anchorToCornerDistance = Math.min(halfWidthDistance, halfHeightDistance);
        shiftedAnchor.x += anchorToCornerDistance * cosine;
        shiftedAnchor.y -= anchorToCornerDistance * sine;
        return new Point(shiftedAnchor.xFloor(), shiftedAnchor.yFloor());
    }

    // TODO: use3D is always false
    void drawDataPoints(Graphics g, DataPoints dataPoints, boolean use3D) {
        if (dataPoints == null)
            return;
        SingleChartRenderer.Points points = this.createPointsView(dataPoints);
        if (points.size() > 0)
            this.drawItems(g, points);
        this.disposePoints(points);
    }

    /// Creates a plot style from explicit fill, stroke paint, and stroke geometry.
    ///
    /// Single renderers reuse this helper so default-style creation and subclass overrides follow
    /// the same fallback rules for missing stroke or stroke paint values.
    PlotStyle createStyle(Paint fillPaint, Paint strokePaint, Stroke stroke) {
        Paint resolvedStrokePaint = strokePaint;
        Stroke resolvedStroke = stroke;
        if (resolvedStroke == null)
            resolvedStroke = PlotStyle.DEFAULT_STROKE;
        if (resolvedStrokePaint == null)
            resolvedStrokePaint = Color.black;
        if (!isFilled())
            return new PlotStyle(resolvedStroke, fillPaint);
        return new PlotStyle(resolvedStroke, resolvedStrokePaint, fillPaint);
    }

    /// Applies the renderer's single base style slot.
    ///
    /// Passing `null` while the renderer is attached regenerates an automatic chart-derived style
    /// and marks that slot as auto-managed so chart palette changes can refresh it later.
    private void applyBaseStyle(PlotStyle style) {
        if (style == null && super.getChart() != null) {
            autoStyle = true;
            baseStyle = makeDefaultStyle();
            super.triggerChange(4);
            return;
        }
        autoStyle = false;
        if (style != baseStyle) {
            baseStyle = style;
            super.triggerChange(4);
        }
    }

    /// Returns the dataset slice that intersects the current visible window or `sliceRect`.
    ///
    /// The returned slice already includes the renderer's display-query padding so line-like
    /// renderers can keep continuity across clip boundaries.
    DataPoints getVisibleData(Rectangle sliceRect) {
        DataWindow visibleWindow;
        if (sliceRect == null)
            visibleWindow = super.getCoordinateSystem().getVisibleWindow();
        else {
            visibleWindow = this.computeVisibleDataWindow(this.prepareSliceRect(sliceRect));
            if (visibleWindow.isEmpty())
                return null;
        }
        return getRenderedDataSet().getDataInside(
                visibleWindow,
                getDisplayQueryPadding(),
                keepOutsideYRangeWhenClipping());
    }

    /// Adds every explicit fill and stroke color referenced by `styles` to `usedColors`.
    private void collectStyleColors(Set<Color> usedColors, PlotStyle[] styles) {
        if (styles == null)
            return;
        for (PlotStyle style : styles) {
            Color fillColor = style.getFillColor();
            if (fillColor != null)
                usedColors.add(fillColor);
            Color strokeColor = style.getStrokeColor();
            if (strokeColor != null)
                usedColors.add(strokeColor);
        }
    }

    /// Disposes one temporary [Points] wrapper.
    void disposePoints(SingleChartRenderer.Points points) {
        points.dispose();
    }

    /// Unmaps a picked point back to the source dataset when this renderer paints through a
    /// [VirtualDataSet].
    ///
    /// `null` is returned when unmapping drops the point because the virtual dataset cannot map the
    /// pick result back to a live source index.
    private DisplayPoint unmapDisplayPoint(DisplayPoint point) {
        if (point == null)
            return null;
        if (point.getDataSet() instanceof VirtualDataSet virtualDataSet) {
            virtualDataSet.unmap(point);
            if (point.getIndex() == -1)
                return null;
        }
        return point;
    }

    /// Picks one logical item from `points`.
    ///
    /// The result comes from the renderer's item traversal rather than from raw sample locations,
    /// so line segments, bar bodies, pie slices, and similar higher-level shapes can supply their
    /// own hit-test and distance semantics. `distanceOut[0]` receives
    /// [Double#POSITIVE_INFINITY] when no item matches.
    DisplayPoint pickItem(SingleChartRenderer.Points points, ChartDataPicker dataPicker, boolean measureDistance, double[] distanceOut) {
        if (points.size() == 0) {
            if (distanceOut != null)
                distanceOut[0] = Double.POSITIVE_INFINITY;
            return null;
        }
        SingleChartRenderer.DistanceItemAction distanceAction = this.createDistanceItemAction(dataPicker, measureDistance);
        distanceAction.startProcessItems();
        this.forEachItem(points, distanceAction);
        distanceAction.endProcessItems();
        if (distanceOut != null)
            distanceOut[0] = distanceAction.getDistance();
        return distanceAction.getPickedPoint();
    }

    /// Expands `bounds` with every annotation painted for `points`.
    void addAnnotationBounds(SingleChartRenderer.Points points, Rectangle2D bounds) {
        Rectangle2D annotationBounds = bounds;
        DataSet dataSet = points.getDataSet();
        int pointCount = points.size();
        double[] xCoords = points.getXCoords();
        double[] yCoords = points.getYCoords();
        int[] indices = points.getIndices();
        DisplayPoint displayPoint = new DisplayPoint(this, dataSet);
        Rectangle2D reuseBounds = null;
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            displayPoint.dataSet = dataSet;
            displayPoint.set(indices[pointIndex], xCoords[pointIndex], yCoords[pointIndex]);
            DataAnnotation annotation = this.getAnnotation(displayPoint);
            if (annotation != null) {
                reuseBounds = annotation.getBounds(displayPoint, reuseBounds);
                annotationBounds = GraphicUtil.addToRect(annotationBounds, reuseBounds);
            }
        }
    }

    /// Traverses the current projected data as renderer-specific logical items.
    ///
    /// The same traversal backs painting, bounds calculation, and item picking. Implementations
    /// may emit one item per point, one item per contiguous run, or any other grouping that matches
    /// the renderer's geometry model.
    abstract void forEachItem(SingleChartRenderer.Points points, SingleChartRenderer.ItemAction action);

    /// Creates and registers the listener used for one connected source dataset.
    DataSetListener createDataSetListener(DataSet dataSet) {
        DataSetRendererProperty rendererProperty = DataSetRendererProperty.getDataSetRendererProperty(this, dataSet, true);
        if (rendererProperty.listener == null) {
            SingleChartRenderer.MyDataSetListener dataSetListener = new SingleChartRenderer.MyDataSetListener();
            rendererProperty.listener = dataSetListener;
            return dataSetListener;
        }
        throw new IllegalStateException(this + " is already connected to " + dataSet +
                ". " + "If you really want to connect the same data set multiple times " +
                "to the same renderer, wrap it in an FilterDataSet.");
    }

    /// Returns the renderer's base style before any per-point rendering-hint override is applied.
    PlotStyle getBaseStyle(DataSet dataSet, int dataIndex) {
        return this.getStyle();
    }

    /// Resolves the rendering hint for one logical point after virtual-dataset unmapping.
    DataRenderingHint getRenderingHint(DataSetPoint point) {
        if (point.getDataSet() instanceof VirtualDataSet)
            ((VirtualDataSet) point.getDataSet()).unmap(point);

        DefaultedRenderingModifierArray modifiers = getModifiers(point.getDataSet(), false);
        if (modifiers != null) {
            DataRenderingHint renderingHint = modifiers.getRenderingHint(point.getIndex());
            if (renderingHint != null)
                return renderingHint;
        }
        return getRenderingHintOrInherit();
    }

    /// Draws the logical items represented by `points`.
    ///
    /// The default implementation simply traverses items and lets each item paint itself with the
    /// resolved [PlotStyle]. Subclasses override this when the paint path needs an extra pass, such
    /// as drawing markers after a polyline stroke.
    void drawItems(Graphics g, SingleChartRenderer.Points points) {
        SingleChartRenderer.DefaultDrawItemAction drawAction = createDrawItemAction();
        drawAction.setGraphics(g);
        drawAction.startProcessItems();
        this.forEachItem(points, drawAction);
        drawAction.endProcessItems();
    }

    /// Resolves the rendered dataset view for one dataset index in the current data source.
    DataSet getResolvedDataSet(int dataSetIndex) {
        return getResolvedDataSet(super.getDataSource().get(dataSetIndex));
    }

    /// Expands or normalizes one display-space slice before it is projected back into data space.
    ///
    /// Fast-bounds styles can widen the supplied rectangle in place. Styles without reliable quick
    /// bounds fall back to the full plot rectangle so clipping does not accidentally drop geometry
    /// that paints outside its sample anchors.
    Rectangle prepareSliceRect(Rectangle sliceRect) {
        PlotStyle style = this.getStyle();
        if (!style.quickBounds())
            sliceRect.setBounds(super.getPlotRect());
        else
            style.expand(false, sliceRect);
        return sliceRect;
    }

    /// Returns the existing listener for `dataSet`.
    DataSetListener requireDataSetListener(DataSet dataSet) {
        DataSetRendererProperty rendererProperty = DataSetRendererProperty.getDataSetRendererProperty(this, dataSet, false);
        if (rendererProperty != null && rendererProperty.listener != null)
            return rendererProperty.listener;
        throw new IllegalStateException(this + " is not currently connected to " + dataSet);
    }

    /// Draws every visible annotation attached to `points`.
    ///
    /// Undefined samples are skipped before annotation lookup. Each annotation still computes its
    /// own bounds first so clipping can cheaply reject off-screen annotations without forcing the
    /// annotation implementation to duplicate that check.
    void drawPointAnnotations(Graphics g, SingleChartRenderer.Points points) {
        DataSet dataSet = points.getDataSet();
        int pointCount = points.size();
        double[] xCoords = points.getXCoords();
        double[] yCoords = points.getYCoords();
        int[] indices = points.getIndices();
        DisplayPoint displayPoint = new DisplayPoint(this, dataSet);
        Rectangle2D annotationBounds = null;
        Rectangle clipBounds = g.getClipBounds();
        Double undefinedMarker = dataSet.getUndefValue();
        double undefinedValue = (undefinedMarker == null) ? 0.0 : undefinedMarker.doubleValue();
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            double y = points.getYData(pointIndex);
            if ((undefinedMarker != null && y == undefinedValue) || Double.isNaN(y))
                continue;

            displayPoint.dataSet = dataSet;
            displayPoint.set(indices[pointIndex], xCoords[pointIndex], yCoords[pointIndex]);
            DataAnnotation annotation = this.getAnnotation(displayPoint);
            if (annotation == null)
                continue;

            annotationBounds = annotation.getBounds(displayPoint, annotationBounds);
            if (clipBounds != null
                    && !clipBounds.intersects(annotationBounds.getX(), annotationBounds.getY(),
                    annotationBounds.getWidth(), annotationBounds.getHeight()))
                continue;
            annotation.draw(g, displayPoint);
        }
    }

    /// Converts one prepared display-space slice into the corresponding visible data window.
    ///
    /// Subclass widening runs before the renderer's x-shift is reversed and before both axes clamp
    /// the result to their current visible ranges. Overrides of [#adjustVisibleWindow(DataWindow)]
    /// may mutate the supplied window in place or replace it with another instance.
    DataWindow computeVisibleDataWindow(Rectangle sliceRect) {
        Rectangle plotRect = super.getPlotRect();
        DataWindow window = super.getChart().getProjector().toDataWindow(sliceRect, plotRect, super.getCoordinateSystem());
        window = this.adjustVisibleWindow(window);
        if (super.getXShift() != 0.0)
            window.xRange.translate(-super.getXShift());
        window.xRange.intersection(getXAxis().getVisibleRange());
        window.yRange.intersection(getYAxis().getVisibleRange());
        return window;
    }

    /// Computes the display-space bounds of the logical items in `points`.
    ///
    /// This method measures only the item geometry produced by [#forEachItem(Points, ItemAction)].
    /// Callers that need annotation bounds must add them separately.
    Rectangle2D computeItemBounds(SingleChartRenderer.Points points) {
        SingleChartRenderer.BoundsItemAction boundsAction = createBoundsItemAction();
        boundsAction.startProcessItems();
        this.forEachItem(points, boundsAction);
        boundsAction.endProcessItems();
        return boundsAction.getBounds();
    }

    /// {@inheritDoc}
    ///
    /// Single renderers mirror chart-level property changes into their own styling and annotation
    /// state, so they keep one listener registered on the current owning chart. Connecting also
    /// resolves the lazy default base style once a chart becomes available.
    @Override
    protected void chartConnected(Chart previousChart, Chart newChart) {
        super.chartConnected(previousChart, newChart);
        if (previousChart != null)
            previousChart.removePropertyChangeListener(chartListener);
        if (newChart != null) {
            newChart.addPropertyChangeListener(chartListener);
            if (baseStyle == null)
                applyBaseStyle(null);
        }
    }

    @Override
    protected Iterable<LegendEntry> createLegendEntries() {
        if (isViewable() && isLegended())
            return Collections.singleton(new ChartRendererLegendItem(this));
        else
            return Collections.emptySet();
    }

    /// Returns whether visible-data clipping should keep points selected only for x-range continuity
    /// even when their y-values lie outside the current y-range.
    ///
    /// Line-like renderers keep those points so segments can cross the plot edge without gaps.
    /// Point-like renderers typically return `false` because they have no multi-point geometry to
    /// preserve.
    boolean keepOutsideYRangeWhenClipping() {
        return true;
    }

    public void dataSetContentsChanged(DataSetContentsEvent event) {
        switch (event.getType()) {
            case -2:
                activeDataSetListener.batchBeginEvent = event;
                break;

            case -1:
                activeDataSetListener.flushBatchedChanges();
                break;

            case 1:
                break;

            default:
                if (activeDataSetListener.batchBeginEvent == null) {
                    handleDynamicStylingChange(event);
                    break;
                }
                activeDataSetListener.recordBatchedChange(event);
                break;

        }
        if (super.isViewable())
            super.getChart().dataSetContentsChanged(event, this);
    }

    public void dataSetPropertyChanged(DataSetPropertyEvent event) {
        if ("name".equals(event.getPropertyName()))
            super.triggerChange(6);
    }

    /// {@inheritDoc}
    ///
    /// Single renderers attach one dataset listener per source dataset and synthesize a batch-begin
    /// event for already batched datasets so the owning chart keeps its batching state in sync.
    @Override
    protected void dataSetsAdded(int firstDataSetIndex, int lastDataSetIndex, DataSet[] addedDataSets) {
        for (int dataSetIndex = firstDataSetIndex; dataSetIndex <= lastDataSetIndex; dataSetIndex++) {
            DataSet dataSet = super.getDataSource().get(dataSetIndex);
            dataSet.addDataSetListener(this.createDataSetListener(dataSet));
            if (dataSet instanceof AbstractDataSet abstractDataSet
                    && abstractDataSet.isBatched()
                    && super.isViewable()
                    && super.getChart() != null) {
                super.getChart().dataSetContentsChanged(
                        new DataSetContentsEvent(dataSet, DataSetContentsEvent.BATCH_BEGIN, -1, -1),
                        this);
            }
        }
        super.dataSetsAdded(firstDataSetIndex, lastDataSetIndex, addedDataSets);
    }

    /// {@inheritDoc}
    ///
    /// Removing a dataset clears both modifier and virtual-dataset state, detaches the dataset
    /// listener, and, for batched datasets, synthesizes the matching batch-end event before the
    /// renderer forgets the dataset.
    @Override
    protected void dataSetsRemoved(int firstDataSetIndex, int lastDataSetIndex, DataSet[] removedDataSets) {
        boolean dataRangeChanged = false;
        for (int dataSetIndex = firstDataSetIndex; dataSetIndex <= lastDataSetIndex; dataSetIndex++) {
            DataSet removedDataSet = removedDataSets[dataSetIndex];
            this.setModifiers(removedDataSet, null);
            dataRangeChanged |= this.setVirtualDataSet(removedDataSet, null);
            if (removedDataSet instanceof AbstractDataSet abstractDataSet
                    && abstractDataSet.isBatched()
                    && super.isViewable()
                    && super.getChart() != null) {
                super.getChart().dataSetContentsChanged(
                        new DataSetContentsEvent(removedDataSet, DataSetContentsEvent.BATCH_END, -1, -1),
                        this);
            }
            removedDataSet.removeDataSetListener(this.requireDataSetListener(removedDataSet));
            DataSetRendererProperty.clearDataSetRendererProperty(this, removedDataSet);
        }
        if (dataRangeChanged)
            if (super.getChart() != null)
                super.getChart().updateDataRange();
        super.dataSetsRemoved(firstDataSetIndex, lastDataSetIndex, removedDataSets);
    }

    /// {@inheritDoc}
    ///
    /// The renderer always clips painting to its current chart clip rectangle. During optimized
    /// repaints, only the current graphics clip is projected back into data space; otherwise the
    /// full visible batch is repainted.
    @Override
    public void draw(Graphics g) {
        Shape originalClip = g.getClip();
        Rectangle clipRect = super.getClipRect();
        g.clipRect(clipRect.x, clipRect.y, clipRect.width, clipRect.height);

        Chart chart = super.getChart();
        Rectangle sliceRect = (chart == null || chart.isOptimizedRepaint()) ? g.getClipBounds() : null;
        this.drawDataPoints(g, this.getVisibleData(sliceRect), false);
        g.setClip(originalClip);
    }

    /// Paints one explicit source-dataset slice without consulting the visible window.
    public void draw(Graphics g, DataSet dataSet, int fromIndex, int toIndex) {
        this.drawDataPoints(g, this.getDataSlice(dataSet, fromIndex, toIndex, getDisplayQueryPadding()), false);
    }

    /// {@inheritDoc}
    ///
    /// Annotation painting loads one visible batch, creates the matching points view, and then
    /// reuses the normal point-annotation traversal used by bounds collection and picking.
    @Override
    public void drawAnnotations(Graphics g) {
        if (!holdsAnnotations())
            return;

        DataPoints visibleData = this.getVisibleData(null);
        if (visibleData == null)
            return;

        SingleChartRenderer.Points points = this.createPointsView(visibleData);
        try {
            if (points.size() > 0)
                this.drawPointAnnotations(g, points);
        } finally {
            this.disposePoints(points);
        }
    }

    @Override
    public void drawLegendMarker(LegendEntry legend, Graphics g, int x, int y, int w, int h) {
        int markerExtent = Math.min(w, h);
        super.getLegendStyle().plotRect(g, x + (w - markerExtent) / 2, y + (h - markerExtent) / 2,
                markerExtent, markerExtent);
    }

    /// Returns the dataset instance currently used for painting and geometry queries.
    ///
    /// The result is usually the first source dataset, but renderers that install a
    /// [VirtualDataSet] receive that mapped view instead so drawing, picking, and write-back stay
    /// aligned with the transformed representation.
    DataSet getRenderedDataSet() {
        return this.getResolvedDataSet(getPrimaryDataSet());
    }

    /// Returns the first source dataset owned by this single-series renderer.
    ///
    /// Single renderers treat index `0` as the canonical dataset for legend text, default styling,
    /// and any optional virtual remapping layered on top of the source data.
    final DataSet getPrimaryDataSet() {
        return super.getDataSource().get(0);
    }

    /// Returns the extra sample count fetched on each side of display-oriented data queries.
    ///
    /// Paint, picking, and clip-window extraction may need neighbor samples outside the strict
    /// visible range so connected geometry can cross the slice boundary without gaps.
    int getDisplayQueryPadding() {
        return 0;
    }

    @Override
    public DataAnnotation getAnnotation(DataSet dataSet, int dataIndex) {
        return this.getAnnotation(new DataSetPoint(dataSet, dataIndex));
    }

    /// {@inheritDoc}
    ///
    /// If this renderer projects the requested source dataset through a [VirtualDataSet], bounds
    /// are measured in the rendered dataset's coordinate space before any picked point is unmapped
    /// back to the source. Monotonic virtual mappings can translate only the requested end points;
    /// non-monotonic mappings fall back to remapping each source sample in the slice.
    @Override
    public Rectangle2D getBounds(DataSet dataSet, int firstIndex, int lastIndex, Rectangle2D reuseBounds, boolean includeAnnotations) {
        Rectangle2D bounds = reuseBounds;
        if (bounds == null)
            bounds = new Rectangle2D.Double();
        else
            bounds.setRect(0.0, 0.0, 0.0, 0.0);
        if (super.getChart() == null)
            return bounds;

        DataPoints boundsData;
        if (dataSet instanceof VirtualDataSet) {
            boundsData = this.getDataSlice(dataSet, firstIndex, lastIndex, getBoundsQueryPadding());
        } else {
            if (!super.isDisplayingDataSet(dataSet))
                return null;

            VirtualDataSet virtualDataSet = DataSetRendererProperty.getVirtualDataSet(this, dataSet);
            if (virtualDataSet == null) {
                boundsData = this.getDataSlice(dataSet, firstIndex, lastIndex, getBoundsQueryPadding());
            } else if (virtualDataSet.mapsMonotonically()) {
                DataSetPoint firstPoint = new DataSetPoint(dataSet, firstIndex);
                DataSetPoint lastPoint = new DataSetPoint(dataSet, lastIndex);
                virtualDataSet.map(firstPoint);
                virtualDataSet.map(lastPoint);
                if (!SingleChartRenderer.ASSERTIONS_DISABLED && firstPoint.getDataSet() != virtualDataSet)
                    throw new AssertionError();
                if (!SingleChartRenderer.ASSERTIONS_DISABLED && lastPoint.getDataSet() != virtualDataSet)
                    throw new AssertionError();
                boundsData = this.getDataSlice(virtualDataSet, firstPoint.getIndex(), lastPoint.getIndex(), getBoundsQueryPadding());
            } else {
                DataSetPoint mappedPoint = new DataSetPoint();
                DataPoints sourceSlice = this.getDataSlice(dataSet, firstIndex, lastIndex, getBoundsQueryPadding());
                if (sourceSlice == null) {
                    boundsData = null;
                } else {
                    try {
                        int pointCount = sourceSlice.size();
                        boundsData = new DataPoints(virtualDataSet, pointCount);
                        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                            mappedPoint.dataSet = dataSet;
                            mappedPoint.index = sourceSlice.getIndex(pointIndex);
                            virtualDataSet.map(mappedPoint);
                            if (!SingleChartRenderer.ASSERTIONS_DISABLED && mappedPoint.getDataSet() != virtualDataSet)
                                throw new AssertionError();
                            boundsData.add(
                                    virtualDataSet.getXData(mappedPoint.index),
                                    virtualDataSet.getYData(mappedPoint.index),
                                    mappedPoint.index);
                        }
                    } finally {
                        sourceSlice.dispose();
                    }
                }
            }
        }
        if (boundsData != null) {
            SingleChartRenderer.Points points = this.createPointsView(boundsData);
            try {
                if (points.size() > 0) {
                    bounds = this.computeItemBounds(points);
                    if (includeAnnotations && holdsAnnotations())
                        this.addAnnotationBounds(points, bounds);
                }
            } finally {
                this.disposePoints(points);
            }
        }
        return bounds;
    }

    /// {@inheritDoc}
    ///
    /// This overload measures the geometry currently visible through the renderer's own clipping
    /// rules. Annotation bounds are optional and are accumulated only after the logical item bounds
    /// have been collected from the visible rendered slice.
    @Override
    public Rectangle2D getBounds(Rectangle2D reuseBounds, boolean includeAnnotations) {
        Rectangle2D bounds = reuseBounds;
        if (super.getChart() != null) {
            DataPoints visibleData = this.getVisibleData(null);
            if (visibleData != null) {
                Points points = this.createPointsView(visibleData);
                try {
                    if (points.size() > 0) {
                        bounds = this.computeItemBounds(points);
                        if (includeAnnotations && holdsAnnotations())
                            this.addAnnotationBounds(points, bounds);
                        return bounds;
                    }
                } finally {
                    this.disposePoints(points);
                }
            }
        } // end block

        if (bounds == null)
            bounds = new Rectangle2D.Double();
        else
            bounds.setRect(0.0, 0.0, 0.0, 0.0);
        return bounds;
    }

    public String getDefaultLegendText() {
        if (super.getName() != null)
            return super.getName();
        DataSet primaryDataSet = getPrimaryDataSet();
        if (primaryDataSet == null)
            return "";
        String dataSetName = primaryDataSet.getName();
        return (dataSetName == null) ? "" : dataSetName;
    }

    /// {@inheritDoc}
    ///
    /// This lookup narrows the candidate dataset batch to a small display-space rectangle centered
    /// on the pick anchor and returns the first logical item whose geometry contains that anchor.
    /// Results are unmapped back to the source dataset when the renderer paints a
    /// [VirtualDataSet].
    @Override
    public DisplayPoint getDisplayItem(ChartDataPicker dataPicker) {
        int pickX = dataPicker.getPickX();
        int pickY = dataPicker.getPickY();
        Rectangle pickRect = new Rectangle((int) Math.floor(pickX - 1), (int) Math.floor(pickY - 1), 2, 2);
        DataPoints visibleData = this.getVisibleData(pickRect);
        if (visibleData == null)
            return null;
        SingleChartRenderer.Points points = this.createPointsView(visibleData);
        DisplayPoint pickedPoint = this.pickItem(points, dataPicker, false, null);
        this.disposePoints(points);
        return unmapDisplayPoint(pickedPoint);
    }

    /// {@inheritDoc}
    ///
    /// Any active [VirtualDataSet] mapping is used only to project the point. The returned
    /// [DisplayPoint] still refers to `dataSet` and the source index supplied by the caller.
    @Override
    public DisplayPoint getDisplayPoint(DataSet dataSet, int dataIndex) {
        DataSet resolvedDataSet = this.getResolvedDataSet(dataSet);
        if (resolvedDataSet == null)
            return null;
        DataSetPoint mappedPoint = new DataSetPoint(dataSet, dataIndex);
        if (resolvedDataSet instanceof VirtualDataSet)
            ((VirtualDataSet) resolvedDataSet).map(mappedPoint);
        DataPoints pointData = mappedPoint.getData();
        this.toDisplay(pointData);
        double xCoord = pointData.getX(0);
        double yCoord = pointData.getY(0);
        pointData.dispose();
        return new DisplayPoint(this, dataSet, dataIndex, xCoord, yCoord);
    }

    public PlotStyle getDisplayStyle(int dataIndex) {
        return this.getStyle(getPrimaryDataSet(), dataIndex);
    }

    @Override
    public String getLegendText(LegendEntry legend) {
        return getDefaultLegendText();
    }

    /// {@inheritDoc}
    ///
    /// Distance is measured against renderer-specific logical items rather than against raw points.
    /// Item geometry decides both hit testing and the optional distance value reported through
    /// `distanceOut`.
    @Override
    public DisplayPoint getNearestItem(ChartDataPicker dataPicker, double[] distanceOut) {
        int pickX = dataPicker.getPickX();
        int pickY = dataPicker.getPickY();
        int pickDistance = dataPicker.getPickDistance();
        Rectangle pickRect = null;
        if (pickDistance >= 0 && pickDistance < 2000)
            pickRect = new Rectangle((int) Math.floor(pickX - pickDistance), (int) Math.floor(pickY - pickDistance), 2 * pickDistance, 2 * pickDistance);
        DataPoints visibleData = this.getVisibleData(pickRect);
        if (visibleData == null) {
            if (distanceOut != null)
                distanceOut[0] = Double.POSITIVE_INFINITY;
            return null;
        }
        SingleChartRenderer.Points points = this.createPointsView(visibleData);
        double[] measuredDistance = new double[1];
        DisplayPoint pickedPoint = this.pickItem(points, dataPicker, true, measuredDistance);
        if (measuredDistance[0] > pickDistance)
            pickedPoint = null;
        this.disposePoints(points);
        pickedPoint = unmapDisplayPoint(pickedPoint);
        if (distanceOut != null)
            distanceOut[0] = (pickedPoint == null) ? Double.POSITIVE_INFINITY : measuredDistance[0];
        return pickedPoint;
    }

    /// {@inheritDoc}
    ///
    /// Unlike [#getNearestItem(ChartDataPicker, double[])], this method compares only point
    /// locations. When [ChartDataPicker#useDataSpace()] is `true`, the pick anchor is projected
    /// back into data space before candidate distances are evaluated, but the same pick-distance
    /// threshold still applies to the resulting data-space distance.
    @Override
    public DisplayPoint getNearestPoint(ChartDataPicker dataPicker) {
        if (super.getChart() != null) {
            int pickX = dataPicker.getPickX();
            int pickY = dataPicker.getPickY();
            int pickDistance = dataPicker.getPickDistance();
            Rectangle pickRect = null;
            if (pickDistance >= 0 && pickDistance < 2000)
                pickRect = new Rectangle((int) Math.floor(pickX - pickDistance), (int) Math.floor(pickY - pickDistance), 2 * pickDistance, 2 * pickDistance);
            DataPoints visibleData = this.getVisibleData(pickRect);
            if (visibleData == null)
                return null;
            SingleChartRenderer.Points points = this.createPointsView(visibleData);
            int pointCount = points.size();
            DisplayPoint nearestPoint = null;
            if (pointCount > 0) {
                int nearestPointIndex = -1;
                DataSet dataSet = points.getDataSet();
                Double undefinedMarker = dataSet.getUndefValue();
                double undefinedValue = (undefinedMarker == null) ? 0.0 : undefinedMarker.doubleValue();
                if (!dataPicker.useDataSpace()) {
                    double[] xCoords = points.getXCoords();
                    double[] yCoords = points.getYCoords();
                    double[] yData = points.getYData();
                    double bestDistance = pickDistance;
                    for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                        double y = yData[pointIndex];
                        if ((undefinedMarker != null && y == undefinedValue) || Double.isNaN(y))
                            continue;

                        double distance = dataPicker.computeDistance(pickX, pickY, xCoords[pointIndex], yCoords[pointIndex]);
                        if (distance <= bestDistance) {
                            nearestPointIndex = pointIndex;
                            if (distance == 0.0)
                                break;
                            bestDistance = distance;
                        }
                    }
                    if (nearestPointIndex != -1)
                        nearestPoint = new DisplayPoint(this, dataSet, points.getDataIndex(nearestPointIndex), xCoords[nearestPointIndex], yCoords[nearestPointIndex]);
                } else {
                    DoublePoints dataPickPoint = new DoublePoints(pickX, pickY);
                    super.toData(dataPickPoint);
                    double[] xData = points.getXData();
                    double[] yData = points.getYData();
                    double bestDistance = pickDistance;
                    for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                        double y = yData[pointIndex];
                        if ((undefinedMarker != null && y == undefinedValue) || Double.isNaN(y))
                            continue;

                        double distance = dataPicker.computeDistance(
                                dataPickPoint.getX(0),
                                dataPickPoint.getY(0),
                                xData[pointIndex],
                                y);
                        if (distance <= bestDistance) {
                            nearestPointIndex = pointIndex;
                            if (distance == 0.0)
                                break;
                            bestDistance = distance;
                        }
                    }
                    if (nearestPointIndex != -1) {
                        dataPickPoint.set(0, xData[nearestPointIndex], yData[nearestPointIndex]);
                        this.toDisplay(dataPickPoint);
                        nearestPoint = new DisplayPoint(
                                this,
                                dataSet,
                                points.getDataIndex(nearestPointIndex),
                                dataPickPoint.getX(0),
                                dataPickPoint.getY(0));
                    }
                    dataPickPoint.dispose();
                }
                nearestPoint = unmapDisplayPoint(nearestPoint);
            }
            this.disposePoints(points);
            return nearestPoint;
        }
        return null;
    }

    @Override
    public Insets getPreferredMargins() {
        return new Insets(0, 0, 0, 0);
    }

    @Override
    public DataRenderingHint getRenderingHint(DataSet dataSet) {
        DefaultedRenderingModifierArray modifiers = this.getModifiers(dataSet, false);
        if (modifiers == null)
            return null;
        return modifiers.getRenderingHint();
    }

    @Override
    public DataRenderingHint getRenderingHint(DataSet dataSet, int dataIndex) {
        return this.getRenderingHint(new DataSetPoint(dataSet, dataIndex));
    }

    private DataRenderingHint getRenderingHintOrInherit() {
        DataRenderingHint hint = getRenderingHint();

        ChartRenderer parent;
        if (hint == null && (parent = getParent()) != null)
            hint = parent.getRenderingHint();

        return hint;
    }

    public PlotStyle getStyle() {
        return baseStyle;
    }

    @Override
    public PlotStyle getStyle(DataSet dataSet, int dataIndex) {
        if (!super.isDisplayingDataSet(dataSet))
            return null;
        PlotStyle baseStyle = this.getBaseStyle(dataSet, dataIndex);
        if (hasRenderingHints()) {
            DisplayPoint displayPoint = new DisplayPoint(this, dataSet, dataIndex, 0.0, 0.0);
            DataRenderingHint renderingHint = this.getRenderingHint(displayPoint);
            if (renderingHint != null)
                return renderingHint.getStyle(displayPoint, baseStyle);
        }
        return baseStyle;
    }

    @Override
    public PlotStyle[] getStyles() {
        if (baseStyle == null)
            return new PlotStyle[0];
        return new PlotStyle[]{baseStyle};
    }

    /// {@inheritDoc}
    ///
    /// The returned x-range reflects painted geometry rather than just raw dataset coordinates. In
    /// addition to any virtual-dataset transform, the range includes the renderer's effective
    /// x-shift and any width/category-padding expansion required by [VariableWidthRenderer].
    @Override
    public DataInterval getXRange(DataInterval reuseRange) {
        DataSet renderedDataSet = getRenderedDataSet();
        DataInterval xRange;
        if (renderedDataSet != null)
            xRange = renderedDataSet.getXRange(reuseRange);
        else if (reuseRange == null)
            xRange = new DataInterval();
        else {
            reuseRange.empty();
            xRange = reuseRange;
        }
        if (!xRange.isEmpty()) {
            double xShift = super.getXShift();
            if (this instanceof VariableWidthRenderer variableWidthRenderer) {
                double width = variableWidthRenderer.getWidth();
                if (!variableWidthRenderer.isUseCategorySpacingAtBorders()) {
                    if (xShift != 0.0)
                        xRange.translate(xShift);
                    xRange.expand(0.5 * width);
                } else {
                    double categoryWidth = getCategoryWidth();
                    xRange.min = xRange.min + Math.min(-0.5 * categoryWidth, xShift - 0.5 * width);
                    xRange.max = xRange.max + Math.max(0.5 * categoryWidth, xShift + 0.5 * width);
                }
            } else if (xShift != 0.0)
                xRange.translate(xShift);
        }
        return xRange;
    }

    /// {@inheritDoc}
    ///
    /// This overload reports the full rendered y-range without applying the current visible
    /// x-window. It simply delegates to the rendered dataset view when one exists.
    @Override
    public DataInterval getYRange(DataInterval reuseRange) {
        DataInterval yRange = reuseRange;
        DataSet renderedDataSet = getRenderedDataSet();
        if (renderedDataSet != null)
            return renderedDataSet.getYRange(yRange);
        if (yRange != null)
            yRange.empty();
        else
            yRange = new DataInterval();
        return yRange;
    }

    /// {@inheritDoc}
    ///
    /// Only the rendered slice inside `xRange` contributes to the result. Undefined samples
    /// encoded as `NaN` or as the dataset's explicit undefined-value sentinel are ignored.
    @Override
    public DataInterval getYRange(DataInterval xRange, DataInterval reuseRange) {
        DataInterval yRange = reuseRange;
        if (yRange != null)
            yRange.empty();
        else
            yRange = new DataInterval();
        DataSet renderedDataSet = getRenderedDataSet();
        if (renderedDataSet == null)
            return yRange;
        DataWindow window = new DataWindow(xRange, getYAxis().getVisibleRange());
        DataPoints visibleData = renderedDataSet.getDataInside(window, 0, true);
        if (visibleData == null)
            return yRange;
        int pointCount = visibleData.size();
        Double undefinedMarker = renderedDataSet.getUndefValue();
        double undefinedValue = (undefinedMarker == null) ? 0.0 : undefinedMarker.doubleValue();
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            double y = visibleData.getY(pointIndex);
            if ((undefinedMarker != null && y == undefinedValue) || Double.isNaN(y))
                continue;
            yRange.add(y);
        }
        visibleData.dispose();
        return yRange;
    }

    public String getZAnnotationText() {
        return null;
    }

    /// Returns the extra sample count fetched on each side of explicit index-range bounds queries.
    ///
    /// The default matches [#getDisplayQueryPadding()], but subclasses can keep bounds requests
    /// strict even when paint-time sampling widens the fetched range.
    int getBoundsQueryPadding() {
        return getDisplayQueryPadding();
    }

    boolean hasRenderingHints() {
        if (getRenderingHintOrInherit() != null)
            return true;

        DataSource dataSource = getDataSource();
        for (int i = 0, count = dataSource.size(); i < count; i++) {
            DataSet series = dataSource.get(i);

            DefaultedRenderingModifierArray holder = this.getModifiers(series, false);
            if (holder != null && holder.holdsRenderingHint())
                return true;
        }
        return false;
    }

    @Override
    public boolean holdsAnnotations() {
        if (getAnnotationOrInherit() != null)
            return true;

        DataSource dataSource = super.getDataSource();
        for (int dataSetIndex = 0, dataSetCount = dataSource.size(); dataSetIndex < dataSetCount; dataSetIndex++) {
            DataSet dataSet = dataSource.get(dataSetIndex);
            DefaultedRenderingModifierArray modifiers = this.getModifiers(dataSet, false);
            if (modifiers != null && modifiers.holdsAnnotation())
                return true;
        }
        return false;
    }

    /// Returns the live y-axis from the renderer's current coordinate system.
    ///
    /// Single renderers cache no axis state of their own, so every call reflects the chart's
    /// current axis binding.
    final Axis getYAxis() {
        return super.getCoordinateSystem().getYAxis();
    }

    public boolean isFilled() {
        return true;
    }

    /// Returns the live x-axis from the renderer's current coordinate system.
    ///
    /// Callers use this helper when visible-range checks or projector calculations must follow the
    /// chart's current axis binding rather than a stored snapshot.
    final Axis getXAxis() {
        return super.getCoordinateSystem().getXAxis();
    }

    /// Creates the fallback style used when no explicit style was assigned.
    ///
    /// The default color is chosen from the owning chart when available so sibling single renderers
    /// avoid obvious color collisions.
    protected PlotStyle makeDefaultStyle() {
        return this.createStyle(chooseDefaultColor(), null, defaultStroke);
    }

    /// Creates the draw action used by [#draw(Graphics)].
    ///
    /// Subclasses override this when the normal `Item.draw(...)` path needs extra per-item
    /// preparation or cleanup.
    SingleChartRenderer.DefaultDrawItemAction createDrawItemAction() {
        return new SingleChartRenderer.DefaultDrawItemAction();
    }

    /// Creates the bounds collector used by [#getBounds(Rectangle2D, boolean)] and related slice
    /// queries.
    ///
    /// Subclasses override this when their painted extent extends beyond the raw item geometry
    /// returned by [Item#getBounds(PlotStyle, boolean, Rectangle2D)].
    SingleChartRenderer.BoundsItemAction createBoundsItemAction() {
        return new SingleChartRenderer.DefaultBoundsItemAction();
    }

    /// Returns the x-axis span treated as one category slot for this renderer.
    ///
    /// Explicit dataset category metadata takes precedence. Otherwise the value is inferred from
    /// the minimum x-distance in the rendered dataset, with conservative fallbacks for degenerate
    /// or single-point sources so width-based renderers can still paint a usable glyph.
    final double getCategoryWidth() {
        DataSet renderedDataSet = getRenderedDataSet();
        if (renderedDataSet == null)
            return 0.0;

        Double categoryWidth = DataSetProperty.getCategory(renderedDataSet);
        if (categoryWidth != null)
            return categoryWidth.doubleValue();

        double minimumXDifference = (!(renderedDataSet instanceof AbstractDataSet))
                ? AbstractDataSet.computeMinimumXDifference(renderedDataSet)
                : ((AbstractDataSet) renderedDataSet).getMinimumXDifference();
        if (minimumXDifference == -1.0) {
            DataPoints dataPoints = renderedDataSet.getData();
            if (dataPoints != null) {
                try {
                    if (dataPoints.size() > 0) {
                        double firstX = dataPoints.getX(0);
                        return (firstX == 0.0) ? 1.0 : (Math.abs(firstX) <= 1.0) ? Math.abs(firstX) : 0.5 * Math.abs(firstX);
                    }
                } finally {
                    dataPoints.dispose();
                }
            }
            return 1.0;
        }
        if (!SingleChartRenderer.ASSERTIONS_DISABLED)
            if (minimumXDifference < 0.0)
                throw new AssertionError();
        return minimumXDifference;
    }

    void refreshAutoStyle() {
        if (autoStyle) {
            baseStyle = null;
            applyBaseStyle(null);
        }
    }

    private Color chooseDefaultColor() {
        return (super.getChart() != null)
                ? super.findAppropriateColor(collectUsedColors())
                : ColorData.getDefaultColors().get(0);
    }

    /// Sets the dataset-wide annotation fallback for one dataset view.
    ///
    /// `SingleChartRenderer` stores dataset-wide and point-specific annotations in a shared
    /// [DefaultedRenderingModifierArray]. When the renderer is attached to a chart, changing the
    /// dataset-wide fallback triggers the same annotation refresh path as a renderer-wide annotation
    /// change because any logical item in that dataset may now resolve to a different annotation.
    @Override
    public void setAnnotation(DataSet dataSet, DataAnnotation annotation) {
        DefaultedRenderingModifierArray modifiers = getModifiers(dataSet, annotation != null);
        if (modifiers == null)
            return;

        if (super.getChart() == null) {
            modifiers.setAnnotation(annotation);
            return;
        }

        DataAnnotation previousAnnotation = modifiers.getAnnotation();
        modifiers.setAnnotation(annotation);
        if (annotation != previousAnnotation)
            super.triggerChange(8);
    }

    /// Sets the annotation override for one logical item in `dataSet`.
    ///
    /// When the chart layout includes annotation bounds, any per-point annotation change forces a
    /// layout pass because the plot rectangle itself may need to expand. Otherwise the method limits
    /// repaints to the old and new annotation bounds for visible points and skips repaint work for
    /// items that are currently outside the visible x-range.
    @Override
    public void setAnnotation(DataSet dataSet, int index, DataAnnotation annotation) {
        DefaultedRenderingModifierArray modifiers = getModifiers(dataSet, annotation != null);
        if (modifiers == null)
            return;

        if (super.getChart() == null) {
            modifiers.setAnnotation(index, annotation);
            return;
        }

        Chart.Area chartArea = super.getChart().getChartArea();
        if (chartArea.isPlotRectIncludingAnnotations()) {
            modifiers.setAnnotation(index, annotation);
            chartArea.revalidateLayout();
            return;
        }

        DataInterval visibleRange = getXAxis().getVisibleRange();
        if (!visibleRange.isInside(dataSet.getXData(index))) {
            modifiers.setAnnotation(index, annotation);
            return;
        }

        Rectangle2D dirtyBounds = getAnnotationBounds(dataSet, index);
        modifiers.setAnnotation(index, annotation);

        if (annotation != null || getAnnotationOrInherit() != null) {
            Rectangle2D updatedBounds = getAnnotationBounds(dataSet, index);
            if (dirtyBounds == null)
                dirtyBounds = updatedBounds;
            else if (updatedBounds != null)
                GraphicUtil.addToRect(dirtyBounds, updatedBounds);
        }

        if (dirtyBounds != null && !dirtyBounds.isEmpty())
            chartArea.repaint2D(dirtyBounds);
    }

    /// Writes new source-space coordinates back through this renderer's current dataset view.
    ///
    /// When the displayed dataset is wrapped in a [VirtualDataSet], the edit is routed through that
    /// virtual view first. This lets the virtual dataset remap the logical point and, when needed,
    /// translate the proposed coordinates before the backing source dataset receives the change.
    /// Direct datasets are updated in place.
    @Override
    public void setDataPoint(DataSet dataSet, int index, double x, double y) {
        if (!super.isDisplayingDataSet(dataSet))
            return;

        DataSetPoint dataPoint = new DataSetPoint(dataSet, index);
        VirtualDataSet virtualDataSet = DataSetRendererProperty.getVirtualDataSet(this, dataSet);
        if (virtualDataSet != null)
            virtualDataSet.map(dataPoint);
        dataPoint.setData(x, y);
    }

    /// Writes new display-space coordinates back through this renderer's projection and dataset
    /// mapping pipeline.
    ///
    /// The method first converts `(x, y)` from display space into this renderer's current data
    /// space and then delegates to [#setDataPoint(DataSet, int, double, double)] so virtual
    /// datasets and direct datasets share the same write-back path.
    @Override
    public void setDisplayPoint(DataSet dataSet, int index, double x, double y) {
        if (!super.isDisplayingDataSet(dataSet))
            return;

        DoublePoints dataPoint = new DoublePoints(x, y);
        try {
            super.toData(dataPoint);
            setDataPoint(dataSet, index, dataPoint.getX(0), dataPoint.getY(0));
        } finally {
            dataPoint.dispose();
        }
    }

    /// Sets the dataset-wide rendering-hint fallback for one dataset view.
    ///
    /// Dataset-wide hint changes can affect every logical item in the series, so attached charts
    /// repaint the full chart area. Subclasses also get the before/after hint pair through the
    /// internal hint-change hook.
    @Override
    public void setRenderingHint(DataSet dataSet, DataRenderingHint renderingHint) {
        DefaultedRenderingModifierArray modifiers = getModifiers(dataSet, renderingHint != null);
        if (modifiers == null)
            return;

        DataRenderingHint previousRenderingHint = modifiers.getRenderingHint();
        modifiers.setRenderingHint(renderingHint);
        if (super.getChart() != null) {
            super.getChart().getChartArea().repaint();
            handleRenderingHintChange(previousRenderingHint, renderingHint);
        }
    }

    /// Sets the rendering-hint override for one logical item in `dataSet`.
    ///
    /// Unlike dataset-wide hint changes, point-specific changes limit repaint work to the union of
    /// the old and new painted bounds for that one item.
    @Override
    public void setRenderingHint(DataSet dataSet, int index, DataRenderingHint renderingHint) {
        DefaultedRenderingModifierArray modifiers = getModifiers(dataSet, renderingHint != null);
        if (modifiers == null)
            return;

        if (super.getChart() == null) {
            modifiers.setRenderingHint(index, renderingHint);
            return;
        }

        DataRenderingHint previousRenderingHint = modifiers.getRenderingHint(index);
        Rectangle2D dirtyBounds = super.getBounds(dataSet, index, index, null);
        modifiers.setRenderingHint(index, renderingHint);
        Rectangle2D updatedBounds = super.getBounds(dataSet, index, index, null);
        GraphicUtil.addToRect(dirtyBounds, updatedBounds);
        super.getChart().getChartArea().repaint2D(dirtyBounds);
        handleRenderingHintChange(previousRenderingHint, renderingHint);
    }

    /// Sets the single renderer-wide base style.
    ///
    /// Passing `null` while the renderer is attached restores an auto-generated default style that
    /// is derived from the owning chart's palette. [SingleChartRenderer] exposes only this single
    /// base slot, so the style also underpins [#getStyles()] and legend-marker rendering.
    public void setStyle(PlotStyle style) {
        applyBaseStyle(style);
    }

    @Override
    public void setStyles(PlotStyle[] styles) {
        setStyle((styles == null) ? null : styles[0]);
    }

    private Set<Color> collectUsedColors() {
        Set<Color> usedColors = new HashSet<>();
        Iterator<ChartRenderer> rendererIterator = super.getChart().getRendererIterator();
        while (rendererIterator.hasNext()) {
            ChartRenderer renderer = rendererIterator.next();
            collectStyleColors(usedColors, renderer.getStyles());
        }
        return usedColors;
    }

    private DataAnnotation getAnnotationOrInherit() {
        DataAnnotation annotation = super.getAnnotation();
        if (annotation == null && super.getParent() != null)
            annotation = super.getParent().getAnnotation();
        return annotation;
    }
}

