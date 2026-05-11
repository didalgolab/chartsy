package one.chartsy.charting.data;

import java.util.Iterator;

import one.chartsy.charting.event.DataSourceListener;

/// Represents the ordered collection of datasets consumed as a single renderer input.
///
/// A `DataSource` groups related `DataSet` instances, such as the value series that make up one
/// chart layer or indicator. The zero-based indices exposed here are the same indices reported in
/// `DataSourceEvent`s, so callers can use them to keep renderer state, styles, and legends aligned
/// with the current dataset order.
public interface DataSource {

    /// Appends a dataset at the end of the source.
    void add(DataSet dataSet);

    /// Inserts a dataset at the requested index.
    ///
    /// Datasets currently at or after `index` move to higher indices.
    void add(int index, DataSet dataSet);

    /// Registers a listener for structural changes in this source.
    ///
    /// Listeners that also implement `DataSourceListener2` may receive batch-boundary callbacks
    /// from batching implementations such as `AbstractDataSource`.
    void addDataSourceListener(DataSourceListener listener);

    /// Tests whether the source currently exposes the given dataset.
    boolean contains(DataSet dataSet);

    /// Returns the dataset currently stored at `index`.
    DataSet get(int index);

    /// Returns the current index of `dataSet`, or `-1` when it is not present.
    int indexOf(DataSet dataSet);

    /// Returns an iterator that traverses datasets in source order.
    ///
    /// Callers that need a stable copy should prefer `toArray()`.
    Iterator<DataSet> iterator();

    /// Unregisters a listener previously added through `addDataSourceListener(DataSourceListener)`.
    void removeDataSourceListener(DataSourceListener listener);

    /// Replaces the dataset currently stored at `index`.
    void set(int index, DataSet dataSet);

    /// Replaces the full dataset list in source order.
    ///
    /// Implementations may report this as a wholesale remove-and-add sequence instead of a
    /// fine-grained diff.
    void setAll(DataSet[] dataSets);

    /// Returns the number of datasets currently exposed by this source.
    int size();

    /// Returns the current datasets as an ordered array snapshot.
    DataSet[] toArray();
}
