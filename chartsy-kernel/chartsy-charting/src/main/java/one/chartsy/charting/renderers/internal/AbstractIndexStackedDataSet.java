package one.chartsy.charting.renderers.internal;

import one.chartsy.charting.data.AbstractDataSet;
import one.chartsy.charting.data.DataSet;
import one.chartsy.charting.data.DataSetPoint;
import one.chartsy.charting.event.DataSetContentsEvent;
import one.chartsy.charting.event.DataSetPropertyEvent;
import one.chartsy.charting.util.DoubleArray;

/// Base class for stacked virtual datasets whose points remain aligned by logical index.
///
/// This class underpins stacked renderers that derive one visible series from several x-aligned
/// source datasets. It keeps a reusable cache of computed stacked y-values, tracks the dirty index
/// range that must be recomputed after child changes, and exposes the baseline underneath each
/// point through [StackedDataSet#getPreviousYData(int)].
///
/// Mapping is intentionally index-preserving: virtual and source indices are the same, so
/// [#map(DataSetPoint)] and [#unmap(DataSetPoint)] only switch the addressed dataset reference.
/// Subclasses decide which backing dataset acts as the external reference through
/// [#getRefDataSet()] and provide the actual stacking formula through
/// [#computeStackedData(DoubleArray, int, int)].
public abstract class AbstractIndexStackedDataSet extends VirtualDataSet implements StackedDataSet {
    AbstractIndexStackedDataSet previousStackedDataSet;
    private DoubleArray stackedData;
    private int dirtyFirstIndex;
    private int dirtyLastIndex;

    /// Creates a stacked dataset that may use `previousStackedDataSet` as its baseline source.
    protected AbstractIndexStackedDataSet(AbstractIndexStackedDataSet previousStackedDataSet) {
        stackedData = new DoubleArray();
        dirtyFirstIndex = Integer.MAX_VALUE;
        dirtyLastIndex = Integer.MIN_VALUE;
        this.previousStackedDataSet = previousStackedDataSet;
    }

    private void markDirtyRange(int firstIndex, int lastIndex, boolean clearCache) {
        if (firstIndex < dirtyFirstIndex)
            dirtyFirstIndex = firstIndex;
        if (lastIndex > dirtyLastIndex)
            dirtyLastIndex = lastIndex;
        if (clearCache)
            stackedData.clear();
    }

    private void refreshStackedDataIfNeeded() {
        if (dirtyFirstIndex == Integer.MAX_VALUE)
            return;

        if (stackedData.size() != 0) {
            stackedData = computeStackedData(stackedData, dirtyFirstIndex, dirtyLastIndex);
        } else {
            int dataCount = super.size();
            if (dataCount > 0)
                stackedData = computeStackedData(stackedData, 0, dataCount - 1);
        }
        dirtyFirstIndex = Integer.MAX_VALUE;
        dirtyLastIndex = Integer.MIN_VALUE;
    }

    /// Recomputes cached stacked y-values for the inclusive logical range.
    ///
    /// Implementations may reuse and return `stackedData` to avoid reallocating the backing
    /// storage on every update.
    protected abstract DoubleArray computeStackedData(DoubleArray stackedData, int firstIndex, int lastIndex);

    /// Marks cached stacked values dirty after child data changes and forwards the translated event.
    ///
    /// `FULL_UPDATE` clears the cached stacked values entirely and re-expands the forwarded range to
    /// the current dataset size. Range-limited events outside the current visible size are ignored.
    @Override
    protected void dataSetContentsChanged(DataSetContentsEvent event) {
        super.dataSetContentsChanged(event);
        int dataCount = super.size();
        if (event.getType() != DataSetContentsEvent.FULL_UPDATE
                && (event.getFirstIdx() >= dataCount || event.getLastIdx() >= dataCount))
            return;

        int lastIndex = event.getLastIdx();
        switch (event.getType()) {
            case DataSetContentsEvent.AFTER_DATA_CHANGED,
                    DataSetContentsEvent.DATA_CHANGED,
                    DataSetContentsEvent.DATA_ADDED -> markDirtyRange(event.getFirstIdx(), lastIndex, false);
            case DataSetContentsEvent.FULL_UPDATE -> {
                lastIndex = dataCount - 1;
                markDirtyRange(event.getFirstIdx(), lastIndex, true);
            }
            default -> {
            }
        }
        super.fireDataSetContentsEvent(new DataSetContentsEvent(this, event.getType(), event.getFirstIdx(), lastIndex));
    }

    /// Forwards property changes only when they originate from [#getRefDataSet()].
    ///
    /// Other child datasets influence the derived y-values but do not define this dataset's public
    /// identity, labels, or direct editable target.
    @Override
    protected void dataSetPropertyChanged(DataSetPropertyEvent event) {
        if (event.getDataSet() == getRefDataSet())
            super.fireDataSetPropertyEvent(event);
    }

    /// Invalidates the full cached stacked range after the child dataset list changes.
    @Override
    protected void dataSetsChanged() {
        super.dataSetsChanged();
        int dataCount = super.size();
        if (dataCount > 0)
            markDirtyRange(0, dataCount - 1, true);
    }

    /// Returns the minimum x-distance reported by the first child dataset.
    @Override
    public double getMinimumXDifference() {
        if (super.getDataSetCount() <= 0)
            return -2.0;

        DataSet dataSet = super.getDataSet(0);
        return (dataSet instanceof AbstractDataSet abstractDataSet)
                ? abstractDataSet.getMinimumXDifference()
                : AbstractDataSet.computeMinimumXDifference(dataSet);
    }

    /// Returns the name of the reference dataset.
    @Override
    public String getName() {
        DataSet dataSet = getRefDataSet();
        return (dataSet == null) ? null : dataSet.getName();
    }

    /// Returns the nearest non-empty baseline value from the preceding stacked dataset chain.
    ///
    /// `Double.NaN` means this series starts the stack at the requested index.
    @Override
    public double getPreviousYData(int index) {
        AbstractIndexStackedDataSet previousStackedDataSet = this.previousStackedDataSet;
        while (previousStackedDataSet != null) {
            double y = previousStackedDataSet.getYData(index);
            Double undefValue = previousStackedDataSet.getUndefValue();
            if ((undefValue == null || y != undefValue.doubleValue()) && !Double.isNaN(y))
                return y;
            previousStackedDataSet = previousStackedDataSet.previousStackedDataSet;
        }
        return Double.NaN;
    }

    /// Returns the child dataset that defines this virtual dataset's external identity.
    ///
    /// This dataset supplies the name, property forwarding, and default `unmap(...)` target.
    protected abstract DataSet getRefDataSet();

    /// Returns `null`.
    ///
    /// Undefined stacked values are represented with `Double.NaN` rather than a dataset-specific
    /// sentinel.
    @Override
    public Double getUndefValue() {
        return null;
    }

    /// Returns the x-value from the first child dataset at the same logical index.
    @Override
    public double getXData(int index) {
        return super.getDataSet(0).getXData(index);
    }

    /// Returns the cached or freshly recomputed stacked y-value at `index`.
    @Override
    public double getYData(int index) {
        refreshStackedDataIfNeeded();
        return stackedData.get(index);
    }

    /// Returns `true`; stacked datasets support write-through editing via `VirtualDataSet`.
    @Override
    public boolean isEditable() {
        return true;
    }

    /// Returns whether the first child dataset reports sorted x-values.
    @Override
    public boolean isXValuesSorted() {
        return super.getDataSetCount() <= 0 || super.getDataSet(0).isXValuesSorted();
    }

    /// Maps a reference-dataset point into this stacked virtual dataset without changing its index.
    @Override
    public void map(DataSetPoint point) {
        point.dataSet = this;
    }

    /// Returns `true`; the index-preserving mapping keeps source order intact.
    @Override
    public boolean mapsMonotonically() {
        return true;
    }

    /// Maps a virtual point back to the reference dataset without changing its index.
    @Override
    public void unmap(DataSetPoint point) {
        point.dataSet = getRefDataSet();
    }
}
