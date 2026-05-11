package one.chartsy.charting.util;

import java.io.Serializable;
import java.util.Objects;

/// Stores a mutable sequence of primitive `double` values without boxing.
///
/// Chart scales, datasets, and renderer helpers use this type as a compact working buffer. The
/// array-based constructor adopts the supplied array directly, [#data()] exposes the live backing
/// array, and [#reset()] differs from [#clear()] by keeping the current capacity for reuse.
///
/// Instances are mutable and not thread-safe.
public final class DoubleArray implements Serializable {
    private static final double[] EMPTY = new double[0];
    double[] elements;
    int size;

    /// Creates an empty array backed by a shared zero-length storage array.
    public DoubleArray() {
        this(DoubleArray.EMPTY);
    }

    /// Creates a wrapper over an existing backing array.
    ///
    /// A `null` input is treated as an empty array. The supplied array is retained directly rather
    /// than copied, so later external mutations are reflected by this instance.
    public DoubleArray(double[] values) {
        adopt(values);
    }

    /// Creates an empty array with the requested initial capacity.
    ///
    /// Unlike later growth through [Arrays], this constructor uses the requested capacity exactly.
    public DoubleArray(int capacity) {
        elements = new double[capacity];
        size = 0;
    }

    private void trimStorage(boolean exactSize) {
        int targetLength = exactSize ? size : Arrays.normalizeCapacity(size);
        if (targetLength < elements.length) {
            double[] trimmed = new double[targetLength];
            System.arraycopy(elements, 0, trimmed, 0, size);
            elements = trimmed;
        }
    }

    private void adopt(double[] values) {
        double[] adoptedValues = (values == null) ? DoubleArray.EMPTY : values;
        elements = adoptedValues;
        size = adoptedValues.length;
    }

    private void ensureCapacity(int minimumCapacity) {
        if (minimumCapacity > elements.length)
            elements = Arrays.growDoubleArray(elements, minimumCapacity);
    }

    private int checkExistingIndex(int index) {
        return Objects.checkIndex(index, size);
    }

    /// Appends one value to the logical end of this array.
    public void add(double value) {
        ensureCapacity(size + 1);
        elements[size++] = value;
    }

    /// Appends every element from `values`.
    public void add(double[] values) {
        add(values, values.length);
    }

    /// Appends the first `count` elements from `values`.
    ///
    /// The copied prefix is appended in order. The source array is read immediately and is not
    /// retained.
    ///
    /// @param values source array whose prefix should be appended
    /// @param count  number of leading values to append
    /// @throws NullPointerException      if `values` is `null`
    /// @throws IndexOutOfBoundsException if `count` is negative or greater than `values.length`
    public void add(double[] values, int count) {
        Objects.requireNonNull(values, "values");
        Objects.checkFromIndexSize(0, count, values.length);
        if (count == 0)
            return;

        int newSize = size + count;
        ensureCapacity(newSize);
        System.arraycopy(values, 0, elements, size, count);
        size = newSize;
    }

    /// Appends the logical contents of another [DoubleArray].
    ///
    /// Spare capacity beyond `other.size()` is ignored.
    public void add(DoubleArray other) {
        Objects.requireNonNull(other, "other");
        add(other.elements, other.size);
    }

    /// Inserts the first `count` values from `values` at a logical index.
    ///
    /// Existing elements from `index` onward are shifted to the right. The insertion index must lie
    /// in the closed range `0..size()`.
    ///
    /// @param index  insertion position in the current logical prefix
    /// @param values source array whose prefix should be inserted
    /// @param count  number of leading values to insert
    /// @throws NullPointerException      if `values` is `null`
    /// @throws IndexOutOfBoundsException if `index` is outside `0..size()` or if `count` is negative or greater than
    ///                                       `values.length`
    public void add(int index, double[] values, int count) {
        Objects.requireNonNull(values, "values");
        Objects.checkIndex(index, size + 1);
        Objects.checkFromIndexSize(0, count, values.length);
        if (count == 0)
            return;
        if (index == size) {
            add(values, count);
            return;
        }

        int newSize = size + count;
        double[] originalValues = DoubleArrayPool.take(size);
        try {
            System.arraycopy(elements, 0, originalValues, 0, size);
            ensureCapacity(newSize);
            System.arraycopy(values, 0, elements, index, count);
            System.arraycopy(originalValues, index, elements, index + count, size - index);
            size = newSize;
        } finally {
            DoubleArrayPool.release(originalValues);
        }
    }

    /// Empties the array and discards the current backing storage.
    ///
    /// Use [#reset()] instead when the current capacity should be kept for reuse.
    public void clear() {
        adopt(null);
    }

    /// Returns an independent copy of the current logical contents.
    ///
    /// The returned [DoubleArray] has its own backing storage sized exactly to [#size()].
    public DoubleArray copy() {
        DoubleArray copy = new DoubleArray(size);
        copy.add(this);
        return copy;
    }

    /// Exposes the live backing array.
    ///
    /// The returned array may be longer than [#size()], and slots beyond the logical prefix may
    /// still contain stale values left behind by prior updates. Mutating the returned array changes
    /// this object immediately.
    public double[] data() {
        return elements;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this)
            return true;
        if (!(obj instanceof DoubleArray other))
            return false;
        if (other.size != size)
            return false;
        for (int index = 0; index < size; index++) {
            if (elements[index] != other.elements[index])
                return false;
        }
        return true;
    }

    /// Returns a hash code derived from the logical prefix only.
    ///
    /// Spare capacity beyond [#size()] does not contribute to the result.
    @Override
    public int hashCode() {
        int hash = 1;
        for (int index = 0; index < size; index++) {
            double value = elements[index];
            long bits = (value == 0.0d) ? 0L : Double.doubleToLongBits(value);
            hash = 31 * hash + Long.hashCode(bits);
        }
        return hash;
    }

    /// Returns the value at `index` within the logical prefix.
    ///
    /// @throws IndexOutOfBoundsException if `index` is outside `0..size()-1`
    public double get(int index) {
        return elements[checkExistingIndex(index)];
    }

    /// Removes `count` consecutive values starting at `index`.
    ///
    /// The removed suffix is compacted in place. When the retained logical prefix drops below a
    /// quarter of the current capacity, the backing array is compacted to the package's normalized
    /// growth size.
    ///
    /// @param index first logical element to remove
    /// @param count number of consecutive elements to remove; must be strictly positive
    /// @throws IndexOutOfBoundsException if `count` is not positive or if the removal range falls outside the current logical
    ///                                       prefix
    public void remove(int index, int count) {
        if (count <= 0)
            throw new IndexOutOfBoundsException("count must be strictly positive");
        Objects.checkFromIndexSize(index, count, size);

        int firstTrailingIndex = index + count;
        int trailingCount = size - firstTrailingIndex;
        if (trailingCount > 0)
            System.arraycopy(elements, firstTrailingIndex, elements, index, trailingCount);
        size -= count;
        if (elements.length > size * 4)
            trimStorage(false);
    }

    /// Sets the logical size to `0` while keeping the current backing storage.
    public void reset() {
        size = 0;
    }

    /// Reverses the logical prefix `0..size()-1` in place.
    public void reverse() {
        Arrays.reverse(elements, size);
    }

    /// Replaces the value at `index` within the logical prefix.
    ///
    /// @throws IndexOutOfBoundsException if `index` is outside `0..size()-1`
    public void set(int index, double value) {
        elements[checkExistingIndex(index)] = value;
    }

    /// Returns the number of logical values currently stored.
    public int size() {
        return size;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(size);
        builder.append('[');
        for (int index = 0; index < size; index++) {
            builder.append(elements[index]);
            if (index != size - 1)
                builder.append(", ");
            if (index > 0 && index % 16 == 0)
                builder.append('\n');
        }
        builder.append(']');
        return builder.toString();
    }

    /// Shrinks the backing array so its length matches the current logical size exactly.
    public void trim() {
        trimStorage(true);
    }
}
