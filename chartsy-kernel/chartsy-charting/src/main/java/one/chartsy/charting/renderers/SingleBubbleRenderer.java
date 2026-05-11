package one.chartsy.charting.renderers;

import java.awt.geom.Rectangle2D;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.PlotStyle;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSetPoint;
import one.chartsy.charting.event.DataSetContentsEvent;
import one.chartsy.charting.graphic.DataAnnotation;
import one.chartsy.charting.graphic.Marker;

/// Single-series bubble renderer that reads positions from one dataset and bubble sizes from a
/// second paired dataset.
///
/// The primary dataset supplies the visible x/y coordinates in the same way as
/// [SingleScatterRenderer]. The paired size dataset contributes y-values only. Those values are
/// normalized over the paired dataset's current y-range and mapped into the inclusive
/// `[minSize, maxSize]` interval returned by [#getMinSize()] and [#getMaxSize()].
///
/// Bounds and annotation lookups accept either paired dataset. Requests that address the size
/// dataset are redirected to the primary position dataset because the rendered geometry is anchored
/// there.
public class SingleBubbleRenderer extends SingleScatterRenderer {
    static final int DEFAULT_MAX_SIZE = 30;
    static final int DEFAULT_MIN_SIZE = 10;
    private static final int DEFAULT_MARKER_SIZE = 3;

    static {
        ChartRenderer.register("SingleBubble", SingleBubbleRenderer.class);
    }

    private DataInterval bubbleSizeRange;
    private int minSize;
    private int maxSize;

    /// Creates a bubble renderer that uses circular markers sized in the `10..30` range.
    public SingleBubbleRenderer() {
        this(null);
    }

    /// Creates a bubble renderer with explicit bubble-size bounds.
    ///
    /// @param marker  bubble glyph template used for every rendered point
    /// @param minSize minimum marker half-size produced from the paired size dataset
    /// @param maxSize maximum marker half-size produced from the paired size dataset
    /// @param style   base plot style, or `null` to let the renderer resolve a default
    public SingleBubbleRenderer(Marker marker, int minSize, int maxSize, PlotStyle style) {
        super(marker, DEFAULT_MARKER_SIZE, style);
        bubbleSizeRange = new DataInterval();
        this.minSize = minSize;
        this.maxSize = maxSize;
        validateMarkerConfiguration();
    }

    /// Creates a bubble renderer with circular markers and an explicit base style.
    public SingleBubbleRenderer(PlotStyle style) {
        this(Marker.CIRCLE, DEFAULT_MIN_SIZE, DEFAULT_MAX_SIZE, style);
    }

    /// Resolves annotations from the paired size dataset when the primary position dataset has no
    /// explicit annotation for the same logical index.
    ///
    /// The caller's [DataSetPoint] is restored before returning so probing the size dataset does
    /// not leak a dataset swap back into the shared annotation and bounds code paths.
    @Override
    DataAnnotation getAnnotation(DataSetPoint point) {
        DataAnnotation annotation = super.getAnnotation(point);
        if (annotation == null
                && point.getDataSet() == super.getPrimaryDataSet()
                && super.getDataSource().size() >= getRequiredDataSetCount()) {
            DataSet originalDataSet = point.getDataSet();
            try {
                point.dataSet = getBubbleSizeDataSet();
                annotation = super.getAnnotation(point);
            } finally {
                point.dataSet = originalDataSet;
            }
        }
        return annotation;
    }

    /// Returns how many datasets this renderer needs before it can render bubble geometry.
    ///
    /// Dataset `0` supplies the bubble centers and dataset `1` supplies the bubble-size values.
    int getRequiredDataSetCount() {
        return 2;
    }

    /// Returns the bubble half-size for the point at `dataIndex`.
    ///
    /// Undefined or missing size values suppress the bubble by returning `0`. When the paired size
    /// range collapses to one value, every defined bubble falls back to [#getMinSize()] rather
    /// than relying on `NaN`-driven normalization.
    @Override
    int getMarkerHalfSize(int dataIndex) {
        if (super.getDataSource().size() < getRequiredDataSetCount()) {
            return 0;
        }

        DataSet bubbleSizeDataSet = getBubbleSizeDataSet();
        if (dataIndex >= bubbleSizeDataSet.size()) {
            return 0;
        }

        double sizeValue = bubbleSizeDataSet.getYData(dataIndex);
        Double undefinedValue = bubbleSizeDataSet.getUndefValue();
        if ((undefinedValue != null && sizeValue == undefinedValue.doubleValue()) || Double.isNaN(sizeValue)) {
            return 0;
        }

        double sizeRangeLength = bubbleSizeRange.getLength();
        if (!Double.isFinite(sizeRangeLength) || sizeRangeLength <= 0.0) {
            return getMinSize();
        }

        double normalizedSize = (sizeValue - bubbleSizeRange.getMin()) / sizeRangeLength;
        normalizedSize = Math.max(0.0, Math.min(1.0, normalizedSize));
        return (int) Math.floor((getMaxSize() - getMinSize()) * normalizedSize) + getMinSize();
    }

    /// Refreshes the cached size range when the paired size dataset changes.
    ///
    /// Bubble radii depend on the full y-range of the size dataset rather than on any one point, so
    /// size-dataset content changes can require repainting every bubble even when center positions
    /// stay unchanged.
    @Override
    public void dataSetContentsChanged(DataSetContentsEvent event) {
        if (!super.isViewable()) {
            return;
        }

        if (super.getDataSource().size() >= getRequiredDataSetCount() && event.getDataSet() == getBubbleSizeDataSet()) {
            switch (event.getType()) {
                case DataSetContentsEvent.AFTER_DATA_CHANGED,
                     DataSetContentsEvent.DATA_CHANGED,
                     DataSetContentsEvent.DATA_ADDED,
                     DataSetContentsEvent.FULL_UPDATE -> {
                    DataInterval range = getBubbleSizeDataSet().getYRange(null);
                    if (!bubbleSizeRange.equals(range)) {
                        super.getChart().getChartArea().repaint();
                    }
                    bubbleSizeRange = range;
                }
                default -> {
                }
            }
        }

        super.dataSetContentsChanged(event);
    }

    @Override
    protected void dataSetsAdded(int fromIndex, int toIndex, DataSet[] oldDataSets) {
        if (super.getDataSource().size() >= getRequiredDataSetCount()) {
            bubbleSizeRange = getBubbleSizeDataSet().getYRange(bubbleSizeRange);
        }
        super.dataSetsAdded(fromIndex, toIndex, oldDataSets);
    }

    /// Returns the bounds of the rendered bubbles for the requested dataset range.
    ///
    /// When callers ask for bounds on the paired size dataset, the request is resolved against the
    /// primary position dataset because the visible bubble centers come from that dataset.
    @Override
    public Rectangle2D getBounds(
            DataSet dataSet,
            int fromIndex,
            int toIndex,
            Rectangle2D reuseBounds,
            boolean includeAnnotations) {
        DataSet effectiveDataSet = dataSet;
        if (super.getDataSource().size() >= getRequiredDataSetCount() && dataSet == getBubbleSizeDataSet()) {
            effectiveDataSet = super.getPrimaryDataSet();
        }
        return super.getBounds(effectiveDataSet, fromIndex, toIndex, reuseBounds, includeAnnotations);
    }

    /// Returns the largest marker half-size this renderer may produce.
    @Override
    public final int getMaxSize() {
        return maxSize;
    }

    /// Returns the smallest marker half-size this renderer may produce.
    public final int getMinSize() {
        return minSize;
    }

    /// Bubble markers stay clipped to the plot rectangle.
    ///
    /// Unlike the base scatter renderer, this subtype treats marker size as intrinsic bubble
    /// geometry rather than as a decorative point glyph that should bleed past the plot edge.
    @Override
    protected boolean isClipped() {
        return true;
    }

    /// Sets the largest marker half-size produced from the paired size dataset.
    public void setMaxSize(int maxSize) {
        if (maxSize != this.maxSize) {
            this.maxSize = maxSize;
            super.triggerChange(4);
        }
    }

    /// Sets the smallest marker half-size produced from the paired size dataset.
    public void setMinSize(int minSize) {
        if (minSize != this.minSize) {
            this.minSize = minSize;
            super.triggerChange(4);
        }
    }

    @Override
    void validateMarkerConfiguration() {
        if (getMaxSize() < getMinSize()) {
            throw new IllegalStateException("Maximum size must be greater or equal than minimum size");
        }
    }

    /// Returns the paired dataset that contributes size values for the visible bubbles.
    ///
    /// Callers must ensure that the renderer currently owns at least
    /// [#getRequiredDataSetCount()] datasets before calling this method.
    DataSet getBubbleSizeDataSet() {
        return super.getDataSource().get(1);
    }
}

