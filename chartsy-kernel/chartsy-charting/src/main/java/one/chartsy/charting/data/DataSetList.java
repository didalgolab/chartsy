package one.chartsy.charting.data;

/// Mutable collection facade for ordered `DataSet` members.
///
/// `CombinedDataSet`, data sources, and renderer adapters use this abstraction to manage child
/// datasets without exposing the concrete storage type. Indices refer to the current collection
/// order.
public interface DataSetList {

    /// Appends `dataSet` if capacity allows.
    void addDataSet(DataSet dataSet);

    /// Inserts `dataSet` at `index`, shifting later entries as needed.
    void addDataSet(int index, DataSet dataSet);

    /// Returns the dataset at `index`.
    DataSet getDataSet(int index);

    /// Returns the current number of datasets.
    int getDataSetCount();

    /// Returns the current index of `dataSet`, or `-1` when it is not present.
    int getDataSetIndex(DataSet dataSet);

    /// Returns the current datasets as an array snapshot.
    DataSet[] getDataSets();

    /// Returns the configured capacity limit.
    int getMaxDataSetCount();

    /// Removes `dataSet` if present.
    boolean removeDataSet(DataSet dataSet);

    /// Removes the dataset at `index`.
    void removeDataSet(int index);

    /// Replaces `currentDataSet` with `newDataSet` when the current entry exists.
    boolean replaceDataSet(DataSet currentDataSet, DataSet newDataSet);

    /// Replaces the dataset at `index`.
    void setDataSet(int index, DataSet dataSet);

    /// Replaces the entire contents with `dataSets`.
    void setDataSets(DataSet[] dataSets);

    /// Sets the maximum number of datasets this list may contain.
    void setMaxDataSetCount(int maxDataSetCount);
}
