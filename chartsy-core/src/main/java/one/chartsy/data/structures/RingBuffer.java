/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.structures;

import java.util.NoSuchElementException;

public class RingBuffer<T> {
    public static final int DEFAULT_CAPACITY = 8;

    private Object[] items;
    private int firstIdx = 0;   // Index of the first elements
    private int nextIdx = 0;    // Index of the slot into which we would insert an element on addLast
    // If nextIdx == (firstIdx - 1) % array_size, then insertion requires resizing the array.
    // If nextIdx == firstIdx, then the buffer is empty.

    public RingBuffer() {
        this(DEFAULT_CAPACITY);
    }

    public RingBuffer(int initialCapacity) {
        items = new Object[initialCapacity];
    }

    private void doubleBackingArraySize() {
        int newSize = items.length * 2;
        Object[] newArray = new Object[newSize];
        int oldSize;

        if (nextIdx < firstIdx) {
            int numElementsToEndOfArray = items.length - firstIdx;
            System.arraycopy(items, firstIdx, newArray, 0, numElementsToEndOfArray);
            System.arraycopy(items, 0, newArray, numElementsToEndOfArray, nextIdx);
            oldSize = numElementsToEndOfArray + nextIdx;
        } else {
            // This will happen if firstIdx == 0
            System.arraycopy(items, firstIdx, newArray, 0, nextIdx - firstIdx);
            oldSize = nextIdx - firstIdx;
        }

        items = newArray;
        // Update our indices into that array.
        firstIdx = 0;
        nextIdx = oldSize;
    }

    /**
     * Returns the number of elements that the array backing this can hold.
     * This is NOT necessarily the number of elements presently in the buffer.
     * Useful for testing that the implementation works correctly, and for figuring out if an add{First, Last} will
     * cause a re-allocation.
     */
    public int capacity() {
        return items.length - 1;
    }

    final int inc(int i) {
        return ++i % items.length;
    }

    final int dec(int i) {
        return (--i + items.length) % items.length;
    }

    final void checkArrayCapacity() {
        if (inc(nextIdx) == firstIdx)
            doubleBackingArraySize();
    }

    /**
     * The number of elements currently held in the buffer.
     */
    public int size() {
        if (firstIdx <= nextIdx) {
            return nextIdx - firstIdx;
        } else {
            return (items.length - firstIdx + nextIdx);
        }
    }

    public void addLast(T x) {
        checkArrayCapacity();
        items[nextIdx] = x;
        nextIdx = inc(nextIdx);
    }

    public void addFirst(T x) {
        checkArrayCapacity();
        firstIdx = dec(firstIdx);
        items[firstIdx] = x;
    }

    public void removeFirst() {
        if (size() == 0)
            throw new NoSuchElementException();

        firstIdx = inc(firstIdx);
    }

    public void removeLast() {
        if (size() == 0)
            throw new NoSuchElementException();

        nextIdx = dec(nextIdx);
    }

    public T getFromFirst(int i) {
        if (i < 0 || i >= size())
            throw new NoSuchElementException();

        //noinspection unchecked
        return (T) items[(firstIdx + i) % items.length];
    }

    public T getFromLast(int i) {
        if (i < 0 || i >= size()) {
            throw new NoSuchElementException();
        } else {
            int idx = nextIdx - 1 - i;
            if (idx < 0) {
                idx += items.length;
            }
            //noinspection unchecked
            return (T) items[idx];
        }
    }
}
