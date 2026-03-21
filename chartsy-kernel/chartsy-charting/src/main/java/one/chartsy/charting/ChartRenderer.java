package one.chartsy.charting;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSetPoint;
import one.chartsy.charting.data.DataSource;
import one.chartsy.charting.data.DefaultDataSource;
import one.chartsy.charting.event.DataSourceEvent;
import one.chartsy.charting.event.DataSourceListener;
import one.chartsy.charting.event.DataSourceListener2;
import one.chartsy.charting.graphic.DataAnnotation;
import one.chartsy.charting.graphic.DataLabelAnnotation;
import one.chartsy.charting.graphic.DataRenderingHint;
import one.chartsy.charting.renderers.AreaChartRenderer;
import one.chartsy.charting.renderers.BarChartRenderer;
import one.chartsy.charting.renderers.BubbleChartRenderer;
import one.chartsy.charting.renderers.ComboChartRenderer;
import one.chartsy.charting.renderers.HiLoChartRenderer;
import one.chartsy.charting.renderers.PieChartRenderer;
import one.chartsy.charting.renderers.PolylineChartRenderer;
import one.chartsy.charting.renderers.ScatterChartRenderer;
import one.chartsy.charting.renderers.SingleChartRenderer;
import one.chartsy.charting.renderers.SingleHiLoRenderer.Type;
import one.chartsy.charting.renderers.StairChartRenderer;
import one.chartsy.charting.renderers.internal.DataSetRendererProperty;
import one.chartsy.charting.renderers.internal.VirtualDataSet;
import one.chartsy.charting.util.ColorUtil;
import one.chartsy.charting.util.Flags;
import one.chartsy.charting.util.GraphicUtil;

/// Base class for chart renderers that turn one or more datasets into painted chart content.
///
/// A renderer owns a live [DataSource], can expose child renderers through
/// [#getChildIterator()] and [#getChildren()], and may either attach directly to a [Chart] or
/// inherit chart ownership from a parent renderer. Root renderers are installed through chart
/// APIs; child renderers are attached through composite renderer implementations.
///
/// Three state layers drive whether a renderer actively participates in painting:
/// - [#isVisible()] is the explicit user-facing visibility flag
/// - [#getMinDataSetCount()] gates renderers that require at least one dataset
/// - internal axis attachment must resolve successfully for the current y-axis slot
///
/// When all three conditions hold the renderer becomes [#isViewable()], which is the signal used
/// by chart paint, data picking, legend population, and range calculation.
///
/// The class also centralizes:
/// - dataset lifecycle bridging from [DataSource] events into renderer hooks and chart updates
/// - legend entry creation through [LegendEntryProvider]
/// - inherited presentation settings such as data labeling, data-label layout, and x-shift
/// - renderer-change batching through [#startRendererChanges()] and [#endRendererChanges()]
///
/// Renderers are mutable UI model objects and are not thread-safe.
public abstract class ChartRenderer implements IChartRenderer, Serializable {

    /// Bridges structural [DataSource] events back into renderer lifecycle hooks.
    ///
    /// The listener preserves the same event ordering seen by the underlying source:
    /// - batched change boundaries are forwarded through `dataSetsChangesBatchStarting()` and
    ///   `dataSetsChangesBatchEnding()`
    /// - insertion hooks observe the current source together with the full pre-change snapshot
    /// - removal hooks recover the removed interval from that same pre-change snapshot because the
    ///   live source no longer contains those datasets
    ///
    /// After the renderer hooks run, the listener recomputes the derived viewable flag and emits
    /// change type `7`, which is the chart-level signal for "the displayed data source changed and
    /// ranges may need to be recomputed".
    class DataSourceBridgeListener implements DataSourceListener2, Serializable {

        /// Creates the listener used by the owning renderer's current data source.
        DataSourceBridgeListener() {
        }

        /// Reacts to one structural change emitted after the data source has already been updated.
        ///
        /// Insertions pass the full pre-change source snapshot to `dataSetsAdded(...)`. Removals
        /// use that same snapshot because the removed datasets are no longer addressable from the
        /// live source by the time the event is delivered.
        @Override
        public void dataSourceChanged(DataSourceEvent event) {
            int firstIndex = event.getFirstIdx();
            int lastIndex = event.getLastIdx();
            DataSet[] previousDataSets = event.getOldDataSets();

            switch (event.getType()) {
                case DataSourceEvent.DATASET_ADDED -> dataSetsAdded(firstIndex, lastIndex, previousDataSets);
                case DataSourceEvent.DATASET_REMOVED -> dataSetsRemoved(firstIndex, lastIndex, previousDataSets);
                default -> {
                }
            }
            ChartRenderer.this.updateViewableState();
            triggerChange(7);
        }

        /// Signals the end of a batched data-source mutation sequence.
        @Override
        public void endDataSourceChanges() {
            dataSetsChangesBatchEnding();
        }

        /// Signals the start of a batched data-source mutation sequence.
        @Override
        public void startDataSourceChanges() {
            dataSetsChangesBatchStarting();
        }
    }

    public static final int BAR = 0;
    public static final int STACKED_BAR = 1;
    public static final int STACKED100_BAR = 17;
    public static final int STACKED_DIVERGING_BAR = 24;
    public static final int SUPERIMPOSED_BAR = 2;
    public static final int AREA = 3;
    public static final int STACKED_AREA = 4;
    public static final int STACKED100_AREA = 19;
    public static final int POLYLINE = 5;
    public static final int STACKED_POLYLINE = 11;
    public static final int STACKED100_POLYLINE = 18;
    public static final int SCATTER = 6;
    public static final int STAIR = 7;
    public static final int STACKED_STAIR = 14;
    public static final int STACKED100_STAIR = 20;
    public static final int SUMMED_STAIR = 15;
    public static final int BUBBLE = 8;
    public static final int HILO = 9;
    public static final int HILO_ARROW = 21;
    public static final int HILO_STICK = 22;
    public static final int CANDLE = 12;
    public static final int HLOC = 13;
    public static final int PIE = 10;
    public static final int TREEMAP = 23;
    public static final int COMBO = 16;
    public static final int DATA_LABEL = 1;
    public static final int Y_VALUE_LABEL = 2;
    public static final int XY_VALUES_LABEL = 3;
    public static final int X_VALUE_LABEL = 4;
    public static final int PERCENT_LABEL = 5;
    public static final int CENTERED_LABEL_LAYOUT = 1;
    public static final int OUTSIDE_LABEL_LAYOUT = 2;
    public static final int DEFAULT_LABEL_LAYOUT = 1;
    public static final int DEFAULT_LABELING = 2;
    private static final Map<String, Class<? extends ChartRenderer>> registeredRendererClasses = new HashMap<>();

    /// Validates that `rendererType` can currently be materialized by [#createRenderer(int)].
    ///
    /// This method is used when charts store a default renderer type without creating the renderer
    /// immediately. It therefore accepts only the renderer constants backed by concrete
    /// implementations in this module.
    ///
    /// @param rendererType renderer type constant to validate
    /// @throws IllegalArgumentException if `rendererType` has no concrete factory implementation
    static void validateRendererType(int rendererType) {
        switch (rendererType) {
            case BAR, STACKED_BAR, SUPERIMPOSED_BAR, AREA, STACKED_AREA, POLYLINE, SCATTER, STAIR,
                 BUBBLE, HILO, PIE, STACKED_POLYLINE, CANDLE, HLOC, STACKED_STAIR, SUMMED_STAIR,
                 COMBO, STACKED100_BAR, STACKED100_POLYLINE, STACKED100_AREA, STACKED100_STAIR,
                 HILO_ARROW, HILO_STICK, STACKED_DIVERGING_BAR -> {
                return;
            }
            default -> throw new IllegalArgumentException("Unknown chart renderer type: " + rendererType);
        }
    }

    /// Creates a renderer resolved from a registered short name or a fully qualified class name.
    ///
    /// Resolution first consults the short-name registry populated by [#register(String, Class)].
    /// When no mapping exists, the method falls back to `Class.forName(...)`. The resolved class
    /// must be a [ChartRenderer] subtype with a public no-argument constructor.
    ///
    /// @param name the registered short name or fully qualified renderer class name
    /// @return a new renderer instance
    /// @throws IllegalAccessException if the renderer's public no-argument constructor is
    ///                                    inaccessible
    /// @throws ClassNotFoundException if `name` resolves to no known class
    /// @throws InstantiationException if the resolved class is not a [ChartRenderer], lacks a
    ///                                    public no-argument constructor, or its constructor fails during instantiation
    public static ChartRenderer create(String name)
            throws IllegalAccessException, ClassNotFoundException, InstantiationException {
        Class<? extends ChartRenderer> rendererClass = registeredRendererClasses.get(name);
        if (rendererClass == null) {
            try {
                rendererClass = Class.forName(name).asSubclass(ChartRenderer.class);
            } catch (ClassCastException exception) {
                InstantiationException wrapped = new InstantiationException(name);
                wrapped.initCause(exception);
                throw wrapped;
            }
        }

        try {
            return rendererClass.getConstructor().newInstance();
        } catch (NoSuchMethodException exception) {
            InstantiationException wrapped = new InstantiationException(rendererClass.getName());
            wrapped.initCause(exception);
            throw wrapped;
        } catch (InvocationTargetException exception) {
            InstantiationException wrapped = new InstantiationException(rendererClass.getName());
            wrapped.initCause(exception.getTargetException());
            throw wrapped;
        }
    }

    /// Creates a concrete renderer for one of the public renderer-type constants.
    ///
    /// This factory is used by chart configuration code and UI workflows that store renderer types
    /// as integers rather than classes. Only renderer constants backed by concrete implementations
    /// in this module are accepted.
    ///
    /// @param rendererType one of the `BAR`, `AREA`, `POLYLINE`, `PIE`, and related constants
    /// @return a new renderer instance matching `rendererType`
    /// @throws IllegalArgumentException if `rendererType` is unknown
    public static ChartRenderer createRenderer(int rendererType) {
        return switch (rendererType) {
            case BAR -> new BarChartRenderer();
            case STACKED_BAR -> new BarChartRenderer(3);
            case SUPERIMPOSED_BAR -> new BarChartRenderer(1);
            case AREA -> new AreaChartRenderer();
            case STACKED_AREA -> new AreaChartRenderer(2);
            case POLYLINE -> new PolylineChartRenderer();
            case SCATTER -> new ScatterChartRenderer();
            case STAIR -> new StairChartRenderer();
            case BUBBLE -> new BubbleChartRenderer();
            case HILO -> new HiLoChartRenderer();
            case PIE -> new PieChartRenderer();
            case STACKED_POLYLINE -> new PolylineChartRenderer(2);
            case CANDLE -> new HiLoChartRenderer(HiLoChartRenderer.Mode.CANDLE, Type.BAR, 80.0);
            case HLOC -> new HiLoChartRenderer(HiLoChartRenderer.Mode.OPENCLOSE, Type.BAR, 80.0);
            case STACKED_STAIR -> new StairChartRenderer(2);
            case SUMMED_STAIR -> new StairChartRenderer(3);
            case COMBO -> new ComboChartRenderer();
            case STACKED100_BAR -> {
                BarChartRenderer renderer = new BarChartRenderer(3);
                renderer.setStacked100Percent(true);
                yield renderer;
            }
            case STACKED100_POLYLINE -> {
                PolylineChartRenderer renderer = new PolylineChartRenderer(2);
                renderer.setStacked100Percent(true);
                yield renderer;
            }
            case STACKED100_AREA -> {
                AreaChartRenderer renderer = new AreaChartRenderer(2);
                renderer.setStacked100Percent(true);
                yield renderer;
            }
            case STACKED100_STAIR -> {
                StairChartRenderer renderer = new StairChartRenderer(2);
                renderer.setStacked100Percent(true);
                yield renderer;
            }
            case HILO_ARROW -> new HiLoChartRenderer(HiLoChartRenderer.Mode.CLUSTERED, Type.ARROW, 80.0);
            case HILO_STICK -> new HiLoChartRenderer(HiLoChartRenderer.Mode.CLUSTERED, Type.MARKED, 80.0);
            case STACKED_DIVERGING_BAR -> {
                BarChartRenderer renderer = new BarChartRenderer(3);
                renderer.setDiverging(true);
                yield renderer;
            }
            default -> throw new IllegalArgumentException("Unknown chart renderer type: " + rendererType);
        };
    }

    /// Returns the first matching display item produced during one renderer traversal.
    ///
    /// The supplied iterator defines precedence. Aggregators such as [Chart] and
    /// `CompositeChartRenderer` pass hit-test iterators that walk renderers from visually topmost
    /// to backmost, so the first non-`null` item wins.
    ///
    /// Only renderers that are both [#isViewable()] and accepted by `picker` participate.
    ///
    /// @param renderers the renderers to query in precedence order
    /// @param picker    the picker describing the desired item and pick anchor
    /// @return the first matching display item, or `null` if none match
    public static DisplayPoint getDisplayItem(Iterator<ChartRenderer> renderers, ChartDataPicker picker) {
        while (renderers.hasNext()) {
            ChartRenderer renderer = renderers.next();
            if (!renderer.isViewable() || !picker.accept(renderer))
                continue;

            DisplayPoint displayItem = renderer.getDisplayItem(picker);
            if (displayItem != null)
                return displayItem;
        }
        return null;
    }

    /// Collects every matching display item produced during one renderer traversal.
    ///
    /// Result order follows renderer traversal order first and each renderer's own emission order
    /// second, which lets callers preserve the same front-to-back semantics used for picking when
    /// they need every match instead of only the winner.
    ///
    /// @param renderers the renderers to query in order
    /// @param picker    the picker describing the desired items and pick anchor
    /// @return a new list of matching display items
    public static List<DisplayPoint> getDisplayItems(Iterator<ChartRenderer> renderers,
                                                     ChartDataPicker picker) {
        ArrayList<DisplayPoint> displayItems = new ArrayList<>();
        while (renderers.hasNext()) {
            ChartRenderer renderer = renderers.next();
            if (renderer.isViewable() && picker.accept(renderer))
                renderer.collectDisplayItems(picker, displayItems);
        }
        return displayItems;
    }

    /// Returns the nearest logical item reported by the supplied renderer traversal.
    ///
    /// Each renderer contributes distances in its own item space through
    /// [#getNearestItem(ChartDataPicker, double[])]. The helper compares only those reported
    /// scalar distances and therefore does not impose a global geometry metric across renderer
    /// types.
    ///
    /// Ties keep the earlier renderer in iterator order because only strictly smaller distances
    /// replace the current winner. When `distanceHolder` is supplied, it receives the winning
    /// distance, or [Double#POSITIVE_INFINITY] when no renderer reports a match.
    ///
    /// @param renderers      the renderers to query in order
    /// @param picker         the picker describing the desired item and pick anchor
    /// @param distanceHolder receives the winning distance when non-null
    /// @return the nearest matching display item, or `null` if none match
    public static DisplayPoint getNearestItem(Iterator<ChartRenderer> renderers, ChartDataPicker picker,
                                              double[] distanceHolder) {
        DisplayPoint nearestItem = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        double[] rendererDistanceBuffer = new double[1];
        while (renderers.hasNext()) {
            ChartRenderer renderer = renderers.next();
            if (!renderer.isViewable() || !picker.accept(renderer))
                continue;

            DisplayPoint candidateItem = renderer.getNearestItem(picker, rendererDistanceBuffer);
            if (candidateItem != null && rendererDistanceBuffer[0] < nearestDistance) {
                nearestDistance = rendererDistanceBuffer[0];
                nearestItem = candidateItem;
            }
        }
        if (distanceHolder != null)
            distanceHolder[0] = nearestDistance;
        return nearestItem;
    }

    /// Returns the nearest display point produced during one renderer traversal.
    ///
    /// Unlike [#getNearestItem(Iterator, ChartDataPicker, double[])], this helper computes its own
    /// distance metric in display space from the pick anchor exposed by `picker`. Distances are
    /// compared as squared Euclidean distances, which avoids extra square-root work while
    /// preserving ordering. Ties keep the earlier renderer in iterator order.
    ///
    /// @param renderers the renderers to query in order
    /// @param picker    the picker describing the desired point and pick anchor
    /// @return the nearest matching display point, or `null` if none match
    public static DisplayPoint getNearestPoint(Iterator<ChartRenderer> renderers, ChartDataPicker picker) {
        DisplayPoint nearestPoint = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        while (renderers.hasNext()) {
            ChartRenderer renderer = renderers.next();
            if (!renderer.isViewable() || !picker.accept(renderer))
                continue;

            DisplayPoint candidatePoint = renderer.getNearestPoint(picker);
            if (candidatePoint == null)
                continue;

            double xOffset = candidatePoint.getXCoord() - picker.getPickX();
            double yOffset = candidatePoint.getYCoord() - picker.getPickY();
            double squaredDistance = xOffset * xOffset + yOffset * yOffset;
            if (squaredDistance < nearestDistance) {
                nearestDistance = squaredDistance;
                nearestPoint = candidatePoint;
            }
        }
        return nearestPoint;
    }

    /// Registers `shortName` so [#create(String)] can resolve it later.
    ///
    /// Concrete renderer classes typically call this from a static initializer. Registering the
    /// same short name more than once replaces the previous mapping.
    ///
    /// @param shortName     the external identifier for the renderer
    /// @param rendererClass the concrete class to instantiate for `shortName`
    protected static void register(String shortName, Class<? extends ChartRenderer> rendererClass) {
        registeredRendererClasses.put(shortName, rendererClass);
    }

    private Chart chart;
    private int yAxisNumber;
    private Chart.AxisElement axisElement;
    private String name;
    private ChartRenderer parent;
    private Flags flags;

    private double xShift;

    private DataSource dataSource;

    private DataSourceListener dataSourceListener;

    private DataAnnotation dataAnnotation;

    private DataRenderingHint dataRenderingHint;

    private Integer dataLabeling;

    private Integer dataLabelLayout;

    private Boolean legended;

    private LegendEntryProvider legendEntryProvider = LegendEntryProvider.EMPTY;

    private transient int rendererChangeBatchDepth;

    /// Creates a detached renderer with an empty default data source and the default legend-entry
    /// strategy.
    protected ChartRenderer() {
        yAxisNumber = -1;
        flags = new Flags(1);
        setDataSource(new DefaultDataSource());
        setLegendEntryProvider(this::createLegendEntries);
    }

    /// Resolves the current axis element from the owning chart and resolved y-axis number.
    ///
    /// Renderers return `null` while detached or while their preferred y-axis slot has not yet
    /// been created on the owning chart.
    final Chart.AxisElement resolveAxisElement() {
        Chart chart = getChart();
        if (chart != null) {
            int yAxisNumber = getYAxisNumber();
            if (yAxisNumber < chart.getYAxisCount())
                return chart.getAxisElement(yAxisNumber + 1);
        }
        return null;
    }

    /// Rebinds this renderer branch to `chart` and optionally updates its preferred y-axis slot.
    ///
    /// Any active renderer-change batch depth is mirrored from the old chart to the new one so
    /// nested batch scopes remain balanced across reattachment. Child renderers inherit the new
    /// chart owner immediately.
    void setChartOwner(Chart chart, int yAxisNumber) {
        if (this.chart == chart)
            return;
        Chart previousChart = this.chart;
        this.chart = chart;
        if (previousChart != null) {
            previousChart.removePendingDataSetEventsForRenderer(this);
            for (int batchDepth = rendererChangeBatchDepth; batchDepth > 0; batchDepth--)
                previousChart.endRendererChanges();
        }
        if (chart == null)
            axisElement = null;
        else {
            if (yAxisNumber >= 0)
                this.yAxisNumber = yAxisNumber;
            axisElement = resolveAxisElement();
            for (int batchDepth = rendererChangeBatchDepth; batchDepth > 0; batchDepth--)
                chart.startRendererChanges();
        }
        if (previousChart != null)
            previousChart.handleRendererChanged(this, 2);
        if (chart != null)
            chart.handleRendererChanged(this, 1);
        updateViewableState();
        chartConnected(previousChart, chart);
        if (previousChart != null)
            previousChart.startRendererChanges();
        if (chart != null)
            chart.startRendererChanges();
        try {
            Iterator<ChartRenderer> childIterator = getChildIterator();
            while (childIterator.hasNext()) {
                ChartRenderer child = childIterator.next();
                if (child != null)
                    child.setChartOwner(chart, -1);
            }
        } finally {
            if (previousChart != null)
                previousChart.endRendererChanges();
            if (chart != null)
                chart.endRendererChanges();
        }
    }

    /// Replaces the resolved axis element for this renderer and inherited-y-axis descendants.
    ///
    /// Child renderers with an explicit `yAxisNumber` keep their own axis binding. The return value
    /// tells callers whether anything in the branch changed, which lets outer lifecycle code avoid
    /// unnecessary chart notifications.
    private boolean updateResolvedAxisElement(Chart.AxisElement axisElement) {
        boolean changed = axisElement != this.axisElement;
        boolean branchChanged = changed;
        this.axisElement = axisElement;
        Iterator<ChartRenderer> childIterator = getChildIterator();
        while (childIterator.hasNext()) {
            ChartRenderer child = childIterator.next();
            if (child != null && child.yAxisNumber < 0)
                branchChanged |= child.updateResolvedAxisElement(axisElement);
        }
        if (changed)
            updateViewableState();
        return branchChanged;
    }

    /// Copies the presentation settings preserved when one renderer implementation replaces another.
    ///
    /// The source renderer keeps ownership of its data source, axis assignment, styles, and child
    /// structure. Only the shared chart-level presentation state that the replacement should carry
    /// forward is copied here.
    void copyPresentationSettingsFrom(ChartRenderer renderer) {
        setRenderingHint(renderer.getRenderingHint());
        setAnnotation(renderer.getAnnotation());
        setDataLabelLayout(renderer.getDataLabelLayout());
        setDataLabeling(renderer.getDataLabeling());
    }

    private String formatXDataLabel(DataSetPoint dataPoint) {
        return getChart().formatXValue(dataPoint.getXData());
    }

    private String formatYDataLabel(DataSetPoint dataPoint) {
        return getChart().formatYValue(getYAxisNumber(), dataPoint.getYData());
    }

    private void attachDataSource(DataSource dataSource) {
        dataSource.addDataSourceListener(getOrCreateDataSourceListener());
        if (dataSource.size() > 0)
            dataSetsAdded(0, dataSource.size() - 1, new DataSet[0]);
    }

    /// Binds a newly created y-axis element to any renderer branch now resolved to that slot.
    ///
    /// This is used after [Chart#addYAxis(boolean, boolean)] extends the chart's y-axis list.
    void handleYAxisElementAdded(int yAxisNumber, Chart.AxisElement axisElement) {
        if (getYAxisNumber() == yAxisNumber) {
            this.axisElement = axisElement;
            updateViewableState();
        }
        Iterator<ChartRenderer> childIterator = getChildIterator();
        while (childIterator.hasNext()) {
            ChartRenderer child = childIterator.next();
            if (child != null)
                child.handleYAxisElementAdded(yAxisNumber, axisElement);
        }
    }

    private void detachDataSource(DataSource dataSource) {
        DataSet[] previousDataSets = dataSource.toArray();
        dataSource.removeDataSourceListener(getOrCreateDataSourceListener());
        if (previousDataSets.length > 0)
            dataSetsRemoved(0, previousDataSets.length - 1, previousDataSets);
    }

    /// Reacts to a change in chart ownership.
    ///
    /// By the time this hook runs, the backing chart field, resolved axis element, viewable state,
    /// and chart renderer-change batching have already been updated. The base implementation does
    /// not perform any extra work, so overrides can focus on rebinding chart-owned resources or
    /// refreshing cached rendering state. Delegating to `super` is harmless and preserves future
    /// compatibility.
    ///
    /// @param previousChart the old owner, or `null` if the renderer was detached
    /// @param chart         the new owner, or `null` if the renderer was detached
    protected void chartConnected(Chart previousChart, Chart chart) {
    }

    /// Adds this renderer's matching display items to `items`.
    ///
    /// The base implementation contributes at most one item by delegating to
    /// [#getDisplayItem(ChartDataPicker)]. Composite renderers commonly override this to emit all
    /// matching child items while preserving their own hit-test precedence rules.
    ///
    /// @param picker the picker describing the desired items and pick anchor
    /// @param items  the collection receiving matching display items
    public void collectDisplayItems(ChartDataPicker picker, ArrayList<DisplayPoint> items) {
        DisplayPoint displayItem = getDisplayItem(picker);
        if (displayItem != null)
            items.add(displayItem);
    }

    /// Resolves the label text for one displayed data point.
    ///
    /// The default implementation interprets [#getDataLabeling()] as follows:
    /// - [#DATA_LABEL] returns the dataset-provided label for the point
    /// - [#Y_VALUE_LABEL] formats only the point's y value through the owning chart
    /// - [#XY_VALUES_LABEL] formats both coordinates as `(x,y)`
    /// - [#X_VALUE_LABEL] formats only the x value through the owning chart
    /// - any other mode returns `null`, which instructs [DataLabelAnnotation] to skip the label
    ///
    /// Format-based modes require the renderer to be attached to a chart because the chart owns the
    /// active axis value formatters.
    @Override
    public String computeDataLabel(DataSetPoint dataPoint) {
        return switch (getDataLabeling()) {
            case DATA_LABEL -> dataPoint.getDataSet().getDataLabel(dataPoint.getIndex());
            case Y_VALUE_LABEL -> formatYDataLabel(dataPoint);
            case XY_VALUES_LABEL -> "(" + formatXDataLabel(dataPoint) + "," + formatYDataLabel(dataPoint) + ")";
            case X_VALUE_LABEL -> formatXDataLabel(dataPoint);
            default -> null;
        };
    }

    /// Returns the default display-space anchor for a point label.
    ///
    /// The base implementation ignores `labelSize` and centers the label on the point itself.
    /// Renderers with bars, slices, or offset markers override this to move labels away from the
    /// point while keeping [DataLabelAnnotation] agnostic of renderer geometry.
    @Override
    public Point computeDataLabelLocation(DisplayPoint point, Dimension labelSize) {
        return new Point(GraphicUtil.toInt(point.getXCoord()), GraphicUtil.toInt(point.getYCoord()));
    }

    /// Default legend-entry creation strategy.
    ///
    /// [Legend] asks the current [LegendEntryProvider] for entries. Unless a caller installs a
    /// custom provider through [#setLegendEntryProvider(LegendEntryProvider)], that provider
    /// delegates to this method.
    ///
    /// @return the legend entries contributed by this renderer
    protected Iterable<LegendEntry> createLegendEntries() {
        return LegendEntryProvider.EMPTY.createLegendEntries();
    }

    /// Notifies subclasses that datasets were inserted into the current data source.
    ///
    /// The newly inserted datasets are already readable from [#getDataSource()] at
    /// `firstIndex..lastIndex`. `previousDataSets` is the full source snapshot from before the
    /// insertion, which lets overrides compare old and new structure without retaining their own
    /// copy.
    ///
    /// @param firstIndex       the first inserted dataset index in the current source
    /// @param lastIndex        the last inserted dataset index in the current source
    /// @param previousDataSets the full data-source contents before the insertion
    protected void dataSetsAdded(int firstIndex, int lastIndex, DataSet[] previousDataSets) {
    }

    /// Signals that a batched data-source mutation sequence has completed.
    ///
    /// The base implementation closes the matching chart renderer-change batch. Overrides that
    /// maintain additional batch-scoped state should normally delegate to `super`.
    protected void dataSetsChangesBatchEnding() {
        endRendererChanges();
    }

    /// Signals that a batched data-source mutation sequence is starting.
    ///
    /// The base implementation opens a matching chart renderer-change batch. Overrides that
    /// maintain additional batch-scoped state should normally delegate to `super`.
    protected void dataSetsChangesBatchStarting() {
        startRendererChanges();
    }

    /// Notifies subclasses that datasets were removed from the current data source.
    ///
    /// Because the live source has already been updated, overrides must recover the removed
    /// datasets from `previousDataSets[firstIndex..lastIndex]`. The array itself is the full
    /// pre-change source snapshot, matching the contract used by [DataSourceEvent].
    ///
    /// @param firstIndex       the first removed dataset index in the pre-change source
    /// @param lastIndex        the last removed dataset index in the pre-change source
    /// @param previousDataSets the full data-source contents before the removal
    protected void dataSetsRemoved(int firstIndex, int lastIndex, DataSet[] previousDataSets) {
    }

    /// Permanently releases this renderer and its child renderer tree.
    ///
    /// Disposal is allowed only after the renderer has been detached both from its chart and from
    /// any parent renderer. The base implementation unregisters the current data source, disposes
    /// children recursively, and clears internal state.
    public void dispose() {
        if (chart != null)
            throw new IllegalStateException(
                    "dispose: renderer still connected to a chart. You need to remove it from the chart before being able to dispose it.");
        if (parent != null)
            throw new IllegalArgumentException("dispose: renderer is a child renderer, belonging to " + parent);
        if (flags != null) {
            if (dataSource != null) {
                DataSource detachedDataSource = dataSource;
                dataSource = new DefaultDataSource();
                detachDataSource(detachedDataSource);
            }
            Iterator<ChartRenderer> childIterator = getChildren().iterator();
            while (childIterator.hasNext()) {
                ChartRenderer child = childIterator.next();
                child.dispose();
            }
            axisElement = null;
            name = null;
            flags = null;
            dataSource = null;
            dataSourceListener = null;
            dataAnnotation = null;
            dataRenderingHint = null;
        }
    }

    /// Paints this renderer's primary visual representation.
    ///
    /// Implementations are expected to honor [#getClipRect()], the current plot rectangle, and the
    /// coordinate system resolved from the owning chart.
    ///
    /// @param g the target graphics context
    @Override
    public abstract void draw(Graphics g);

    /// Paints annotation overlays owned by this renderer.
    ///
    /// This pass is invoked separately from [#draw(Graphics)] so charts can exclude or relayout
    /// annotations independently of the main plot geometry.
    ///
    /// @param g the target graphics context
    @Override
    public abstract void drawAnnotations(Graphics g);

    /// Paints a legend marker representative of this renderer inside the supplied bounds.
    ///
    /// @param legend the legend entry requesting the marker
    /// @param g      the target graphics context
    /// @param x      the marker area's x coordinate
    /// @param y      the marker area's y coordinate
    /// @param w      the marker area's width
    /// @param h      the marker area's height
    @Override
    public abstract void drawLegendMarker(LegendEntry legend, Graphics g, int x, int y, int w, int h);

    /// Lazily creates the listener that bridges data-source events back into this renderer.
    private synchronized DataSourceListener getOrCreateDataSourceListener() {
        if (dataSourceListener == null)
            dataSourceListener = new DataSourceBridgeListener();
        return dataSourceListener;
    }

    /// Closes one renderer-change batch on the owning chart, if attached.
    protected void endRendererChanges() {
        if (chart != null)
            chart.endRendererChanges();
    }

    /// Returns this renderer's zero-based position among single renderers in chart traversal order.
    ///
    /// Composite renderers are ignored. The count stops once traversal reaches `this`, which keeps
    /// the ordinal aligned with the default series-color slot used by
    /// [#findAppropriateColor(Collection)].
    private int getSingleRendererOrdinal() {
        int singleRendererOrdinal = 0;
        Iterator<ChartRenderer> rendererIterator = getChart().getAllRendererIterator();
        while (rendererIterator.hasNext()) {
            ChartRenderer renderer = rendererIterator.next();
            if (renderer == this)
                break;
            if (renderer instanceof SingleChartRenderer)
                singleRendererOrdinal++;
        }
        return singleRendererOrdinal;
    }

    /// Chooses a fallback series color that avoids collisions with already claimed colors.
    ///
    /// The selection follows three tiers:
    /// - prefer the palette entry at this renderer's single-renderer ordinal so auto-assigned
    ///   colors remain stable while sibling ordering stays the same
    /// - otherwise return the first unused color from the fallback palette in palette order
    /// - if the entire palette is exhausted, synthesize a random color as a last resort
    ///
    /// The fallback palette starts with [#getDefaultColors()] when the renderer tree or owning
    /// chart provides one, and is then padded with the shared [ColorData] defaults so short custom
    /// palettes still degrade predictably.
    ///
    /// @param usedColors the colors already claimed by sibling renderers
    /// @return a preferred fallback color for this renderer
    protected Color findAppropriateColor(Collection<Color> usedColors) {
        Color[] inheritedPalette = getDefaultColors();
        List<Color> palette;
        if (inheritedPalette == null)
            palette = ColorData.getDefaultColors();
        else {
            palette = new ArrayList<>(Arrays.asList(inheritedPalette));
            List<Color> defaultPalette = ColorData.getDefaultColors();
            for (int colorIndex = palette.size(); colorIndex < defaultPalette.size(); colorIndex++)
                palette.add(defaultPalette.get(colorIndex));
        }

        int preferredOrdinal = getSingleRendererOrdinal();
        if (preferredOrdinal < palette.size()) {
            Color preferredColor = palette.get(preferredOrdinal);
            if (!usedColors.contains(preferredColor))
                return preferredColor;
        }

        HashSet<Color> availableColors = new HashSet<>(palette);
        availableColors.removeAll(usedColors);
        if (availableColors.isEmpty())
            return ColorUtil.getRandomColor();

        for (Color candidateColor : palette)
            if (availableColors.contains(candidateColor))
                return candidateColor;

        return ColorUtil.getRandomColor();
    }

    /// Recomputes the cached viewable flag and emits change type `3` only on transitions.
    private void updateViewableState() {
        boolean wasViewable = flags.getFlag(2);
        boolean isViewable = isVisible()
                && getDataSource().size() >= getMinDataSetCount()
                && getAxisElement() != null;
        flags.setFlag(2, isViewable);
        if (wasViewable != isViewable)
            triggerChange(3);
    }

    /// Returns the renderer-wide annotation object, if one has been assigned.
    public final DataAnnotation getAnnotation() {
        return dataAnnotation;
    }

    /// Returns the annotation associated with one dataset point.
    ///
    /// @param dataSet the dataset that owns the point
    /// @param index   the point index within `dataSet`
    /// @return the matching annotation, or `null` when none is present
    public abstract DataAnnotation getAnnotation(DataSet dataSet, int index);

    final Chart.AxisElement getAxisElement() {
        return axisElement;
    }

    /// Returns display bounds for a contiguous dataset slice, including annotations by default.
    ///
    /// @param dataSet    the dataset whose points should be measured
    /// @param firstIndex the first point index to consider
    /// @param lastIndex  the last point index to consider
    /// @param bounds     an optional reusable rectangle to receive the result
    /// @return the measured bounds, or `null` when the dataset is not rendered by this renderer
    public Rectangle2D getBounds(DataSet dataSet, int firstIndex, int lastIndex, Rectangle2D bounds) {
        return this.getBounds(dataSet, firstIndex, lastIndex, bounds, true);
    }

    /// Returns display bounds for a contiguous dataset slice.
    ///
    /// Implementations may reuse `bounds` as scratch storage and return it, or allocate a new
    /// rectangle when `bounds` is `null`.
    ///
    /// @param dataSet            the dataset whose points should be measured
    /// @param firstIndex         the first point index to consider
    /// @param lastIndex          the last point index to consider
    /// @param bounds             an optional reusable rectangle to receive the result
    /// @param includeAnnotations whether annotation extents should be merged into the result
    /// @return the measured bounds, or `null` when the dataset is not rendered by this renderer
    public abstract Rectangle2D getBounds(
            DataSet dataSet,
            int firstIndex,
            int lastIndex,
            Rectangle2D bounds,
            boolean includeAnnotations);

    /// Returns display bounds for all currently rendered content.
    ///
    /// @param bounds             an optional reusable rectangle to receive the result
    /// @param includeAnnotations whether annotation extents should be merged into the result
    /// @return the measured bounds
    public abstract Rectangle2D getBounds(Rectangle2D bounds, boolean includeAnnotations);

    /// Returns the chart that currently owns this renderer, directly or through its parent branch.
    public final Chart getChart() {
        if (chart != null)
            return chart;
        return (parent == null) ? null : parent.getChart();
    }

    /// Returns the child renderer at `index`.
    ///
    /// Renderers without composition support inherit the default empty child list and therefore
    /// always throw for any index.
    ///
    /// @param index the child index
    /// @return the child renderer
    /// @throws IndexOutOfBoundsException if `index` is negative or not less than
    ///                                       [#getChildCount()]
    public ChartRenderer getChild(int index) {
        return getChildren().get(index);
    }

    /// Returns the number of direct child renderers contributed by this renderer.
    public int getChildCount() {
        return getChildren().size();
    }

    /// Returns an iterator over this renderer's direct children.
    ///
    /// The default implementation returns an empty iterator.
    public Iterator<ChartRenderer> getChildIterator() {
        return Collections.emptyIterator();
    }

    /// Returns the direct child renderers associated with this renderer.
    ///
    /// The default implementation returns an empty, immutable list.
    public List<ChartRenderer> getChildren() {
        return Collections.emptyList();
    }

    /// Returns the display-space clip rectangle that this renderer should use for painting.
    ///
    /// The base implementation delegates to [#getPlotRect()]. Subclasses can narrow this when they
    /// intentionally paint only a subregion.
    public Rectangle getClipRect() {
        return getPlotRect();
    }

    /// Returns the coordinate system currently associated with this renderer's resolved axis
    /// element.
    public final CoordinateSystem getCoordinateSystem() {
        Chart.AxisElement axisElement = getAxisElement();
        return (axisElement == null) ? null : axisElement.getCoordinateSystem();
    }

    /// Returns the effective data-label mode for this renderer branch.
    ///
    /// A local override wins first. Otherwise the value inherits from the parent renderer and falls
    /// back to [#DEFAULT_LABELING] at the root. That makes composite renderers able to impose one
    /// labeling policy across all descendants unless a child explicitly opts out.
    public int getDataLabeling() {
        return (dataLabeling != null) ? dataLabeling
                : (getParent() == null) ? DEFAULT_LABELING : getParent().getDataLabeling();
    }

    /// Returns the effective data-label placement mode for this renderer branch.
    ///
    /// Layout mode follows the same inheritance chain as [#getDataLabeling()], ending at
    /// [#DEFAULT_LABEL_LAYOUT] when neither this renderer nor any parent overrides it.
    public int getDataLabelLayout() {
        return (dataLabelLayout != null) ? dataLabelLayout
                : (getParent() == null) ? DEFAULT_LABEL_LAYOUT : getParent().getDataLabelLayout();
    }

    /// Returns the position of `dataSet` in this renderer's direct live data source.
    ///
    /// This does not search parents or children. Composite renderers use it to decide whether a
    /// specific child still owns a dataset entry after source updates.
    public int getDataSetIndex(DataSet dataSet) {
        return (dataSource == null) ? -1 : dataSource.indexOf(dataSet);
    }

    /// Returns the dataset index in the highest renderer of this parent chain that still exposes
    /// `dataSet`.
    ///
    /// This is useful for composite renderers that surface one logical dataset through several
    /// nested renderer layers but still need to mutate the branch node that owns the direct
    /// source-data entry.
    public int getDataSetMainIndex(DataSet dataSet) {
        ChartRenderer mainRenderer = getDataSetMainRenderer(dataSet);
        return (mainRenderer == null) ? -1 : mainRenderer.getDataSource().indexOf(dataSet);
    }

    /// Returns the highest renderer in this branch that still exposes `dataSet` directly.
    ///
    /// A renderer qualifies only if its own [DataSource] contains `dataSet`. The search then walks
    /// toward the root so composite renderers can delegate edits and ownership-sensitive queries to
    /// the branch node that actually stores the dataset reference.
    public ChartRenderer getDataSetMainRenderer(DataSet dataSet) {
        if (dataSource.indexOf(dataSet) < 0)
            return null;
        if (parent != null) {
            ChartRenderer mainRenderer = parent.getDataSetMainRenderer(dataSet);
            if (mainRenderer != null)
                return mainRenderer;
        }
        return this;
    }

    /// Returns the live data source currently driving this renderer.
    public final DataSource getDataSource() {
        return dataSource;
    }

    /// Returns the default color palette inherited from the parent renderer or owning chart.
    public Color[] getDefaultColors() {
        if (parent != null)
            return parent.getDefaultColors();
        if (chart == null)
            return null;
        return chart.getDefaultColors();
    }

    /// Returns a renderer-specific fallback legend title, or `null` when none is defined.
    public String getDefaultLegendTitle() {
        return null;
    }

    /// Returns one display item matching `picker`, or `null` when this renderer has no match.
    ///
    /// Implementations may choose whichever of their internal items should represent the renderer
    /// for this pick request. Aggregating helpers such as [#getDisplayItem(Iterator, ChartDataPicker)]
    /// then resolve precedence between renderers by traversal order.
    public abstract DisplayPoint getDisplayItem(ChartDataPicker picker);

    /// Returns the display-space representation of one dataset point.
    ///
    /// @param dataSet the dataset that owns the point
    /// @param index   the point index within `dataSet`
    /// @return the matching display point, or `null` when it cannot be resolved
    public abstract DisplayPoint getDisplayPoint(DataSet dataSet, int index);

    /// Returns the legend-entry provider currently associated with this renderer.
    public LegendEntryProvider getLegendEntryProvider() {
        return legendEntryProvider;
    }

    /// Returns the style used to paint legend markers for this renderer.
    ///
    /// The base implementation reuses style slot `0` and falls back to an empty [PlotStyle] when
    /// no concrete style is available.
    public PlotStyle getLegendStyle() {
        PlotStyle legendStyle = getStyle(0);
        return (legendStyle != null) ? legendStyle : new PlotStyle();
    }

    /// Returns the label text to show for `legend`.
    ///
    /// The default implementation mirrors [#getName()].
    public String getLegendText(LegendEntry legend) {
        return getName();
    }

    /// Returns the minimum dataset count required before this renderer can become viewable.
    public int getMinDataSetCount() {
        return 1;
    }

    /// Returns the explicit renderer name, if one has been assigned.
    public String getName() {
        return name;
    }

    /// Returns the nearest matching logical item and optionally reports its renderer-specific distance.
    ///
    /// The reported distance is compared only against values from other renderers that implement
    /// the same callback. Implementations that find no match should leave callers with no winner
    /// and, when `distanceHolder` is supplied, typically store [Double#POSITIVE_INFINITY].
    ///
    /// @param picker         the picker describing the reference location and match rules
    /// @param distanceHolder optional single-element array receiving the measured distance
    /// @return the nearest match, or `null` when none qualifies
    public abstract DisplayPoint getNearestItem(ChartDataPicker picker, double[] distanceHolder);

    /// Returns the nearest display point to the location described by `picker`.
    ///
    /// This method models point proximity rather than renderer-item proximity and is typically used
    /// for point snapping or point-oriented data inspection modes.
    public abstract DisplayPoint getNearestPoint(ChartDataPicker picker);

    /// Returns the parent renderer, or `null` when this renderer is chart-rooted or detached.
    public ChartRenderer getParent() {
        return parent;
    }

    /// Returns the plot rectangle resolved from the owning chart or parent renderer.
    public final Rectangle getPlotRect() {
        if (parent != null)
            return parent.getPlotRect();
        return chart.getChartArea().getPlotRect();
    }

    /// Returns extra layout margins required by currently viewable child renderers.
    ///
    /// The base implementation contributes no margins of its own and instead merges child requests.
    /// Chart layout uses the merged result to keep labels, markers, or exploded geometry from being
    /// clipped by the plot rectangle.
    public Insets getPreferredMargins() {
        Insets preferredMargins = new Insets(0, 0, 0, 0);
        Iterator<ChartRenderer> childIterator = getChildIterator();
        while (childIterator.hasNext()) {
            ChartRenderer child = childIterator.next();
            if (child.isViewable())
                preferredMargins = GraphicUtil.mergeInsets(preferredMargins, child.getPreferredMargins());
        }
        return preferredMargins;
    }

    /// Returns the renderer-wide rendering hint, if one has been assigned.
    public final DataRenderingHint getRenderingHint() {
        return dataRenderingHint;
    }

    /// Returns the rendering hint for one dataset.
    public abstract DataRenderingHint getRenderingHint(DataSet dataSet);

    /// Returns the rendering hint for one dataset point.
    public abstract DataRenderingHint getRenderingHint(DataSet dataSet, int index);

    /// Returns the style for one dataset point.
    public abstract PlotStyle getStyle(DataSet dataSet, int index);

    /// Returns the style stored in one style slot, or `null` when the slot is absent.
    ///
    /// Subclasses define how style slots map to datasets, segments, or marker roles. The base
    /// helper only performs bounds lookup against [#getStyles()].
    public PlotStyle getStyle(int index) {
        PlotStyle[] styles = getStyles();
        if (styles == null || index >= styles.length)
            return null;
        return styles[index];
    }

    /// Returns all style slots managed by this renderer.
    public abstract PlotStyle[] getStyles();

    /// Returns the dataset view this renderer expects callers to use for `dataSet`.
    ///
    /// Some renderers expose transformed or derived datasets to editing and picking code while
    /// still storing the original source dataset in the underlying [DataSource]. When no such view
    /// is active, callers get `dataSet` back unchanged.
    public final DataSet getVirtualDataSet(DataSet dataSet) {
        VirtualDataSet virtualDataSet = DataSetRendererProperty.getVirtualDataSet(this, dataSet);
        return (virtualDataSet != null) ? virtualDataSet : dataSet;
    }

    /// Returns the x-axis data range implied by `rangeContext`.
    public abstract DataInterval getXRange(DataInterval rangeContext);

    /// Returns the effective x-shift after inheriting offsets from parent renderers.
    public double getXShift() {
        return (parent == null) ? xShift : xShift + parent.getXShift();
    }

    /// Returns the resolved y-axis number for this renderer.
    ///
    /// Negative values mean the renderer is not yet bound to a concrete y-axis.
    public final int getYAxisNumber() {
        if (yAxisNumber >= 0)
            return yAxisNumber;
        if (parent == null)
            return -1;
        return parent.getYAxisNumber();
    }

    /// Returns the y-axis data range implied by `rangeContext`.
    public abstract DataInterval getYRange(DataInterval rangeContext);

    /// Returns the y-axis data range implied by `xRange` and an existing `rangeContext`.
    public abstract DataInterval getYRange(DataInterval xRange, DataInterval rangeContext);

    /// Returns whether this renderer can expose editable or displayable annotations.
    public abstract boolean holdsAnnotations();

    /// Returns whether `dataSet` is present in this renderer's direct data source.
    public final boolean isDisplayingDataSet(DataSet dataSet) {
        return dataSource != null && dataSource.contains(dataSet);
    }

    /// Returns whether this renderer should contribute entries to the chart legend.
    ///
    /// When no local value has been set, the setting inherits from the parent renderer or defaults
    /// to `true` at the root.
    public boolean isLegended() {
        return (legended != null) ? legended : getParent() == null || getParent().isLegended();
    }

    /// Returns whether this renderer is currently eligible to participate in painting and range
    /// calculation.
    public boolean isViewable() {
        return flags.getFlag(2);
    }

    /// Returns the explicit visibility flag for this renderer.
    public final boolean isVisible() {
        return flags.getFlag(1);
    }

    /// Sets a renderer-wide annotation object and notifies the owning chart.
    public void setAnnotation(DataAnnotation annotation) {
        if (annotation != dataAnnotation) {
            dataAnnotation = annotation;
            triggerChange(8);
        }
    }

    /// Assigns an annotation to all relevant points in `dataSet`.
    public abstract void setAnnotation(DataSet dataSet, DataAnnotation annotation);

    /// Assigns an annotation to one dataset point.
    public abstract void setAnnotation(DataSet dataSet, int index, DataAnnotation annotation);

    /// Sets the data-label content mode used by this renderer.
    ///
    /// Layout is revalidated because label text can change the space required around the plot.
    public void setDataLabeling(int dataLabeling) {
        if (this.dataLabeling != null && dataLabeling == this.dataLabeling)
            return;
        this.dataLabeling = dataLabeling;
        Chart chart = getChart();
        if (chart != null)
            chart.getChartArea().revalidateLayout();
    }

    /// Sets the data-label placement mode used by this renderer.
    ///
    /// Layout is revalidated because outside or centered labels can change the plot margins needed
    /// by this renderer branch.
    public void setDataLabelLayout(int dataLabelLayout) {
        if (this.dataLabelLayout != null && dataLabelLayout == this.dataLabelLayout)
            return;
        this.dataLabelLayout = dataLabelLayout;
        Chart chart = getChart();
        if (chart != null)
            chart.getChartArea().revalidateLayout();
    }

    /// Compatibility alias for [#setDataLabeling(int)].
    public void setDataLabelling(int dataLabeling) {
        setDataLabeling(dataLabeling);
    }

    /// Updates one point in source-data coordinates when this renderer displays `dataSet` directly.
    ///
    /// The base implementation writes back to the addressed dataset entry without any mapping.
    /// Renderers that expose virtual datasets override this method to translate the edit back into
    /// the current source dataset first.
    public void setDataPoint(DataSet dataSet, int index, double x, double y) {
        if (isDisplayingDataSet(dataSet)) {
            DataSetPoint dataPoint = new DataSetPoint(dataSet, index);
            dataPoint.setData(x, y);
        }
    }

    /// Replaces the live data source used by this renderer.
    ///
    /// The old source is detached, the new source is attached, dataset lifecycle callbacks are
    /// emitted as needed, and the chart is notified so data ranges can be recomputed.
    ///
    /// @param dataSource the new data source to use
    /// @throws IllegalArgumentException if `dataSource` is `null`
    public void setDataSource(DataSource dataSource) {
        if (dataSource == null)
            throw new IllegalArgumentException("DataSource cannot be NULL");

        DataSource oldDataSource = getDataSource();
        if (oldDataSource == dataSource)
            return;

        this.dataSource = dataSource;
        if (oldDataSource != null)
            detachDataSource(oldDataSource);
        attachDataSource(dataSource);
        triggerChange(7);
        updateViewableState();
    }

    /// Updates one point using display-space coordinates rather than raw data coordinates.
    public abstract void setDisplayPoint(DataSet dataSet, int index, double x, double y);

    /// Controls whether this renderer should contribute legend entries.
    public void setLegended(boolean legended) {
        if (legended == isLegended())
            return;
        this.legended = Boolean.valueOf(legended);
        triggerChange(6);
    }

    /// Replaces the provider used to generate legend entries for this renderer.
    public void setLegendEntryProvider(LegendEntryProvider provider) {
        legendEntryProvider = Objects.requireNonNull(provider, "legendEntryProvider");
    }

    /// Sets the display name used by default legend text and UI labels.
    public void setName(String name) {
        block:
        {
            if (name == null) {
                if (this.name == null)
                    break block;
            } else if (name.equals(this.name))
                break block;
            this.name = name;
            triggerChange(6);
        } // end block

    }

    /// Attaches this renderer to `parent` or detaches it when `parent` is `null`.
    ///
    /// A renderer cannot be both chart-owned and child-owned at the same time.
    /// Child renderers inherit chart ownership from `parent` immediately.
    ///
    /// @throws IllegalArgumentException if this renderer is already attached elsewhere
    public void setParent(ChartRenderer parent) {
        if (parent == null) {
            this.parent = null;
            this.setChartOwner(null, -1);
        } else {
            if (this.parent != null)
                throw new IllegalArgumentException("Renderer already connected to a parent");
            if (getChart() != null)
                throw new IllegalArgumentException("Renderer already connected to a chart");
            this.parent = parent;
            this.setChartOwner(parent.getChart(), -1);
        }
    }

    /// Sets a renderer-wide rendering hint and repaints the chart area when attached.
    public void setRenderingHint(DataRenderingHint renderingHint) {
        if (renderingHint != dataRenderingHint) {
            dataRenderingHint = renderingHint;
            if (getChart() != null)
                getChart().getChartArea().repaint();
        }
    }

    /// Sets the rendering hint for one dataset.
    public abstract void setRenderingHint(DataSet dataSet, DataRenderingHint renderingHint);

    /// Sets the rendering hint for one dataset point.
    public abstract void setRenderingHint(DataSet dataSet, int index, DataRenderingHint renderingHint);

    /// Replaces one style slot and delegates persistence to [#setStyles(PlotStyle[])].
    public void setStyle(int index, PlotStyle style) {
        PlotStyle[] styles = getStyles();
        if (styles != null)
            if (index < styles.length) {
                styles[index] = style;
                setStyles(styles);
            }
    }

    /// Replaces all style slots managed by this renderer.
    public abstract void setStyles(PlotStyle[] styles);

    /// Sets the explicit visibility flag and reevaluates the renderer's viewable state.
    public void setVisible(boolean visible) {
        if (visible == isVisible())
            return;
        flags.setFlag(1, visible);
        updateViewableState();
    }

    /// Sets the renderer-local x offset applied before data is projected into display space.
    ///
    /// Child renderers inherit and accumulate parent x-shifts through [#getXShift()]. Changing the
    /// value therefore affects both this renderer's own geometry and any descendant projection that
    /// relies on the inherited branch offset.
    public void setXShift(double xShift) {
        if (this.xShift != xShift) {
            this.xShift = xShift;
            triggerChange(5);
        }
    }

    /// Assigns the preferred y-axis number for this renderer branch.
    ///
    /// When the resolved axis element changes, the chart is notified so data ranges and painting
    /// can be updated.
    public void setYAxisNumber(int yAxisNumber) {
        if (yAxisNumber == this.yAxisNumber)
            return;
        int previousResolvedYAxisNumber = getYAxisNumber();
        Chart.AxisElement previousAxisElement = axisElement;
        this.yAxisNumber = yAxisNumber;
        int resolvedYAxisNumber = getYAxisNumber();
        if (resolvedYAxisNumber != previousResolvedYAxisNumber) {
            Chart.AxisElement resolvedAxisElement = resolveAxisElement();
            if (resolvedAxisElement != previousAxisElement && updateResolvedAxisElement(resolvedAxisElement))
                triggerChange(5);
        }
    }

    /// Opens one renderer-change batch on the owning chart, if attached.
    protected void startRendererChanges() {
        if (chart != null)
            chart.startRendererChanges();
    }

    private void applyXShift(DoublePoints points) {
        double xShift = getXShift();
        if (xShift == 0.0)
            return;
        for (int pointIndex = 0; pointIndex < points.size; pointIndex++)
            points.setX(pointIndex, points.getX(pointIndex) + xShift);
    }

    /// Converts `points` from display coordinates into data coordinates in place.
    ///
    /// The method is a no-op while the renderer is detached because projector and coordinate-system
    /// resolution both come from the owning chart.
    public void toData(DoublePoints points) {
        Chart chart = getChart();
        if (chart != null)
            chart.getProjector().toData(points, getPlotRect(), getCoordinateSystem());
    }

    /// Converts `points` from data coordinates into display coordinates in place.
    ///
    /// The renderer's effective branch x-shift is applied before projection. Like [#toData(DoublePoints)],
    /// the method does nothing while detached from a chart.
    public void toDisplay(DoublePoints points) {
        Chart chart = getChart();
        if (chart == null)
            return;
        applyXShift(points);
        chart.getProjector().toDisplay(points, getPlotRect(), getCoordinateSystem());
    }

    /// Converts `points` from data coordinates into display coordinates using an explicit
    /// projection context.
    ///
    /// The renderer's effective branch x-shift is still applied before the supplied projector runs.
    /// This lets callers reuse renderer-local projection rules with a clipped plot rectangle or a
    /// temporary coordinate system while still honoring the renderer's own x offset.
    public void toDisplay(
            DoublePoints points,
            ChartProjector projector,
            Rectangle plotRect,
            CoordinateSystem coordinateSystem) {
        if (getChart() == null)
            return;
        applyXShift(points);
        projector.toDisplay(points, plotRect, coordinateSystem);
    }

    /// Notifies the owning chart that this renderer changed.
    ///
    /// `changeType` participates in an internal chart protocol used to choose between data-range
    /// recomputation, repaint, and layout invalidation.
    public void triggerChange(int changeType) {
        if (chart != null)
            chart.handleRendererChanged(this, changeType);
    }
}
