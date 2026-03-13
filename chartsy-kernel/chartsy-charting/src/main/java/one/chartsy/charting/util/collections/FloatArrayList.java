package one.chartsy.charting.util.collections;

import java.io.Serializable;

/// Stores a mutable sequence of primitive `float` values without boxing.
///
/// Charting's internal dataset containers use this type to track per-dataset positions alongside
/// an object list, so the implementation is intentionally small and favors indexed insertion,
/// removal, and replacement over the richer contract of `java.util.List`.
///
/// Equality and hashing compare each element by its raw IEEE 754 bit pattern via
/// `Float.floatToRawIntBits(float)`. Distinct `NaN` payloads and the sign bit of zero are
/// therefore preserved by [#equals(Object)] and [#hashCode()].
///
/// Instances are mutable and not thread-safe.
public class FloatArrayList implements Cloneable, Serializable {
    private static final float[] EMPTY_ARRAY = new float[0];
    private float[] elements;
    private int size;

    /// Creates an empty list with room for a small number of elements before the first resize.
    public FloatArrayList() {
        elements = new float[10];
    }

    /// Creates a list containing a copy of `values`.
    ///
    /// Subsequent modifications to this list do not affect the source array.
    public FloatArrayList(float[] values) {
        int initialCapacity = values.length + values.length / 4;
        elements = new float[initialCapacity];
        size = values.length;
        System.arraycopy(values, 0, elements, 0, size);
    }

    /// Creates an empty list with backing storage sized for `initialCapacity` elements.
    ///
    /// @throws NegativeArraySizeException if `initialCapacity` is negative
    public FloatArrayList(int initialCapacity) {
        elements = new float[initialCapacity];
    }

    /// Appends `element` to the end of this list.
    public void add(float element) {
        ensureCapacity(size + 1);
        elements[size++] = element;
    }

    /// Inserts `element` at `index`, shifting the tail one slot to the right.
    ///
    /// @param index the insertion point. `index == size()` appends to the end
    /// @throws IndexOutOfBoundsException if `index` is outside `0..size()`
    public void add(int index, float element) {
        checkPositionIndex(index);
        ensureCapacity(size + 1);
        if (index < size)
            System.arraycopy(elements, index, elements, index + 1, size - index);
        elements[index] = element;
        size++;
    }

    /// Removes all elements without releasing the current backing array.
    public void clear() {
        size = 0;
    }

    /// Returns a copy of this list that does not share backing storage with the original.
    ///
    /// @return cloned list containing the same element values in the same order
    @Override
    public Object clone() {
        try {
            FloatArrayList clone = (FloatArrayList) super.clone();
            clone.elements = toArray();
            return clone;
        } catch (CloneNotSupportedException ex) {
            throw new InternalError(ex);
        }
    }

    /// Copies the stored prefix into `destination` starting at index `0`.
    ///
    /// Elements in `destination` beyond [#size()] are left unchanged.
    ///
    /// @throws ArrayIndexOutOfBoundsException if `destination` is shorter than [#size()]
    /// @throws NullPointerException if `destination` is `null`
    public void copyInto(float[] destination) {
        if (size > 0)
            System.arraycopy(elements, 0, destination, 0, size);
    }

    /// Ensures that this list can hold at least `requiredCapacity` elements without another resize.
    ///
    /// Calling this method never changes [#size()] and never shrinks the backing array.
    public void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity > elements.length) {
            int newCapacity = elements.length + elements.length / 4;
            if (newCapacity < requiredCapacity)
                newCapacity = requiredCapacity;
            float[] expanded = new float[newCapacity];
            System.arraycopy(elements, 0, expanded, 0, size);
            elements = expanded;
        }
    }

    /// Returns whether `obj` stores the same number of elements and the same raw float bits.
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof FloatArrayList other))
            return false;
        if (size != other.size)
            return false;
        for (int i = 0; i < size; i++) {
            if (Float.floatToRawIntBits(elements[i]) != Float.floatToRawIntBits(other.elements[i]))
                return false;
        }
        return true;
    }

    /// Returns the element at `index`.
    ///
    /// @throws IndexOutOfBoundsException if `index` is outside `0..size()-1`
    public float get(int index) {
        checkElementIndex(index);
        return elements[index];
    }

    /// Returns a hash code consistent with [#equals(Object)]'s raw-bit comparison semantics.
    @Override
    public int hashCode() {
        int hash = size + 1;
        for (int i = 0; i < size; i++)
            hash = 31 * hash + Float.floatToRawIntBits(elements[i]);
        return hash;
    }

    /// Returns whether this list currently stores no elements.
    public boolean isEmpty() {
        return size == 0;
    }

    /// Removes and returns the element at `index`, shifting later elements one slot to the left.
    ///
    /// @throws IndexOutOfBoundsException if `index` is outside `0..size()-1`
    public float remove(int index) {
        checkElementIndex(index);
        float removed = elements[index];
        if (index + 1 < size)
            System.arraycopy(elements, index + 1, elements, index, size - index - 1);
        size--;
        return removed;
    }

    /// Removes the half-open range [`fromIndex`, `toIndex`) from this list.
    ///
    /// Elements previously at `toIndex` and beyond are shifted left by
    /// `toIndex - fromIndex` positions.
    ///
    /// @throws IndexOutOfBoundsException if the range is invalid for the current size
    public void removeRange(int fromIndex, int toIndex) {
        if (fromIndex < 0 || toIndex > size || fromIndex > toIndex)
            throw new IndexOutOfBoundsException();
        int removedCount = toIndex - fromIndex;
        if (removedCount > 0) {
            if (toIndex < size)
                System.arraycopy(elements, toIndex, elements, fromIndex, size - toIndex);
            size -= removedCount;
        }
    }

    /// Replaces the element at `index`.
    ///
    /// @return the previous value stored at `index`
    /// @throws IndexOutOfBoundsException if `index` is outside `0..size()-1`
    public float set(int index, float element) {
        checkElementIndex(index);
        float previous = elements[index];
        elements[index] = element;
        return previous;
    }

    /// Returns the number of stored elements.
    public int size() {
        return size;
    }

    /// Returns an exact-size copy of the stored elements.
    ///
    /// Empty lists return a shared zero-length array.
    public float[] toArray() {
        if (size <= 0)
            return EMPTY_ARRAY;
        float[] copy = new float[size];
        System.arraycopy(elements, 0, copy, 0, size);
        return copy;
    }

    /// Returns a debugging representation of the stored values in encounter order.
    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < size; i++) {
            if (i > 0)
                result.append(", ");
            result.append(elements[i]);
        }
        result.append(']');
        return result.toString();
    }

    /// Shrinks the backing array to [#size()].
    ///
    /// Empty lists switch to the shared zero-length array also returned by [#toArray()].
    public void trimToSize() {
        if (elements.length > size) {
            if (size <= 0)
                elements = EMPTY_ARRAY;
            else {
                float[] trimmed = new float[size];
                System.arraycopy(elements, 0, trimmed, 0, size);
                elements = trimmed;
            }
        }
    }

    private void checkElementIndex(int index) {
        if (index < 0 || index >= size)
            throw new IndexOutOfBoundsException("index " + index + " inappropriate for size " + size);
    }

    private void checkPositionIndex(int index) {
        if (index < 0 || index > size)
            throw new IndexOutOfBoundsException("index " + index + " inappropriate for size " + size);
    }
}
