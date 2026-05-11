package one.chartsy.charting.renderers.internal;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.util.Iterator;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.DoublePoint;
import one.chartsy.charting.data.AbstractDataSet;
import one.chartsy.charting.data.DataPoints;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSetPoint;
import one.chartsy.charting.event.DataSetContentsEvent;
import one.chartsy.charting.event.DataSetPropertyEvent;
import one.chartsy.charting.util.IntInterval;
import one.chartsy.charting.util.IntIntervalSet;

/// Builds cumulative [VirtualDataSet] views for summed stair-renderer mode.
///
/// The helper owns one derived dataset per source dataset. Derived dataset `n` exposes the running
/// total of source datasets `0..n` on a shared x-sorted grid. When adjacent source datasets do not
/// sample the same x values, the most recent y value seen so far is carried forward until the next
/// point from that dataset arrives, so the visible cumulative trace behaves like a step series.
///
/// Value or structural source changes rebuild the derived family, while pure
/// [DataSetContentsEvent#DATA_LABEL_CHANGED] notifications can be forwarded incrementally when
/// callers bracket updates with [#dataSourceChangesStarting()] and [#dataSourceChangesEnding()].
///
/// Instances are mutable, tied to the lifecycle of the owning [ChartRenderer], and not
/// thread-safe.
public class XSummedDataSets {

    /// Compact mutable x/y buffer used while materializing one summed dataset.
    ///
    /// The buffer is append-only and keeps only the logical points visible in the derived dataset,
    /// so duplicate source x values can be collapsed before renderers observe the result.
    static final class DoublePoints {
        private int size;
        private final double[] xValues;
        private final double[] yValues;

        public DoublePoints(int capacity) {
            xValues = new double[capacity];
            yValues = new double[capacity];
        }

        /// Appends one visible point.
        public void add(double x, double y) {
            xValues[size] = x;
            yValues[size] = y;
            size++;
        }

        public double getX(int index) {
            return xValues[index];
        }

        public double getY(int index) {
            return yValues[index];
        }

        public int size() {
            return size;
        }
    }

    /// Virtual dataset view that exposes one source dataset as a cumulative running total.
    ///
    /// The view keeps the source dataset's name and editability, but its visible y value at a
    /// given x is the source dataset's current contribution plus the already-summed value from the
    /// previous derived dataset. If the source dataset has no point exactly at that x, the most
    /// recent source value at or before that x is used instead.
    ///
    /// Picks and edits still route back to the source dataset. A virtual point therefore unmaps to
    /// the most recent source index at or before the visible x, and [#setData(int, double, double)]
    /// rewrites only the source y contribution while leaving the source x coordinate unchanged.
    class SummedDataSet extends VirtualDataSet {
        /// Zero-based index of the backing dataset in [XSummedDataSets#sourceDataSets].
        private final int sourceDataSetIndex;
        /// Backing dataset whose metadata and edits are exposed by this virtual view.
        private final DataSet sourceDataSet;
        /// Visible cumulative points materialized for the current source state.
        private transient DoublePoints summedPoints = new DoublePoints(0);
        /// Source x snapshot captured between before/after change notifications.
        private transient double[] sourceXSnapshot;

        SummedDataSet(int sourceDataSetIndex) {
            this.sourceDataSetIndex = sourceDataSetIndex;
            sourceDataSet = sourceDataSets[sourceDataSetIndex];
            refreshSummedPoints();
            setDataSets(new DataSet[]{sourceDataSet});
        }

        /// Builds the first cumulative layer by collapsing duplicate source x values.
        ///
        /// Only the last point from each run of identical x coordinates is retained because later
        /// layers can address at most one carried-forward value per x slot.
        private DoublePoints buildSourcePoints(DataSet dataSet) {
            DataPoints sourcePoints = dataSet.getData();
            try {
                int pointCount = (sourcePoints == null) ? 0 : sourcePoints.size();
                DoublePoints points = new DoublePoints(pointCount);
                for (int sourceIndex = 0; sourceIndex < pointCount; sourceIndex++) {
                    sourceIndex = lastIndexOfDuplicateX(sourcePoints, sourceIndex, pointCount);
                    points.add(sourcePoints.getX(sourceIndex), sourcePoints.getY(sourceIndex));
                }
                return points;
            } finally {
                if (sourcePoints != null)
                    sourcePoints.dispose();
            }
        }

        /// Merges the previous cumulative layer with this dataset into one step-like running total.
        ///
        /// Both inputs are traversed in x order. When only one dataset contributes at the current x,
        /// the most recently seen value from the other dataset is carried forward.
        private DoublePoints buildSummedPoints(DataSet previousSummedDataSet, DataSet dataSet) {
            DataPoints previousPoints = previousSummedDataSet.getData();
            DataPoints currentPoints = dataSet.getData();
            try {
                int previousPointCount = (previousPoints == null) ? 0 : previousPoints.size();
                int currentPointCount = (currentPoints == null) ? 0 : currentPoints.size();
                DoublePoints points = new DoublePoints(previousPointCount + currentPointCount);
                int previousIndex = 0;
                int currentIndex = 0;
                double previousY = 0.0;
                double currentY = 0.0;

                while (previousIndex < previousPointCount && currentIndex < currentPointCount) {
                    double previousX = previousPoints.getX(previousIndex);
                    double currentX = currentPoints.getX(currentIndex);
                    if (previousX == currentX) {
                        currentIndex = lastIndexOfDuplicateX(currentPoints, currentIndex, currentPointCount);
                        previousY = previousPoints.getY(previousIndex);
                        currentY = currentPoints.getY(currentIndex);
                        points.add(currentX, previousY + currentY);
                        previousIndex++;
                        currentIndex++;
                    } else if (previousX < currentX) {
                        previousY = previousPoints.getY(previousIndex);
                        points.add(previousX, previousY + currentY);
                        previousIndex++;
                    } else {
                        currentIndex = lastIndexOfDuplicateX(currentPoints, currentIndex, currentPointCount);
                        currentY = currentPoints.getY(currentIndex);
                        points.add(currentX, previousY + currentY);
                        currentIndex++;
                    }
                }

                while (previousIndex < previousPointCount) {
                    points.add(previousPoints.getX(previousIndex), previousPoints.getY(previousIndex) + currentY);
                    previousIndex++;
                }
                while (currentIndex < currentPointCount) {
                    currentIndex = lastIndexOfDuplicateX(currentPoints, currentIndex, currentPointCount);
                    points.add(currentPoints.getX(currentIndex), currentPoints.getY(currentIndex) + previousY);
                    currentIndex++;
                }
                return points;
            } finally {
                if (previousPoints != null)
                    previousPoints.dispose();
                if (currentPoints != null)
                    currentPoints.dispose();
            }
        }

        /// Recomputes this view from the current source datasets and refreshes cached limits.
        ///
        /// The first derived dataset is built directly from its source dataset. Every later derived
        /// dataset is built from the previous cumulative layer plus its own source dataset.
        void refreshSummedPoints() {
            if (sourceDataSetIndex == 0) {
                summedPoints = isSortedByX(sourceDataSet) ? buildSourcePoints(sourceDataSet) : new DoublePoints(0);
            } else {
                DataSet previousSummedDataSet = summedDataSets[sourceDataSetIndex - 1];
                summedPoints = isSortedByX(sourceDataSet)
                        ? buildSummedPoints(previousSummedDataSet, sourceDataSet)
                        : new DoublePoints(0);
            }
            super.invalidateLimits();
            super.updateDataCount();
        }

        @Override
        protected int computeDataCount() {
            return summedPoints.size();
        }

        /// Tracks whether a source in-place update also moved x coordinates.
        ///
        /// A pure y change stays an `AFTER_DATA_CHANGED` event. If the source x values differ
        /// between the paired before/after notifications, the event is widened to
        /// [DataSetContentsEvent#DATA_CHANGED] so the outer helper treats it as a structural
        /// cumulative refresh.
        @Override
        protected void dataSetContentsChanged(DataSetContentsEvent event) {
            DataSetContentsEvent forwardedEvent = event;
            switch (event.getType()) {
                case DataSetContentsEvent.BEFORE_DATA_CHANGED -> captureSourceXSnapshot(
                        event.getFirstIdx(), event.getLastIdx());
                case DataSetContentsEvent.AFTER_DATA_CHANGED -> {
                    if (!sourceXSnapshotMatches(event.getFirstIdx(), event.getLastIdx())) {
                        forwardedEvent = new DataSetContentsEvent(
                                event.getDataSet(),
                                DataSetContentsEvent.DATA_CHANGED,
                                event.getFirstIdx(),
                                event.getLastIdx());
                    }
                    sourceXSnapshot = null;
                }
                default -> sourceXSnapshot = null;
            }
            XSummedDataSets.this.dataSetContentsChanged(sourceDataSetIndex, forwardedEvent);
        }

        private void captureSourceXSnapshot(int firstIndex, int lastIndex) {
            if (firstIndex > lastIndex) {
                sourceXSnapshot = null;
                return;
            }
            sourceXSnapshot = new double[lastIndex - firstIndex + 1];
            for (int sourceIndex = firstIndex; sourceIndex <= lastIndex; sourceIndex++) {
                sourceXSnapshot[sourceIndex - firstIndex] = sourceDataSet.getXData(sourceIndex);
            }
        }

        private boolean sourceXSnapshotMatches(int firstIndex, int lastIndex) {
            if (firstIndex > lastIndex || sourceXSnapshot == null || sourceXSnapshot.length != lastIndex - firstIndex + 1)
                return false;

            for (int sourceIndex = firstIndex; sourceIndex <= lastIndex; sourceIndex++) {
                double oldX = sourceXSnapshot[sourceIndex - firstIndex];
                double newX = sourceDataSet.getXData(sourceIndex);
                boolean unchanged = Double.isNaN(oldX) ? Double.isNaN(newX) : oldX == newX;
                if (!unchanged)
                    return false;
            }
            return true;
        }

        /// Forwards source property changes unchanged because names and editability are delegated.
        @Override
        protected void dataSetPropertyChanged(DataSetPropertyEvent event) {
            super.fireDataSetPropertyEvent(event);
        }

        @Override
        protected void dataSetsChanged() {
        }

        @Override
        public String getName() {
            return sourceDataSet.getName();
        }

        @Override
        public Double getUndefValue() {
            return null;
        }

        @Override
        public double getXData(int index) {
            return summedPoints.getX(index);
        }

        @Override
        public double getYData(int index) {
            return summedPoints.getY(index);
        }

        @Override
        public boolean isEditable() {
            return sourceDataSet.isEditable();
        }

        @Override
        public boolean isXValuesSorted() {
            return true;
        }

        /// Maps a source point into this cumulative x grid.
        ///
        /// The mapping uses the source point's current x value and resolves it to the last visible
        /// cumulative slot at or before that x. For native source points this is the slot at the
        /// same x; carried-forward cumulative slots are not selected here.
        @Override
        public void map(DataSetPoint point) {
            point.index = findLastIndexAtOrBeforeX(this, point.getXData());
            point.dataSet = this;
        }

        @Override
        public boolean mapsMonotonically() {
            return true;
        }

        @Serial
        private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
            input.defaultReadObject();
            input.registerValidation(this::refreshSummedPoints, 0);
        }

        /// Writes a visible cumulative y value back into the source dataset's own contribution.
        ///
        /// The edit is accepted only when `x` still matches the current visible cumulative x at
        /// `index`. The previous cumulative layer is subtracted first, so only this source dataset's
        /// y contribution is rewritten.
        @Override
        public void setData(int index, double x, double y) {
            if (!isEditable() || x != getXData(index))
                return;

            int sourceIndex = findLastIndexAtOrBeforeX(sourceDataSet, x);
            if (sourceIndex < 0)
                return;

            sourceDataSet.setData(
                    sourceIndex,
                    sourceDataSet.getXData(sourceIndex),
                    y - getPreviousSummedY(x));
        }

        private double getPreviousSummedY(double x) {
            if (sourceDataSetIndex == 0)
                return 0.0;

            DataSet previousSummedDataSet = summedDataSets[sourceDataSetIndex - 1];
            int previousIndex = findLastIndexAtOrBeforeX(previousSummedDataSet, x);
            return (previousIndex < 0) ? 0.0 : previousSummedDataSet.getYData(previousIndex);
        }

        private int resolveSourceIndex(int summedIndex) {
            if (summedIndex < 0 || summedIndex >= size())
                return -1;
            return findLastIndexAtOrBeforeX(sourceDataSet, getXData(summedIndex));
        }

        /// Resolves a cumulative point back to the source dataset point that currently contributes
        /// at that x.
        @Override
        public void unmap(DataSetPoint point) {
            point.index = resolveSourceIndex(point.getIndex());
            point.dataSet = sourceDataSet;
        }

        /// Resolves a cumulative edit target back to the source dataset and source y scale.
        ///
        /// The editable point's y value is reduced by the previous cumulative layer before the
        /// source dataset receives it.
        @Override
        public void unmap(DataSetPoint point, DoublePoint editablePoint) {
            if (point.getIndex() >= 0 && point.getIndex() < size()) {
                double x = getXData(point.getIndex());
                editablePoint.y -= getPreviousSummedY(x);
                point.index = findLastIndexAtOrBeforeX(sourceDataSet, x);
            } else {
                point.index = -1;
            }
            point.dataSet = sourceDataSet;
        }
    }

    private static final boolean assertionsDisabled = !XSummedDataSets.class.desiredAssertionStatus();

    final DataSet[] sourceDataSets;
    final int dataSetCount;
    SummedDataSet[] summedDataSets;
    private final ChartRenderer renderer;

    private transient int batchDepth;
    private transient boolean fullRefreshPending;
    private transient IntIntervalSet[] pendingLabelChangedRanges;

    /// Creates the summed dataset family for `sourceDataSets` in renderer-child order.
    public XSummedDataSets(DataSet[] sourceDataSets, ChartRenderer renderer) {
        this.sourceDataSets = sourceDataSets;
        dataSetCount = sourceDataSets.length;
        this.renderer = renderer;
        initializeSummedDataSets();
    }

    /// Installs one derived dataset per source dataset.
    private void initializeSummedDataSets() {
        summedDataSets = new SummedDataSet[dataSetCount];
        for (int dataSetIndex = 0; dataSetIndex < dataSetCount; dataSetIndex++) {
            summedDataSets[dataSetIndex] = new SummedDataSet(dataSetIndex);
        }
    }

    /// Consumes a source event emitted by one child dataset of the summed family.
    private void dataSetContentsChanged(int dataSetIndex, DataSetContentsEvent event) {
        switch (event.getType()) {
            case DataSetContentsEvent.BATCH_BEGIN -> dataSourceChangesStarting();
            case DataSetContentsEvent.BATCH_END -> dataSourceChangesEnding();
            case DataSetContentsEvent.AFTER_DATA_CHANGED,
                 DataSetContentsEvent.DATA_ADDED,
                 DataSetContentsEvent.DATA_CHANGED,
                 DataSetContentsEvent.FULL_UPDATE -> {
                if (batchDepth <= 0)
                    rebuildAllSummedDataSets();
                else
                    fullRefreshPending = true;
            }
            case DataSetContentsEvent.DATA_LABEL_CHANGED -> {
                int firstIndex = event.getFirstIdx();
                int lastIndex = event.getLastIdx();
                if (firstIndex <= lastIndex) {
                    if (batchDepth <= 0)
                        fireLabelChanges(dataSetIndex, firstIndex, lastIndex);
                    else if (!fullRefreshPending)
                        queueLabelChanges(dataSetIndex, firstIndex, lastIndex);
                }
            }
            default -> {
            }
        }
    }

    private void queueLabelChanges(int dataSetIndex, int firstIndex, int lastIndex) {
        if (pendingLabelChangedRanges == null)
            pendingLabelChangedRanges = new IntIntervalSet[dataSetCount];
        if (pendingLabelChangedRanges[dataSetIndex] == null)
            pendingLabelChangedRanges[dataSetIndex] = new IntIntervalSet();
        pendingLabelChangedRanges[dataSetIndex].add(firstIndex, lastIndex);
    }

    /// Starts the chart-wide batch used while forwarding label-only changes.
    private void beginLabelChangeBatch() {
        if (renderer.getChart() != null)
            renderer.getChart().startRendererChanges();
        for (SummedDataSet summedDataSet : summedDataSets) {
            summedDataSet.startBatch();
        }
    }

    /// Forwards source label changes to the matching derived dataset slots.
    private void fireLabelChanges(int dataSetIndex, int firstIndex, int lastIndex) {
        DataSet sourceDataSet = sourceDataSets[dataSetIndex];
        AbstractDataSet summedDataSet = summedDataSets[dataSetIndex];
        summedDataSet.startBatch();
        try {
            for (int sourceIndex = firstIndex; sourceIndex <= lastIndex; sourceIndex++) {
                double x = sourceDataSet.getXData(sourceIndex);
                int summedIndex = findLastIndexAtOrBeforeX(summedDataSet, x);
                if (summedIndex >= 0) {
                    if (!assertionsDisabled && x != summedDataSet.getXData(summedIndex))
                        throw new AssertionError();
                    summedDataSet.fireDataSetContentsEvent(new DataSetContentsEvent(
                            summedDataSet,
                            DataSetContentsEvent.DATA_LABEL_CHANGED,
                            summedIndex,
                            summedIndex));
                }
            }
        } finally {
            summedDataSet.endBatch();
        }
    }

    /// Ends the chart-wide batch used while forwarding label-only changes.
    private void endLabelChangeBatch() {
        for (SummedDataSet summedDataSet : summedDataSets) {
            summedDataSet.endBatch();
        }
        if (renderer.getChart() != null)
            renderer.getChart().endRendererChanges();
    }

    private void disposeSummedDataSets() {
        for (SummedDataSet summedDataSet : summedDataSets) {
            summedDataSet.dispose();
        }
    }

    /// Closes one deferred source-change batch and flushes any pending derived updates.
    ///
    /// When the outermost batch ends, value or structural changes rebuild every summed dataset.
    /// Otherwise the helper forwards any pending label-only changes without recomputing the
    /// cumulative points.
    public void dataSourceChangesEnding() {
        if (batchDepth <= 0)
            return;

        batchDepth--;
        if (batchDepth > 0)
            return;

        try {
            if (fullRefreshPending) {
                rebuildAllSummedDataSets();
            } else if (pendingLabelChangedRanges != null) {
                beginLabelChangeBatch();
                try {
                    for (int dataSetIndex = 0; dataSetIndex < dataSetCount; dataSetIndex++) {
                        IntIntervalSet intervals = pendingLabelChangedRanges[dataSetIndex];
                        if (intervals == null)
                            continue;

                        Iterator<IntInterval> iterator = intervals.intervalIterator();
                        while (iterator.hasNext()) {
                            IntInterval interval = iterator.next();
                            fireLabelChanges(dataSetIndex, interval.getFirst(), interval.getLast());
                        }
                    }
                } finally {
                    endLabelChangeBatch();
                }
            }
        } finally {
            fullRefreshPending = false;
            pendingLabelChangedRanges = null;
        }
    }

    /// Opens one deferred source-change batch scope.
    public void dataSourceChangesStarting() {
        batchDepth++;
    }

    /// Disposes the current derived datasets and detaches their source listeners.
    public void dispose() {
        disposeSummedDataSets();
    }

    /// Rebuilds every summed dataset and fires a full-update event for each of them.
    private void rebuildAllSummedDataSets() {
        for (SummedDataSet summedDataSet : summedDataSets) {
            summedDataSet.refreshSummedPoints();
        }
        if (renderer.getChart() != null)
            renderer.getChart().startRendererChanges();
        try {
            for (AbstractDataSet summedDataSet : summedDataSets) {
                summedDataSet.fireDataSetContentsEvent(new DataSetContentsEvent(summedDataSet));
            }
        } finally {
            if (renderer.getChart() != null)
                renderer.getChart().endRendererChanges();
        }
    }

    /// Returns the current derived datasets in the same order as [#sourceDataSets].
    ///
    /// The returned array is live and is reused until this helper is rebuilt or disposed.
    public VirtualDataSet[] getSummedDataSets() {
        return summedDataSets;
    }

    private static int lastIndexOfDuplicateX(DataPoints points, int index, int pointCount) {
        while (index + 1 < pointCount && points.getX(index) == points.getX(index + 1)) {
            index++;
        }
        return index;
    }

    /// Returns whether `dataSet` can be traversed in nondecreasing x order.
    private static boolean isSortedByX(DataSet dataSet) {
        if (dataSet.isXValuesSorted())
            return true;

        DataPoints points = dataSet.getData();
        try {
            if (points == null || points.size() <= 1)
                return true;

            double previousX = points.getX(0);
            for (int index = 1, pointCount = points.size(); index < pointCount; index++) {
                double x = points.getX(index);
                if (previousX > x)
                    return false;
                previousX = x;
            }
            return true;
        } finally {
            if (points != null)
                points.dispose();
        }
    }

    /// Finds the last dataset index whose x value is less than or equal to `x`.
    ///
    /// This lookup underpins the helper's carry-forward semantics: when a dataset has no point
    /// exactly at `x`, the most recent point at or before `x` remains the active contribution.
    private static int findLastIndexAtOrBeforeX(DataSet dataSet, double x) {
        int low = 0;
        int high = dataSet.size();
        while (low < high) {
            int mid = low + (high - low) / 2;
            if (dataSet.getXData(mid) > x)
                high = mid;
            else
                low = mid + 1;
        }
        return low - 1;
    }
}
