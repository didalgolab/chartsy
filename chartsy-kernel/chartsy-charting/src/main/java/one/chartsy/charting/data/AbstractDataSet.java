package one.chartsy.charting.data;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import javax.swing.event.EventListenerList;

import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.event.DataSetContentsEvent;
import one.chartsy.charting.event.DataSetListener;
import one.chartsy.charting.event.DataSetPropertyEvent;
import one.chartsy.charting.util.Batchable;

/// Base `DataSet` implementation that supplies lazy range computation, temporary `DataPoints`
/// extraction, shared property storage, and batched listener dispatch.
///
/// Subclasses only need to expose scalar samples through `size()`, `getXData(int)`, and
/// `getYData(int)`. Everything else in the charting pipeline, from visible-window extraction to
/// incremental limits invalidation, is derived from those primitives.
///
/// `AbstractDataSet` maintains two related caches:
/// - current x/y ranges, recomputed lazily
/// - the minimum positive spacing between neighboring x values, also recomputed lazily
///
/// The range cache is bucketed. Small edits can therefore dirty only the affected bucket spans
/// instead of forcing a full rescan on every mutation. That matters for editable datasets and for
/// virtual datasets that synthesize points on demand.
public abstract class AbstractDataSet implements DataSet, Batchable, Serializable {
    private static final int INITIAL_BUCKET_ARRAY_CAPACITY = 10;
    private static final double NO_ACCESSIBLE_X_DIFFERENCE = -2.0;
    private static final double NO_POSITIVE_X_DIFFERENCE = -1.0;

    /// Aggregates min/max x and y values for one contiguous bucket range.
    ///
    /// The outer class splits and dirties these ranges as data changes so range recomputation can
    /// stay proportional to the edited region when possible.
    static final class LimitsCacheBuckets {
        final int firstBucket;
        final int endBucketExclusive;
        boolean dirty;
        boolean hasXValues;
        boolean hasYValues;
        double minX;
        double maxX;
        double minY;
        double maxY;

        LimitsCacheBuckets(int firstBucket, int endBucketExclusive) {
            this.firstBucket = firstBucket;
            this.endBucketExclusive = endBucketExclusive;
            dirty = true;
        }

        /// Clears the cached extrema so this range can be recomputed from source points.
        void clearExtrema() {
            hasXValues = false;
            hasYValues = false;
        }

        /// Expands the cached x-range with one x value.
        void includeX(double xValue) {
            if (!hasXValues) {
                minX = xValue;
                maxX = xValue;
                hasXValues = true;
                return;
            }

            if (xValue < minX)
                minX = xValue;
            if (xValue > maxX)
                maxX = xValue;
        }

        /// Expands the cached y-range with one y value.
        void includeY(double yValue) {
            if (!hasYValues) {
                minY = yValue;
                maxY = yValue;
                hasYValues = true;
                return;
            }

            if (yValue < minY)
                minY = yValue;
            if (yValue > maxY)
                maxY = yValue;
        }
    }

    /// Historical default sentinel for undefined y values.
    ///
    /// Datasets may override this with a domain-specific sentinel or return `null` to indicate
    /// that only `NaN` should be treated as undefined.
    public static final Double DEFAULT_UNDEF_VALUE = Double.MIN_VALUE;

    private final EventListenerList listenerList;
    private Double undefValue;
    private transient Map<Object, Object> properties;
    private boolean xRangeIncludingUndefinedPoints;
    private transient DataInterval xRange;
    private transient DataInterval yRange;
    private transient boolean limitsInvalid;
    private transient int bucketShift;
    private transient LimitsCacheBuckets[] limitsCacheBuckets;
    private transient int limitsCacheBucketCount;
    private transient boolean limitsCacheInvalid;
    private double minimumXDifference;
    private transient boolean minimumXDifferenceInvalid;

    private transient DataSetContentsEvent batchBeginEvent;
    private transient DataSetContentsEvent batchEndEvent;
    private transient int batchDepth;
    private transient boolean batchHasChanges;

    protected AbstractDataSet() {
        listenerList = new EventListenerList();
        xRangeIncludingUndefinedPoints = true;
        minimumXDifference = NO_ACCESSIBLE_X_DIFFERENCE;
        initializeTransients();
    }

    private static LimitsCacheBuckets createCleanGap(int firstBucket, int endBucketExclusive) {
        LimitsCacheBuckets bucketRange = new LimitsCacheBuckets(firstBucket, endBucketExclusive);
        bucketRange.dirty = false;
        return bucketRange;
    }

    private static void filterByWindow(
            DataPoints points, int firstSourceIndex, DataInterval xRange, DataInterval yRange) {
        int pointCount = points.size();
        int[] indices = points.getIndices();
        double[] xValues = points.getXValues();
        double[] yValues = points.getYValues();

        int writeIndex = 0;
        while (writeIndex < pointCount
                && yRange.isInside(yValues[writeIndex])
                && xRange.isInside(xValues[writeIndex])) {
            writeIndex++;
        }

        for (int readIndex = writeIndex; readIndex < pointCount; readIndex++) {
            if (!yRange.isInside(yValues[readIndex]) || !xRange.isInside(xValues[readIndex]))
                continue;

            xValues[writeIndex] = xValues[readIndex];
            yValues[writeIndex] = yValues[readIndex];
            indices[writeIndex] = firstSourceIndex + readIndex;
            writeIndex++;
        }
        points.setSize(writeIndex);
    }

    private static void filterByYRange(DataPoints points, int firstSourceIndex, DataInterval yRange) {
        int pointCount = points.size();
        int[] indices = points.getIndices();
        double[] xValues = points.getXValues();
        double[] yValues = points.getYValues();

        int writeIndex = 0;
        while (writeIndex < pointCount && yRange.isInside(yValues[writeIndex])) {
            writeIndex++;
        }

        for (int readIndex = writeIndex; readIndex < pointCount; readIndex++) {
            if (!yRange.isInside(yValues[readIndex]))
                continue;

            xValues[writeIndex] = xValues[readIndex];
            yValues[writeIndex] = yValues[readIndex];
            indices[writeIndex] = firstSourceIndex + readIndex;
            writeIndex++;
        }
        points.setSize(writeIndex);
    }

    private static boolean isDefinedY(double yValue, Double undefValue) {
        return !Double.isNaN(yValue)
                && (undefValue == null || yValue != undefValue.doubleValue());
    }

    private static boolean segmentTouchesXRange(
            double firstX, double secondX, double minimumX, double maximumX) {
        return (firstX <= maximumX && secondX >= minimumX)
                || (firstX >= minimumX && secondX <= maximumX);
    }

    /// Computes the smallest strictly positive x-distance visible in `dataSet`.
    ///
    /// The method uses `getDataBetween(...)`, so it also works for virtual datasets whose points
    /// are synthesized on demand instead of stored contiguously. It returns:
    /// - `-2.0` when the dataset has no accessible points
    /// - `-1.0` when all accessible x values collapse to one position
    ///
    /// The temporary `DataPoints` batch returned by the dataset is always disposed before this
    /// method returns.
    public static double computeMinimumXDifference(DataSet dataSet) {
        int dataPointCount = dataSet.size();
        if (dataPointCount == 0)
            return NO_ACCESSIBLE_X_DIFFERENCE;

        DataPoints points = dataSet.getDataBetween(0, dataPointCount - 1);
        if (points == null)
            return NO_ACCESSIBLE_X_DIFFERENCE;

        try {
            int accessiblePointCount = points.size();
            if (accessiblePointCount == 0)
                return NO_ACCESSIBLE_X_DIFFERENCE;

            double[] xValues = points.getXValues();
            boolean sorted = true;
            for (int index = 1; index < accessiblePointCount; index++) {
                if (xValues[index - 1] > xValues[index]) {
                    sorted = false;
                    break;
                }
            }
            if (!sorted)
                Arrays.sort(xValues, 0, accessiblePointCount);

            double minimumDifference = Double.POSITIVE_INFINITY;
            for (int index = 1; index < accessiblePointCount; index++) {
                double xDifference = xValues[index] - xValues[index - 1];
                if (xDifference > 0.0)
                    minimumDifference = Math.min(minimumDifference, xDifference);
            }
            return (minimumDifference < Double.POSITIVE_INFINITY)
                    ? minimumDifference
                    : NO_POSITIVE_X_DIFFERENCE;
        } finally {
            points.dispose();
        }
    }

    DataInterval getCachedXRange() {
        if (limitsInvalid)
            recomputeLimits();
        return xRange;
    }

    DataInterval getCachedYRange() {
        if (limitsInvalid)
            recomputeLimits();
        return yRange;
    }

    void fillDataPoints(DataPoints points, int firstIndex, int lastIndex) {
        double[] xValues = points.getXValues();
        double[] yValues = points.getYValues();
        for (int index = firstIndex; index <= lastIndex; index++) {
            int pointIndex = index - firstIndex;
            xValues[pointIndex] = getXData(index);
            yValues[pointIndex] = getYData(index);
        }
        points.setSize(lastIndex - firstIndex + 1);
    }

    private void dispatchContentsEvent(DataSetContentsEvent event) {
        Object[] listeners = listenerList.getListenerList();
        for (int index = listeners.length - 1; index >= 0; index -= 2) {
            ((DataSetListener) listeners[index]).dataSetContentsChanged(event);
        }
    }

    private void dispatchPropertyEvent(DataSetPropertyEvent event) {
        Object[] listeners = listenerList.getListenerList();
        for (int index = listeners.length - 1; index >= 0; index -= 2) {
            ((DataSetListener) listeners[index]).dataSetPropertyChanged(event);
        }
    }

    private int appendBucketEntries(int firstBucket, int lastBucket, int insertionIndex) {
        assert insertionIndex == limitsCacheBucketCount;

        int currentEndBucket = (limitsCacheBucketCount == 0)
                ? 0
                : limitsCacheBuckets[limitsCacheBucketCount - 1].endBucketExclusive;
        boolean needsGap = firstBucket > currentEndBucket;
        int newEntryCount = (needsGap ? 1 : 0) + lastBucket - firstBucket + 1;
        if (!canAddBucketEntries(newEntryCount))
            return -1;

        ensureBucketCacheCapacity(limitsCacheBucketCount + newEntryCount);
        int writeIndex = insertionIndex;
        if (needsGap)
            limitsCacheBuckets[writeIndex++] = createCleanGap(currentEndBucket, firstBucket);

        int firstMaterializedIndex = writeIndex;
        writeIndex = writeDirtyUnitBuckets(writeIndex, firstBucket, lastBucket);
        assert writeIndex == insertionIndex + newEntryCount;

        limitsCacheBucketCount += newEntryCount;
        return firstMaterializedIndex;
    }

    private boolean bucketContains(LimitsCacheBuckets bucket, int bucketIndex) {
        return bucket.firstBucket <= bucketIndex && bucketIndex < bucket.endBucketExclusive;
    }

    private boolean canAddBucketEntries(int additionalEntryCount) {
        if (additionalEntryCount <= 0)
            return true;
        if (limitsCacheBucketCount + additionalEntryCount >= 1 << bucketShift) {
            limitsCacheInvalid = true;
            return false;
        }
        return true;
    }

    /// Computes the current x and y ranges for this dataset.
    ///
    /// The default implementation reuses the bucket cache and treats `NaN` plus `getUndefValue()`
    /// as undefined y values. X positions tied to undefined y values contribute to the x-range only
    /// when [#isXRangeIncludingUndefinedPoints()] is `true`.
    protected void computeLimits(DataInterval xRange, DataInterval yRange) {
        boolean includeUndefinedX = isXRangeIncludingUndefinedPoints();
        if (limitsCacheInvalid)
            rebuildLimitsCache(includeUndefinedX);
        else
            refreshDirtyBucketExtrema(includeUndefinedX);

        updateRangesFromBucketCache(xRange, yRange);
    }

    private int computeInitialBucketShift(int pointCount) {
        int initialShift = 0;
        while (pointCount - 1 > (1 << (2 * initialShift)) - (3 << initialShift)) {
            initialShift++;
        }
        return initialShift;
    }

    /// Convenience hook for subclasses that appended or inserted one point.
    ///
    /// The default implementation fires a `DATA_ADDED` event covering that index.
    protected void dataAdded(int index) {
        fireDataAddedEvent(index, index);
    }

    /// Convenience hook for subclasses that changed a point range in place.
    ///
    /// `eventType` is typically `BEFORE_DATA_CHANGED` or `AFTER_DATA_CHANGED`.
    protected void dataChanged(int firstIndex, int lastIndex, int eventType) {
        fireDataChangedEvent(firstIndex, lastIndex, eventType);
    }

    /// Ends a logical batch started with [#startBatch()].
    ///
    /// A `BATCH_END` marker is emitted only if at least one contents event was fired while the
    /// batch was open.
    @Override
    public void endBatch() {
        boolean dispatchBatchEnd;
        synchronized (this) {
            batchDepth--;
            if (batchDepth != 0) {
                dispatchBatchEnd = false;
            } else {
                dispatchBatchEnd = batchHasChanges;
                batchHasChanges = false;
            }
        }
        if (dispatchBatchEnd)
            dispatchContentsEvent(batchEndEvent);
    }

    private void ensureBucketCacheCapacity(int requiredEntryCount) {
        if (requiredEntryCount <= limitsCacheBuckets.length)
            return;

        int newCapacity = Math.max(limitsCacheBuckets.length * 2, requiredEntryCount);
        LimitsCacheBuckets[] newBuckets = new LimitsCacheBuckets[newCapacity];
        System.arraycopy(limitsCacheBuckets, 0, newBuckets, 0, limitsCacheBucketCount);
        limitsCacheBuckets = newBuckets;
    }

    private int ensureBucketCacheEntries(int firstBucket, int lastBucket) {
        int firstSegmentIndex = findLimitsCacheIndex(firstBucket);
        if (firstSegmentIndex >= limitsCacheBucketCount)
            return appendBucketEntries(firstBucket, lastBucket, firstSegmentIndex);

        int lastSegmentIndex = (lastBucket == firstBucket)
                ? firstSegmentIndex
                : findLimitsCacheIndex(lastBucket);
        if (lastSegmentIndex >= limitsCacheBucketCount)
            return replaceTailWithUnitBuckets(firstBucket, lastBucket, firstSegmentIndex);

        return replaceCoveredSegmentsWithUnitBuckets(firstBucket, lastBucket, firstSegmentIndex, lastSegmentIndex);
    }

    private int findFirstSortedXIndexAtOrAfter(int firstIndex, int lastIndex, double x) {
        if (x <= getXData(firstIndex))
            return firstIndex;

        int index = findSortedXIndex(firstIndex, lastIndex, x);
        if (index < 0)
            return -index - 1;

        while (--index > firstIndex && getXData(index) == x) {
            // Walk back to the first equal x value.
        }
        return index + 1;
    }

    private int findLastSortedXIndexAtOrBefore(int firstIndex, int lastIndex, double x) {
        if (x >= getXData(lastIndex))
            return lastIndex;

        int index = findSortedXIndex(firstIndex, lastIndex, x);
        if (index < 0)
            return -index - 2;

        while (++index < lastIndex && getXData(index) == x) {
            // Walk forward to the last equal x value.
        }
        return index - 1;
    }

    private int findLimitsCacheIndex(int bucketIndex) {
        int upperBound = limitsCacheBucketCount;
        int lowerBound = 0;
        while (upperBound - lowerBound > 1) {
            int middle = (lowerBound + upperBound) >>> 1;
            if (limitsCacheBuckets[middle].firstBucket > bucketIndex)
                upperBound = middle;
            else
                lowerBound = middle;
        }
        if (upperBound > lowerBound
                && upperBound == limitsCacheBucketCount
                && limitsCacheBuckets[lowerBound].endBucketExclusive <= bucketIndex) {
            return upperBound;
        }
        return lowerBound;
    }

    int findSortedXIndex(int firstIndex, int lastIndex, double x) {
        int low = firstIndex;
        int high = lastIndex;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            double xValue = getXData(middle);
            if (xValue < x) {
                low = middle + 1;
            } else if (xValue > x) {
                high = middle - 1;
            } else {
                return middle;
            }
        }
        return -(low + 1);
    }

    /// Registers `listener` for subsequent contents and property events.
    @Override
    public void addDataSetListener(DataSetListener listener) {
        listenerList.add(DataSetListener.class, listener);
    }

    /// Fires a `DATA_ADDED` event for the inclusive index range `[firstIndex, lastIndex]`.
    public void fireDataAddedEvent(int firstIndex, int lastIndex) {
        fireDataSetContentsEvent(
                new DataSetContentsEvent(this, DataSetContentsEvent.DATA_ADDED, firstIndex, lastIndex)
        );
    }

    /// Fires a contents event for the inclusive index range `[firstIndex, lastIndex]`.
    public void fireDataChangedEvent(int firstIndex, int lastIndex, int eventType) {
        fireDataSetContentsEvent(new DataSetContentsEvent(this, eventType, firstIndex, lastIndex));
    }

    /// Invalidates cached range state as needed and dispatches `event` to listeners.
    ///
    /// When this dataset is batched, the first contents event also emits the cached
    /// `BATCH_BEGIN` marker before the event itself.
    public void fireDataSetContentsEvent(DataSetContentsEvent event) {
        if (event == null)
            throw new IllegalArgumentException("null event");

        switch (event.getType()) {
            case DataSetContentsEvent.AFTER_DATA_CHANGED, DataSetContentsEvent.DATA_CHANGED ->
                    invalidateLimits(event.getFirstIdx(), event.getLastIdx());
            case DataSetContentsEvent.DATA_ADDED ->
                    invalidateLimits(event.getFirstIdx(), event.getLastIdx(), true);
            case DataSetContentsEvent.BATCH_BEGIN, DataSetContentsEvent.BATCH_END,
                    DataSetContentsEvent.BEFORE_DATA_CHANGED, DataSetContentsEvent.DATA_LABEL_CHANGED -> {
            }
            default -> invalidateLimits();
        }

        boolean dispatchBatchBegin;
        synchronized (this) {
            dispatchBatchBegin = batchDepth > 0 && !batchHasChanges;
            if (dispatchBatchBegin)
                batchHasChanges = true;
        }
        if (dispatchBatchBegin)
            dispatchContentsEvent(batchBeginEvent);
        dispatchContentsEvent(event);
    }

    /// Dispatches a property change event to all registered listeners.
    public void fireDataSetPropertyEvent(DataSetPropertyEvent event) {
        dispatchPropertyEvent(event);
    }

    /// Returns all currently accessible points as a temporary `DataPoints` batch.
    @Override
    public DataPoints getData() {
        return getDataBetween(0, size() - 1);
    }

    /// Returns a temporary `DataPoints` batch for the requested inclusive index range.
    ///
    /// The default implementation clamps the requested bounds to `[0, size() - 1]`, fills the
    /// returned batch from scalar accessors, and assigns each point its logical dataset index.
    ///
    /// It returns `null` when the clamped range is empty instead of allocating an empty batch.
    @Override
    public DataPoints getDataBetween(int firstIndex, int lastIndex) {
        int clampedFirstIndex = Math.max(firstIndex, 0);
        int clampedLastIndex = Math.min(lastIndex, size() - 1);
        if (clampedFirstIndex > clampedLastIndex)
            return null;

        int pointCount = clampedLastIndex - clampedFirstIndex + 1;
        DataPoints points = new DataPoints(this, pointCount);
        fillDataPoints(points, clampedFirstIndex, clampedLastIndex);

        int[] indices = points.getIndices();
        for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
            indices[pointIndex] = clampedFirstIndex + pointIndex;
        }
        return points;
    }

    /// Returns the points relevant to `window`.
    ///
    /// For datasets with sorted x values, the default implementation narrows the search range with
    /// binary-search helpers before optionally filtering the resulting batch against the y-range.
    /// When `keepOutsideYRange` is `true`, points kept only for x-range continuity remain in the
    /// result.
    @Override
    public DataPoints getDataInside(DataWindow window, int extraPoints, boolean keepOutsideYRange) {
        return getDataInside(window, 0, size() - 1, extraPoints, keepOutsideYRange);
    }

    /// Returns the points relevant to `window` within the inclusive source range
    /// `[firstIndex, lastIndex]`.
    ///
    /// `extraPoints` expands the x-clipped range on both sides before the optional y-range filter
    /// runs. For sorted x-values that means neighboring logical samples. For unsorted x-values that
    /// means retaining the first and last segment that still intersects the visible x-range so
    /// line-style renderers can preserve continuity at the clip edge.
    protected DataPoints getDataInside(
            DataWindow window, int firstIndex, int lastIndex, int extraPoints, boolean keepOutsideYRange) {
        if (lastIndex < firstIndex)
            return null;

        int clippedFirstIndex = firstIndex;
        int clippedLastIndex = lastIndex;
        if (isXValuesSorted()) {
            if (extraPoints == 0
                    && (window.xRange.getMin() > getXData(lastIndex)
                    || window.xRange.getMax() < getXData(firstIndex))) {
                return null;
            }

            clippedFirstIndex = findFirstSortedXIndexAtOrAfter(firstIndex, lastIndex, window.xRange.getMin());
            clippedLastIndex = findLastSortedXIndexAtOrBefore(clippedFirstIndex, lastIndex, window.xRange.getMax());
            if (extraPoints > 0) {
                clippedFirstIndex = Math.max(clippedFirstIndex - extraPoints, firstIndex);
                clippedLastIndex = Math.min(clippedLastIndex + extraPoints, lastIndex);
            }
        } else {
            if (!window.xRange.intersects(getXRange()))
                return null;

            double minimumX = window.xRange.getMin();
            double maximumX = window.xRange.getMax();
            if (extraPoints == 0) {
                while (clippedFirstIndex <= clippedLastIndex) {
                    double xValue = getXData(clippedFirstIndex);
                    if (xValue >= minimumX && xValue <= maximumX)
                        break;
                    clippedFirstIndex++;
                }
                while (clippedLastIndex >= clippedFirstIndex) {
                    double xValue = getXData(clippedLastIndex);
                    if (xValue >= minimumX && xValue <= maximumX)
                        break;
                    clippedLastIndex--;
                }
                if (clippedLastIndex < clippedFirstIndex)
                    return null;
            } else {
                if (clippedFirstIndex == clippedLastIndex) {
                    double xValue = getXData(clippedFirstIndex);
                    return (xValue >= minimumX && xValue <= maximumX)
                            ? getDataBetween(clippedFirstIndex, clippedLastIndex)
                            : null;
                }

                int firstVisibleSegmentStart = Integer.MAX_VALUE;
                double previousX = getXData(clippedFirstIndex);
                for (int index = clippedFirstIndex + 1; index <= clippedLastIndex; index++) {
                    double currentX = getXData(index);
                    if (segmentTouchesXRange(previousX, currentX, minimumX, maximumX)) {
                        firstVisibleSegmentStart = index - 1;
                        break;
                    }
                    previousX = currentX;
                }
                if (firstVisibleSegmentStart > clippedLastIndex)
                    return null;

                int lastVisibleSegmentEnd = firstVisibleSegmentStart + 1;
                double trailingX = getXData(clippedLastIndex);
                for (int index = clippedLastIndex - 1; index > firstVisibleSegmentStart; index--) {
                    double currentX = getXData(index);
                    if (segmentTouchesXRange(trailingX, currentX, minimumX, maximumX)) {
                        lastVisibleSegmentEnd = index + 1;
                        break;
                    }
                    trailingX = currentX;
                }

                clippedFirstIndex = firstVisibleSegmentStart;
                clippedLastIndex = lastVisibleSegmentEnd;
            }
        }

        if (clippedLastIndex < clippedFirstIndex)
            return null;

        DataPoints points = getDataBetween(clippedFirstIndex, clippedLastIndex);
        if (points != null && !keepOutsideYRange) {
            if (isXValuesSorted())
                filterByYRange(points, clippedFirstIndex, window.yRange);
            else
                filterByWindow(points, clippedFirstIndex, window.xRange, window.yRange);
        }
        return points;
    }

    /// Returns an empty label by default.
    ///
    /// Concrete datasets that expose per-point labels should override this.
    @Override
    public String getDataLabel(int index) {
        return "";
    }

    /// Returns the cached minimum positive spacing between neighboring x values.
    ///
    /// The cache is invalidated by content changes and recomputed lazily with
    /// [#computeMinimumXDifference(DataSet)]. A value of `-2.0` means that no points are
    /// accessible, while `-1.0` means the accessible points exist but no strictly positive spacing
    /// could be found.
    public double getMinimumXDifference() {
        if (minimumXDifferenceInvalid) {
            minimumXDifference = computeMinimumXDifference(this);
            minimumXDifferenceInvalid = false;
        }
        return minimumXDifference;
    }

    /// Returns the value stored under the `"name"` property key.
    @Override
    public String getName() {
        return (String) getProperty("name");
    }

    private Map<Object, Object> getOrCreateProperties() {
        if (properties == null)
            properties = new HashMap<>(2);
        return properties;
    }

    /// Returns a property previously stored through [#putProperty(Object, Object, boolean)].
    @Override
    public synchronized Object getProperty(Object key) {
        return (properties == null) ? null : properties.get(key);
    }

    /// Returns the sentinel that the default range computation treats as an undefined y value.
    @Override
    public Double getUndefValue() {
        return undefValue;
    }

    @Override
    public abstract double getXData(int index);

    /// Returns the x-range in a freshly allocated `DataInterval`.
    public DataInterval getXRange() {
        return getXRange(null);
    }

    /// Copies the current x-range into `range` or allocates a new `DataInterval`.
    ///
    /// The returned interval is a snapshot of the cached range state, not a live view.
    @Override
    public DataInterval getXRange(DataInterval range) {
        if (range == null)
            return new DataInterval(getCachedXRange());

        DataInterval cachedRange = getCachedXRange();
        range.setMin(cachedRange.getMin());
        range.setMax(cachedRange.getMax());
        return range;
    }

    @Override
    public abstract double getYData(int index);

    /// Returns the y-range in a freshly allocated `DataInterval`.
    public DataInterval getYRange() {
        return getYRange(null);
    }

    /// Copies the current y-range into `range` or allocates a new `DataInterval`.
    ///
    /// The returned interval is a snapshot of the cached range state, not a live view.
    @Override
    public DataInterval getYRange(DataInterval range) {
        if (range == null)
            return new DataInterval(getCachedYRange());

        DataInterval cachedRange = getCachedYRange();
        range.setMin(cachedRange.getMin());
        range.setMax(cachedRange.getMax());
        return range;
    }

    /// Invalidates all cached limits and minimum-x-difference state.
    public final void invalidateLimits() {
        limitsCacheInvalid = true;
        limitsInvalid = true;
        minimumXDifferenceInvalid = true;
    }

    /// Invalidates cached limits for the inclusive range `[firstIndex, lastIndex]`.
    public final void invalidateLimits(int firstIndex, int lastIndex) {
        invalidateLimits(firstIndex, lastIndex, false);
    }

    /// Invalidates cached limits for the inclusive range `[firstIndex, lastIndex]`.
    ///
    /// When `dataAdded` is `true`, the method attempts to preserve the cached minimum x-spacing for
    /// sorted datasets by updating it incrementally instead of always marking it unknown.
    public final void invalidateLimits(int firstIndex, int lastIndex, boolean dataAdded) {
        if (firstIndex < 0 || firstIndex > lastIndex)
            throw new IllegalArgumentException("firstIdx=" + firstIndex + ", lastIdx=" + lastIndex);

        limitsInvalid = true;
        if (!limitsCacheInvalid)
            markBucketRangeDirty(firstIndex >> bucketShift, lastIndex >> bucketShift);

        if (!dataAdded) {
            minimumXDifferenceInvalid = true;
            return;
        }
        updateMinimumXDifferenceAfterAddition(firstIndex, lastIndex);
    }

    /// Returns whether a batch is currently open.
    public final boolean isBatched() {
        return batchDepth > 0;
    }

    /// Returns `false` in the base class.
    ///
    /// Subclasses that support in-place edits override this together with
    /// [#setData(int, double, double)].
    @Override
    public boolean isEditable() {
        return false;
    }

    /// Returns whether undefined y values still contribute to the computed x-range.
    public boolean isXRangeIncludingUndefinedPoints() {
        return xRangeIncludingUndefinedPoints;
    }

    /// Returns `false` in the base class.
    ///
    /// Subclasses with monotonic x data should override this so range queries can use the faster
    /// binary-search path.
    @Override
    public boolean isXValuesSorted() {
        return false;
    }

    private void includePointInBucket(
            LimitsCacheBuckets bucket, double xValue, double yValue, boolean includeUndefinedX, Double undefValue) {
        if (isDefinedY(yValue, undefValue)) {
            bucket.includeY(yValue);
            bucket.includeX(xValue);
        } else if (includeUndefinedX) {
            bucket.includeX(xValue);
        }
    }

    private void initializeTransients() {
        xRange = new DataInterval();
        yRange = new DataInterval();
        limitsInvalid = true;
        limitsCacheInvalid = true;
        minimumXDifferenceInvalid = true;
        batchBeginEvent = new DataSetContentsEvent(this, DataSetContentsEvent.BATCH_BEGIN, -1, -1);
        batchEndEvent = new DataSetContentsEvent(this, DataSetContentsEvent.BATCH_END, -1, -1);
    }

    private void markBucketRangeDirty(int firstBucket, int lastBucket) {
        int firstMaterializedIndex = ensureBucketCacheEntries(firstBucket, lastBucket);
        if (firstMaterializedIndex < 0)
            return;

        int materializedOffset = firstMaterializedIndex - firstBucket;
        for (int bucketIndex = firstBucket; bucketIndex <= lastBucket; bucketIndex++) {
            LimitsCacheBuckets bucket = limitsCacheBuckets[bucketIndex + materializedOffset];
            assert bucket.firstBucket == bucketIndex && bucket.endBucketExclusive == bucketIndex + 1;
            bucket.dirty = true;
        }
    }

    private boolean materializeBucketEntries(DataPoints points) {
        limitsCacheBucketCount = 0;
        limitsCacheInvalid = false;

        int lastBucketIndex = Integer.MIN_VALUE;
        for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
            int bucketIndex = points.getIndex(pointIndex) >> bucketShift;
            if (bucketIndex == lastBucketIndex)
                continue;
            if (ensureBucketCacheEntries(bucketIndex, bucketIndex) < 0)
                return false;
            lastBucketIndex = bucketIndex;
        }
        return true;
    }

    private void populateBucketCache(DataPoints points, boolean includeUndefinedX) {
        Double undefValue = getUndefValue();
        int currentBucketIndex = Integer.MIN_VALUE;
        LimitsCacheBuckets currentBucket = null;
        for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
            int bucketIndex = points.getIndex(pointIndex) >> bucketShift;
            if (bucketIndex != currentBucketIndex) {
                currentBucketIndex = bucketIndex;
                currentBucket = limitsCacheBuckets[findLimitsCacheIndex(bucketIndex)];
                assert bucketContains(currentBucket, bucketIndex);
            }
            includePointInBucket(
                    currentBucket,
                    points.getX(pointIndex),
                    points.getY(pointIndex),
                    includeUndefinedX,
                    undefValue
            );
        }

        for (int bucketIndex = 0; bucketIndex < limitsCacheBucketCount; bucketIndex++) {
            limitsCacheBuckets[bucketIndex].dirty = false;
        }
    }

    /// Stores or removes a dataset property and optionally emits a property-change event.
    ///
    /// The default implementation uses `key.toString()` as the `PropertyChangeEvent` name.
    /// Only `null` and `Serializable` values survive Java serialization of this dataset; other
    /// values remain runtime-only metadata.
    @Override
    public synchronized void putProperty(Object key, Object value, boolean fireEvent) {
        Object oldValue = (fireEvent && properties != null) ? properties.get(key) : null;
        if (value != null)
            getOrCreateProperties().put(key, value);
        else if (properties != null)
            properties.remove(key);

        if (fireEvent && !Objects.equals(oldValue, value))
            fireDataSetPropertyEvent(new DataSetPropertyEvent(this, key.toString(), oldValue, value));
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        int propertyCount = in.readInt();
        if (propertyCount > 0) {
            properties = new HashMap<>(propertyCount);
            while (propertyCount-- > 0) {
                Object key = in.readObject();
                Object value = in.readObject();
                properties.put(key, value);
            }
        }
        initializeTransients();
    }

    private void rebuildLimitsCache(boolean includeUndefinedX) {
        DataPoints points = getData();
        try {
            if (points == null || points.size() <= 0) {
                bucketShift = 0;
                limitsCacheBucketCount = 0;
                limitsCacheInvalid = false;
                return;
            }

            bucketShift = computeInitialBucketShift(points.size());
            limitsCacheBuckets = new LimitsCacheBuckets[INITIAL_BUCKET_ARRAY_CAPACITY];
            while (!materializeBucketEntries(points)) {
                bucketShift++;
            }

            populateBucketCache(points, includeUndefinedX);
            limitsCacheInvalid = false;
        } finally {
            if (points != null)
                points.dispose();
        }
    }

    private void recomputeBucketExtrema(
            LimitsCacheBuckets bucket, boolean includeUndefinedX, Double undefValue) {
        bucket.clearExtrema();

        DataPoints points = getDataBetween(
                bucket.firstBucket << bucketShift,
                (bucket.endBucketExclusive << bucketShift) - 1
        );
        try {
            if (points == null)
                return;

            for (int pointIndex = 0; pointIndex < points.size(); pointIndex++) {
                int bucketIndex = points.getIndex(pointIndex) >> bucketShift;
                assert bucketContains(bucket, bucketIndex);
                includePointInBucket(
                        bucket,
                        points.getX(pointIndex),
                        points.getY(pointIndex),
                        includeUndefinedX,
                        undefValue
                );
            }
        } finally {
            if (points != null)
                points.dispose();
            bucket.dirty = false;
        }
    }

    private void recomputeLimits() {
        computeLimits(xRange, yRange);
        limitsInvalid = false;
    }

    private void refreshDirtyBucketExtrema(boolean includeUndefinedX) {
        Double undefValue = getUndefValue();
        for (int bucketIndex = 0; bucketIndex < limitsCacheBucketCount; bucketIndex++) {
            LimitsCacheBuckets bucket = limitsCacheBuckets[bucketIndex];
            if (bucket.dirty)
                recomputeBucketExtrema(bucket, includeUndefinedX, undefValue);
        }
    }

    private int replaceCoveredSegmentsWithUnitBuckets(
            int firstBucket, int lastBucket, int firstSegmentIndex, int lastSegmentIndex) {
        LimitsCacheBuckets firstSegment = limitsCacheBuckets[firstSegmentIndex];
        LimitsCacheBuckets lastSegment = limitsCacheBuckets[lastSegmentIndex];
        boolean preservePrefix = firstBucket > firstSegment.firstBucket;
        boolean preserveSuffix = lastBucket + 1 < lastSegment.endBucketExclusive;

        int replacementCount = (preservePrefix ? 1 : 0)
                + (lastBucket - firstBucket + 1)
                + (preserveSuffix ? 1 : 0);
        int removedCount = lastSegmentIndex - firstSegmentIndex + 1;
        int additionalCount = replacementCount - removedCount;
        if (!canAddBucketEntries(additionalCount))
            return -1;
        if (additionalCount > 0)
            ensureBucketCacheCapacity(limitsCacheBucketCount + additionalCount);

        int tailSourceIndex = lastSegmentIndex + 1;
        int tailTargetIndex = tailSourceIndex + additionalCount;
        int trailingCount = limitsCacheBucketCount - tailSourceIndex;
        if (additionalCount != 0 && trailingCount > 0) {
            System.arraycopy(
                    limitsCacheBuckets,
                    tailSourceIndex,
                    limitsCacheBuckets,
                    tailTargetIndex,
                    trailingCount
            );
        }

        int writeIndex = firstSegmentIndex;
        if (preservePrefix)
            limitsCacheBuckets[writeIndex++] = new LimitsCacheBuckets(firstSegment.firstBucket, firstBucket);

        int firstMaterializedIndex = writeIndex;
        writeIndex = writeDirtyUnitBuckets(writeIndex, firstBucket, lastBucket);

        if (preserveSuffix) {
            limitsCacheBuckets[writeIndex++] = new LimitsCacheBuckets(
                    lastBucket + 1,
                    lastSegment.endBucketExclusive
            );
        }
        assert writeIndex == firstSegmentIndex + replacementCount;

        limitsCacheBucketCount += additionalCount;
        return firstMaterializedIndex;
    }

    private int replaceTailWithUnitBuckets(int firstBucket, int lastBucket, int firstSegmentIndex) {
        LimitsCacheBuckets firstSegment = limitsCacheBuckets[firstSegmentIndex];
        boolean preservePrefix = firstBucket > firstSegment.firstBucket;
        int replacementCount = (preservePrefix ? 1 : 0) + (lastBucket - firstBucket + 1);
        int removedCount = limitsCacheBucketCount - firstSegmentIndex;
        int additionalCount = replacementCount - removedCount;
        if (!canAddBucketEntries(additionalCount))
            return -1;
        if (additionalCount > 0)
            ensureBucketCacheCapacity(limitsCacheBucketCount + additionalCount);

        int writeIndex = firstSegmentIndex;
        if (preservePrefix)
            limitsCacheBuckets[writeIndex++] = new LimitsCacheBuckets(firstSegment.firstBucket, firstBucket);

        int firstMaterializedIndex = writeIndex;
        writeIndex = writeDirtyUnitBuckets(writeIndex, firstBucket, lastBucket);
        assert writeIndex == firstSegmentIndex + replacementCount;

        limitsCacheBucketCount += additionalCount;
        return firstMaterializedIndex;
    }

    /// Unregisters a listener previously added with [#addDataSetListener(DataSetListener)].
    @Override
    public void removeDataSetListener(DataSetListener listener) {
        listenerList.remove(DataSetListener.class, listener);
    }

    /// Does nothing by default.
    ///
    /// Editable subclasses override this together with [#isEditable()].
    @Override
    public void setData(int index, double x, double y) {
    }

    /// Forces the cached limits into a specific validity state.
    ///
    /// Passing `true` clears only the top-level invalid flag. Subclasses that use this escape hatch
    /// are responsible for keeping the cached intervals and bucket cache synchronized first.
    protected void setLimitsValid(boolean valid) {
        if (!valid)
            invalidateLimits();
        else
            limitsInvalid = false;
    }

    /// Updates the dataset display name through the shared property bag.
    public void setName(String name) {
        putProperty("name", name, true);
    }

    /// Sets the sentinel used to treat y values as undefined.
    ///
    /// Changing this value invalidates the cached ranges because the default limit scan excludes
    /// samples whose y value matches the sentinel. The default property event name for this change
    /// is `"undefValue"`.
    public void setUndefValue(Double undefValue) {
        Double oldUndefValue = this.undefValue;
        if (Objects.equals(oldUndefValue, undefValue))
            return;

        this.undefValue = undefValue;
        invalidateLimits();
        fireDataSetPropertyEvent(new DataSetPropertyEvent(this, "undefValue", oldUndefValue, undefValue));
    }

    /// Changes whether undefined y values still contribute to the computed x-range.
    ///
    /// Toggling this flag invalidates the cached limits because the default range computation may
    /// need to keep or discard x positions whose y values are `NaN` or equal to [#getUndefValue()].
    public void setXRangeIncludingUndefinedPoints(boolean xRangeIncludingUndefinedPoints) {
        if (this.xRangeIncludingUndefinedPoints == xRangeIncludingUndefinedPoints)
            return;

        this.xRangeIncludingUndefinedPoints = xRangeIncludingUndefinedPoints;
        invalidateLimits();
    }

    /// Returns the logical number of points currently exposed by this dataset.
    @Override
    public abstract int size();

    /// Starts a logical batch of contents events.
    ///
    /// The matching `BATCH_BEGIN` marker is emitted lazily on the first contents event that occurs
    /// inside the batch, not at the moment this method is called.
    @Override
    public void startBatch() {
        synchronized (this) {
            if (batchDepth++ == 0)
                batchHasChanges = false;
        }
    }

    @Override
    public String toString() {
        return getClass().getName() + ": " + getName() + " " + getXRange() + " / " + getYRange();
    }

    private void updateMinimumXDifferenceAfterAddition(int firstIndex, int lastIndex) {
        if (minimumXDifferenceInvalid)
            return;
        if (!isXValuesSorted()) {
            minimumXDifferenceInvalid = true;
            return;
        }

        if (firstIndex <= 0) {
            minimumXDifference = NO_POSITIVE_X_DIFFERENCE;
        } else {
            double xValue = getXData(firstIndex);
            double previousX = getXData(firstIndex - 1);
            if (xValue > previousX) {
                double xDifference = xValue - previousX;
                if (minimumXDifference < 0.0 || minimumXDifference > xDifference)
                    minimumXDifference = xDifference;
            } else if (xValue != previousX) {
                minimumXDifferenceInvalid = true;
            }
        }

        if (firstIndex >= lastIndex || minimumXDifferenceInvalid)
            return;

        double previousX = getXData(firstIndex);
        for (int index = firstIndex + 1; index <= lastIndex; index++) {
            double xValue = getXData(index);
            if (xValue > previousX) {
                double xDifference = xValue - previousX;
                if (minimumXDifference < 0.0 || minimumXDifference > xDifference)
                    minimumXDifference = xDifference;
            } else if (xValue != previousX) {
                minimumXDifferenceInvalid = true;
                return;
            }
            previousX = xValue;
        }
    }

    private void updateRangesFromBucketCache(DataInterval xRange, DataInterval yRange) {
        boolean hasAnyXValues = false;
        boolean hasAnyYValues = false;
        double minimumX = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;

        for (int bucketIndex = 0; bucketIndex < limitsCacheBucketCount; bucketIndex++) {
            LimitsCacheBuckets bucket = limitsCacheBuckets[bucketIndex];
            if (bucket.hasXValues) {
                hasAnyXValues = true;
                minimumX = Math.min(minimumX, bucket.minX);
                maximumX = Math.max(maximumX, bucket.maxX);
            }
            if (bucket.hasYValues) {
                hasAnyYValues = true;
                minimumY = Math.min(minimumY, bucket.minY);
                maximumY = Math.max(maximumY, bucket.maxY);
            }
        }

        if (hasAnyXValues)
            xRange.set(minimumX, maximumX);
        else
            xRange.empty();

        if (hasAnyYValues)
            yRange.set(minimumY, maximumY);
        else
            yRange.empty();
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();

        int serializablePropertyCount = 0;
        if (properties != null) {
            Iterator<Object> values = properties.values().iterator();
            while (values.hasNext()) {
                Object value = values.next();
                if (value == null || value instanceof Serializable)
                    serializablePropertyCount++;
            }
        }

        out.writeInt(serializablePropertyCount);
        if (properties == null)
            return;

        Iterator<Map.Entry<Object, Object>> entries = properties.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Object, Object> entry = entries.next();
            Object value = entry.getValue();
            if (value != null && !(value instanceof Serializable))
                continue;

            out.writeObject(entry.getKey());
            out.writeObject(value);
        }
    }

    private int writeDirtyUnitBuckets(int writeIndex, int firstBucket, int lastBucket) {
        for (int bucketIndex = firstBucket; bucketIndex <= lastBucket; bucketIndex++) {
            limitsCacheBuckets[writeIndex++] = new LimitsCacheBuckets(bucketIndex, bucketIndex + 1);
        }
        return writeIndex;
    }
}
