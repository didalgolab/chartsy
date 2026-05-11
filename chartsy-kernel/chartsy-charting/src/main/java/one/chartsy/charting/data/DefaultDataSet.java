package one.chartsy.charting.data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

import one.chartsy.charting.event.DataSetContentsEvent;
import one.chartsy.charting.util.DoubleArray;

/// Default in-memory `DataSet` backed by mutable `double` arrays.
///
/// `DefaultDataSet` is the charting module's editable concrete series type. It can operate in two
/// x-domain modes:
///
/// - with explicit `xValues`, where each logical point stores both coordinates
/// - without explicit `xValues`, where the logical x coordinate is the point index `0..size()-1`
///
/// The implicit mode is treated as categorical data and publishes a category-width hint of `1.0`
/// through `DataSetProperty`. That hint is consumed by renderers and step definitions that need to
/// expand ranges or resolve labels around evenly spaced categories.
///
/// The static [#create(double[][], int, String[], String[])] factory builds aligned sibling
/// datasets from a matrix-like input. Those datasets stay linked so they continue to describe the
/// same logical x domain, which is why appending to one linked dataset is rejected.
public class DefaultDataSet extends AbstractDataSet {
    private static final String SHARED_X_PROPERTY = "__SHAREDX_PROP";
    private static final Double DEFAULT_CATEGORY_WIDTH = 1.0;
    private static final String[] EMPTY_LABELS = new String[0];

    /// Serializable marker stored on datasets created as part of one linked x-domain group.
    ///
    /// The property value itself is not interpreted elsewhere in the current codebase; it exists so
    /// the shared-x relationship can be tagged without depending on a non-serializable sentinel.
    private static final class SharedXMarker implements Serializable {
        private SharedXMarker() {
        }
    }

    private boolean xValuesSorted;
    protected DoubleArray xValues;
    protected DoubleArray yValues;
    protected ArrayList<String> labels;

    private DefaultDataSet[] sharedXDataSets;

    /// Creates a dataset with the given name, implicit x values, and an initial capacity of `32`.
    public DefaultDataSet(String name) {
        this(name, true);
    }

    /// Creates an empty dataset with an initial capacity of `32`.
    ///
    /// When `useXValues` is `false`, the dataset exposes implicit x coordinates equal to the point
    /// index.
    public DefaultDataSet(String name, boolean useXValues) {
        this(name, 32, useXValues);
    }

    /// Creates a dataset from y-values and implicit x coordinates.
    ///
    /// The supplied array is cloned before it becomes dataset storage.
    public DefaultDataSet(String name, double[] yValues) {
        this(name, null, yValues, true);
    }

    /// Creates a dataset from caller-provided coordinate arrays.
    ///
    /// Passing `null` for `xValues` selects implicit x coordinates. When `copyArrays` is `false`,
    /// the dataset retains the supplied arrays through `DoubleArray` and future edits may therefore
    /// mutate caller-owned storage.
    public DefaultDataSet(String name, double[] xValues, double[] yValues, boolean copyArrays) {
        this(
                name,
                (xValues == null) ? null : new DoubleArray(copyArrays ? xValues.clone() : xValues),
                new DoubleArray(copyArrays ? yValues.clone() : yValues)
        );
    }

    /// Creates an empty dataset with the requested initial capacity.
    ///
    /// When `useXValues` is `false`, the dataset uses implicit x coordinates.
    public DefaultDataSet(String name, int capacity, boolean useXValues) {
        this(name, useXValues ? new DoubleArray(capacity) : null, new DoubleArray(capacity));
    }

    DefaultDataSet(String name, DoubleArray xValues, DoubleArray yValues) {
        xValuesSorted = true;
        super.setName(name);
        this.xValues = xValues;
        if (xValues == null) {
            DataSetProperty.setCategory(this, DEFAULT_CATEGORY_WIDTH);
        }
        this.yValues = yValues;
        if (size() > 0) {
            recomputeXValuesSorted();
        }
    }

    /// Builds one dataset per input series, optionally treating one series as the shared x domain.
    ///
    /// When `xSeriesIndex` is `-1`, every input row becomes a y-series with implicit x positions.
    /// Otherwise the row at `xSeriesIndex` is cloned once and reused as the explicit x-values of all
    /// returned datasets, and that row is omitted from the result.
    ///
    /// `names` is interpreted in result order, not source-row order, so when `xSeriesIndex != -1`
    /// it must exclude the x-series label. `dataLabels`, when provided, are copied and installed on
    /// every returned dataset.
    public static DefaultDataSet[] create(
            double[][] values,
            int xSeriesIndex,
            String[] names,
            String[] dataLabels
    ) {
        DefaultDataSet[] dataSets;
        if (xSeriesIndex == -1) {
            dataSets = new DefaultDataSet[values.length];
            for (int sourceIndex = 0; sourceIndex < values.length; sourceIndex++) {
                String name = (names == null) ? "" : names[sourceIndex];
                dataSets[sourceIndex] = new DefaultDataSet(name, values[sourceIndex]);
            }
        } else {
            dataSets = new DefaultDataSet[values.length - 1];
            SharedXMarker sharedXMarker = new SharedXMarker();
            DoubleArray sharedXValues = new DoubleArray(values[xSeriesIndex].clone());
            int resultIndex = 0;
            for (int sourceIndex = 0; sourceIndex < values.length; sourceIndex++) {
                if (sourceIndex == xSeriesIndex) {
                    continue;
                }

                DoubleArray seriesYValues = new DoubleArray(values[sourceIndex].clone());
                String name = (names == null) ? "" : names[resultIndex];
                DefaultDataSet dataSet = new DefaultDataSet(name, sharedXValues, seriesYValues);
                dataSet.putProperty(SHARED_X_PROPERTY, sharedXMarker, false);
                dataSets[resultIndex++] = dataSet;
            }
        }

        String[] labelCopy = (dataLabels == null) ? null : Arrays.copyOf(dataLabels, dataLabels.length);
        for (DefaultDataSet dataSet : dataSets) {
            dataSet.setDataLabels(labelCopy);
            dataSet.setSharedXDataSets(dataSets);
        }
        return dataSets;
    }

    /// Fills `points` directly from the backing arrays for the requested inclusive index range.
    ///
    /// This override avoids repeated virtual `getXData(int)` and `getYData(int)` calls when the
    /// dataset is already backed by contiguous primitive arrays.
    @Override
    void fillDataPoints(DataPoints points, int firstIndex, int lastIndex) {
        double[] xValues = points.getXValues();
        double[] yValues = points.getYValues();
        int pointCount = lastIndex - firstIndex + 1;

        if (this.xValues != null) {
            System.arraycopy(this.xValues.data(), firstIndex, xValues, 0, pointCount);
        } else {
            for (int pointIndex = 0; pointIndex < pointCount; pointIndex++) {
                xValues[pointIndex] = firstIndex + pointIndex;
            }
        }

        System.arraycopy(this.yValues.data(), firstIndex, yValues, 0, pointCount);
        points.setSize(pointCount);
    }

    private void setSharedXDataSets(DefaultDataSet[] sharedXDataSets) {
        this.sharedXDataSets = sharedXDataSets;
    }

    /// Stores one logical point directly into the backing arrays.
    private void setStoredData(int index, double x, double y) {
        if (xValues != null) {
            xValues.set(index, x);
        }
        yValues.set(index, y);
    }

    /// Marks the sorted-x cache invalid when the edited window introduces an out-of-order x value.
    ///
    /// The method only needs to inspect the changed range plus its immediate neighbors because a
    /// previously sorted dataset can only become unsorted at those boundaries.
    private void invalidateSortedFlagIfNeeded(int firstIndex, int lastIndex) {
        int dataPointCount = size();
        if (dataPointCount <= 1) {
            return;
        }

        int fromIndex = Math.max(firstIndex - 1, 0);
        int toIndex = Math.min(lastIndex + 1, dataPointCount - 1);
        if (fromIndex >= toIndex) {
            return;
        }

        double previousX = getXData(fromIndex);
        for (int index = fromIndex + 1; index <= toIndex; index++) {
            double currentX = getXData(index);
            if (currentX < previousX) {
                xValuesSorted = false;
                return;
            }
            previousX = currentX;
        }
    }

    @Override
    int findSortedXIndex(int firstIndex, int lastIndex, double x) {
        if (xValues != null) {
            return super.findSortedXIndex(firstIndex, lastIndex, x);
        }
        if (x < firstIndex) {
            return -firstIndex - 1;
        }
        if (x > lastIndex) {
            return -lastIndex - 2;
        }

        int insertionPoint = (int) Math.ceil(x);
        return (insertionPoint == x) ? insertionPoint : -insertionPoint - 1;
    }

    /// Recomputes whether iterating by logical index also visits x values in ascending order.
    private void recomputeXValuesSorted() {
        xValuesSorted = true;
        if (xValues == null) {
            return;
        }

        int dataPointCount = size();
        if (dataPointCount <= 1) {
            return;
        }

        double previousX = getXData(0);
        for (int index = 1; index < dataPointCount; index++) {
            double currentX = getXData(index);
            if (currentX < previousX) {
                xValuesSorted = false;
                return;
            }
            previousX = currentX;
        }
    }

    /// Appends one unlabeled point.
    public void addData(double x, double y) {
        addData(x, y, null);
    }

    /// Appends one point, optionally with a label.
    ///
    /// Datasets created as part of one linked x-domain group reject appending so the sibling series
    /// cannot silently drift out of alignment.
    public void addData(double x, double y, String label) {
        if (sharedXDataSets != null) {
            throw new UnsupportedOperationException(
                    "Cannot append data to indices data set sharing its X series"
            );
        }

        if (label != null) {
            int dataPointCount = size();
            if (labels != null) {
                for (int missingLabels = dataPointCount - labels.size(); missingLabels > 0; missingLabels--) {
                    labels.add(null);
                }
            } else {
                labels = new ArrayList<>(Math.max(10, dataPointCount + 1));
                for (int missingLabels = dataPointCount; missingLabels > 0; missingLabels--) {
                    labels.add(null);
                }
            }
        }

        if (xValues != null) {
            xValues.add(x);
        }
        yValues.add(y);
        if (label != null) {
            labels.add(label);
        }
        dataAdded(size() - 1);
    }

    @Override
    protected void dataAdded(int index) {
        if (xValuesSorted && xValues != null) {
            invalidateSortedFlagIfNeeded(index, index);
        }
        super.dataAdded(index);
    }

    @Override
    protected void dataChanged(int firstIndex, int lastIndex, int type) {
        if (type != DataSetContentsEvent.BEFORE_DATA_CHANGED && xValuesSorted && xValues != null) {
            invalidateSortedFlagIfNeeded(firstIndex, lastIndex);
        }
        super.dataChanged(firstIndex, lastIndex, type);
    }

    /// Returns the label assigned to the point at `index`, if one is currently stored.
    @Override
    public String getDataLabel(int index) {
        if (labels != null && index < labels.size()) {
            return labels.get(index);
        }
        return null;
    }

    /// Returns a snapshot of all currently stored labels.
    ///
    /// Missing labels remain `null` in the returned array.
    public String[] getDataLabels() {
        return (labels == null) ? EMPTY_LABELS : labels.toArray(String[]::new);
    }

    @Override
    public double getXData(int index) {
        return (xValues == null) ? index : xValues.get(index);
    }

    /// Returns the x coordinates as an array snapshot.
    ///
    /// For subclasses, the snapshot is built through `getXData(int)` so overriding coordinate logic
    /// is preserved.
    public double[] getXValues() {
        int dataPointCount = size();
        double[] xValues = new double[dataPointCount];
        if (getClass() != DefaultDataSet.class) {
            for (int index = 0; index < dataPointCount; index++) {
                xValues[index] = getXData(index);
            }
        } else if (this.xValues != null) {
            System.arraycopy(this.xValues.data(), 0, xValues, 0, dataPointCount);
        } else {
            for (int index = 0; index < dataPointCount; index++) {
                xValues[index] = index;
            }
        }
        return xValues;
    }

    @Override
    public double getYData(int index) {
        return yValues.get(index);
    }

    /// Returns the y coordinates as an array snapshot.
    ///
    /// For subclasses, the snapshot is built through `getYData(int)` so overriding value logic is
    /// preserved.
    public double[] getYValues() {
        int dataPointCount = size();
        double[] yValues = new double[dataPointCount];
        if (getClass() == DefaultDataSet.class) {
            System.arraycopy(this.yValues.data(), 0, yValues, 0, dataPointCount);
        } else {
            for (int index = 0; index < dataPointCount; index++) {
                yValues[index] = getYData(index);
            }
        }
        return yValues;
    }

    /// Returns `true`; this dataset supports in-place mutation.
    @Override
    public boolean isEditable() {
        return true;
    }

    /// Returns whether this dataset currently stores explicit x coordinates.
    ///
    /// When this is `false`, `getXData(int)` returns the logical point index.
    public final boolean isUsingXValues() {
        return xValues != null;
    }

    /// Returns whether the current logical iteration order is ascending in x.
    @Override
    public boolean isXValuesSorted() {
        return xValuesSorted;
    }

    /// Replaces the full dataset contents.
    ///
    /// `size` selects the populated prefix of the supplied arrays. Passing `null` for `xValues`
    /// switches the dataset back to implicit x coordinates. The replacement breaks any linked
    /// sibling relationship established by [#create(double[][], int, String[], String[])] and emits
    /// a full contents update.
    public void setData(double[] xValues, double[] yValues, int size) {
        if (xValues != null && xValues.length < size) {
            throw new IllegalArgumentException("xValues array too short");
        }
        if (!((yValues != null && yValues.length >= size) || (yValues == null && size == 0))) {
            throw new IllegalArgumentException("yValues array too short");
        }

        if (xValues == null) {
            this.xValues = null;
            DataSetProperty.setCategory(this, DEFAULT_CATEGORY_WIDTH);
        } else {
            if (this.xValues != null) {
                this.xValues.reset();
            } else {
                this.xValues = new DoubleArray();
            }
            this.xValues.add(xValues, size);
            DataSetProperty.setCategory(this, null);
        }

        if (this.yValues != null) {
            this.yValues.reset();
        } else {
            this.yValues = new DoubleArray();
        }
        if (yValues != null) {
            this.yValues.add(yValues, size);
        }

        sharedXDataSets = null;
        recomputeXValuesSorted();
        super.invalidateLimits();
        super.fireDataSetContentsEvent(new DataSetContentsEvent(this));
    }

    /// Updates one logical point.
    ///
    /// When this dataset belongs to a linked sibling group and the x value changes, before/after
    /// change notifications are sent for every linked dataset because they all observe the same x
    /// domain.
    @Override
    public void setData(int index, double x, double y) {
        boolean xChanged = getXData(index) != x;
        if (!xChanged && getYData(index) == y) {
            return;
        }

        if (xChanged && sharedXDataSets != null) {
            for (int dataSetIndex = sharedXDataSets.length - 1; dataSetIndex >= 0; dataSetIndex--) {
                sharedXDataSets[dataSetIndex].dataChanged(
                        index,
                        index,
                        DataSetContentsEvent.BEFORE_DATA_CHANGED
                );
            }
        } else {
            dataChanged(index, index, DataSetContentsEvent.BEFORE_DATA_CHANGED);
        }

        setStoredData(index, x, y);

        if (xChanged && sharedXDataSets != null) {
            for (int dataSetIndex = sharedXDataSets.length - 1; dataSetIndex >= 0; dataSetIndex--) {
                sharedXDataSets[dataSetIndex].dataChanged(
                        index,
                        index,
                        DataSetContentsEvent.AFTER_DATA_CHANGED
                );
            }
        } else {
            dataChanged(index, index, DataSetContentsEvent.AFTER_DATA_CHANGED);
        }
    }

    /// Stores or clears the label associated with one point.
    ///
    /// Labels are sparse: intermediate unlabeled points are stored as `null` entries so indices stay
    /// aligned with the dataset contents.
    public void setDataLabel(int index, String label) {
        if (index < 0) {
            throw new IllegalArgumentException("negative data index: " + index);
        }

        if (label != null) {
            if (labels == null) {
                labels = new ArrayList<>(Math.max(10, index + 1));
            }
            while (labels.size() < index) {
                labels.add(null);
            }
            if (index < labels.size()) {
                labels.set(index, label);
            } else {
                labels.add(label);
            }
            super.fireDataSetContentsEvent(
                    new DataSetContentsEvent(this, DataSetContentsEvent.DATA_LABEL_CHANGED, index, index)
            );
        } else if (labels != null && index < labels.size()) {
            labels.set(index, null);
            super.fireDataSetContentsEvent(
                    new DataSetContentsEvent(this, DataSetContentsEvent.DATA_LABEL_CHANGED, index, index)
            );
        }
    }

    /// Replaces the full per-point label array.
    ///
    /// The supplied array is copied into an `ArrayList` so later caller mutations do not affect the
    /// dataset.
    public void setDataLabels(String[] labels) {
        this.labels = (labels == null) ? null : new ArrayList<>(Arrays.asList(labels));
        if (size() > 0) {
            super.fireDataSetContentsEvent(
                    new DataSetContentsEvent(this, DataSetContentsEvent.DATA_LABEL_CHANGED, 0, size() - 1)
            );
        }
    }

    /// Updates one explicit x coordinate.
    ///
    /// This is a no-op when the dataset currently uses implicit x coordinates. When `index` is past
    /// the end of the dataset, intermediate points are appended with `Double.NaN` y-values.
    public void setXData(int index, double x) {
        if (index < 0) {
            throw new IllegalArgumentException("negative data index: " + index);
        }
        if (xValues == null) {
            return;
        }

        if (index < size()) {
            setData(index, x, getYData(index));
            return;
        }

        super.startBatch();
        try {
            for (int currentIndex = size(); currentIndex <= index; currentIndex++) {
                addData(x, Double.NaN, getDataLabel(currentIndex));
            }
        } finally {
            super.endBatch();
        }
    }

    /// Replaces the stored x-value array when explicit x storage is enabled.
    ///
    /// The overlapping prefix keeps the current y-values. Any newly exposed points receive
    /// `Double.NaN` y-values.
    public void setXValues(double[] xValues) {
        if (this.xValues == null) {
            return;
        }

        int newSize = xValues.length;
        double[] yValues = new double[newSize];
        int copiedCount = Math.min(size(), newSize);
        if (getClass() == DefaultDataSet.class) {
            System.arraycopy(this.yValues.data(), 0, yValues, 0, copiedCount);
        } else {
            for (int index = 0; index < copiedCount; index++) {
                yValues[index] = getYData(index);
            }
        }
        for (int index = copiedCount; index < newSize; index++) {
            yValues[index] = Double.NaN;
        }
        setData(xValues, yValues, newSize);
    }

    /// Updates one y coordinate, extending the dataset when necessary.
    ///
    /// Intermediate points created during extension receive `Double.NaN` y-values. In explicit-x
    /// mode, their x coordinate repeats the last known x value until the requested `index` exists.
    public void setYData(int index, double y) {
        if (index < 0) {
            throw new IllegalArgumentException("negative data index: " + index);
        }
        if (index < size()) {
            setData(index, getXData(index), y);
            return;
        }

        super.startBatch();
        try {
            int currentIndex = size();
            double lastX = (currentIndex <= 0) ? 0.0 : getXData(currentIndex - 1);
            while (currentIndex < index) {
                addData(lastX, Double.NaN, getDataLabel(currentIndex));
                currentIndex++;
            }
            addData(lastX, y, getDataLabel(currentIndex));
        } finally {
            super.endBatch();
        }
    }

    /// Replaces y-values while preserving the current x-domain strategy.
    ///
    /// In implicit-x mode, the dataset length becomes `yValues.length`. In explicit-x mode, the
    /// current x-array length is preserved instead, so `yValues` must provide at least that many
    /// entries.
    public void setYValues(double[] yValues) {
        if (xValues == null) {
            setData(null, yValues, yValues.length);
            return;
        }

        int xValueCount = xValues.size();
        double[] xValues = new double[xValueCount];
        int copiedCount = Math.min(size(), xValueCount);
        double lastX = (copiedCount <= 0) ? 0.0 : getXData(copiedCount - 1);
        if (getClass() == DefaultDataSet.class) {
            System.arraycopy(this.xValues.data(), 0, xValues, 0, copiedCount);
        } else {
            for (int index = 0; index < copiedCount; index++) {
                xValues[index] = getXData(index);
            }
        }
        for (int index = copiedCount; index < xValueCount; index++) {
            xValues[index] = lastX;
        }
        setData(xValues, yValues, xValueCount);
    }

    @Override
    public int size() {
        return yValues.size();
    }

    /// Switches between explicit and implicit x storage.
    ///
    /// Enabling explicit x storage materializes the current implicit indices as `0..size()-1`.
    /// Disabling explicit x storage discards the stored x array and restores the default category
    /// width hint. This method does not emit dataset contents events.
    public void useXValues(boolean useXValues) {
        if (!useXValues) {
            xValues = null;
            DataSetProperty.setCategory(this, DEFAULT_CATEGORY_WIDTH);
        } else if (xValues == null) {
            int dataPointCount = size();
            double[] xValues = new double[dataPointCount];
            for (int index = 0; index < dataPointCount; index++) {
                xValues[index] = index;
            }
            this.xValues = new DoubleArray(xValues);
            DataSetProperty.setCategory(this, null);
        }
    }
}
