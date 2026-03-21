package one.chartsy.charting.data;

/// Mutable general-purpose [DataSource] backed directly by [AbstractDataSource].
///
/// This is the concrete data source used when callers want to manage a renderer's datasets
/// imperatively rather than deriving them from another domain object. It exposes the ordinary
/// `DataSource` mutation methods from [AbstractDataSource] and also surfaces the backing
/// [DataSetArray]'s logical-position API for callers that need stable slots independent of the
/// current dense list index.
public class DefaultDataSource extends AbstractDataSource {

    /// Creates an empty mutable data source.
    public DefaultDataSource() {
    }

    /// Creates a mutable data source initialized with `dataSets` in encounter order.
    ///
    /// The supplied references are installed through [#setAll(DataSet[])], so dataset ownership and
    /// structural event dispatch behave the same as for later wholesale replacements.
    public DefaultDataSource(DataSet[] dataSets) {
        setAll(dataSets);
    }

    /// Creates datasets from raw series arrays and installs them immediately.
    ///
    /// This is a convenience wrapper around [DefaultDataSet#create(double[][], int, String[],
    /// String[])] followed by [#setAll(DataSet[])]. `xSeriesIndex` selects the row that should be
    /// treated as the shared x-domain, or `-1` when every row should become an implicit-x y-series.
    public DefaultDataSource(
            double[][] values,
            int xSeriesIndex,
            String[] names,
            String[] dataLabels
    ) {
        setAll(DefaultDataSet.create(values, xSeriesIndex, names, dataLabels));
    }

    @Override
    public void add(DataSet dataSet) {
        dataSets.addDataSet(dataSet);
    }

    @Override
    public void add(int index, DataSet dataSet) {
        dataSets.addDataSet(index, dataSet);
    }

    /// Returns the dataset currently assigned to logical `position`.
    ///
    /// Positions come from the backing [DataSetArray], not from the current dense list index.
    /// Unassigned positions return `null`.
    public DataSet getDataSetAtPosition(int position) {
        return dataSets.getDataSetAtPosition(position);
    }

    /// Replaces the dataset at `index`, or removes that slot when `dataSet` is `null`.
    @Override
    public void set(int index, DataSet dataSet) {
        if (dataSet == null)
            dataSets.removeDataSet(index);
        else
            dataSets.setDataSet(index, dataSet);
    }

    /// Replaces the entire dataset list with `dataSets`.
    @Override
    public void setAll(DataSet[] dataSets) {
        initDataSets(dataSets);
    }

    /// Replaces or removes the dataset stored at logical `position`.
    ///
    /// Passing `null` removes any dataset currently assigned to that position. When no dataset is
    /// assigned there, the call is a no-op.
    public void setDataSetAtPosition(int position, DataSet dataSet) {
        this.dataSets.setDataSetAtPosition(position, dataSet);
    }
}
