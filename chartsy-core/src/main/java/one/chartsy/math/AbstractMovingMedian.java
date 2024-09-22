/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.math;

/**
 * An abstract class for computing the moving median over a sliding window of values.
 * This class contains the common logic and structure for different data types.
 *
 * @author Mariusz Bernacki
 */
abstract class AbstractMovingMedian {
    /** The index into the heap for each value. */
    protected final int[] pos;
    /** The max/median/min heap holding indexes into the data array. */
    protected final int[] heap;
    /** The index of the middle heap element, representing the median. */
    protected final int middle;
    /** The allocated size of the moving median window. */
    protected final int size;
    /** The current position in the circular queue. */
    protected int index;
    /** The count of items currently in the queue. */
    protected int count;

    /**
     * Constructs a new {@code AbstractMovingMedian} with the specified window size.
     *
     * @param windowSize the size of the moving median window
     * @throws IllegalArgumentException if {@code windowSize} is less than 1
     */
    public AbstractMovingMedian(int windowSize) {
        if (windowSize < 1) {
            throw new IllegalArgumentException("Window size must be at least 1");
        }
        pos = new int[windowSize];
        heap = new int[windowSize];
        middle = windowSize / 2;
        size = windowSize;
        // Set up initial heap fill pattern: median, max, min, max, ...
        int index = windowSize;
        while (index-- > 0) {
            pos[index] = (1 - 2 * (index & 1)) * ((index + 1) / 2);
            heap[middle + pos[index]] = index;
        }
    }

    /**
     * Returns the number of items in the min-heap.
     *
     * @return the number of items in the min-heap
     */
    protected int minHeapCount() {
        return (count - 1) / 2;
    }

    /**
     * Returns the number of items in the max-heap.
     *
     * @return the number of items in the max-heap
     */
    protected int maxHeapCount() {
        return count / 2;
    }

    /**
     * Swaps two elements in the heap and updates their positions in the {@code pos} array.
     *
     * @param i the index of the first element to swap
     * @param j the index of the second element to swap
     */
    protected void swap(int i, int j) {
        int t = heap[middle + i];
        heap[middle + i] = heap[middle + j];
        heap[middle + j] = t;
        pos[heap[middle + i]] = i;
        pos[heap[middle + j]] = j;
    }

    /**
     * Swaps two elements in the heap if necessary.
     *
     * @param i the index of the first element
     * @param j the index of the second element
     * @return {@code true} if the elements were swapped, {@code false} otherwise
     */
    protected boolean sort(int i, int j) {
        if (compareAt(i, j) < 0) {
            swap(i, j);
            return true;
        }
        return false;
    }

    /**
     * Maintains the min-heap property for all items below {@code i / 2}.
     *
     * @param i the index to start sorting down from
     */
    protected void minHeapifyDown(int i) {
        int heapCount = minHeapCount();
        while (i <= heapCount) {
            if (i > 1 && i < heapCount && compareAt(i + 1, i) < 0) {
                ++i;
            }
            if (!sort(i, i / 2)) {
                break;
            }
            i *= 2;
        }
    }

    /**
     * Maintains the max-heap property for all items below {@code i / 2}.
     *
     * @param i the index to start sorting down from
     */
    protected void maxHeapifyDown(int i) {
        int heapCount = maxHeapCount();
        while (i >= -heapCount) {
            if (i < -1 && i > -heapCount && compareAt(i, i - 1) < 0) {
                --i;
            }
            if (!sort(i / 2, i)) {
                break;
            }
            i *= 2;
        }
    }

    /**
     * Maintains the min-heap property for all items above {@code i}, including the median.
     *
     * @param i the index to start sorting up from
     * @return {@code true} if the median changed, {@code false} otherwise
     */
    protected boolean minHeapifyUp(int i) {
        while (i > 0 && sort(i, i / 2)) {
            i /= 2;
        }
        return (i == 0);
    }

    /**
     * Maintains the max-heap property for all items above {@code i}, including the median.
     *
     * @param i the index to start sorting up from
     * @return {@code true} if the median changed, {@code false} otherwise
     */
    protected boolean maxHeapifyUp(int i) {
        while (i < 0 && sort(i / 2, i)) {
            i /= 2;
        }
        return (i == 0);
    }

    /**
     * Inserts a new value into the moving median window and updates the median.
     * This method contains the common logic for managing the heaps.
     *
     * @param p             the position in the heap
     * @param isFull        whether the window is full
     * @param compareOldNew the result of comparing the old and new values (old vs. new)
     */
    protected void acceptValue(int p, boolean isFull, int compareOldNew) {
        if (p > 0) { // new item is in min-heap
            if (isFull && compareOldNew < 0)
                minHeapifyDown(p * 2);
            else if (minHeapifyUp(p))
                maxHeapifyDown(-1);
        } else if (p < 0) { // new item is in max-heap
            if (isFull && compareOldNew > 0)
                maxHeapifyDown(p * 2);
            else if (maxHeapifyUp(p))
                minHeapifyDown(1);
        } else { // new item is at the median
            if (maxHeapCount() > 0)
                maxHeapifyDown(-1);
            if (minHeapCount() > 0)
                minHeapifyDown(1);
        }
    }

    /**
     * Checks if the moving median window is empty.
     *
     * @return {@code true} if the window is empty, {@code false} otherwise
     */
    public boolean isEmpty() {
        return count == 0;
    }

    /**
     * Checks if the moving median window is full.
     *
     * @return {@code true} if the window is full, {@code false} otherwise
     */
    public boolean isFull() {
        return count == size;
    }

    /**
     * Compares two elements in the heap.
     *
     * @param i the index of the first element to compare
     * @param j the index of the second element to compare
     * @return negative if first is less than second, zero if equal, positive if greater
     */
    protected abstract int compareAt(int i, int j);

}
