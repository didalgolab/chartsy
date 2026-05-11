package one.chartsy.charting.data;

/// Centralizes the well-known entries stored in a `DataSet` property bag.
///
/// The charting layer uses these helpers for lightweight metadata that sits beside the raw point
/// arrays: category-width hints for datasets with implicit x positions, optional back-references to
/// the enclosing `DataSource`, and legacy x/y time-series markers. All setters write through
/// `DataSet.putProperty(..., false)`, so this metadata can be updated without emitting
/// `DataSetPropertyEvent`s.
public final class DataSetProperty {
    static final String X_TIME_SERIES_PROPERTY = "XTimeSeries";
    static final String Y_TIME_SERIES_PROPERTY = "YTimeSeries";
    static final String CATEGORY_PROPERTY = "_DS_CATEGORY";
    static final String DATA_SOURCE_PROPERTY = "_DATASOURCE_";

    /// Returns the optional category-width hint stored on `dataSet`.
    ///
    /// Renderers use this value when the dataset represents equally spaced categories rather than
    /// explicit x coordinates.
    public static Double getCategory(DataSet dataSet) {
        if (dataSet == null)
            throw new IllegalArgumentException();
        return (Double) dataSet.getProperty(CATEGORY_PROPERTY);
    }

    /// Returns the stored data-source back-reference for `dataSet`, or `null` when absent.
    public static DataSource getDataSource(DataSet dataSet) {
        if (dataSet == null)
            throw new IllegalArgumentException();
        return (DataSource) dataSet.getProperty(DATA_SOURCE_PROPERTY);
    }

    /// Resolves the current index of `dataSet` inside its recorded data source.
    ///
    /// Returns `-1` when the dataset has no associated source or when that source no longer
    /// contains it.
    public static int getIndex(DataSet dataSet) {
        DataSource dataSource = getDataSource(dataSet);
        return (dataSource == null) ? -1 : dataSource.indexOf(dataSet);
    }

    /// Returns whether the x-values are marked as a time series.
    public static boolean isXTimeSeries(DataSet dataSet) {
        return dataSet != null && dataSet.getProperty(X_TIME_SERIES_PROPERTY) == DataSetProperty.class;
    }

    /// Returns whether the y-values are marked as a time series.
    public static boolean isYTimeSeries(DataSet dataSet) {
        return dataSet != null && dataSet.getProperty(Y_TIME_SERIES_PROPERTY) == DataSetProperty.class;
    }

    /// Stores or clears the category-width hint on `dataSet`.
    public static void setCategory(DataSet dataSet, Double category) {
        if (dataSet == null)
            throw new IllegalArgumentException();
        dataSet.putProperty(CATEGORY_PROPERTY, category, false);
    }

    /// Records a non-null data source for `dataSet` when no source has been stored yet.
    ///
    /// This helper is intentionally one-way: it does not clear the property and it does not
    /// overwrite an existing association.
    public static void setDataSource(DataSet dataSet, DataSource dataSource) {
        if (dataSource != null) {
            synchronized (dataSet) {
                if (getDataSource(dataSet) == null) {
                    dataSet.putProperty(DATA_SOURCE_PROPERTY, dataSource, false);
                }
            }
        }
    }

    /// Sets or clears the x-time-series marker on `dataSet`.
    public static void setXTimeSeries(DataSet dataSet, boolean enabled) {
        if (dataSet == null)
            throw new IllegalArgumentException();
        dataSet.putProperty(X_TIME_SERIES_PROPERTY, enabled ? DataSetProperty.class : null, false);
    }

    /// Sets or clears the y-time-series marker on `dataSet`.
    public static void setYTimeSeries(DataSet dataSet, boolean enabled) {
        if (dataSet == null)
            throw new IllegalArgumentException();
        dataSet.putProperty(Y_TIME_SERIES_PROPERTY, enabled ? DataSetProperty.class : null, false);
    }

    private DataSetProperty() {
    }
}
