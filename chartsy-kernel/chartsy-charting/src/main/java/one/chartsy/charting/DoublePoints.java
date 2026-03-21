package one.chartsy.charting;

import java.io.Serializable;

import one.chartsy.charting.util.DoubleArrayPool;
import one.chartsy.charting.util.GraphicUtil;

/// Stores a mutable batch of points as parallel `double[]` x and y coordinate arrays.
///
/// The charting pipeline uses this type as a reusable transport for in-place projector
/// transformations, scale tick layout, renderer geometry, and interaction hit testing. Only the
/// first `size()` entries are logically valid; the backing arrays may have additional spare
/// capacity.
///
/// Instances created with the capacity-based constructors borrow backing arrays from
/// `DoubleArrayPool`. Call [#dispose()] when a temporary batch is no longer needed so pooled arrays
/// can be reclaimed. The array-wrapping constructor retains the supplied arrays without copying
/// them, which is useful for zero-copy adapters but means subsequent mutations affect both the
/// `DoublePoints` view and the original arrays.
public class DoublePoints implements Serializable {
    private static final double[] EMPTY_VALUES = new double[0];
    int size;
    double[] x;
    double[] y;

    /// Creates an empty points container with no allocated backing storage.
    public DoublePoints() {
        this(0, EMPTY_VALUES, EMPTY_VALUES);
    }

    /// Creates a container holding one point.
    ///
    /// @param x the x coordinate of the initial point
    /// @param y the y coordinate of the initial point
    public DoublePoints(double x, double y) {
        this(1);
        add(x, y);
    }

    /// Creates an empty container with space for at least `initialCapacity` points.
    ///
    /// Backing arrays are borrowed from `DoubleArrayPool`.
    ///
    /// @param initialCapacity requested point capacity
    public DoublePoints(int initialCapacity) {
        size = 0;
        x = DoubleArrayPool.take(initialCapacity, this);
        y = DoubleArrayPool.take(initialCapacity, this);
    }

    /// Creates a view over the supplied backing arrays.
    ///
    /// No copy is performed. Only the prefix `[0, size)` is considered part of this container.
    ///
    /// @param size    the logical number of points stored in the arrays
    /// @param xValues backing x-coordinate array
    /// @param yValues backing y-coordinate array
    public DoublePoints(int size, double[] xValues, double[] yValues) {
        x = xValues;
        y = yValues;
        this.size = size;
    }

    /// Appends one point to the end of this container.
    public void add(double x, double y) {
        ensureCapacity(size + 1);
        this.x[size] = x;
        this.y[size] = y;
        size++;
    }

    /// Appends the first `count` entries from the supplied coordinate arrays.
    ///
    /// @param xValues source x coordinates
    /// @param yValues source y coordinates
    /// @param count   number of entries to append from each array
    public void add(double[] xValues, double[] yValues, int count) {
        int newSize = size + count;
        ensureCapacity(newSize);
        System.arraycopy(xValues, 0, x, size, count);
        System.arraycopy(yValues, 0, y, size, count);
        size = newSize;
    }

    /// Appends all logical points from `points`.
    public final void add(DoublePoints points) {
        add(points.x, points.y, points.size);
    }

    /// Inserts `count` points at `index`, shifting later points to the right.
    ///
    /// @param index   insertion position in the logical point sequence
    /// @param xValues source x coordinates
    /// @param yValues source y coordinates
    /// @param count   number of points to insert
    public void add(int index, double[] xValues, double[] yValues, int count) {
        if (index < 0 || index > size) {
            throw new IndexOutOfBoundsException("Insertion index out of bounds");
        }

        int newSize = size + count;
        if (index == size) {
            ensureCapacity(newSize);
            System.arraycopy(xValues, 0, x, size, count);
            System.arraycopy(yValues, 0, y, size, count);
            size = newSize;
            return;
        }

        double[] scratch = DoubleArrayPool.take(size);
        try {
            System.arraycopy(x, 0, scratch, 0, size);
            if (newSize > x.length) {
                DoubleArrayPool.release(x);
                x = DoubleArrayPool.take(newSize, this);
                if (index > 0) {
                    System.arraycopy(scratch, 0, x, 0, index);
                }
            }
            System.arraycopy(xValues, 0, x, index, count);
            System.arraycopy(scratch, index, x, index + count, size - index);

            System.arraycopy(y, 0, scratch, 0, size);
            if (newSize > y.length) {
                DoubleArrayPool.release(y);
                y = DoubleArrayPool.take(newSize, this);
                if (index > 0) {
                    System.arraycopy(scratch, 0, y, 0, index);
                }
            }
            System.arraycopy(yValues, 0, y, index, count);
            System.arraycopy(scratch, index, y, index + count, size - index);
            size = newSize;
        } finally {
            DoubleArrayPool.release(scratch);
        }
    }

    /// Returns the current backing arrays to `DoubleArrayPool`.
    ///
    /// Pool release is a no-op for arrays that were never borrowed from the pool, but callers
    /// should still treat a disposed instance as finished scratch state rather than continuing to
    /// mutate it.
    public void dispose() {
        DoubleArrayPool.release(x);
        DoubleArrayPool.release(y);
    }

    /// Returns the x coordinate at `index`.
    public final double getX(int index) {
        return x[index];
    }

    /// Returns the x coordinate at `index` snapped with `GraphicUtil.toInt(...)`.
    public final int getXFloor(int index) {
        return GraphicUtil.toInt(x[index]);
    }

    /// Returns the live backing x-coordinate array.
    ///
    /// Only the prefix `[0, size())` is logically populated.
    public final double[] getXValues() {
        return x;
    }

    /// Returns a detached copy of the logical x-coordinate prefix.
    public final double[] getXValuesClone() {
        double[] clone = new double[size];
        System.arraycopy(x, 0, clone, 0, size);
        return clone;
    }

    /// Returns the y coordinate at `index`.
    public final double getY(int index) {
        return y[index];
    }

    /// Returns the y coordinate at `index` snapped with `GraphicUtil.toInt(...)`.
    public final int getYFloor(int index) {
        return GraphicUtil.toInt(y[index]);
    }

    /// Returns the live backing y-coordinate array.
    ///
    /// Only the prefix `[0, size())` is logically populated.
    public final double[] getYValues() {
        return y;
    }

    /// Returns a detached copy of the logical y-coordinate prefix.
    public final double[] getYValuesClone() {
        double[] clone = new double[size];
        System.arraycopy(y, 0, clone, 0, size);
        return clone;
    }

    /// Removes `count` points starting at `index`.
    public void remove(int index, int count) {
        if (count <= 0) {
            throw new IndexOutOfBoundsException("count must be strictly positive");
        }

        int endIndex = index + count;
        int trailingCount = size - endIndex;
        if (trailingCount < 0) {
            throw new IndexOutOfBoundsException("Points out of bounds");
        }

        if (trailingCount > 0) {
            System.arraycopy(x, endIndex, x, index, trailingCount);
            System.arraycopy(y, endIndex, y, index, trailingCount);
        }
        size -= count;
    }

    /// Clears the logical contents without shrinking the backing arrays.
    public void reset() {
        setSize(0);
    }

    /// Replaces the point at `index`.
    public final void set(int index, double x, double y) {
        this.x[index] = x;
        this.y[index] = y;
    }

    /// Changes the logical number of points without reallocating backing arrays.
    ///
    /// The requested size must not exceed the current backing-array capacity.
    public void setSize(int size) {
        if (size <= x.length) {
            this.size = size;
            return;
        }
        throw new IndexOutOfBoundsException("Number of points cannot exceed available space");
    }

    /// Replaces the x coordinate at `index`.
    public final void setX(int index, double x) {
        this.x[index] = x;
    }

    /// Replaces the y coordinate at `index`.
    public final void setY(int index, double y) {
        this.y[index] = y;
    }

    /// Returns the logical number of points currently stored.
    public final int size() {
        return size;
    }

    /// Swaps the backing x and y coordinate arrays in place.
    ///
    /// This is used by helpers that want to reinterpret the same batch against the opposite axis
    /// without copying every point.
    public final void swapXYValues() {
        double[] values = x;
        x = y;
        y = values;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(size);
        builder.append('[');
        for (int index = 0; index < size; index++) {
            builder.append('(').append(x[index]).append(',').append(y[index]).append(')');
            if (index != size - 1) {
                builder.append(", ");
            }
            if (index > 0 && index % 16 == 0) {
                builder.append('\n');
            }
        }
        builder.append(']');
        return builder.toString();
    }

    private void ensureCapacity(int minSize) {
        if (x == EMPTY_VALUES) {
            x = DoubleArrayPool.take(minSize, this);
            y = DoubleArrayPool.take(minSize, this);
            return;
        }
        if (minSize > x.length) {
            x = DoubleArrayPool.reAlloc(x, minSize, this);
        }
        if (minSize > y.length) {
            y = DoubleArrayPool.reAlloc(y, minSize, this);
        }
    }
}
