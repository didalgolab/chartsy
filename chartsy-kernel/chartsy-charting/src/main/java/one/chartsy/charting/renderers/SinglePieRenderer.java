package one.chartsy.charting.renderers;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import one.chartsy.charting.Chart;
import one.chartsy.charting.ChartProjector;
import one.chartsy.charting.ColorData;
import one.chartsy.charting.CoordinateSystem;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.DisplayPoint;
import one.chartsy.charting.DoublePoint;
import one.chartsy.charting.DoublePoints;
import one.chartsy.charting.LegendEntry;
import one.chartsy.charting.PieRendererLegendItem;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.AbstractDataSet;
import one.chartsy.charting.data.DataPoints;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSetPoint;
import one.chartsy.charting.event.DataSetContentsEvent;
import one.chartsy.charting.event.DataSetPropertyEvent;
import one.chartsy.charting.graphic.DataRenderingHint;
import one.chartsy.charting.renderers.internal.DataSetRendererProperty;
import one.chartsy.charting.renderers.internal.PieSliceInfo;
import one.chartsy.charting.renderers.internal.VirtualDataSet;
import one.chartsy.charting.util.ColorUtil;
import one.chartsy.charting.util.DoubleArray;
import one.chartsy.charting.util.GraphicUtil;
import one.chartsy.charting.util.MathUtil;
import one.chartsy.charting.util.text.NumberFormatFactory;

/// Single-dataset renderer that draws one dataset as a pie or donut ring.
///
/// The renderer interprets each defined y-value in the source dataset as a slice weight. It wraps
/// that dataset in a [PieDataSet] whose x-values are cumulative slice-end percentages on a shared
/// `0..100` sweep, then combines that angular span with the current radial range to obtain one
/// projected slice shape per source point.
///
/// [PieChartRenderer] uses [#setRadialRange(DataInterval)] to stack multiple `SinglePieRenderer`
/// children into concentric rings. Slice-specific [PieSliceInfo] metadata can override style and
/// explode ratio, while legend text falls back to the source dataset's data labels unless some
/// internal caller installs a slice-specific override. When any slice is exploded, the renderer
/// shrinks the effective slice thickness just enough to keep the farthest displaced slice inside
/// the assigned radial range.
///
/// Legend rows are created per slice rather than per dataset, and percentage labeling mode formats
/// each slice against the current total of defined source values. Instances are mutable renderer
/// models and are not thread-safe.
public class SinglePieRenderer extends SingleChartRenderer {
    private static boolean isDefinedSliceValue(double value, Double undefinedValue) {
        return !Double.isNaN(value)
                && (undefinedValue == null || value != undefinedValue.doubleValue());
    }

    /// Virtual dataset that exposes one source dataset as cumulative slice boundaries.
    ///
    /// Each x-value is the cumulative end percentage for one slice on the `0..100` sweep. The
    /// y-value is the current outer radius of the owning [SinglePieRenderer], which lets projector
    /// code derive one sector shape from the angular span plus the renderer's current radial range.
    ///
    /// The virtual dataset does not currently advertise editability, but its coordinate-unmapping
    /// logic still defines how a slice-percentage edit would be translated back into one source
    /// y-value while leaving all other slice values unchanged.
    class PieDataSet extends VirtualDataSet {
        private double totalValue;
        private final DoubleArray sliceEnds;

        PieDataSet(DataSet dataSet) {
            sliceEnds = new DoubleArray();
            super.setMaxDataSetCount(1);
            super.addDataSet(dataSet);
        }

        /// Returns the slice's share of the total defined value as a percentage.
        double getSlicePercent(int sliceIndex) {
            if (super.getDataSetCount() == 0)
                return 0.0;

            DataSet dataSet = super.getDataSet(0);
            double value = dataSet.getYData(sliceIndex);
            Double undefinedValue = dataSet.getUndefValue();
            if (undefinedValue != null && value == undefinedValue)
                value = Double.NaN;
            return value * 100.0 / totalValue;
        }

        private void refreshSliceEnds(int firstSliceIndex, int lastSliceIndex, boolean resetBuffer) {
            if (resetBuffer)
                sliceEnds.clear();
            totalValue = computeSliceEnds(sliceEnds, firstSliceIndex, lastSliceIndex);
        }

        /// Returns the raw partial sum through `sliceIndex`.
        double getPartialSum(int sliceIndex) {
            if (super.getDataSetCount() == 0)
                return 0.0;

            DataSet dataSet = super.getDataSet(0);
            double partialSum = 0.0;
            for (int index = 0; index <= sliceIndex; index++)
                partialSum += dataSet.getYData(index);
            return partialSum;
        }

        @Override
        protected void computeLimits(DataInterval xRange, DataInterval yRange) {
            if (super.size() <= 0)
                xRange.empty();
            else
                xRange.set(0.0, 100.0);
            yRange.set(radialRange.getMin(), radialRange.getMax());
        }

        protected double computeSliceEnds(DoubleArray sliceEnds, int firstSliceIndex, int lastSliceIndex) {
            DataSet dataSet = super.getDataSet(0);
            Double undefinedValue = dataSet.getUndefValue();
            int sliceCount = dataSet.size();
            int cachedSize = sliceEnds.size();
            double total = 0.0;
            double partialSum = 0.0;

            for (int index = 0; index < sliceCount; index++) {
                double value = dataSet.getYData(index);
                if (!isDefinedSliceValue(value, undefinedValue))
                    continue;

                if (index < firstSliceIndex)
                    partialSum += value;
                total += value;
            }

            double percentScale = 100.0 / total;
            for (int index = firstSliceIndex; index <= lastSliceIndex; index++) {
                double value = dataSet.getYData(index);
                if (!isDefinedSliceValue(value, undefinedValue)) {
                    value = AbstractDataSet.DEFAULT_UNDEF_VALUE;
                } else if (index == sliceCount - 1) {
                    value = 100.0;
                } else {
                    partialSum += value;
                    value = MathUtil.clamp(partialSum * percentScale, 0.0, 100.0);
                }

                if (index >= cachedSize)
                    sliceEnds.add(value);
                else
                    sliceEnds.set(index, value);
            }
            return total;
        }

        @Override
        public void dataSetContentsChanged(DataSetContentsEvent event) {
            super.dataSetContentsChanged(event);
            switch (event.getType()) {
                case DataSetContentsEvent.BATCH_BEGIN,
                     DataSetContentsEvent.BATCH_END,
                     DataSetContentsEvent.BEFORE_DATA_CHANGED -> {
                }
                case DataSetContentsEvent.DATA_LABEL_CHANGED -> triggerChange(DataSetContentsEvent.FULL_UPDATE);
                default -> {
                    refreshSliceEnds(
                            0,
                            super.size() - 1,
                            event.getType() == DataSetContentsEvent.FULL_UPDATE
                    );
                    super.fireDataSetContentsEvent(new DataSetContentsEvent(this));
                    if (event.getType() == DataSetContentsEvent.FULL_UPDATE
                            || event.getType() == DataSetContentsEvent.DATA_ADDED) {
                        triggerChange(DataSetContentsEvent.FULL_UPDATE);
                    }
                }
            }
        }

        @Override
        protected void dataSetPropertyChanged(DataSetPropertyEvent event) {
            if (event.getDataSet() == super.getDataSet(0))
                super.fireDataSetPropertyEvent(new DataSetPropertyEvent(this, event));
        }

        @Override
        protected void dataSetsChanged() {
            super.dataSetsChanged();
            int size = super.size();
            if (size > 0)
                refreshSliceEnds(0, size - 1, true);
        }

        @Override
        public String getName() {
            if (super.getDataSetCount() != 1)
                return null;
            return super.getDataSet(0).getName();
        }

        /// Returns the previous defined cumulative slice boundary before `sliceIndex`.
        public double getPreviousSliceEnd(int sliceIndex) {
            if (sliceIndex == 0)
                return 0.0;

            double previousSliceEnd = getXData(sliceIndex - 1);
            for (int index = sliceIndex - 1;
                 !isDefinedSliceValue(previousSliceEnd, getUndefValue());
                 index--) {
                if (index == 0)
                    return 0.0;
                previousSliceEnd = getXData(index - 1);
            }
            return previousSliceEnd;
        }

        @Override
        public Double getUndefValue() {
            if (super.getDataSetCount() == 0)
                return null;

            DataSet dataSet = super.getDataSet(0);
            if (dataSet != null && dataSet.getUndefValue() != null)
                return AbstractDataSet.DEFAULT_UNDEF_VALUE;
            return null;
        }

        @Override
        public double getXData(int index) {
            return sliceEnds.get(index);
        }

        @Override
        public double getYData(int index) {
            return radialRange.getMax();
        }

        @Override
        public boolean isXValuesSorted() {
            return true;
        }

        @Override
        public void map(DataSetPoint point) {
            point.dataSet = this;
        }

        @Override
        public boolean mapsMonotonically() {
            return true;
        }

        @Override
        public void unmap(DataSetPoint point) {
            if (super.getDataSetCount() == 1)
                point.dataSet = super.getDataSet(0);
        }

        @Override
        public void unmap(DataSetPoint point, DoublePoint editablePoint) {
            unmap(point);
            DataSet dataSet = point.dataSet;
            editablePoint.x = point.getXData();
            if (editablePoint.x > 100.0) {
                editablePoint.y = point.getYData();
                return;
            }

            int size = dataSet.size();
            double previousSliceEnd = (point.index == 0) ? 0.0 : getXData(point.index - 1);
            double slicePercent = editablePoint.x - previousSliceEnd;
            if (slicePercent <= 0.0) {
                editablePoint.y = 0.0;
                return;
            }

            double sumBefore = 0.0;
            double sumAfter = 0.0;
            Double undefinedValue = dataSet.getUndefValue();
            for (int index = 0; index < point.index; index++) {
                double value = dataSet.getYData(index);
                if (isDefinedSliceValue(value, undefinedValue))
                    sumBefore += value;
            }
            for (int index = point.index + 1; index < size; index++) {
                double value = dataSet.getYData(index);
                if (isDefinedSliceValue(value, undefinedValue))
                    sumAfter += value;
            }
            editablePoint.y = slicePercent * (sumBefore + sumAfter) / (100.0 - slicePercent);
        }
    }

    /// Reusable logical item that carries the projected shape of one slice.
    ///
    /// The item keeps only the projected [Shape]. Stroke handling is delegated partly to the owning
    /// renderer because [#isStrokeOn()] acts as a renderer-wide outline toggle even when a
    /// slice-specific [PlotStyle] disables its own stroke.
    class PieItem implements SingleChartRenderer.Item {
        Shape shape;

        PieItem() {
        }

        @Override
        public double distance(PlotStyle style, double x, double y, boolean outlineOnly) {
            return style.distanceToShape(shape, x, y, outlineOnly);
        }

        @Override
        public void draw(Graphics g, PlotStyle style) {
            if (isStrokeOn()) {
                style.plotShape(g, shape);
                if (!style.isStrokeOn())
                    SinglePieRenderer.this.getStyle().draw(g, shape);
            } else if (style.isFillOn()) {
                style.fill(g, shape);
            }
        }

        @Override
        public Rectangle2D getBounds(PlotStyle style, boolean expand, Rectangle2D bounds) {
            return style.getShapeBounds(shape);
        }
    }

    /// Default explode ratio applied by [#setExploded(int, boolean)].
    public static final int DEFAULT_EXPLODE_RATIO = 20;

    private boolean strokeOn;
    private DataInterval radialRange;
    private Map<Integer, PieSliceInfo> sliceInfoByIndex;
    private int maxExplodeRatio;

    /// Creates a pie renderer with the default style and the full `0..100` radial span.
    public SinglePieRenderer() {
        this(null);
    }

    /// Creates a pie renderer with an explicit base style and the full `0..100` radial span.
    public SinglePieRenderer(PlotStyle style) {
        this(style, 0.0, 100.0);
    }

    /// Creates a pie renderer with an explicit base style and radial span.
    SinglePieRenderer(PlotStyle style, double minRadius, double maxRadius) {
        super(style);
        strokeOn = true;
        radialRange = new DataInterval(minRadius, maxRadius);
    }

    /// Replaces this renderer's assigned radial range with a defensive copy.
    void setRadialRange(DataInterval radialRange) {
        this.radialRange = new DataInterval(radialRange);
    }

    private PieSliceInfo getSliceInfo(int sliceIndex, boolean create) {
        PieSliceInfo sliceInfo = null;
        if (sliceInfoByIndex != null) {
            sliceInfo = sliceInfoByIndex.get(sliceIndex);
            if (create && sliceInfo == null) {
                sliceInfo = new PieSliceInfo();
                sliceInfoByIndex.put(sliceIndex, sliceInfo);
            }
        } else if (create) {
            sliceInfo = new PieSliceInfo();
            sliceInfoByIndex = new TreeMap<>();
            sliceInfoByIndex.put(sliceIndex, sliceInfo);
        }
        return sliceInfo;
    }

    /// Returns the plot rectangle translated for the current explode ratio of one slice.
    Rectangle getSlicePlotRect(int sliceIndex, double middleAngle, DataInterval sliceYRange) {
        Rectangle slicePlotRect = super.getPlotRect();
        int explodeRatio = getExplodeRatio(sliceIndex);
        if (explodeRatio != 0) {
            DoublePoints explodedOffset = new DoublePoints(2);
            explodedOffset.add(middleAngle, sliceYRange.getMin());
            explodedOffset.add(
                    explodedOffset.getX(0),
                    explodedOffset.getY(0) + sliceYRange.getLength() * explodeRatio / 100.0);
            super.getChart().getProjector().toDisplay(explodedOffset, slicePlotRect, super.getCoordinateSystem());
            slicePlotRect = (Rectangle) slicePlotRect.clone();
            slicePlotRect.translate(
                    GraphicUtil.toInt(explodedOffset.getX(1) - explodedOffset.getX(0)),
                    GraphicUtil.toInt(explodedOffset.getY(1) - explodedOffset.getY(0)));
            explodedOffset.dispose();
        }
        return slicePlotRect;
    }

    /// Narrows the visible angular range to the inclusive slice span.
    void showSlices(int firstSliceIndex, int lastSliceIndex) {
        if (lastSliceIndex < firstSliceIndex)
            throw new IllegalArgumentException("First slice must be before last slice");
        if (super.getChart() != null) {
            if (lastSliceIndex >= getPieDataSet().size())
                throw new IllegalArgumentException("No such slice: " + lastSliceIndex);
            double firstBoundary = (firstSliceIndex == 0) ? 0.0 : getPieDataSet().getXData(firstSliceIndex - 1);
            super.getXAxis().setVisibleRange(firstBoundary, getPieDataSet().getXData(lastSliceIndex));
        }
    }

    @Override
    void forEachItem(SingleChartRenderer.Points points, SingleChartRenderer.ItemAction action) {
        int count = points.size();
        ChartProjector projector = super.getChart().getProjector();
        CoordinateSystem coordinateSystem = super.getCoordinateSystem();
        DataWindow sliceWindow = new DataWindow(0.0, 0.0, 0.0, 0.0);
        PieItem pieItem = new PieItem();
        boolean resolveRenderingHints = super.hasRenderingHints();
        int[] indices = points.getIndices();
        double[] sliceEnds = points.getXData();
        PieDataSet pieDataSet = (PieDataSet) points.getDataSet();
        DisplayPoint displayPoint = new DisplayPoint(this, pieDataSet);
        Double undefinedValue = pieDataSet.getUndefValue();
        double undefinedMarker = (undefinedValue == null) ? 0.0 : undefinedValue;
        int pointIndex = 0;
        while (true) {
            if (pointIndex >= count)
                break;
            int sliceIndex = indices[pointIndex];
            double sliceEnd = sliceEnds[pointIndex];
            block:
            {
                if (undefinedValue != null)
                    if (sliceEnd == undefinedMarker)
                        break block;
                if (!Double.isNaN(sliceEnd)) {
                    if (sliceIndex == 0)
                        sliceWindow.xRange.set(0.0, sliceEnd);
                    else {
                        double previousSliceEnd = pieDataSet.getPreviousSliceEnd(sliceIndex);
                        sliceWindow.xRange.set(previousSliceEnd, sliceEnd);
                    }
                    if (!sliceWindow.xRange.isEmpty()) {
                        sliceWindow.yRange = getYRange(pointIndex, sliceWindow.yRange);
                        Rectangle slicePlotRect =
                                getSlicePlotRect(sliceIndex, sliceWindow.xRange.getMiddle(), sliceWindow.yRange);
                        pieItem.shape = projector.getShape(sliceWindow, slicePlotRect, coordinateSystem);
                        PlotStyle sliceStyle = getSliceStyle(sliceIndex);
                        if (resolveRenderingHints) {
                            displayPoint.dataSet = pieDataSet;
                            displayPoint.set(sliceIndex, 0.0, 0.0);
                            DataRenderingHint renderingHint = super.getRenderingHint(displayPoint);
                            if (renderingHint != null)
                                sliceStyle = renderingHint.getStyle(displayPoint, sliceStyle);
                        }
                        if (sliceStyle != null)
                            action.processItem(points, pointIndex, pieItem, sliceStyle);
                    }
                }
            } // end block

            pointIndex++;
        }
    }

    @Override
    PlotStyle getBaseStyle(DataSet dataSet, int index) {
        return getSliceStyle(index).setStrokeOn(isStrokeOn());
    }

    private boolean updateSliceExplodeRatio(int sliceIndex, int explodeRatio) {
        if (explodeRatio >= 0)
            if (explodeRatio <= 100) {
                PieSliceInfo sliceInfo = getSliceInfo(sliceIndex, true);
                boolean changed = sliceInfo.explodeRatio != explodeRatio;
                sliceInfo.explodeRatio = explodeRatio;
                return changed;
            }
        throw new IllegalArgumentException("Value must be in [0..100]");
    }

    @Override
    protected void chartConnected(Chart previousChart, Chart newChart) {
        super.chartConnected(previousChart, newChart);
        if (newChart != null)
            ensureDefaultSliceStyles();
    }

    /// Returns the formatted data label for one slice.
    ///
    /// Data-labeling mode `5` renders the slice's percentage share and appends `%`. Other labeling
    /// modes inherit the default [SingleChartRenderer] behavior.
    @Override
    public String computeDataLabel(DataSetPoint point) {
        if (super.getDataLabeling() != 5)
            return super.computeDataLabel(point);

        double slicePercent = getPieDataSet().getSlicePercent(point.getIndex());
        if (super.getChart() == null) {
            NumberFormat numberFormat = NumberFormatFactory.getInstance(Locale.getDefault());
            return numberFormat.format(slicePercent) + "%";
        }

        String formattedValue = super.getChart().formatYValue(0, slicePercent);
        return (formattedValue == null) ? null : formattedValue + "%";
    }

    /// Returns the preferred label location for one slice.
    ///
    /// Centered label layouts place the label at the middle of the slice sector. Outside layouts
    /// start from the outer arc midpoint and then shift the label a few pixels farther outward
    /// along the local radial axis.
    @Override
    public Point computeDataLabelLocation(DisplayPoint point, Dimension labelSize) {
        DisplayPoint displayPoint = point;
        DataSet dataSet = displayPoint.getDataSet();
        VirtualDataSet virtualDataSet = DataSetRendererProperty.getVirtualDataSet(this, dataSet);
        if (virtualDataSet != null) {
            displayPoint = (DisplayPoint) displayPoint.clone();
            virtualDataSet.map(displayPoint);
            dataSet = virtualDataSet;
        }

        ChartProjector projector = super.getChart().getProjector();
        DataInterval yRange = new DataInterval();
        getYRange(displayPoint.getIndex(), yRange);
        double previousSliceEnd = (displayPoint.getIndex() == 0) ? 0.0 : dataSet.getXData(displayPoint.getIndex() - 1);
        double middleAngle = (previousSliceEnd + dataSet.getXData(displayPoint.getIndex())) / 2.0;
        Rectangle slicePlotRect = getSlicePlotRect(displayPoint.getIndex(), middleAngle, yRange);
        if (super.getDataLabelLayout() != 2) {
            DoublePoints labelAnchor = new DoublePoints(middleAngle, yRange.getMiddle());
            projector.toDisplay(labelAnchor, slicePlotRect, super.getCoordinateSystem());
            double x = labelAnchor.getX(0);
            double y = labelAnchor.getY(0);
            labelAnchor.dispose();
            return new Point(GraphicUtil.toInt(x), GraphicUtil.toInt(y));
        }

        DoublePoints labelAnchor = new DoublePoints(middleAngle, yRange.getMax());
        projector.toDisplay(labelAnchor, slicePlotRect, super.getCoordinateSystem());
        double x = labelAnchor.getX(0);
        double y = labelAnchor.getY(0);
        labelAnchor.dispose();
        DoublePoint shiftedAnchor = new DoublePoint(x, y);
        projector.shiftAlongAxis(slicePlotRect, super.getYAxis(), shiftedAnchor, 4.0);
        double angle = GraphicUtil.pointAngleDeg(x, y, shiftedAnchor.x, shiftedAnchor.y);
        shiftedAnchor = GraphicUtil.computeTextLocation(shiftedAnchor, angle, 6, labelSize.width, labelSize.height);
        return new Point(shiftedAnchor.xFloor(), shiftedAnchor.yFloor());
    }

    @Override
    protected Iterable<LegendEntry> createLegendEntries() {
        int count;
        if (isViewable() && isLegended() && (count = getDataSource().get(0).size()) > 0) {
            List<LegendEntry> resultList = new ArrayList<>(count);

            for (int index = 0; index < count; index++)
                resultList.add(new PieRendererLegendItem(index, this));

            return resultList;
        }
        return Collections.emptyList();
    }

    @Override
    boolean keepOutsideYRangeWhenClipping() {
        return true;
    }

    @Override
    protected void dataSetsAdded(int firstIndex, int lastIndex, DataSet[] oldDataSets) {
        super.dataSetsAdded(firstIndex, lastIndex, oldDataSets);
        if (super.getDataSource().size() == 1) {
            refreshPieDataSetBinding();
            ensureDefaultSliceStyles();
        }
    }

    @Override
    protected void dataSetsRemoved(int firstIndex, int lastIndex, DataSet[] oldDataSets) {
        super.dataSetsRemoved(firstIndex, lastIndex, oldDataSets);
        if (super.getDataSource().size() == 0 && sliceInfoByIndex != null)
            sliceInfoByIndex.clear();
    }

    /// Draws the legend marker for one slice.
    @Override
    public void drawLegendMarker(LegendEntry legend, Graphics g, int x, int y, int w, int h) {
        if (!(legend instanceof PieRendererLegendItem))
            super.drawLegendMarker(legend, g, x, y, w, h);
        else {
            PlotStyle sliceStyle = getSliceStyle(((PieRendererLegendItem) legend).getDataIndex());
            int markerSize = Math.min(w, h);
            sliceStyle.plotRect(g, x + (w - markerSize) / 2, y + (h - markerSize) / 2, markerSize, markerSize);
            if (!sliceStyle.isStrokeOn())
                super.getLegendStyle().drawRect(
                        g,
                        x + (w - markerSize) / 2,
                        y + (h - markerSize) / 2,
                        markerSize,
                        markerSize);
        }
    }

    @Override
    int getDisplayQueryPadding() {
        return 1;
    }

    /// Returns the current explode ratio for one slice.
    ///
    /// The ratio is expressed as a percentage of that slice's radial thickness.
    public final int getExplodeRatio(int sliceIndex) {
        PieSliceInfo sliceInfo = getSliceInfo(sliceIndex, false);
        return (sliceInfo == null) ? 0 : sliceInfo.explodeRatio;
    }

    /// Returns the legend text for one slice.
    ///
    /// When no slice-specific text was configured, the renderer falls back to the source dataset's
    /// data label for that slice.
    @Override
    public String getLegendText(LegendEntry legend) {
        if (!(legend instanceof PieRendererLegendItem))
            return super.getLegendText(legend);

        int sliceIndex = ((PieRendererLegendItem) legend).getDataIndex();
        PieSliceInfo sliceInfo = getSliceInfo(sliceIndex, false);
        String legendText = (sliceInfo == null) ? null : sliceInfo.getLegendText();
        if (legendText == null && sliceIndex < getSourceDataSet().size())
            legendText = getSourceDataSet().getDataLabel(sliceIndex);
        return legendText;
    }

    /// Returns the raw partial sum of source slice values through `sliceIndex`.
    ///
    /// Unlike [#getSlicePercent(int)], this does not normalize by the current total and therefore
    /// preserves the source dataset's original units.
    public double getPartialSum(int sliceIndex) {
        return getPieDataSet().getPartialSum(sliceIndex);
    }

    @Override
    public Insets getPreferredMargins() {
        Insets margins = new Insets(0, 0, 0, 0);
        if (super.holdsAnnotations()) {
            DataPoints points = super.getVisibleData(null);
            if (points != null) {
                SingleChartRenderer.Points projectedPoints = super.createPointsView(points);
                if (projectedPoints.size() > 0) {
                    Rectangle2D.Double annotationBounds = new Rectangle2D.Double();
                    super.addAnnotationBounds(projectedPoints, annotationBounds);
                    Rectangle plotRect = super.getPlotRect();
                    Rectangle annotationRect = GraphicUtil.toRectangle(annotationBounds, null);
                    if (!annotationRect.isEmpty()) {
                        margins.left = Math.max(0, plotRect.x - annotationRect.x + 1);
                        margins.right = Math.max(0, annotationRect.x + annotationRect.width - plotRect.width - plotRect.x + 1);
                        margins.top = Math.max(0, plotRect.y - annotationRect.y + 1);
                        margins.bottom = Math.max(0, annotationRect.y + annotationRect.height - plotRect.height - plotRect.y + 1);
                    }
                }
                super.disposePoints(projectedPoints);
            }
        }
        return margins;
    }

    /// Returns the current percentage share for one slice.
    ///
    /// The result is based on the current total of defined slice values only. Undefined slices are
    /// therefore excluded from the denominator.
    public double getSlicePercent(int sliceIndex) {
        return getPieDataSet().getSlicePercent(sliceIndex);
    }

    /// Returns the explicit per-slice style, or the renderer base style when none was assigned.
    public final PlotStyle getSliceStyle(int sliceIndex) {
        PieSliceInfo sliceInfo = getSliceInfo(sliceIndex, false);
        return (sliceInfo != null) ? sliceInfo.getStyle() : super.getStyle();
    }

    /// Returns the radial range currently used for slice `sliceIndex`.
    ///
    /// When at least one slice is exploded, every slice is drawn slightly thinner so the farthest
    /// displaced slice still fits into [#setRadialRange(DataInterval)].
    public DataInterval getYRange(int sliceIndex, DataInterval range) {
        DataInterval result = range;
        if (result == null)
            result = new DataInterval();
        if (maxExplodeRatio == 0)
            result.set(radialRange.getMin(), radialRange.getMax());
        else {
            double scale = 1.0 / (1.0 + maxExplodeRatio / 100.0);
            result.set(radialRange.getMin(), radialRange.getMin() + radialRange.getLength() * scale);
        }
        return result;
    }

    @Override
    int getBoundsQueryPadding() {
        return 0;
    }

    /// Returns whether one slice currently has a non-zero explode ratio.
    public final boolean isExploded(int sliceIndex) {
        return getExplodeRatio(sliceIndex) != 0;
    }

    /// Returns whether slice outlines are currently drawn.
    public final boolean isStrokeOn() {
        return strokeOn;
    }

    /// Rebuilds the internal [PieDataSet] binding for the current source dataset.
    void refreshPieDataSetBinding() {
        DataSet dataSet = getSourceDataSet();
        PieDataSet pieDataSet = new PieDataSet(dataSet);
        if (super.setVirtualDataSet(dataSet, pieDataSet))
            if (super.getChart() != null)
                super.getChart().updateDataRange();
    }

    /// Toggles one slice between the default explode ratio and no explosion.
    public final void setExploded(int sliceIndex, boolean exploded) {
        setExplodeRatio(sliceIndex, exploded ? DEFAULT_EXPLODE_RATIO : 0);
    }

    /// Applies the same explode ratio to every current slice.
    ///
    /// @throws IllegalArgumentException if `explodeRatio` is outside the inclusive range `0..100`
    public void setExplodeRatio(int explodeRatio) {
        if (super.getDataSource().size() == 0)
            return;

        boolean changed = false;
        int sliceCount = super.getDataSource().get(0).size();
        for (int sliceIndex = 0; sliceIndex < sliceCount; sliceIndex++) {
            if (updateSliceExplodeRatio(sliceIndex, explodeRatio))
                changed = true;
        }
        if (changed) {
            refreshMaxExplodeRatio();
            if (super.getChart() != null)
                super.getChart().getChartArea().revalidateLayout();
        }
    }

    /// Sets the explode ratio for one slice.
    ///
    /// The ratio is expressed as a percentage of that slice's radial thickness.
    ///
    /// @throws IllegalArgumentException if `explodeRatio` is outside the inclusive range `0..100`
    public void setExplodeRatio(int sliceIndex, int explodeRatio) {
        boolean changed = updateSliceExplodeRatio(sliceIndex, explodeRatio);
        if (changed) {
            if (explodeRatio < maxExplodeRatio)
                refreshMaxExplodeRatio();
            else
                maxExplodeRatio = explodeRatio;
            if (super.getChart() != null)
                super.getChart().getChartArea().revalidateLayout();
        }
    }

    /// Assigns fill colors to slices while preserving the renderer's base stroke styling.
    public void setSliceColors(Color[] colors) {
        PlotStyle baseStyle = super.getStyle();
        for (int sliceIndex = 0; sliceIndex < colors.length; sliceIndex++) {
            PlotStyle sliceStyle = (baseStyle == null)
                    ? new PlotStyle(Color.black, colors[sliceIndex])
                    : (!baseStyle.isStrokeOn())
                    ? new PlotStyle(colors[sliceIndex])
                    : new PlotStyle(baseStyle.getStrokePaint(), colors[sliceIndex]);
            setSliceStyle(sliceIndex, sliceStyle);
        }
    }

    /// Sets the explicit style for one slice.
    public void setSliceStyle(int sliceIndex, PlotStyle style) {
        getSliceInfo(sliceIndex, true).setStyle(style);
    }

    /// Enables or disables outline strokes for every slice.
    public void setStrokeOn(boolean strokeOn) {
        if (this.strokeOn == strokeOn)
            return;
        this.strokeOn = strokeOn;
        super.triggerChange(4);
    }

    /// Returns the current virtual dataset used for pie traversal.
    final SinglePieRenderer.PieDataSet getPieDataSet() {
        return (SinglePieRenderer.PieDataSet) super.getResolvedDataSet(0);
    }

    /// Returns the single source dataset currently rendered by this instance.
    final DataSet getSourceDataSet() {
        return super.getDataSource().get(0);
    }

    /// Assigns default slice styles when no slice has an explicit style yet.
    private void ensureDefaultSliceStyles() {
        if (super.getDataSource().size() == 0)
            return;

        int sliceCount = super.getDataSource().get(0).size();
        boolean applyDefaults = sliceCount > 0;
        for (int sliceIndex = 0; sliceIndex < sliceCount; sliceIndex++) {
            PieSliceInfo sliceInfo = getSliceInfo(sliceIndex, false);
            if (sliceInfo != null && sliceInfo.getStyle() != null) {
                applyDefaults = false;
                break;
            }
        }

        if (applyDefaults) {
            Color[] defaultColors = super.getDefaultColors();
            if (defaultColors == null)
                setSliceColors(ColorData.generateColors(sliceCount));
            else {
                Color[] sliceColors = new Color[sliceCount];
                int colorIndex = 0;
                while (colorIndex < sliceCount && colorIndex < defaultColors.length) {
                    sliceColors[colorIndex] = defaultColors[colorIndex];
                    colorIndex++;
                }
                while (colorIndex < sliceCount) {
                    sliceColors[colorIndex] = ColorUtil.getRandomColor();
                    colorIndex++;
                }
                setSliceColors(sliceColors);
            }
        }
    }

    /// Recomputes the largest explode ratio currently assigned to any slice.
    private void refreshMaxExplodeRatio() {
        int currentMaxExplodeRatio = 0;
        if (sliceInfoByIndex != null) {
            Iterator<PieSliceInfo> sliceInfos = sliceInfoByIndex.values().iterator();
            while (sliceInfos.hasNext())
                currentMaxExplodeRatio = Math.max(sliceInfos.next().explodeRatio, currentMaxExplodeRatio);
        }
        maxExplodeRatio = currentMaxExplodeRatio;
    }
}

