package one.chartsy.charting.renderers.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

import one.chartsy.charting.ChartRenderer;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.DoublePoint;
import one.chartsy.charting.data.AbstractDataSet;
import one.chartsy.charting.data.DataPoints;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSetPoint;
import one.chartsy.charting.event.DataSetContentsEvent;
import one.chartsy.charting.event.DataSetPropertyEvent;
import one.chartsy.charting.util.DoubleTreeMap;
import one.chartsy.charting.util.IntInterval;
import one.chartsy.charting.util.IntIntervalSet;

/// Rewrites several source datasets into x-aligned stacked [VirtualDataSet] views.
///
/// The helper builds one shared tower model keyed by x value. Each tower collects the
/// contributions of every source dataset that currently has a point at that x and exposes one
/// derived [StackedDataSet] per original dataset through [#getStackedDataSets()]. Renderers then
/// paint, pick, and edit against those virtual datasets as if the stacks were native data.
///
/// Two stacking behaviors are supported:
/// - regular cumulative stacking, optionally with separate positive and negative baselines
/// - `100%` stacking, where derived y-values and baselines are normalized to the `0..100` range
///
/// Source-dataset content events can be bracketed by [#dataSourceChangesStarting()] and
/// [#dataSourceChangesEnding()] so the helper can coalesce incremental updates and rebuild the
/// shared tower model only when necessary.
///
/// Instances are mutable, tied to the lifecycle of the owning [ChartRenderer], and not
/// thread-safe.
public class XStackedDataSets {

    /// Virtual dataset view that exposes one source dataset inside the shared x-aligned tower model.
    ///
    /// Each logical index in this view refers to the x-sorted tower order maintained by the
    /// surrounding [XStackedDataSets]. Labels, editability, and writes are delegated back to the
    /// matching source point, while [#getYData(int)] and [#getPreviousYData(int)] expose the
    /// cumulative stack top and baseline visible at that tower slot.
    ///
    /// ### API Note
    ///
    /// In `100%` mode the view preserves the source x coordinates but rewrites visible y values and
    /// editable baselines into the `0..100` range.
    class StackedDataSet extends VirtualDataSet
            implements one.chartsy.charting.renderers.internal.StackedDataSet {
        /// Zero-based position of the backing dataset in the surrounding stack order.
        private final int sourceDataSetIndex;
        /// Backing dataset whose labels, editability, and raw writes are forwarded.
        private final DataSet sourceDataSet;
        /// Temporary x snapshot captured between before/after change notifications.
        private transient double[] sourceXSnapshot;

        StackedDataSet(int sourceDataSetIndex) {
            this.sourceDataSetIndex = sourceDataSetIndex;
            sourceDataSet = XStackedDataSets.this.sourceDataSets[sourceDataSetIndex];
            setDataSets(new DataSet[] { sourceDataSet });
        }

        /// Converts one cumulative tower contribution into its `100%` stacked y value.
        private double toNormalizedDisplayedY(
                ArrayList<XStackedDataSets.StackedPoint> stackedPoints,
                XStackedDataSets.StackedPointS point
        ) {
            if (Double.isNaN(point.visibleY))
                return Double.NaN;
            if (sourceDataSetIndex == XStackedDataSets.this.sourceDataSets.length - 1)
                return 100.0;
            double cumulativeY = point.cumulativeY;
            double towerTotal = ((XStackedDataSets.StackedPointS) stackedPoints.getLast()).cumulativeY;
            if (towerTotal == 0.0)
                return 0.0;
            return 100.0 * (cumulativeY / towerTotal);
        }

        /// Finds this dataset's contribution inside the supplied x-aligned tower.
        private XStackedDataSets.StackedPoint findContribution(XStackedDataSets.StackedTower tower) {
            ArrayList<XStackedDataSets.StackedPoint> stackedPoints = tower.contributions;
            int upperBound = stackedPoints.size();
            int lowerBound = 0;
            while (upperBound > lowerBound) {
                int midIndex = lowerBound + upperBound >> 1;
                XStackedDataSets.StackedPoint point = stackedPoints.get(midIndex);
                int comparison = point.dataSetIndex - sourceDataSetIndex;
                if (comparison < 0) {
                    lowerBound = midIndex + 1;
                } else {
                    if (comparison == 0)
                        return point;
                    upperBound = midIndex;
                }
            }
            return null;
        }

        private ArrayList<DoubleTreeMap.Entry<XStackedDataSets.StackedTower>> getTowerEntries() {
            return towerEntriesByDataSet[sourceDataSetIndex];
        }

        private DoubleTreeMap.Entry<XStackedDataSets.StackedTower> getTowerEntry(int logicalIndex) {
            ArrayList<DoubleTreeMap.Entry<XStackedDataSets.StackedTower>> towerEntries = getTowerEntries();
            if (logicalIndex >= towerEntries.size())
                throw new ArrayIndexOutOfBoundsException(logicalIndex);
            return towerEntries.get(logicalIndex);
        }

        private XStackedDataSets.StackedTower getTower(int logicalIndex) {
            return getTowerEntry(logicalIndex).getValue();
        }

        private XStackedDataSets.StackedPoint getContribution(int logicalIndex) {
            return findContribution(getTower(logicalIndex));
        }

        private int getContributionIndex(XStackedDataSets.StackedTower tower) {
            return XStackedDataSets.this.findContributionIndex(tower, sourceDataSetIndex);
        }

        private int getSourceIndex(ArrayList<XStackedDataSets.StackedPoint> stackedPoints, int contributionIndex) {
            return stackedPoints.get(contributionIndex).sourceIndex;
        }

        private double getDisplayedY(XStackedDataSets.StackedTower tower, XStackedDataSets.StackedPoint contribution) {
            if (!XStackedDataSets.this.stacked100Percent)
                return contribution.visibleY;
            return toNormalizedDisplayedY(tower.contributions, (XStackedDataSets.StackedPointS) contribution);
        }

        private boolean hasDefinedContribution(XStackedDataSets.StackedTower tower) {
            XStackedDataSets.StackedPoint contribution = findContribution(tower);
            return contribution != null && !Double.isNaN(contribution.visibleY);
        }

        private boolean isTopNormalizedDataSet() {
            return sourceDataSetIndex == XStackedDataSets.this.sourceDataSets.length - 1;
        }

        private double getRegularBaseline(ArrayList<XStackedDataSets.StackedPoint> stackedPoints, int contributionIndex) {
            return (contributionIndex <= 0)
                    ? 0.0
                    : ((XStackedDataSets.StackedPointS) stackedPoints.get(contributionIndex - 1)).cumulativeY;
        }

        private double getDivergingBaseline(ArrayList<XStackedDataSets.StackedPoint> stackedPoints, int contributionIndex) {
            if (contributionIndex <= 0)
                return 0.0;

            XStackedDataSets.StackedPointD currentContribution =
                    (XStackedDataSets.StackedPointD) stackedPoints.get(contributionIndex);
            XStackedDataSets.StackedPointD previousContribution =
                    (XStackedDataSets.StackedPointD) stackedPoints.get(contributionIndex - 1);
            return currentContribution.negativeBranch
                    ? previousContribution.negativeY
                    : previousContribution.positiveY;
        }

        private double getEditBaseline(ArrayList<XStackedDataSets.StackedPoint> stackedPoints, int contributionIndex) {
            return XStackedDataSets.this.diverging
                    ? getDivergingBaseline(stackedPoints, contributionIndex)
                    : getRegularBaseline(stackedPoints, contributionIndex);
        }

        private double getNormalizedTrailingSourceY(
                ArrayList<XStackedDataSets.StackedPoint> stackedPoints,
                int contributionIndex
        ) {
            double trailingSourceY = 0.0;
            for (int nextIndex = stackedPoints.size() - 1; nextIndex > contributionIndex; nextIndex--) {
                XStackedDataSets.StackedPoint nextContribution = stackedPoints.get(nextIndex);
                if (!Double.isNaN(nextContribution.visibleY)) {
                    trailingSourceY += XStackedDataSets.this.sourceDataSets[nextContribution.dataSetIndex]
                            .getYData(nextContribution.sourceIndex);
                }
            }
            return trailingSourceY;
        }

        private double toNormalizedSourceY(ArrayList<XStackedDataSets.StackedPoint> stackedPoints, int contributionIndex, double normalizedY) {
            if (normalizedY <= 0.0)
                return 0.0;

            double baseline = getRegularBaseline(stackedPoints, contributionIndex);
            double trailingSourceY = getNormalizedTrailingSourceY(stackedPoints, contributionIndex);
            return ((baseline + trailingSourceY) * normalizedY - 100.0 * baseline) / (100.0 - normalizedY);
        }

        private void zeroOtherNormalizedContributors(
                ArrayList<XStackedDataSets.StackedPoint> stackedPoints,
                double x
        ) {
            XStackedDataSets.this.startDerivedDataSetBatches();
            try {
                for (XStackedDataSets.StackedPoint contribution : stackedPoints) {
                    int dataSetIndex = contribution.dataSetIndex;
                    if (dataSetIndex != sourceDataSetIndex) {
                        XStackedDataSets.this.sourceDataSets[dataSetIndex]
                                .setData(contribution.sourceIndex, x, 0.0);
                    }
                }
            } finally {
                XStackedDataSets.this.endDerivedDataSetBatches();
            }
        }

        private DataInterval emptyRange(DataInterval reuse) {
            if (reuse == null)
                return new DataInterval();
            reuse.empty();
            return reuse;
        }

        @Override
        protected void computeLimits(DataInterval xRange, DataInterval yRange) {
            super.computeLimits(xRange, yRange);
            if (XStackedDataSets.this.stacked100Percent)
                yRange.set(0.0, 100.0);
        }

        /// Forwards backing-dataset mutations to the shared tower model.
        ///
        /// Pure y changes can stay incremental. When the changed range also shifts x values between
        /// the paired before/after notifications, the event is widened to `DATA_CHANGED` so the
        /// outer helper refreshes the affected tower mapping instead of treating it as an in-place
        /// value edit.
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
            XStackedDataSets.this.dataSetContentsChanged(sourceDataSetIndex, forwardedEvent);
        }

        private void captureSourceXSnapshot(int firstIndex, int lastIndex) {
            if (firstIndex > lastIndex) {
                sourceXSnapshot = null;
                return;
            }
            sourceXSnapshot = new double[lastIndex - firstIndex + 1];
            for (int sourceIndex = firstIndex; sourceIndex <= lastIndex; sourceIndex++)
                sourceXSnapshot[sourceIndex - firstIndex] = sourceDataSet.getXData(sourceIndex);
        }

        private boolean sourceXSnapshotMatches(int firstIndex, int lastIndex) {
            if (firstIndex > lastIndex
                    || sourceXSnapshot == null
                    || sourceXSnapshot.length != lastIndex - firstIndex + 1) {
                return false;
            }

            for (int sourceIndex = firstIndex; sourceIndex <= lastIndex; sourceIndex++) {
                double oldX = sourceXSnapshot[sourceIndex - firstIndex];
                double newX = sourceDataSet.getXData(sourceIndex);
                boolean unchanged = Double.isNaN(oldX) ? Double.isNaN(newX) : oldX == newX;
                if (!unchanged)
                    return false;
            }
            return true;
        }

        @Override
        protected void dataSetPropertyChanged(DataSetPropertyEvent event) {
            super.fireDataSetPropertyEvent(event);
        }

        @Override
        protected void dataSetsChanged() {
        }

        /// Materializes the requested logical range as stacked points owned by this virtual view.
        ///
        /// Returned [DataPoints] keep this view's logical indices so downstream renderers can
        /// continue addressing the same tower slots.
        @Override
        public DataPoints getDataBetween(int firstIndex, int lastIndex) {
            int clampedFirstIndex = Math.max(0, firstIndex);
            ArrayList<DoubleTreeMap.Entry<XStackedDataSets.StackedTower>> towerEntries = getTowerEntries();
            int clampedLastIndex = Math.min(lastIndex, towerEntries.size() - 1);
            if (clampedFirstIndex > clampedLastIndex)
                return null;

            DataPoints points = new DataPoints(this, clampedLastIndex - clampedFirstIndex + 1);
            for (int logicalIndex = clampedFirstIndex; logicalIndex <= clampedLastIndex; logicalIndex++) {
                DoubleTreeMap.Entry<XStackedDataSets.StackedTower> towerEntry = towerEntries.get(logicalIndex);
                XStackedDataSets.StackedTower tower = towerEntry.getValue();
                XStackedDataSets.StackedPoint contribution = findContribution(tower);
                if (contribution != null)
                    points.add(towerEntry.getKey(), getDisplayedY(tower, contribution), logicalIndex);
            }
            return points;
        }

        @Override
        public String getDataLabel(int logicalIndex) {
            XStackedDataSets.StackedPoint contribution = getContribution(logicalIndex);
            return (contribution == null) ? null : sourceDataSet.getDataLabel(contribution.sourceIndex);
        }

        @Override
        public String getName() {
            return sourceDataSet.getName();
        }

        /// {@inheritDoc}
        ///
        /// In regular mode the baseline comes from the preceding contribution in the same tower.
        /// Diverging stacks choose the positive or negative running total that matches the current
        /// contribution's branch. In `100%` mode the returned baseline is normalized to the same
        /// `0..100` scale as [#getYData(int)].
        @Override
        public double getPreviousYData(int logicalIndex) {
            XStackedDataSets.StackedTower tower = getTower(logicalIndex);
            int contributionIndex = XStackedDataSets.this.findContributionIndex(tower, sourceDataSetIndex);
            if (contributionIndex <= 0)
                return Double.NaN;

            ArrayList<XStackedDataSets.StackedPoint> stackedPoints = tower.contributions;
            XStackedDataSets.StackedPoint previousContribution = stackedPoints.get(contributionIndex - 1);
            if (!XStackedDataSets.this.stacked100Percent) {
                if (!XStackedDataSets.this.diverging)
                    return ((XStackedDataSets.StackedPointS) previousContribution).cumulativeY;

                XStackedDataSets.StackedPointD currentContribution =
                        (XStackedDataSets.StackedPointD) stackedPoints.get(contributionIndex);
                XStackedDataSets.StackedPointD previousDivergingContribution =
                        (XStackedDataSets.StackedPointD) previousContribution;
                return currentContribution.negativeBranch
                        ? previousDivergingContribution.negativeY
                        : previousDivergingContribution.positiveY;
            }

            double cumulativeBaseline = ((XStackedDataSets.StackedPointS) previousContribution).cumulativeY;
            double towerTotal = ((XStackedDataSets.StackedPointS) stackedPoints.getLast()).cumulativeY;
            if (towerTotal == 0.0)
                return 0.0;
            return 100.0 * (cumulativeBaseline / towerTotal);
        }

        @Override
        public Double getUndefValue() {
            return null;
        }

        @Override
        public double getXData(int logicalIndex) {
            return getTowerEntry(logicalIndex).getKey();
        }

        /// Returns the x range covered by currently defined stacked points in this view.
        ///
        /// For x-sorted inputs the method scans only the leading and trailing tower windows until it
        /// finds non-`NaN` stacked values and otherwise falls back to the backing dataset's range.
        @Override
        public DataInterval getXRange(DataInterval reuse) {
            if (towerCount == 0)
                return emptyRange(reuse);
            if (!sourceDataSet.isXValuesSorted())
                return sourceDataSet.getXRange(reuse);

            int firstTowerIndex = 0;
            int lastTowerIndex = towerCount - 1;
            int leadingScanLimit = firstTowerIndex + edgeScanLimit;
            int trailingScanLimit = lastTowerIndex - edgeScanLimit;
            while (firstTowerIndex <= lastTowerIndex) {
                if (hasDefinedContribution(sortedTowerEntries[firstTowerIndex].getValue()))
                    break;
                firstTowerIndex++;
                if (firstTowerIndex >= leadingScanLimit)
                    return sourceDataSet.getXRange(reuse);
            }
            while (firstTowerIndex <= lastTowerIndex) {
                if (hasDefinedContribution(sortedTowerEntries[lastTowerIndex].getValue()))
                    break;
                lastTowerIndex--;
                if (lastTowerIndex <= trailingScanLimit)
                    return sourceDataSet.getXRange(reuse);
            }
            if (firstTowerIndex > lastTowerIndex)
                return emptyRange(reuse);

            double minimumX = sortedTowerEntries[firstTowerIndex].getKey();
            double maximumX = sortedTowerEntries[lastTowerIndex].getKey();
            if (reuse == null)
                return new DataInterval(minimumX, maximumX);
            reuse.setMin(minimumX);
            reuse.setMax(maximumX);
            return reuse;
        }

        /// Returns the visible stacked y value for the logical point at `index`.
        ///
        /// In regular mode this is the cumulative tower top cached for the matching contribution.
        /// In `100%` mode it is normalized against the last defined cumulative value in the same
        /// tower.
        @Override
        public double getYData(int index) {
            XStackedDataSets.StackedTower tower = getTower(index);
            XStackedDataSets.StackedPoint contribution = findContribution(tower);
            if (contribution == null)
                return Double.NaN;
            return getDisplayedY(tower, contribution);
        }

        @Override
        public boolean isEditable() {
            return sourceDataSet.isEditable();
        }

        @Override
        public boolean isXValuesSorted() {
            return true;
        }

        /// Maps a source-space point onto this stacked view by x value.
        ///
        /// `point.index` becomes the logical index of the matching x tower in this stacked view, or
        /// `-1` when the source x value is currently absent from the stacked family. Because the
        /// tower model is x-sorted, successful mappings preserve x-order when the backing dataset
        /// itself reports sorted x values.
        @Override
        public void map(DataSetPoint point) {
            double x = point.getXData();
            XStackedDataSets.StackedTower tower = towersByX.get(x);
            if (tower == null) {
                point.index = -1;
            } else {
                int contributionIndex = XStackedDataSets.this.findContributionIndex(tower, sourceDataSetIndex);
                point.index = (contributionIndex < 0)
                        ? -1
                        : tower.contributions.get(contributionIndex).logicalIndex;
            }
            point.dataSet = this;
        }

        @Override
        public boolean mapsMonotonically() {
            return sourceDataSet.isXValuesSorted();
        }

        /// Rewrites the underlying source value represented by one stacked logical point.
        ///
        /// The update is ignored when `x` no longer matches the current tower addressed by
        /// `index`.
        ///
        /// The supplied y coordinate is interpreted in stacked coordinates. Regular and diverging
        /// stacks subtract the visible baseline below this series before forwarding the edit.
        /// In `100%` mode the method solves for the raw source contribution that would place this
        /// series at the requested cumulative percentage while leaving the current tower
        /// composition intact. Requests at or above `100` instead zero every other contributor in
        /// the same tower so this dataset occupies the full normalized stack height.
        @Override
        public void setData(int index, double x, double y) {
            if (!isEditable() || x != getXData(index))
                return;

            XStackedDataSets.StackedTower tower = getTower(index);
            ArrayList<XStackedDataSets.StackedPoint> stackedPoints = tower.contributions;
            int contributionIndex = getContributionIndex(tower);
            if (contributionIndex < 0)
                return;

            double sourceY = y;
            if (XStackedDataSets.this.stacked100Percent) {
                if (!isTopNormalizedDataSet()) {
                    if (sourceY >= 100.0) {
                        zeroOtherNormalizedContributors(stackedPoints, x);
                        return;
                    }
                    sourceY = toNormalizedSourceY(stackedPoints, contributionIndex, sourceY);
                }
            } else {
                sourceY -= getEditBaseline(stackedPoints, contributionIndex);
            }
            sourceDataSet.setData(getSourceIndex(stackedPoints, contributionIndex), x, sourceY);
        }

        @Override
        public int size() {
            return towerEntriesByDataSet[sourceDataSetIndex].size();
        }

        /// Maps a stacked-view point back to the underlying source dataset and source index.
        @Override
        public void unmap(DataSetPoint point) {
            int logicalIndex = point.getIndex();
            if (logicalIndex < 0 || logicalIndex >= size()) {
                point.index = -1;
            } else {
                XStackedDataSets.StackedPoint contribution = getContribution(logicalIndex);
                point.index = (contribution == null) ? -1 : contribution.sourceIndex;
            }
            point.dataSet = sourceDataSet;
        }

        /// Maps a stacked-view edit point back to source coordinates.
        ///
        /// The rewritten `editablePoint.y` value is the raw source contribution that would
        /// reproduce the requested stacked position while the other datasets in the same tower stay
        /// unchanged. For non-diverging stacks, negative source contributions are clamped to `0`
        /// after translation.
        @Override
        public void unmap(DataSetPoint point, DoublePoint editablePoint) {
            int logicalIndex = point.getIndex();
            if (logicalIndex < 0 || logicalIndex >= size()) {
                point.index = -1;
            } else {
                XStackedDataSets.StackedTower tower = getTower(logicalIndex);
                ArrayList<XStackedDataSets.StackedPoint> stackedPoints = tower.contributions;
                int contributionIndex = getContributionIndex(tower);
                if (contributionIndex < 0) {
                    point.index = -1;
                } else {
                    point.index = getSourceIndex(stackedPoints, contributionIndex);
                    double sourceY = editablePoint.y;
                    if (!XStackedDataSets.this.stacked100Percent) {
                        sourceY -= getEditBaseline(stackedPoints, contributionIndex);
                    } else if (!isTopNormalizedDataSet()) {
                        if (sourceY <= 0.0) {
                            sourceY = -getRegularBaseline(stackedPoints, contributionIndex);
                        } else if (sourceY < 100.0) {
                            sourceY = toNormalizedSourceY(stackedPoints, contributionIndex, sourceY);
                        }
                    }
                    if (!XStackedDataSets.this.diverging && sourceY < 0.0)
                        sourceY = 0.0;
                    editablePoint.y = sourceY;
                }
            }

            point.dataSet = sourceDataSet;
        }

        @Override
        protected void updateDataCount() {
        }
    }

    /// Shared metadata for one dataset contribution stored inside a [StackedTower].
    ///
    /// The base record keeps the owning dataset index, the source index within that dataset, the
    /// logical index of the derived stacked view, and the currently visible stacked y coordinate.
    static abstract class StackedPoint {
        /// Owning dataset position in the surrounding stack order.
        int dataSetIndex;
        /// Source index inside the owning dataset.
        int sourceIndex;
        /// Logical index of this contribution inside the derived stacked view.
        int logicalIndex;
        /// Visible stacked y value exposed by the derived view, or `NaN` when undefined.
        double visibleY;

        StackedPoint() {
        }
    }

    /// Diverging-stack contribution that tracks separate positive and negative running totals.
    ///
    /// `visibleY` from [StackedPoint] mirrors whichever side this contribution currently occupies.
    static final class StackedPointD extends XStackedDataSets.StackedPoint {
        /// Positive running total up to and including this contribution.
        double positiveY;
        /// Negative running total up to and including this contribution.
        double negativeY;
        /// Whether the current source contribution belongs to the negative branch.
        boolean negativeBranch;

        StackedPointD() {
        }
    }

    /// Non-diverging contribution that tracks one cumulative stacked y value.
    static final class StackedPointS extends XStackedDataSets.StackedPoint {
        /// Cumulative stacked y after applying this contribution.
        double cumulativeY;

        StackedPointS() {
        }
    }

    /// All dataset contributions currently aligned to the same x value.
    ///
    /// Points stay ordered by source dataset index so the surrounding helper can binary-search the
    /// contribution for any family member without rebuilding the tower.
    static final class StackedTower {
        /// Per-dataset contributions for this x value, ordered by source dataset index.
        ArrayList<StackedPoint> contributions;
        /// Position of this tower inside `sortedTowerEntries`.
        int towerIndex;

        StackedTower() {
        }
    }

    private static final Comparator<DoubleTreeMap.Entry<XStackedDataSets.StackedTower>> towerEntryIndexComparator = (
            DoubleTreeMap.Entry<XStackedDataSets.StackedTower> leftEntry,
            DoubleTreeMap.Entry<XStackedDataSets.StackedTower> rightEntry) ->
            Integer.compare(leftEntry.getValue().towerIndex, rightEntry.getValue().towerIndex);
    /* synthetic */ static final boolean assertionsDisabled = !XStackedDataSets.class.desiredAssertionStatus();

    final DataSet[] sourceDataSets;
    final boolean stacked100Percent;
    final boolean diverging;
    DoubleTreeMap<XStackedDataSets.StackedTower> towersByX;
    int towerCount;
    int edgeScanLimit;
    DoubleTreeMap.Entry<XStackedDataSets.StackedTower>[] sortedTowerEntries;
    ArrayList<DoubleTreeMap.Entry<XStackedDataSets.StackedTower>>[] towerEntriesByDataSet;
    VirtualDataSet[] stackedDataSets;
    private final ChartRenderer renderer;
    private transient int dataSourceChangeDepth;
    private transient boolean fullRefreshPending;
    private transient IntIntervalSet[] changedIntervalsByDataSet;

    private transient IntIntervalSet[] labelChangedIntervalsByDataSet;

    private transient DoubleTreeMap<Boolean> pendingAddedXValues;

    private transient int[] pendingAppendRanges;

    /// Creates x-aligned stacked views for `dataSets`.
    ///
    /// `stacked100Percent` switches derived y-values and baselines to percentages.
    /// `diverging` separates positive and negative accumulations, but only when
    /// `stacked100Percent` is `false`.
    ///
    /// @param sourceDataSets source datasets in stacking order
    /// @param renderer owning renderer whose chart batching is reused for stacked-view
    ///               notifications
    /// @param stacked100Percent whether derived values should be normalized to `0..100`
    /// @param diverging whether positive and negative contributions should accumulate
    ///               independently
    public XStackedDataSets(DataSet[] sourceDataSets, ChartRenderer renderer, boolean stacked100Percent, boolean diverging) {
        this.sourceDataSets = sourceDataSets;
        this.renderer = renderer;
        this.stacked100Percent = stacked100Percent;
        this.diverging = diverging && !stacked100Percent;
        towersByX = new DoubleTreeMap<>(-1);
        towerEntriesByDataSet = new ArrayList[sourceDataSets.length];
        rebuildTowerModel();
        initializeStackedDataSets();
    }

    /// Rebuilds the shared x-keyed tower model from the current source datasets.
    ///
    /// Each source dataset contributes at most one point per x tower. When one dataset supplies
    /// duplicate x values, the later source point replaces the earlier contribution before the
    /// cumulative totals for that tower are finalized.
    private void rebuildTowerModel() {
        towersByX.clear();
        for (int dataSetIndex = 0; dataSetIndex < sourceDataSets.length; dataSetIndex++) {
            DataSet sourceDataSet = sourceDataSets[dataSetIndex];
            double undefinedY = getUndefinedSourceY(sourceDataSet);
            DataPoints sourcePoints = sourceDataSet.getData();
            if (sourcePoints == null)
                continue;

            try {
                for (int pointIndex = 0, pointCount = sourcePoints.size(); pointIndex < pointCount; pointIndex++) {
                    double x = sourcePoints.getX(pointIndex);
                    double y = sourcePoints.getY(pointIndex);
                    int sourceIndex = sourcePoints.getIndex(pointIndex);
                    XStackedDataSets.StackedTower tower = getOrCreateTower(x);
                    ArrayList<XStackedDataSets.StackedPoint> contributions = tower.contributions;
                    if (!diverging)
                        addRegularContribution(contributions, dataSetIndex, sourceIndex, y, undefinedY);
                    else
                        addDivergingContribution(contributions, dataSetIndex, sourceIndex, y, undefinedY);
                }
            } finally {
                sourcePoints.dispose();
            }
        }
        rebuildSortedTowerEntries();
        rebuildTowerEntriesByDataSet();
    }

    private static double getUndefinedSourceY(DataSet sourceDataSet) {
        Double undefinedY = sourceDataSet.getUndefValue();
        return (undefinedY == null) ? Double.NaN : undefinedY.doubleValue();
    }

    private static boolean isDefinedSourceY(double y, double undefinedY) {
        return y != undefinedY && !Double.isNaN(y);
    }

    private XStackedDataSets.StackedTower getOrCreateTower(double x) {
        XStackedDataSets.StackedTower tower = towersByX.get(x);
        if (tower == null) {
            tower = createTower();
            towersByX.put(x, tower);
        }
        return tower;
    }

    private void addRegularContribution(
            ArrayList<XStackedDataSets.StackedPoint> contributions,
            int dataSetIndex,
            int sourceIndex,
            double y,
            double undefinedY
    ) {
        double cumulativeBaseline = 0.0;
        if (!contributions.isEmpty()) {
            XStackedDataSets.StackedPointS previousContribution =
                    (XStackedDataSets.StackedPointS) contributions.getLast();
            if (previousContribution.dataSetIndex == dataSetIndex) {
                contributions.removeLast();
                previousContribution = contributions.isEmpty()
                        ? null
                        : (XStackedDataSets.StackedPointS) contributions.getLast();
            }
            if (previousContribution != null)
                cumulativeBaseline = previousContribution.cumulativeY;
        }

        XStackedDataSets.StackedPointS contribution = new XStackedDataSets.StackedPointS();
        contribution.dataSetIndex = dataSetIndex;
        contribution.sourceIndex = sourceIndex;
        contribution.logicalIndex = -1;
        if (isDefinedSourceY(y, undefinedY)) {
            contribution.cumulativeY = cumulativeBaseline + y;
            contribution.visibleY = contribution.cumulativeY;
        } else {
            contribution.cumulativeY = cumulativeBaseline;
            contribution.visibleY = Double.NaN;
        }
        contributions.add(contribution);
    }

    private void addDivergingContribution(
            ArrayList<XStackedDataSets.StackedPoint> contributions,
            int dataSetIndex,
            int sourceIndex,
            double y,
            double undefinedY
    ) {
        double positiveBaseline = 0.0;
        double negativeBaseline = 0.0;
        if (!contributions.isEmpty()) {
            XStackedDataSets.StackedPointD previousContribution =
                    (XStackedDataSets.StackedPointD) contributions.getLast();
            if (previousContribution.dataSetIndex == dataSetIndex) {
                contributions.removeLast();
                previousContribution = contributions.isEmpty()
                        ? null
                        : (XStackedDataSets.StackedPointD) contributions.getLast();
            }
            if (previousContribution != null) {
                positiveBaseline = previousContribution.positiveY;
                negativeBaseline = previousContribution.negativeY;
            }
        }

        XStackedDataSets.StackedPointD contribution = new XStackedDataSets.StackedPointD();
        contribution.dataSetIndex = dataSetIndex;
        contribution.sourceIndex = sourceIndex;
        contribution.logicalIndex = -1;
        if (isDefinedSourceY(y, undefinedY)) {
            if (y >= 0.0) {
                contribution.positiveY = positiveBaseline + y;
                contribution.negativeY = negativeBaseline;
                contribution.visibleY = contribution.positiveY;
            } else {
                contribution.negativeBranch = true;
                contribution.positiveY = positiveBaseline;
                contribution.negativeY = negativeBaseline + y;
                contribution.visibleY = contribution.negativeY;
            }
        } else {
            contribution.positiveY = positiveBaseline;
            contribution.negativeY = negativeBaseline;
            contribution.visibleY = Double.NaN;
        }
        contributions.add(contribution);
    }

    @SuppressWarnings("unchecked")
    private void rebuildSortedTowerEntries() {
        towerCount = towersByX.size();
        edgeScanLimit = (int) Math.sqrt(towerCount);
        sortedTowerEntries = new DoubleTreeMap.Entry[towerCount];
        int towerIndex = towerCount;
        for (DoubleTreeMap.Entry<XStackedDataSets.StackedTower> towerEntry : towersByX.entrySet()) {
            towerIndex--;
            sortedTowerEntries[towerIndex] = towerEntry;
            towerEntry.getValue().towerIndex = towerIndex;
        }
    }

    private void rebuildTowerEntriesByDataSet() {
        for (int dataSetIndex = 0; dataSetIndex < sourceDataSets.length; dataSetIndex++) {
            towerEntriesByDataSet[dataSetIndex] = new ArrayList<>();
        }
        for (int towerIndex = 0; towerIndex < towerCount; towerIndex++) {
            DoubleTreeMap.Entry<XStackedDataSets.StackedTower> towerEntry = sortedTowerEntries[towerIndex];
            ArrayList<XStackedDataSets.StackedPoint> contributions = towerEntry.getValue().contributions;
            for (XStackedDataSets.StackedPoint contribution : contributions) {
                ArrayList<DoubleTreeMap.Entry<XStackedDataSets.StackedTower>> dataSetTowerEntries =
                        towerEntriesByDataSet[contribution.dataSetIndex];
                contribution.logicalIndex = dataSetTowerEntries.size();
                dataSetTowerEntries.add(towerEntry);
            }
        }
    }

    /// Consumes one contents event emitted by a source dataset participating in the stack family.
    ///
    /// Label-only changes can usually be replayed against the existing tower model. Value changes
    /// can stay incremental only when the affected x coordinates still resolve to existing towers.
    /// Structural additions can also stay incremental, but only while they append to the tail of
    /// the source dataset or reuse already-known x values.
    private void dataSetContentsChanged(int dataSetIndex, DataSetContentsEvent event) {
        int firstIndex = event.getFirstIdx();
        int lastIndex = event.getLastIdx();
        switch (event.getType()) {
            case DataSetContentsEvent.BATCH_BEGIN -> dataSourceChangesStarting();
            case DataSetContentsEvent.BATCH_END -> dataSourceChangesEnding();
            case DataSetContentsEvent.AFTER_DATA_CHANGED -> {
                if (firstIndex <= lastIndex) {
                    if (dataSourceChangeDepth <= 0) {
                        renderer.getChart().startRendererChanges();
                        try {
                            refreshChangedRange(dataSetIndex, firstIndex, lastIndex);
                        } finally {
                            renderer.getChart().endRendererChanges();
                        }
                    } else if (!fullRefreshPending) {
                        queueChangedRange(dataSetIndex, firstIndex, lastIndex);
                    }
                }
            }
            case DataSetContentsEvent.DATA_ADDED -> {
                if (firstIndex > lastIndex)
                    return;
                if (dataSourceChangeDepth > 0) {
                    if (!fullRefreshPending)
                        queueAppendRange(dataSetIndex, firstIndex, lastIndex);
                    return;
                }
                if (!canAppendIncrementally(dataSetIndex, firstIndex, lastIndex)) {
                    rebuildAllStacks();
                    return;
                }
                renderer.getChart().startRendererChanges();
                try {
                    appendDataRange(dataSetIndex, firstIndex, lastIndex);
                } finally {
                    renderer.getChart().endRendererChanges();
                }
            }
            case DataSetContentsEvent.DATA_LABEL_CHANGED -> {
                if (firstIndex <= lastIndex) {
                    if (dataSourceChangeDepth <= 0) {
                        renderer.getChart().startRendererChanges();
                        try {
                            fireLabelChanges(dataSetIndex, firstIndex, lastIndex);
                        } finally {
                            renderer.getChart().endRendererChanges();
                        }
                    } else if (!fullRefreshPending) {
                        queueLabelChanges(dataSetIndex, firstIndex, lastIndex);
                    }
                }
            }
            case DataSetContentsEvent.DATA_CHANGED, DataSetContentsEvent.FULL_UPDATE -> {
                if (dataSourceChangeDepth <= 0)
                    rebuildAllStacks();
                else
                    fullRefreshPending = true;
            }
            default -> {
            }
        }
    }

    private void queueChangedRange(int dataSetIndex, int firstIndex, int lastIndex) {
        if (changedIntervalsByDataSet == null)
            changedIntervalsByDataSet = new IntIntervalSet[sourceDataSets.length];
        if (changedIntervalsByDataSet[dataSetIndex] == null)
            changedIntervalsByDataSet[dataSetIndex] = new IntIntervalSet();
        changedIntervalsByDataSet[dataSetIndex].add(firstIndex, lastIndex);
    }

    private void queueLabelChanges(int dataSetIndex, int firstIndex, int lastIndex) {
        if (labelChangedIntervalsByDataSet == null)
            labelChangedIntervalsByDataSet = new IntIntervalSet[sourceDataSets.length];
        if (labelChangedIntervalsByDataSet[dataSetIndex] == null)
            labelChangedIntervalsByDataSet[dataSetIndex] = new IntIntervalSet();
        labelChangedIntervalsByDataSet[dataSetIndex].add(firstIndex, lastIndex);
    }

    private void queueAppendRange(int dataSetIndex, int firstIndex, int lastIndex) {
        DataSet sourceDataSet = sourceDataSets[dataSetIndex];
        if (pendingAddedXValues == null)
            pendingAddedXValues = new DoubleTreeMap<>(1);
        for (int sourceIndex = firstIndex; sourceIndex <= lastIndex; sourceIndex++) {
            pendingAddedXValues.put(sourceDataSet.getXData(sourceIndex), Boolean.TRUE);
        }
        if (pendingAppendRanges == null)
            pendingAppendRanges = new int[2 * sourceDataSets.length];

        int offset = 2 * dataSetIndex;
        if (pendingAppendRanges[offset] >= pendingAppendRanges[offset + 1]) {
            pendingAppendRanges[offset] = firstIndex;
            pendingAppendRanges[offset + 1] = lastIndex + 1;
            return;
        }
        if (firstIndex != pendingAppendRanges[offset + 1]) {
            fullRefreshPending = true;
            return;
        }
        pendingAppendRanges[offset + 1] = lastIndex + 1;
    }

    private boolean canAppendIncrementally(int dataSetIndex, int firstIndex, int lastIndex) {
        DataSet sourceDataSet = sourceDataSets[dataSetIndex];
        double maximumKnownX = (towerCount <= 0)
                ? Double.NEGATIVE_INFINITY
                : sortedTowerEntries[towerCount - 1].getKey();
        if (sourceDataSet.isXValuesSorted()) {
            for (int sourceIndex = firstIndex; sourceIndex <= lastIndex; sourceIndex++) {
                double x = sourceDataSet.getXData(sourceIndex);
                if (x > maximumKnownX)
                    return true;
                if (!towersByX.containsKey(x))
                    return false;
            }
            return true;
        }
        for (int sourceIndex = firstIndex; sourceIndex <= lastIndex; sourceIndex++) {
            double x = sourceDataSet.getXData(sourceIndex);
            if (x <= maximumKnownX && !towersByX.containsKey(x))
                return false;
        }
        return true;
    }

    /// Returns whether all queued additions can be replayed without rebuilding the tower model.
    ///
    /// Because `pendingAddedXValues` iterates in descending x order, the check must inspect every
    /// queued x value rather than stopping at the first newly-appended tail point.
    private boolean canReplayQueuedAddsIncrementally() {
        if (pendingAddedXValues == null)
            return true;

        double maximumKnownX = (towerCount <= 0)
                ? Double.NEGATIVE_INFINITY
                : sortedTowerEntries[towerCount - 1].getKey();
        for (DoubleTreeMap.Entry<Boolean> entry : pendingAddedXValues.entrySet()) {
            double x = entry.getKey();
            if (x <= maximumKnownX && !towersByX.containsKey(x))
                return false;
        }
        return true;
    }

    private void replayPendingIncrementalChanges() {
        startDerivedDataSetBatches();
        try {
            for (int dataSetIndex = 0; dataSetIndex < sourceDataSets.length; dataSetIndex++) {
                if (changedIntervalsByDataSet != null && changedIntervalsByDataSet[dataSetIndex] != null) {
                    Iterator<IntInterval> iterator = changedIntervalsByDataSet[dataSetIndex].intervalIterator();
                    while (iterator.hasNext()) {
                        IntInterval interval = iterator.next();
                        refreshChangedRange(dataSetIndex, interval.getFirst(), interval.getLast());
                    }
                }

                if (labelChangedIntervalsByDataSet != null && labelChangedIntervalsByDataSet[dataSetIndex] != null) {
                    Iterator<IntInterval> iterator = labelChangedIntervalsByDataSet[dataSetIndex].intervalIterator();
                    while (iterator.hasNext()) {
                        IntInterval interval = iterator.next();
                        fireLabelChanges(dataSetIndex, interval.getFirst(), interval.getLast());
                    }
                }

                if (pendingAppendRanges != null) {
                    int offset = 2 * dataSetIndex;
                    if (pendingAppendRanges[offset] < pendingAppendRanges[offset + 1]) {
                        appendDataRange(dataSetIndex, pendingAppendRanges[offset], pendingAppendRanges[offset + 1] - 1);
                    }
                }
            }
        } finally {
            endDerivedDataSetBatches();
        }
    }

    private void clearPendingBatchState() {
        fullRefreshPending = false;
        changedIntervalsByDataSet = null;
        labelChangedIntervalsByDataSet = null;
        pendingAddedXValues = null;
        pendingAppendRanges = null;
    }

    private void refreshChangedRange(int dataSetIndex, int firstIndex, int lastIndex) {
        DataSet sourceDataSet = sourceDataSets[dataSetIndex];
        int sourceIndex = firstIndex;
        while (true) {
            if (sourceIndex > lastIndex)
                return;
            double x = sourceDataSet.getXData(sourceIndex);
            XStackedDataSets.StackedTower tower = towersByX.get(x);
            if (tower == null)
                break;
            refreshTower(tower, true);
            sourceIndex++;
        }
        throw new RuntimeException(
                "inconsistency among x values of " + sourceDataSet);
    }

    private static boolean isDefinedSourceY(DataSet sourceDataSet, double y) {
        Double undefValue = sourceDataSet.getUndefValue();
        return (undefValue == null || y != undefValue.doubleValue()) && !Double.isNaN(y);
    }

    /// Forwards one point-scoped contents event to every derived dataset entry in `tower`.
    private void fireTowerContentsEvent(XStackedDataSets.StackedTower tower, int eventType) {
        for (XStackedDataSets.StackedPoint contribution : tower.contributions) {
            AbstractDataSet stackedDataSet = stackedDataSets[contribution.dataSetIndex];
            stackedDataSet.fireDataSetContentsEvent(new DataSetContentsEvent(
                    stackedDataSet,
                    eventType,
                    contribution.logicalIndex,
                    contribution.logicalIndex));
        }
    }

    /// Invalidates cached limits for all stacked points represented by `tower`.
    private void invalidateTowerLimits(XStackedDataSets.StackedTower tower) {
        for (XStackedDataSets.StackedPoint contribution : tower.contributions) {
            stackedDataSets[contribution.dataSetIndex]
                    .invalidateLimits(contribution.logicalIndex, contribution.logicalIndex);
        }
    }

    /// Recomputes a non-diverging tower as one cumulative y sequence.
    private void refreshRegularTower(XStackedDataSets.StackedTower tower) {
        double cumulativeY = 0.0;
        for (XStackedDataSets.StackedPoint contribution : tower.contributions) {
            XStackedDataSets.StackedPointS stackedPoint = (XStackedDataSets.StackedPointS) contribution;
            DataSet sourceDataSet = sourceDataSets[contribution.dataSetIndex];
            double y = sourceDataSet.getYData(stackedPoint.sourceIndex);
            if (isDefinedSourceY(sourceDataSet, y)) {
                cumulativeY += y;
                stackedPoint.cumulativeY = cumulativeY;
                stackedPoint.visibleY = cumulativeY;
            } else {
                stackedPoint.cumulativeY = cumulativeY;
                stackedPoint.visibleY = Double.NaN;
            }
        }
    }

    /// Recomputes a diverging tower with separate positive and negative running totals.
    private void refreshDivergingTower(XStackedDataSets.StackedTower tower) {
        double positiveSum = 0.0;
        double negativeSum = 0.0;
        for (XStackedDataSets.StackedPoint contribution : tower.contributions) {
            XStackedDataSets.StackedPointD stackedPoint = (XStackedDataSets.StackedPointD) contribution;
            DataSet sourceDataSet = sourceDataSets[contribution.dataSetIndex];
            double y = sourceDataSet.getYData(contribution.sourceIndex);
            if (isDefinedSourceY(sourceDataSet, y)) {
                if (y >= 0.0) {
                    positiveSum += y;
                    stackedPoint.negativeBranch = false;
                    stackedPoint.positiveY = positiveSum;
                    stackedPoint.negativeY = negativeSum;
                    stackedPoint.visibleY = positiveSum;
                } else {
                    negativeSum += y;
                    stackedPoint.negativeBranch = true;
                    stackedPoint.positiveY = positiveSum;
                    stackedPoint.negativeY = negativeSum;
                    stackedPoint.visibleY = negativeSum;
                }
            } else {
                stackedPoint.negativeBranch = false;
                stackedPoint.positiveY = positiveSum;
                stackedPoint.negativeY = negativeSum;
                stackedPoint.visibleY = Double.NaN;
            }
        }
    }

    /// Recomputes one x-aligned tower from the current source datasets.
    ///
    /// When `fireIncrementalEvents` is `true`, each affected stacked point receives paired
    /// `BEFORE_DATA_CHANGED` and `AFTER_DATA_CHANGED` events. Limits are invalidated only for
    /// non-`100%` stacks because normalized views pin their y range to `0..100`.
    private void refreshTower(XStackedDataSets.StackedTower tower, boolean fireIncrementalEvents) {
        if (fireIncrementalEvents)
            fireTowerContentsEvent(tower, DataSetContentsEvent.BEFORE_DATA_CHANGED);

        if (!diverging)
            refreshRegularTower(tower);
        else
            refreshDivergingTower(tower);

        if (fireIncrementalEvents) {
            if (!stacked100Percent)
                invalidateTowerLimits(tower);
            fireTowerContentsEvent(tower, DataSetContentsEvent.AFTER_DATA_CHANGED);
        }
    }

    /// Binary-searches one tower for the contribution owned by `dataSetIndex`.
    private int findContributionIndex(XStackedDataSets.StackedTower tower, int dataSetIndex) {
        ArrayList<XStackedDataSets.StackedPoint> stackedPoints = tower.contributions;
        int upperBound = stackedPoints.size();
        int lowerBound = 0;
        while (upperBound > lowerBound) {
            int midIndex = lowerBound + upperBound >> 1;
            XStackedDataSets.StackedPoint contribution = stackedPoints.get(midIndex);
            int comparison = contribution.dataSetIndex - dataSetIndex;
            if (comparison < 0) {
                lowerBound = midIndex + 1;
            } else {
                if (comparison == 0)
                    return midIndex;
                upperBound = midIndex;
            }
        }
        return -1 - lowerBound;
    }

    /// Installs one derived stacked dataset per source dataset.
    private void initializeStackedDataSets() {
        int dataSetCount = sourceDataSets.length;
        VirtualDataSet[] stackedViews = new VirtualDataSet[dataSetCount];
        for (int dataSetIndex = 0; dataSetIndex < dataSetCount; dataSetIndex++) {
            stackedViews[dataSetIndex] = new XStackedDataSets.StackedDataSet(dataSetIndex);
        }
        stackedDataSets = stackedViews;
    }

    /// Forwards label-only source changes to every stacked point that shares the affected x value.
    private void fireLabelChanges(int dataSetIndex, int firstIndex, int lastIndex) {
        DataSet sourceDataSet = sourceDataSets[dataSetIndex];
        int sourceIndex = firstIndex;
        while (true) {
            if (sourceIndex > lastIndex)
                return;
            double x = sourceDataSet.getXData(sourceIndex);
            XStackedDataSets.StackedTower tower = towersByX.get(x);
            if (tower == null)
                break;
            for (XStackedDataSets.StackedPoint contribution : tower.contributions) {
                DataSet stackedDataSet = stackedDataSets[contribution.dataSetIndex];
                ((AbstractDataSet) stackedDataSet).fireDataSetContentsEvent(new DataSetContentsEvent(
                        stackedDataSet,
                        DataSetContentsEvent.DATA_LABEL_CHANGED,
                        contribution.logicalIndex,
                        contribution.logicalIndex));
            }
            sourceIndex++;
        }
        throw new RuntimeException(
                "inconsistency among x values of " + sourceDataSet);
    }

    /// Starts one batch on every derived stacked dataset.
    private void startDerivedDataSetBatches() {
        for (VirtualDataSet stackedDataSet : stackedDataSets) {
            stackedDataSet.startBatch();
        }
    }

    private XStackedDataSets.StackedTower createTower() {
        XStackedDataSets.StackedTower tower = new XStackedDataSets.StackedTower();
        tower.contributions = new ArrayList<>(1);
        tower.towerIndex = -1;
        return tower;
    }

    private XStackedDataSets.StackedPoint createContributionPoint(int dataSetIndex, int sourceIndex) {
        XStackedDataSets.StackedPoint contribution = diverging
                ? new XStackedDataSets.StackedPointD()
                : new XStackedDataSets.StackedPointS();
        contribution.dataSetIndex = dataSetIndex;
        contribution.sourceIndex = sourceIndex;
        contribution.logicalIndex = -1;
        return contribution;
    }

    private int findTowerEntryInsertionIndex(
            ArrayList<DoubleTreeMap.Entry<XStackedDataSets.StackedTower>> dataSetTowerEntries,
            int firstInsertedTowerIndex
    ) {
        int lowerBound = 0;
        int upperBound = dataSetTowerEntries.size();
        while (lowerBound < upperBound) {
            int midIndex = (lowerBound + upperBound) >>> 1;
            if (dataSetTowerEntries.get(midIndex).getValue().towerIndex >= firstInsertedTowerIndex)
                upperBound = midIndex;
            else
                lowerBound = midIndex + 1;
        }
        return lowerBound;
    }

    private void updateContributionLogicalIndices(
            ArrayList<DoubleTreeMap.Entry<XStackedDataSets.StackedTower>> dataSetTowerEntries,
            int dataSetIndex,
            int firstLogicalIndex
    ) {
        for (int logicalIndex = firstLogicalIndex; logicalIndex < dataSetTowerEntries.size(); logicalIndex++) {
            XStackedDataSets.StackedTower tower = dataSetTowerEntries.get(logicalIndex).getValue();
            int contributionIndex = findContributionIndex(tower, dataSetIndex);
            if (!XStackedDataSets.assertionsDisabled && contributionIndex < 0)
                throw new AssertionError();
            tower.contributions.get(contributionIndex).logicalIndex = logicalIndex;
        }
    }

    private int mergeInsertedExistingTowers(
            ArrayList<DoubleTreeMap.Entry<XStackedDataSets.StackedTower>> dataSetTowerEntries,
            ArrayList<DoubleTreeMap.Entry<XStackedDataSets.StackedTower>> insertedExistingTowerEntries,
            int dataSetIndex
    ) {
        @SuppressWarnings("unchecked")
        DoubleTreeMap.Entry<XStackedDataSets.StackedTower>[] sortedInsertedEntries =
                insertedExistingTowerEntries.toArray(new DoubleTreeMap.Entry[0]);
        Arrays.sort(sortedInsertedEntries, XStackedDataSets.towerEntryIndexComparator);

        int insertionStart = findTowerEntryInsertionIndex(
                dataSetTowerEntries,
                sortedInsertedEntries[0].getValue().towerIndex);
        int originalSize = dataSetTowerEntries.size();
        for (int i = 0; i < sortedInsertedEntries.length; i++) {
            dataSetTowerEntries.add(null);
        }

        int readIndex = originalSize - 1;
        int writeIndex = dataSetTowerEntries.size() - 1;
        int insertedIndex = sortedInsertedEntries.length - 1;
        while (readIndex >= insertionStart && insertedIndex >= 0) {
            DoubleTreeMap.Entry<XStackedDataSets.StackedTower> existingEntry = dataSetTowerEntries.get(readIndex);
            DoubleTreeMap.Entry<XStackedDataSets.StackedTower> insertedEntry = sortedInsertedEntries[insertedIndex];
            if (existingEntry.getValue().towerIndex > insertedEntry.getValue().towerIndex) {
                dataSetTowerEntries.set(writeIndex--, existingEntry);
                readIndex--;
            } else {
                if (!XStackedDataSets.assertionsDisabled
                        && existingEntry.getValue().towerIndex >= insertedEntry.getValue().towerIndex) {
                    throw new AssertionError("mismatch about x values assertions");
                }
                dataSetTowerEntries.set(writeIndex--, insertedEntry);
                insertedIndex--;
            }
        }
        while (insertedIndex >= 0) {
            dataSetTowerEntries.set(writeIndex--, sortedInsertedEntries[insertedIndex--]);
        }
        updateContributionLogicalIndices(dataSetTowerEntries, dataSetIndex, insertionStart);
        return insertionStart;
    }

    private void ensureSortedTowerCapacity(int requiredCapacity) {
        if (requiredCapacity > sortedTowerEntries.length) {
            sortedTowerEntries = Arrays.copyOf(
                    sortedTowerEntries,
                    Math.max(requiredCapacity, 2 * Math.max(1, sortedTowerEntries.length)));
        }
    }

    private void appendNewTailTowers(
            ArrayList<DoubleTreeMap.Entry<XStackedDataSets.StackedTower>> dataSetTowerEntries,
            int dataSetIndex,
            int newTowerCount
    ) {
        if (newTowerCount <= 0)
            return;

        int previousTowerCount = towerCount;
        ensureSortedTowerCapacity(previousTowerCount + newTowerCount);
        int writeIndex = previousTowerCount + newTowerCount;
        Iterator<DoubleTreeMap.Entry<XStackedDataSets.StackedTower>> iterator = towersByX.entrySet().iterator();
        while (iterator.hasNext()) {
            DoubleTreeMap.Entry<XStackedDataSets.StackedTower> towerEntry = iterator.next();
            writeIndex--;
            sortedTowerEntries[writeIndex] = towerEntry;
            towerEntry.getValue().towerIndex = writeIndex;
            if (writeIndex == previousTowerCount)
                break;
        }

        towerCount = previousTowerCount + newTowerCount;
        if (!XStackedDataSets.assertionsDisabled && towerCount != towersByX.size())
            throw new AssertionError();

        for (int towerIndex = previousTowerCount; towerIndex < towerCount; towerIndex++) {
            DoubleTreeMap.Entry<XStackedDataSets.StackedTower> towerEntry = sortedTowerEntries[towerIndex];
            ArrayList<XStackedDataSets.StackedPoint> stackedPoints = towerEntry.getValue().contributions;
            if (!XStackedDataSets.assertionsDisabled && stackedPoints.size() != 1)
                throw new AssertionError();
            XStackedDataSets.StackedPoint contribution = stackedPoints.get(0);
            if (!XStackedDataSets.assertionsDisabled && contribution.dataSetIndex != dataSetIndex)
                throw new AssertionError();
            contribution.logicalIndex = dataSetTowerEntries.size();
            dataSetTowerEntries.add(towerEntry);
        }
    }

    /// Incrementally incorporates appended source points into the shared tower model.
    ///
    /// Existing towers are refreshed in place. Newly created towers and newly inserted dataset
    /// contributions are staged until their logical indices are known, which avoids emitting
    /// point-scoped change events against unresolved stacked-view positions.
    private void appendDataRange(int dataSetIndex, int firstIndex, int lastIndex) {
        DataSet sourceDataSet = sourceDataSets[dataSetIndex];
        DataPoints sourcePoints = sourceDataSet.getDataBetween(firstIndex, lastIndex);
        if (sourcePoints == null)
            return;

        try {
            int pointCount = sourcePoints.size();
            if (pointCount <= 0)
                return;

            ArrayList<DoubleTreeMap.Entry<XStackedDataSets.StackedTower>> dataSetTowerEntries =
                    towerEntriesByDataSet[dataSetIndex];
            ArrayList<DoubleTreeMap.Entry<XStackedDataSets.StackedTower>> insertedExistingTowerEntries =
                    new ArrayList<>();
            int newTowerCount = 0;
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                double x = sourcePoints.getX(pointIndex);
                DoubleTreeMap.Entry<XStackedDataSets.StackedTower> towerEntry = towersByX.getEntry(x);
                XStackedDataSets.StackedTower tower;
                ArrayList<XStackedDataSets.StackedPoint> stackedPoints;
                if (towerEntry != null) {
                    tower = towerEntry.getValue();
                    stackedPoints = tower.contributions;
                } else {
                    tower = createTower();
                    towersByX.put(x, tower);
                    towerEntry = towersByX.getEntry(x);
                    stackedPoints = tower.contributions;
                    newTowerCount++;
                }

                int contributionIndex = findContributionIndex(tower, dataSetIndex);
                boolean fireIncrementalEvents;
                int sourceIndex = sourcePoints.getIndex(pointIndex);
                if (contributionIndex >= 0) {
                    XStackedDataSets.StackedPoint contribution = stackedPoints.get(contributionIndex);
                    if (!XStackedDataSets.assertionsDisabled && contribution.dataSetIndex != dataSetIndex)
                        throw new AssertionError();
                    contribution.sourceIndex = sourceIndex;
                    fireIncrementalEvents = contribution.logicalIndex >= 0;
                } else {
                    XStackedDataSets.StackedPoint contribution = createContributionPoint(dataSetIndex, sourceIndex);
                    stackedPoints.add(-1 - contributionIndex, contribution);
                    fireIncrementalEvents = false;
                    if (tower.towerIndex >= 0)
                        insertedExistingTowerEntries.add(towerEntry);
                }
                refreshTower(tower, fireIncrementalEvents);
            }

            int firstChangedLogicalIndex = dataSetTowerEntries.size();
            if (!insertedExistingTowerEntries.isEmpty()) {
                firstChangedLogicalIndex = mergeInsertedExistingTowers(
                        dataSetTowerEntries,
                        insertedExistingTowerEntries,
                        dataSetIndex);
            }

            int sizeBeforeTailAppend = dataSetTowerEntries.size();
            appendNewTailTowers(dataSetTowerEntries, dataSetIndex, newTowerCount);
            if (!XStackedDataSets.assertionsDisabled
                    && dataSetTowerEntries.size() != sizeBeforeTailAppend + newTowerCount) {
                throw new AssertionError();
            }

            invalidateAllDerivedLimits();
            AbstractDataSet stackedDataSet = stackedDataSets[dataSetIndex];
            if (firstChangedLogicalIndex < sizeBeforeTailAppend) {
                stackedDataSet.fireDataChangedEvent(
                        firstChangedLogicalIndex,
                        sizeBeforeTailAppend - 1,
                        DataSetContentsEvent.DATA_CHANGED);
            }
            if (newTowerCount > 0) {
                stackedDataSet.fireDataAddedEvent(
                        sizeBeforeTailAppend,
                        sizeBeforeTailAppend + newTowerCount - 1);
            }
        } finally {
            sourcePoints.dispose();
        }
    }

    /// Ends one batch on every derived stacked dataset.
    private void endDerivedDataSetBatches() {
        for (VirtualDataSet stackedDataSet : stackedDataSets) {
            stackedDataSet.endBatch();
        }
    }

    /// Ends one batched source-update scope.
    ///
    /// When the outermost scope closes, queued incremental changes are replayed against the
    /// current tower model when possible. If x alignment can no longer be updated safely,
    /// the helper rebuilds the whole stacked family.
    public void dataSourceChangesEnding() {
        if (dataSourceChangeDepth <= 0)
            return;

        dataSourceChangeDepth--;
        if (dataSourceChangeDepth > 0)
            return;

        try {
            if (fullRefreshPending || !canReplayQueuedAddsIncrementally()) {
                rebuildAllStacks();
            } else {
                replayPendingIncrementalChanges();
            }
        } finally {
            clearPendingBatchState();
        }
    }

    /// Starts a batched source-update scope.
    ///
    /// Nested scopes are allowed. Expensive rebuild work is deferred until the matching
    /// outermost [#dataSourceChangesEnding()] call.
    public void dataSourceChangesStarting() {
        dataSourceChangeDepth++;
    }

    /// Disposes the stacked datasets owned by this helper.
    ///
    /// After disposal no further source-change notifications should be forwarded to this
    /// instance.
    public void dispose() {
        disposeStackedDataSets();
    }

    private void disposeStackedDataSets() {
        for (VirtualDataSet stackedDataSet : stackedDataSets) {
            stackedDataSet.dispose();
        }
    }

    /// Invalidates cached limits for every derived stacked view.
    private void invalidateAllDerivedLimits() {
        for (AbstractDataSet stackedDataSet : stackedDataSets) {
            stackedDataSet.invalidateLimits();
        }
    }

    /// Rebuilds the shared tower model from all source datasets and broadcasts a full refresh.
    private void rebuildAllStacks() {
        rebuildTowerModel();
        invalidateAllDerivedLimits();
        renderer.getChart().startRendererChanges();
        try {
            for (AbstractDataSet stackedDataSet : stackedDataSets) {
                stackedDataSet.fireDataSetContentsEvent(new DataSetContentsEvent(stackedDataSet));
            }
        } finally {
            renderer.getChart().endRendererChanges();
        }
    }

    /// Returns the derived stacked datasets in the same order as the constructor input.
    ///
    /// The returned array is live and is reused for the lifetime of this helper.
    ///
    /// @return stacked virtual datasets, one per source dataset
    public VirtualDataSet[] getStackedDataSets() {
        return stackedDataSets;
    }
}
