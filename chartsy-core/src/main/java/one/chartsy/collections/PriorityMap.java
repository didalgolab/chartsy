/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content ahead.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.collections;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Comparator;

/**
 * The binary min-heap implementation that provides priority queue with a
 * key-value mappings support and removal based on {@code Comparable} ordering
 * of the keys.
 * <p>
 * The removal order of a binary heap is based on the natural sort order of its
 * elements (the keys). The {@link #remove()} method always returns the first
 * element as determined by the sort order. The removal order is the same as the
 * order in which head element is peeked using the {@link #peekKey()} and
 * {@link #peekValue()} methods.
 * <p>
 * The {@link #put(Comparable, Object)} and {@link #remove()} operations perform
 * in logarithmic time. The {@link #peekKey()} and {@link #peekValue()}
 * operations perform in constant time. The {@link #size()} and
 * {@link #isEmpty()} operations are trivial and run in a minimal constant time.
 * All other operations perform in linear time or worse.
 * <p>
 * <b>Note that this implementation is not synchronized.</b> Use additional
 * custom wrapper classes (not included in the <i>Chartsy</i> codebase) to
 * provide synchronized access to a {@code PriorityMap}.
 * 
 * @author Mariusz Bernacki
 *
 */
public class PriorityMap<K extends Comparable<? super K>, V> implements Serializable {

    /** The default capacity for the buffer */
    private static final int DEFAULT_CAPACITY = 13;
    /** The key elements in this buffer */
    private K[] keys;
    /** The value elements in this buffer */
    private V[] values;
    /** The number of elements currently in this buffer */
    private int size;

    private final Comparator<K> keyComparator;


    /**
     * Constructs a new empty buffer that sorts in ascending order by the
     * natural order of the objects added.
     */
    public PriorityMap() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Constructs a new empty buffer that sorts in ascending order by the
     * natural order of the keys added, specifying an initial capacity.
     *
     * @param capacity
     *            the initial capacity for the buffer, greater than zero
     * @throws IllegalArgumentException
     *             if the {@code capacity} is {@code <= 0}
     */
    public PriorityMap(int capacity) {
        this(capacity, (Comparator<K>)null);
    }

    /**
     * Constructs a new empty buffer that sorts in ascending order by the
     * natural order of the keys added, specifying an initial capacity.
     *
     * @param capacity
     *            the initial capacity for the buffer, greater than zero
     * @throws IllegalArgumentException
     *             if the {@code capacity} is {@code <= 0}
     */
    @SuppressWarnings("unchecked")
    public PriorityMap(int capacity, Comparator<K> keyComparator) {
        if (capacity <= 0)
            throw new IllegalArgumentException("invalid capacity");

        this.keys = (K[]) new Comparable[capacity];
        this.values = (V[]) new Object[capacity];
        this.keyComparator = keyComparator;
    }

    /**
     * Returns the number of elements in this buffer.
     *
     * @return the number of elements in this buffer
     */
    public int size() {
        return size;
    }

    /**
     * Returns {@code true} if this buffer is empty.
     *
     * @return {@code true} if {@code this.size() == 0}
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Clears all elements from the buffer.
     */
    public void clear() {
        Arrays.fill(this.keys, null); // for gc
        Arrays.fill(this.values, null); // for gc
        size = 0;
    }

    /**
     * Adds a key-value mapping to the buffer. The element added will be sorted
     * according to the {@code key} {@code Comparable} ordering.
     *
     * @param key
     *            the element to be added
     * @param value
     *            the value to be associated with the given {@code key}
     */
    public void put(K key, V value) {
        if (key == null)
            throw new NullPointerException();
        if (isAtCapacity())
            grow();

        // percolate element to it's place in tree
        if (keyComparator != null)
            siftUpUsingComparator(size++, key, value);
        else
            siftUpComparable(size++, key, value);
    }

    private void siftUpComparable(int k, K key, V value) {
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            K e = keys[parent];
            if (key.compareTo(e) >= 0)
                break;
            keys[k] = e;
            values[k] = values[parent];
            k = parent;
        }
        keys[k] = key;
        values[k] = value;
    }

    private void siftUpUsingComparator(int k, K key, V value) {
        while (k > 0) {
            int parent = (k - 1) >>> 1;
            K e = keys[parent];
            if (keyComparator.compare(key, e) >= 0)
                break;
            keys[k] = e;
            values[k] = values[parent];
            k = parent;
        }
        keys[k] = key;
        values[k] = value;
    }

    /**
     * Gets the next key element to be removed without actually removing it
     * (peek).
     *
     * @return the next key element or {@code null} if the buffer is empty
     */
    public K peekKey() {
        return keys[0];
    }

    /**
     * Gets the next value element to be removed without actually removing it
     * (peek).
     *
     * @return the next value element or {@code null} if the buffer is empty
     */
    public V peekValue() {
        return values[0];
    }

    /**
     * Gets and removes the next element (pop). The method removes both the next
     * key and the value mapping associated with the key.
     *
     * @return the next value element or {@code null} is the buffer is empty
     */
    public V remove() {
        final V result = values[0];

        if (size > 0) {
            int n;
            K x = keys[(n = --size)];
            V v = values[n];
            keys[n] = null;
            values[n] = null;
            if (n > 0) {
                if (keyComparator != null)
                    siftDownUsingComparator(0, x, v, n);
                else
                    siftDownComparable(0, x, v, n);
            }
        }
        return result;
    }

    private void siftDownComparable(int k, K key, V value, int n) {
        // assert n > 0;
        int half = n >>> 1;           // loop while a non-leaf
        while (k < half) {
            int child = (k << 1) + 1; // assume left child is least
            K c = keys[child];
            int right = child + 1;
            if (right < n && c.compareTo(keys[right]) > 0)
                c = keys[child = right];
            if (key.compareTo(c) <= 0)
                break;
            keys[k] = c;
            values[k] = values[child];
            k = child;
        }
        keys[k] = key;
        values[k] = value;
    }

    private void siftDownUsingComparator(int k, K key, V value, int n) {
        // assert n > 0;
        int half = n >>> 1;           // loop while a non-leaf
        while (k < half) {
            int child = (k << 1) + 1; // assume left child is least
            K c = keys[child];
            int right = child + 1;
            if (right < n && keyComparator.compare(c, keys[right]) > 0)
                c = keys[child = right];
            if (keyComparator.compare(key, c) <= 0)
                break;
            keys[k] = c;
            values[k] = values[child];
            k = child;
        }
        keys[k] = key;
        values[k] = value;
    }

    /**
     * Tests if the buffer is at capacity.
     *
     * @return {@code true} if buffer is full; {@code false} otherwise
     */
    public boolean isAtCapacity() {
        return keys.length == size;
    }

    /**
     * Increases the size of the heap to support additional elements
     */
    private void grow() {
        keys = Arrays.copyOf(keys, keys.length * 2);
        values = Arrays.copyOf(values, values.length * 2);
    }

    /**
     * Returns a string representation of this heap.  The returned string
     * is similar to those produced by standard JDK collections.
     *
     * @return a string representation of this heap
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (int i = 0; i < size; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(keys[i]);
            sb.append('=');
            sb.append(values[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
