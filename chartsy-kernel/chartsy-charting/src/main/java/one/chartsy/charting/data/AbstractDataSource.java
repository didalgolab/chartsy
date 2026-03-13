package one.chartsy.charting.data;

import java.io.Serializable;
import java.util.Iterator;

import javax.swing.event.EventListenerList;

import one.chartsy.charting.event.DataSourceEvent;
import one.chartsy.charting.event.DataSourceListener;
import one.chartsy.charting.event.DataSourceListener2;
import one.chartsy.charting.util.Batchable;

/// Base `DataSource` implementation backed by [DataSetArray] and `EventListenerList`.
///
/// This class centralizes the storage and listener behavior shared by the charting module's data
/// sources:
/// - datasets are stored in one internal [DataSetArray]
/// - structural mutations are translated into [DataSourceEvent] snapshots
/// - datasets added to the source receive a back-reference through [DataSetProperty]
/// - listeners that also implement [DataSourceListener2] observe balanced batch-depth changes
///
/// The public mutation methods [#add(DataSet)], [#add(int, DataSet)], [#set(int, DataSet)], and
/// [#setAll(DataSet[])] intentionally do nothing here. Mutable subclasses such as
/// [DefaultDataSource] override the subset they want to expose, while read-only sources can inherit
/// the no-op behavior.
public abstract class AbstractDataSource implements DataSource, Batchable, Serializable {
    private final EventListenerList listenerList;
    final DataSetArray dataSets;

    private transient int batchDepth;

    /// Creates an empty data source with listener dispatch and dataset ownership hooks enabled.
    protected AbstractDataSource() {
        listenerList = new EventListenerList();
        dataSets = createDataSetArray();
    }

    private DataSetArray createDataSetArray() {
        return new DataSetArray() {
            @Override
            protected void dataSetsAdded(int firstIndex, int lastIndex, DataSet[] oldDataSets) {
                for (int index = firstIndex; index <= lastIndex; index++)
                    DataSetProperty.setDataSource(get(index), AbstractDataSource.this);
                if (listenerList.getListenerCount(DataSourceListener.class) > 0) {
                    fireDataSourceEvent(new DataSourceEvent(
                            AbstractDataSource.this,
                            DataSourceEvent.DATASET_ADDED,
                            firstIndex,
                            lastIndex,
                            oldDataSets));
                }
            }

            @Override
            protected void dataSetsRemoved(int firstIndex, int lastIndex, DataSet[] oldDataSets) {
                for (int index = firstIndex; index <= lastIndex; index++)
                    clearDataSource(oldDataSets[index]);
                if (listenerList.getListenerCount(DataSourceListener.class) > 0) {
                    fireDataSourceEvent(new DataSourceEvent(
                            AbstractDataSource.this,
                            DataSourceEvent.DATASET_REMOVED,
                            firstIndex,
                            lastIndex,
                            oldDataSets));
                }
            }
        };
    }

    private static void clearDataSource(DataSet dataSet) {
        if (dataSet != null)
            dataSet.putProperty(DataSetProperty.DATA_SOURCE_PROPERTY, null, false);
    }

    private void updateBatchState(boolean starting) {
        if (starting)
            batchDepth++;
        else
            batchDepth--;
        notifyBatchListeners(starting);
    }

    private void notifyBatchListeners(boolean starting) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == DataSourceListener.class
                    && listeners[i + 1] instanceof DataSourceListener2 batchListener) {
                if (starting)
                    batchListener.startDataSourceChanges();
                else
                    batchListener.endDataSourceChanges();
            }
        }
    }

    /// Does nothing in the base class.
    ///
    /// Mutable subclasses override this to append datasets through the shared [#dataSets] storage.
    @Override
    public void add(DataSet dataSet) {
    }

    /// Does nothing in the base class.
    ///
    /// Mutable subclasses override this to insert datasets through the shared [#dataSets] storage.
    @Override
    public void add(int index, DataSet dataSet) {
    }

    /// Registers `listener` for structural data-source changes.
    ///
    /// If `listener` also implements [DataSourceListener2] and a batch is already open, this method
    /// immediately replays `startDataSourceChanges()` once per currently open batch level so the new
    /// listener can balance future [#endBatch()] calls.
    @Override
    public synchronized void addDataSourceListener(DataSourceListener listener) {
        if (listener instanceof DataSourceListener2 batchListener) {
            for (int remaining = batchDepth; remaining > 0; remaining--)
                batchListener.startDataSourceChanges();
        }
        listenerList.add(DataSourceListener.class, listener);
    }

    /// Returns whether `dataSet` is currently present in this source.
    @Override
    public boolean contains(DataSet dataSet) {
        return dataSets.getDataSetIndex(dataSet) >= 0;
    }

    /// Ends one logical batch of data-source changes.
    ///
    /// Each call is balanced against one earlier [#startBatch()] call and produces one matching
    /// [DataSourceListener2#endDataSourceChanges()] callback for currently registered batch-aware
    /// listeners.
    @Override
    public void endBatch() {
        updateBatchState(false);
    }

    /// Delivers `event` to all currently registered `DataSourceListener`s.
    ///
    /// The event is dispatched synchronously on the calling thread.
    public void fireDataSourceEvent(DataSourceEvent event) {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == DataSourceListener.class)
                ((DataSourceListener) listeners[i + 1]).dataSourceChanged(event);
        }
    }

    /// Returns the dataset currently stored at `index`.
    ///
    /// This delegates to the backing [DataSetArray], so indexes on or beyond the current size
    /// return `null`.
    @Override
    public DataSet get(int index) {
        return dataSets.getDataSet(index);
    }

    /// Returns the first dataset whose [DataSet#getName()] equals `name`.
    ///
    /// @return the first matching dataset, or `null` when no dataset has that name
    public DataSet getDataSetByName(String name) {
        for (int index = 0, count = size(); index < count; index++) {
            DataSet dataSet = get(index);
            if (dataSet != null) {
                String dataSetName = dataSet.getName();
                if (dataSetName != null && dataSetName.equals(name))
                    return dataSet;
            }
        }
        return null;
    }

    /// Returns the live mutable dataset storage used by this source.
    ///
    /// Subclasses typically use this for bulk replacement or specialized position-aware updates.
    protected DataSetList getDataSetList() {
        return dataSets;
    }

    /// Returns the current index of `dataSet`, or `-1` when absent.
    @Override
    public int indexOf(DataSet dataSet) {
        return dataSets.getDataSetIndex(dataSet);
    }

    /// Replaces the backing datasets without exposing a public mutation API.
    ///
    /// This helper underpins subclasses that populate their contents from a separate domain object
    /// rather than through the `DataSource` mutator methods.
    protected void initDataSets(DataSet[] dataSets) {
        this.dataSets.setDataSets(dataSets);
    }

    /// Returns a read-only iterator backed by the current live dataset storage.
    @Override
    public Iterator<DataSet> iterator() {
        return dataSets.getDataSetIterator();
    }

    /// Unregisters `listener`.
    ///
    /// If the listener also implements [DataSourceListener2] and batches are currently open, this
    /// method replays the outstanding `endDataSourceChanges()` callbacks so the removed listener can
    /// drop any deferred state it was holding for those batches.
    @Override
    public synchronized void removeDataSourceListener(DataSourceListener listener) {
        listenerList.remove(DataSourceListener.class, listener);
        if (listener instanceof DataSourceListener2 batchListener) {
            for (int remaining = batchDepth; remaining > 0; remaining--)
                batchListener.endDataSourceChanges();
        }
    }

    /// Does nothing in the base class.
    ///
    /// Mutable subclasses override this to replace or remove one dataset in the shared storage.
    @Override
    public void set(int index, DataSet dataSet) {
    }

    /// Does nothing in the base class.
    ///
    /// Mutable subclasses override this to expose wholesale replacement of the shared storage.
    @Override
    public void setAll(DataSet[] dataSets) {
    }

    /// Returns the current number of datasets.
    @Override
    public int size() {
        return dataSets.getDataSetCount();
    }

    /// Starts one logical batch of data-source changes.
    ///
    /// Each call increments the current batch depth and produces one matching
    /// [DataSourceListener2#startDataSourceChanges()] callback for currently registered
    /// batch-aware listeners.
    @Override
    public void startBatch() {
        updateBatchState(true);
    }

    /// Returns the current datasets as an ordered array snapshot.
    @Override
    public DataSet[] toArray() {
        return dataSets.getDataSets();
    }
}
