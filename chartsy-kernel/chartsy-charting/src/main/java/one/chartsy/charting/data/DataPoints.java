package one.chartsy.charting.data;

import java.util.Arrays;

import one.chartsy.charting.DoublePoints;
import one.chartsy.charting.util.IntArrayPool;

/// Extends `DoublePoints` with dataset-relative source indices for each logical point.
///
/// `AbstractDataSet` and renderer code use this type as the bridge between numeric point batches
/// and the dataset entries those points came from. The coordinate arrays may be filtered or
/// transformed in place during projection, while the parallel index array preserves enough
/// information to recover labels, annotations, and virtual-dataset mappings afterward.
///
/// The index metadata is lazy. When a point has no known source entry, its stored index is `-1`.
/// The array returned by [#getIndices()] is live scratch state, not a defensive copy.
public class DataPoints extends DoublePoints {
    private static final int MISSING_INDEX = -1;

    int[] indices;
    DataSet dataSet;

    /// Creates an empty batch associated with `dataSet`.
    ///
    /// The inherited coordinate arrays are borrowed from the shared pools. Index metadata is not
    /// allocated until a caller stores or requests it.
    public DataPoints(DataSet dataSet, int initialCapacity) {
        super(initialCapacity);
        this.dataSet = dataSet;
    }

    /// Creates a zero-copy batch over caller-provided coordinate and index arrays.
    ///
    /// The arrays and dataset reference are retained exactly as supplied. This constructor is used
    /// for adapters such as `DataSetPoint#getData()` where copying a small scratch batch would add
    /// unnecessary allocation.
    public DataPoints(DataSet dataSet, int size, double[] xValues, double[] yValues, int[] indices) {
        super(size, xValues, yValues);
        this.dataSet = dataSet;
        this.indices = indices;
    }

    /// Creates a detached batch whose indices, if present, are interpreted without a dataset owner.
    public DataPoints(int initialCapacity) {
        this(null, initialCapacity);
    }

    /// Appends all points and stored source indices from `points`.
    ///
    /// This does not replace [#getDataSet()]; callers normally use it only for batches that refer
    /// to the same dataset or index space.
    public void add(DataPoints points) {
        add(points.getXValues(), points.getYValues(), points.getIndices(), points.size());
    }

    /// Appends one point whose source dataset index is unknown.
    ///
    /// The appended index slot is filled with `-1`.
    @Override
    public void add(double x, double y) {
        add(x, y, MISSING_INDEX);
    }

    /// Appends one point with an explicit source dataset index.
    public void add(double x, double y, int dataIndex) {
        int currentSize = super.size();
        int[] currentIndices = getIndices();
        if (currentIndices.length > currentSize) {
            currentIndices[currentSize] = dataIndex;
            super.add(x, y);
            return;
        }

        add(new double[] { x }, new double[] { y }, new int[] { dataIndex }, 1);
    }

    /// Appends `count` points whose source dataset indices are unknown.
    ///
    /// The appended index slots are filled with `-1`.
    @Override
    public void add(double[] xValues, double[] yValues, int count) {
        insertIndices(super.size(), count);
        super.add(xValues, yValues, count);
    }

    /// Appends `count` points together with their source dataset indices.
    ///
    /// The supplied `sourceIndices` array is read immediately; it is not retained.
    public void add(double[] xValues, double[] yValues, int[] sourceIndices, int count) {
        int[] currentIndices = getIndices();
        int currentSize = super.size();
        super.add(xValues, yValues, count);
        int newSize = super.size();
        indices = IntArrayPool.reAlloc(currentIndices, newSize, this);
        System.arraycopy(sourceIndices, 0, indices, currentSize, count);
    }

    /// Inserts `count` points at `index` and marks their source dataset indices as unknown.
    ///
    /// @throws IndexOutOfBoundsException when `index` is outside `[0, size()]`
    @Override
    public void add(int index, double[] xValues, double[] yValues, int count) {
        validateInsertionIndex(index);
        insertIndices(index, count);
        super.add(index, xValues, yValues, count);
    }

    /// Inserts `count` points at `index` together with explicit source dataset indices.
    ///
    /// @throws IndexOutOfBoundsException when `index` is outside `[0, size()]`
    public void add(int index, double[] xValues, double[] yValues, int[] sourceIndices, int count) {
        validateInsertionIndex(index);

        int[] currentIndices = getIndices();
        int currentSize = super.size();
        super.add(index, xValues, yValues, count);
        int newSize = super.size();
        indices = IntArrayPool.reAlloc(currentIndices, newSize, this);
        if (index < currentSize) {
            System.arraycopy(indices, index, indices, index + count, currentSize - index);
        }
        System.arraycopy(sourceIndices, 0, indices, index, count);
    }

    /// Releases the pooled index array in addition to the coordinate arrays owned by
    /// `DoublePoints`.
    @Override
    public void dispose() {
        super.dispose();
        if (indices != null) {
            IntArrayPool.release(indices);
            indices = null;
        }
    }

    /// Alias for [#getIndex(int)] for call sites that already speak in terms of dataset indices.
    public final int getDataIndex(int index) {
        return getIndex(index);
    }

    /// Returns the dataset whose index space the stored source indices refer to.
    ///
    /// Detached scratch batches may return `null`.
    public DataSet getDataSet() {
        return dataSet;
    }

    /// Returns the stored source dataset index for the point at `index`.
    ///
    /// The result is `-1` when this batch does not currently know which dataset element produced
    /// that point.
    public final int getIndex(int index) {
        if (indices == null) {
            return MISSING_INDEX;
        }
        return indices[index];
    }

    /// Returns the live backing source-index array.
    ///
    /// If the array has not been materialized yet, this method allocates one from `IntArrayPool`
    /// and fills every logical slot with `-1`. Callers may mutate the returned array directly; the
    /// charting pipeline relies on that for in-place point filtering and remapping.
    public final int[] getIndices() {
        if (indices == null) {
            indices = IntArrayPool.take(super.size(), this);
            Arrays.fill(indices, 0, super.size(), MISSING_INDEX);
        }
        return indices;
    }

    /// Removes `count` points and keeps any materialized source indices aligned with the remaining
    /// coordinates.
    @Override
    public void remove(int index, int count) {
        int originalSize = super.size();
        super.remove(index, count);

        int trailingCount = originalSize - (index + count);
        if (indices != null && trailingCount > 0) {
            System.arraycopy(indices, index + count, indices, index, trailingCount);
        }
    }

    /// Replaces the stored source-index metadata.
    ///
    /// At most `count` entries are copied from `sourceIndices`. Any remaining logical points are
    /// marked with `-1`. Passing `count <= 0` clears the metadata entirely.
    public void setIndices(int[] sourceIndices, int count) {
        int size = super.size();
        int copiedCount = Math.min(size, count);
        if (copiedCount <= 0) {
            IntArrayPool.release(indices);
            indices = null;
            return;
        }

        int[] newIndices = IntArrayPool.take(size, this);
        System.arraycopy(sourceIndices, 0, newIndices, 0, copiedCount);
        Arrays.fill(newIndices, copiedCount, size, MISSING_INDEX);
        IntArrayPool.release(indices);
        indices = newIndices;
    }

    @Override
    public String toString() {
        int size = super.size();
        StringBuilder builder = new StringBuilder(size * 16);
        builder.append('[');
        for (int index = 0; index < size; index++) {
            builder.append('#').append(getIndex(index));
            builder.append(" (").append(super.getX(index)).append(',').append(super.getY(index))
                    .append(')');
            if (index != size - 1) {
                builder.append(", ");
            }
            if (index > 0 && index % 16 == 0) {
                builder.append('\n');
            }
        }
        builder.append("] from ").append(dataSet);
        return builder.toString();
    }

    private void insertIndices(int index, int count) {
        int[] scratch = IntArrayPool.take(count);
        try {
            Arrays.fill(scratch, 0, count, MISSING_INDEX);
            insertIndices(index, count, scratch);
        } finally {
            IntArrayPool.release(scratch);
        }
    }

    private void insertIndices(int index, int count, int[] sourceIndices) {
        int[] currentIndices = getIndices();
        int currentSize = super.size();
        int newSize = currentSize + count;

        if (index == currentSize) {
            if (newSize > currentIndices.length) {
                currentIndices = IntArrayPool.reAlloc(currentIndices, newSize, this);
            }
            System.arraycopy(sourceIndices, 0, currentIndices, index, count);
            indices = currentIndices;
            return;
        }

        int[] scratch = IntArrayPool.take(currentSize);
        try {
            System.arraycopy(currentIndices, 0, scratch, 0, currentSize);
            if (newSize > currentIndices.length) {
                IntArrayPool.release(currentIndices);
                currentIndices = IntArrayPool.take(newSize, this);
                if (index > 0) {
                    System.arraycopy(scratch, 0, currentIndices, 0, index);
                }
            }
            System.arraycopy(sourceIndices, 0, currentIndices, index, count);
            System.arraycopy(scratch, index, currentIndices, index + count, currentSize - index);
            indices = currentIndices;
        } finally {
            IntArrayPool.release(scratch);
        }
    }

    private void validateInsertionIndex(int index) {
        if (index < 0 || index > super.size()) {
            throw new IndexOutOfBoundsException("Insertion index out of bounds");
        }
    }
}
