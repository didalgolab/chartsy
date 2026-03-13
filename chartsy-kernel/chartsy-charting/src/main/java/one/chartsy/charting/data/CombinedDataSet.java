package one.chartsy.charting.data;

import java.io.Serializable;

import one.chartsy.charting.event.DataSetContentsEvent;
import one.chartsy.charting.event.DataSetListener;
import one.chartsy.charting.event.DataSetPropertyEvent;

/// Base class for datasets derived from an ordered set of child datasets.
///
/// `CombinedDataSet` owns a mutable [DataSetList] of source datasets and keeps listener
/// registration synchronized with that list. Structural changes to the child list trigger a full
/// update from this dataset after its cached [#size()] value and cached limits are refreshed.
///
/// The default [#computeDataCount()] implementation reports the smallest child `size()`. That
/// matches derived views whose points are defined only while every child contributes data. Virtual
/// datasets that expand, collapse, or otherwise remap child points override that method together
/// with the event hooks below.
///
/// Child batch nesting is mirrored rather than flattened. Every child `BATCH_BEGIN` calls
/// [#startBatch()], and every matching `BATCH_END` calls [#endBatch()]. Subclasses can therefore
/// rely on this dataset's batched state reflecting the aggregate child mutation depth.
public abstract class CombinedDataSet extends AbstractDataSet implements DataSetList {

    /// Serializable listener adapter that routes child dataset events back to the owning
    /// `CombinedDataSet`.
    ///
    /// Keeping the listener itself serializable allows composites to restore child observation
    /// after deserialization without exposing the forwarding hooks as separate top-level types.
    private final class SerDataSetListener implements DataSetListener, Serializable {
        @Override
        public void dataSetContentsChanged(DataSetContentsEvent event) {
            CombinedDataSet.this.dataSetContentsChanged(event);
        }

        @Override
        public void dataSetPropertyChanged(DataSetPropertyEvent event) {
            CombinedDataSet.this.dataSetPropertyChanged(event);
        }
    }

    private final DataSetList dataSets;
    private int dataCount;
    private DataSetListener dataSetListener;

    /// Creates an empty combined dataset with mutable child storage.
    protected CombinedDataSet() {
        dataSets = new DataSetArray() {
            @Override
            protected void dataSetsAdded(int firstIndex, int lastIndex, DataSet[] oldDataSets) {
                for (int index = firstIndex; index <= lastIndex; index++) {
                    DataSet dataSet = getDataSet(index);
                    if (dataSet instanceof AbstractDataSet abstractDataSet && abstractDataSet.isBatched())
                        startBatch();
                    dataSet.addDataSetListener(getDataSetListener());
                }
            }

            @Override
            protected void dataSetsRemoved(int firstIndex, int lastIndex, DataSet[] oldDataSets) {
                for (int index = firstIndex; index <= lastIndex; index++) {
                    DataSet dataSet = oldDataSets[index];
                    dataSet.removeDataSetListener(getDataSetListener());
                    if (dataSet instanceof AbstractDataSet abstractDataSet && abstractDataSet.isBatched())
                        endBatch();
                }
            }
        };
    }

    private DataSetListener getDataSetListener() {
        if (dataSetListener == null)
            dataSetListener = new SerDataSetListener();
        return dataSetListener;
    }

    private boolean hasSameDataSetSequence(DataSet[] dataSets) {
        int count = (dataSets == null) ? 0 : dataSets.length;
        if (count != this.dataSets.getDataSetCount())
            return false;

        for (int index = 0; index < count; index++) {
            if (this.dataSets.getDataSet(index) != dataSets[index])
                return false;
        }
        return true;
    }

    @Override
    public void addDataSet(DataSet dataSet) {
        dataSets.addDataSet(dataSet);
        dataSetsChanged();
        fireDataSetContentsEvent(new DataSetContentsEvent(this));
    }

    @Override
    public void addDataSet(int index, DataSet dataSet) {
        dataSets.addDataSet(index, dataSet);
        dataSetsChanged();
        fireDataSetContentsEvent(new DataSetContentsEvent(this));
    }

    /// Computes the derived number of visible data points.
    ///
    /// The default implementation returns the smallest `size()` among the current child datasets,
    /// or `0` when no children are installed.
    protected int computeDataCount() {
        int dataSetCount = getDataSetCount();
        if (dataSetCount == 0)
            return 0;

        int dataCount = getDataSet(0).size();
        for (int index = 1; index < dataSetCount; index++) {
            dataCount = Math.min(dataCount, getDataSet(index).size());
        }
        return dataCount;
    }

    /// Reacts to a contents event from one of the child datasets.
    ///
    /// The default implementation mirrors child batch boundaries and keeps this dataset's cached
    /// size and limits synchronized for every child event that can change visible values.
    /// Subclasses usually call `super` first and then translate or forward the event in the shape
    /// expected by their own consumers.
    protected void dataSetContentsChanged(DataSetContentsEvent event) {
        switch (event.getType()) {
            case DataSetContentsEvent.BATCH_BEGIN -> super.startBatch();
            case DataSetContentsEvent.BATCH_END -> super.endBatch();
            case DataSetContentsEvent.BEFORE_DATA_CHANGED, DataSetContentsEvent.DATA_LABEL_CHANGED -> {
            }
            default -> {
                updateDataCount();
                super.invalidateLimits();
            }
        }
    }

    /// Reacts to a property event from one of the child datasets.
    ///
    /// The default implementation ignores child property changes. Subclasses override this hook
    /// when they expose translated child properties or need to invalidate derived state.
    protected void dataSetPropertyChanged(DataSetPropertyEvent event) {
    }

    /// Refreshes derived caches after the child list itself changes.
    ///
    /// The default implementation recomputes [#size()] and invalidates cached limits. Subclasses
    /// may extend it to rebuild other derived state, but should still call `super`.
    protected void dataSetsChanged() {
        updateDataCount();
        super.invalidateLimits();
    }

    /// Removes all child datasets and detaches their listeners.
    ///
    /// This is equivalent to `setDataSets(null)`.
    public void dispose() {
        setDataSets(null);
    }

    /// Publishes a contents event after first synchronizing local caches.
    ///
    /// Cache synchronization is skipped for batch boundaries, `BEFORE_DATA_CHANGED`, and
    /// `DATA_LABEL_CHANGED` because those events do not represent a completed visible value change.
    ///
    /// @throws IllegalArgumentException if `event` is `null`
    @Override
    public void fireDataSetContentsEvent(DataSetContentsEvent event) {
        if (event == null)
            throw new IllegalArgumentException("null event");

        switch (event.getType()) {
            case DataSetContentsEvent.BATCH_BEGIN, DataSetContentsEvent.BATCH_END,
                    DataSetContentsEvent.BEFORE_DATA_CHANGED, DataSetContentsEvent.DATA_LABEL_CHANGED -> {
            }
            default -> {
                updateDataCount();
                super.invalidateLimits();
            }
        }
        super.fireDataSetContentsEvent(event);
    }

    @Override
    public DataSet getDataSet(int index) {
        return dataSets.getDataSet(index);
    }

    @Override
    public int getDataSetCount() {
        return dataSets.getDataSetCount();
    }

    @Override
    public int getDataSetIndex(DataSet dataSet) {
        return dataSets.getDataSetIndex(dataSet);
    }

    @Override
    public DataSet[] getDataSets() {
        return dataSets.getDataSets();
    }

    @Override
    public int getMaxDataSetCount() {
        return dataSets.getMaxDataSetCount();
    }

    /// Returns `false` by default.
    ///
    /// Derived datasets often synthesize or reorder points, so the base class does not assume that
    /// child ordering implies sorted x-values.
    @Override
    public boolean isXValuesSorted() {
        return false;
    }

    @Override
    public boolean removeDataSet(DataSet dataSet) {
        if (!dataSets.removeDataSet(dataSet))
            return false;

        dataSetsChanged();
        fireDataSetContentsEvent(new DataSetContentsEvent(this));
        return true;
    }

    @Override
    public void removeDataSet(int index) {
        dataSets.removeDataSet(index);
        dataSetsChanged();
        fireDataSetContentsEvent(new DataSetContentsEvent(this));
    }

    @Override
    public boolean replaceDataSet(DataSet currentDataSet, DataSet newDataSet) {
        if (!dataSets.replaceDataSet(currentDataSet, newDataSet))
            return false;

        dataSetsChanged();
        fireDataSetContentsEvent(new DataSetContentsEvent(this));
        return true;
    }

    @Override
    public void setDataSet(int index, DataSet dataSet) {
        if (dataSets.getDataSet(index) == dataSet)
            return;

        dataSets.setDataSet(index, dataSet);
        dataSetsChanged();
        fireDataSetContentsEvent(new DataSetContentsEvent(this));
    }

    /// Replaces the current child dataset sequence when it differs by length or reference identity.
    ///
    /// Passing `null` clears all children. Reinstalling the exact same dataset references in the
    /// same order is treated as a no-op so callers can avoid unnecessary listener churn.
    @Override
    public void setDataSets(DataSet[] dataSets) {
        if (hasSameDataSetSequence(dataSets))
            return;

        this.dataSets.setDataSets(dataSets);
        dataSetsChanged();
        fireDataSetContentsEvent(new DataSetContentsEvent(this));
    }

    /// Sets the maximum number of child datasets this composite may retain.
    ///
    /// Shrinking the limit can remove trailing child datasets. When that happens, listeners are
    /// detached and this dataset emits a full update after refreshing its derived caches.
    @Override
    public void setMaxDataSetCount(int maxDataSetCount) {
        int dataSetCount = dataSets.getDataSetCount();
        dataSets.setMaxDataSetCount(maxDataSetCount);
        if (dataSets.getDataSetCount() != dataSetCount) {
            dataSetsChanged();
            fireDataSetContentsEvent(new DataSetContentsEvent(this));
        }
    }

    /// Returns the cached derived data-point count.
    ///
    /// The cache is refreshed through [#updateDataCount()] whenever the child list or relevant
    /// child contents change.
    @Override
    public int size() {
        return dataCount;
    }

    /// Recomputes the cached value returned by [#size()].
    protected void updateDataCount() {
        dataCount = computeDataCount();
    }
}
