package one.chartsy.charting.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import one.chartsy.charting.util.collections.FloatArrayList;

/// Package-private ordered storage behind [AbstractDataSource] and [CombinedDataSet].
///
/// Besides the dataset order exposed through [DataSetList], this class optionally tracks a
/// non-negative logical position for each entry. Position-aware callers use
/// [#getDataSetAtPosition(int)] and [#setDataSetAtPosition(int, DataSet)] to address entries by
/// that logical position instead of by current list index. Entries inserted through the ordinary
/// list methods receive the sentinel position `-1` and are skipped by position lookups until a
/// non-negative position is assigned.
///
/// The protected [#dataSetsAdded(int, int, DataSet[])] and
/// [#dataSetsRemoved(int, int, DataSet[])] hooks are the reason this type exists instead of a raw
/// `List<DataSet>`. [AbstractDataSource] and [CombinedDataSet] override them to update back
/// references, attach listeners, and emit structural events from the supplied pre-change snapshots.
class DataSetArray implements DataSetList, Serializable {
    private static final DataSet[] EMPTY_DATA_SETS = new DataSet[0];
    private static final int UNPOSITIONED = -1;

    private final List<DataSet> dataSets;
    private final FloatArrayList positions;
    private int maxDataSetCount;

    /// Creates empty storage with no positioned datasets and no capacity limit.
    public DataSetArray() {
        dataSets = new ArrayList<>();
        positions = new FloatArrayList();
        maxDataSetCount = Integer.MAX_VALUE;
    }

    private int searchPositionIndex(int position) {
        int low = 0;
        int high = dataSets.size();
        while (low < high) {
            int searchIndex = (low + high) >>> 1;
            int comparableIndex = resolveComparableIndex(low, high, searchIndex);
            if (comparableIndex < 0)
                return comparableIndex;

            float currentPosition = positions.get(comparableIndex);
            if (currentPosition > position)
                high = comparableIndex;
            else if (currentPosition < position)
                low = comparableIndex + 1;
            else
                return comparableIndex;
        }
        return -1 - low;
    }

    private int resolveComparableIndex(int low, int high, int searchIndex) {
        if (positions.get(searchIndex) >= 0.0f)
            return searchIndex;

        for (int offset = 1; ; offset++) {
            if (searchIndex + offset >= high)
                return -1 - searchIndex;
            if (positions.get(searchIndex + offset) >= 0.0f)
                return searchIndex + offset;
            if (searchIndex - offset < low)
                return -1 - searchIndex;
            if (positions.get(searchIndex - offset) >= 0.0f)
                return searchIndex - offset;
        }
    }

    private void replaceOrRemoveDataSet(int index, int position, DataSet dataSet) {
        if (dataSet == null) {
            if (index < dataSets.size())
                removeDataSet(index);
            return;
        }

        if (index >= dataSets.size()) {
            addDataSet(dataSet);
            return;
        }

        DataSet[] oldDataSets = getDataSets();
        dataSets.set(index, dataSet);
        positions.set(index, position);
        dataSetsRemoved(index, index, oldDataSets);
        dataSetsAdded(index, index, snapshotWithoutIndex(oldDataSets, index));
    }

    private static DataSet[] snapshotWithoutIndex(DataSet[] dataSetsSnapshot, int index) {
        DataSet[] snapshot = new DataSet[dataSetsSnapshot.length - 1];
        if (index > 0)
            System.arraycopy(dataSetsSnapshot, 0, snapshot, 0, index);
        if (index < snapshot.length)
            System.arraycopy(dataSetsSnapshot, index + 1, snapshot, index, snapshot.length - index);
        return snapshot;
    }

    /// Appends `dataSet` if [#getMaxDataSetCount()] has not been reached.
    ///
    /// The appended entry receives no logical position and is therefore invisible to
    /// [#getDataSetAtPosition(int)] until a non-negative position is assigned later.
    @Override
    public void addDataSet(DataSet dataSet) {
        int index = dataSets.size();
        if (index == maxDataSetCount)
            return;

        DataSet[] oldDataSets = getDataSets();
        dataSets.add(dataSet);
        positions.add(UNPOSITIONED);
        dataSetsAdded(index, index, oldDataSets);
    }

    /// Inserts `dataSet` at `index`, clamping indexes beyond the current size to an append.
    ///
    /// The inserted entry receives no logical position.
    ///
    /// @throws IndexOutOfBoundsException if `index` is negative
    @Override
    public void addDataSet(int index, DataSet dataSet) {
        insertDataSet(index, UNPOSITIONED, dataSet);
    }

    private void insertDataSet(int index, int position, DataSet dataSet) {
        int insertionIndex = Math.min(index, dataSets.size());
        if (dataSets.size() == maxDataSetCount)
            return;

        DataSet[] oldDataSets = getDataSets();
        dataSets.add(insertionIndex, dataSet);
        positions.add(insertionIndex, position);
        dataSetsAdded(insertionIndex, insertionIndex, oldDataSets);
    }

    /// Hook invoked after datasets are added to the live storage.
    ///
    /// The index range is inclusive and refers to the current list state after the add. `oldDataSets`
    /// is the snapshot from immediately before the logical add step. During replacement operations
    /// that snapshot already excludes the dataset that was just logically removed.
    protected void dataSetsAdded(int firstIndex, int lastIndex, DataSet[] oldDataSets) {
    }

    /// Hook invoked after datasets are removed from the live storage.
    ///
    /// The index range is inclusive and refers to the positions that were occupied immediately
    /// before removal. `oldDataSets` is the full snapshot from before the removal step.
    protected void dataSetsRemoved(int firstIndex, int lastIndex, DataSet[] oldDataSets) {
    }

    /// Returns the dataset at `index`.
    ///
    /// Unlike a strict list accessor, indexes on or beyond the current size return `null`.
    ///
    /// @throws IndexOutOfBoundsException if `index` is negative
    @Override
    public DataSet getDataSet(int index) {
        return (index >= dataSets.size()) ? null : dataSets.get(index);
    }

    /// Returns the dataset whose logical position equals `position`.
    ///
    /// Only non-negative positions participate in this lookup. Entries added through the ordinary
    /// list APIs keep the sentinel position `-1` and are skipped.
    ///
    /// @return the matching dataset, or `null` when no dataset has that logical position
    /// @throws IndexOutOfBoundsException if `position` is negative
    public DataSet getDataSetAtPosition(int position) {
        if (position < 0)
            throw new IndexOutOfBoundsException("negative position: " + position);

        int index = searchPositionIndex(position);
        if (index < 0)
            return null;
        return dataSets.get(index);
    }

    /// Returns the current number of datasets.
    @Override
    public int getDataSetCount() {
        return dataSets.size();
    }

    /// Returns the current list index of `dataSet`, or `-1` when it is not present.
    @Override
    public final int getDataSetIndex(DataSet dataSet) {
        return dataSets.indexOf(dataSet);
    }

    /// Returns a read-only iterator backed by the current live list.
    public Iterator<DataSet> getDataSetIterator() {
        return Collections.unmodifiableList(dataSets).iterator();
    }

    /// Returns the current datasets as an array snapshot.
    @Override
    public DataSet[] getDataSets() {
        return dataSets.toArray(EMPTY_DATA_SETS);
    }

    /// Returns the configured maximum number of datasets this storage will hold.
    @Override
    public int getMaxDataSetCount() {
        return maxDataSetCount;
    }

    /// Removes `dataSet` if it is present.
    @Override
    public boolean removeDataSet(DataSet dataSet) {
        int index = getDataSetIndex(dataSet);
        if (index == -1)
            return false;
        removeDataSet(index);
        return true;
    }

    /// Removes the dataset at `index`.
    ///
    /// @throws IndexOutOfBoundsException if `index` is invalid for the current list
    @Override
    public void removeDataSet(int index) {
        DataSet[] oldDataSets = getDataSets();
        dataSets.remove(index);
        positions.remove(index);
        dataSetsRemoved(index, index, oldDataSets);
    }

    /// Replaces `currentDataSet` with `newDataSet` when the current entry exists.
    @Override
    public boolean replaceDataSet(DataSet currentDataSet, DataSet newDataSet) {
        int index = getDataSetIndex(currentDataSet);
        if (index == -1)
            return false;
        setDataSet(index, newDataSet);
        return true;
    }

    /// Replaces the dataset at `index`.
    ///
    /// Passing `null` removes the dataset at `index`. Indexes on or beyond the current size append
    /// `dataSet` when it is non-null and otherwise leave the storage unchanged.
    ///
    /// @throws IndexOutOfBoundsException if `index` is negative
    @Override
    public void setDataSet(int index, DataSet dataSet) {
        replaceOrRemoveDataSet(index, UNPOSITIONED, dataSet);
    }

    /// Replaces or inserts the dataset assigned to `position`.
    ///
    /// When a dataset with that logical position already exists, it is replaced in place. When no
    /// such dataset exists, `dataSet` is inserted at the position-sorted insertion point. Passing
    /// `null` removes the dataset currently stored at `position`; if none exists, the call is a
    /// no-op.
    ///
    /// @throws IndexOutOfBoundsException if `position` is negative
    public void setDataSetAtPosition(int position, DataSet dataSet) {
        if (position < 0)
            throw new IndexOutOfBoundsException("negative position: " + position);

        int index = searchPositionIndex(position);
        if (index >= 0)
            replaceOrRemoveDataSet(index, position, dataSet);
        else if (dataSet != null)
            insertDataSet(-1 - index, position, dataSet);
    }

    /// Replaces the entire contents with `dataSets`.
    ///
    /// The replacement array is copied in encounter order and assigned logical positions
    /// `0..n-1`. Passing `null` clears the storage. When the incoming array is longer than
    /// [#getMaxDataSetCount()], only the prefix that fits is retained.
    @Override
    public void setDataSets(DataSet[] newDataSets) {
        DataSet[] oldDataSets = getDataSets();
        dataSets.clear();
        positions.clear();
        if (oldDataSets.length > 0)
            dataSetsRemoved(0, oldDataSets.length - 1, oldDataSets);

        if (newDataSets != null) {
            int retainedCount = Math.min(newDataSets.length, maxDataSetCount);
            if (retainedCount > 0) {
                DataSet[] clearedDataSets = getDataSets();
                for (int i = 0; i < retainedCount; i++) {
                    dataSets.add(newDataSets[i]);
                    positions.add(i);
                }
                dataSetsAdded(0, retainedCount - 1, clearedDataSets);
            }
        }
    }

    /// Sets the maximum number of datasets this storage may contain.
    ///
    /// If the new limit is smaller than the current size, datasets are removed from the end until
    /// the limit is satisfied.
    ///
    /// @throws IllegalArgumentException if `maxDataSetCount` is negative
    @Override
    public void setMaxDataSetCount(int maxDataSetCount) {
        if (maxDataSetCount < 0)
            throw new IllegalArgumentException("maxDataSetCount must be non-negative");

        while (maxDataSetCount < dataSets.size())
            removeDataSet(dataSets.size() - 1);
        this.maxDataSetCount = maxDataSetCount;
    }
}
