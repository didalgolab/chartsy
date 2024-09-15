/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.collections;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * A custom efficient deque (double-ended queue) implementation specialized for primitive integers.
 * This implementation avoids the overhead of boxing and unboxing {@code Integer} objects,
 * making it more efficient than {@code ArrayDeque<Integer>}.
 *
 * <p>This class provides methods to add, remove, and inspect elements at both ends of the deque.
 * It uses a circular buffer internally and automatically expands its capacity when full.
 *
 * <p><strong>Note:</strong> This implementation is not thread-safe. If multiple threads access
 * an {@code IntArrayDeque} instance concurrently, external synchronization is required.
 *
 * @author Mariusz Bernacki
 */
public class IntArrayDeque implements Iterable<Integer> {

    /**
     * The minimum capacity for the internal array.
     * Must be a power of two.
     */
    private static final int MIN_INITIAL_CAPACITY = 8;

    /**
     * The internal array for storing elements.
     */
    private int[] elements;

    /**
     * The index of the first (head) element.
     */
    private int head;

    /**
     * The index following the last (tail) element.
     */
    private int tail;

    /**
     * Constructs an empty deque with the specified initial capacity.
     *
     * @param initialCapacity the initial capacity of the deque
     * @throws IllegalArgumentException if the specified initial capacity is negative
     */
    public IntArrayDeque(int initialCapacity) {
        if (initialCapacity < 0) {
            throw new IllegalArgumentException("Initial capacity must be non-negative, but was: " + initialCapacity);
        }
        allocateElements(initialCapacity);
    }

    /**
     * Allocates the internal array with a capacity that is a power of two
     * and at least equal to the specified number of elements.
     *
     * @param numElements the required minimum capacity
     */
    private void allocateElements(int numElements) {
        int initialCapacity = MIN_INITIAL_CAPACITY;
        if (numElements > initialCapacity) {
            initialCapacity = Integer.highestOneBit(numElements);
            if (initialCapacity < numElements)
                initialCapacity <<= 1;
            if (initialCapacity < 0) // Overflow occurred
                initialCapacity >>>= 1;
        }
        elements = new int[initialCapacity];
    }

    /**
     * Inserts the specified element at the front of this deque.
     *
     * @param e the element to add
     */
    public void offerFirst(int e) {
        head = mask(head - 1);
        elements[head] = e;
        if (head == tail) {
            expandCapacity();
        }
    }

    /**
     * Inserts the specified element at the end of this deque.
     *
     * @param e the element to add
     */
    public void offerLast(int e) {
        elements[tail] = e;
        tail = mask(tail + 1);
        if (tail == head) {
            expandCapacity();
        }
    }

    /**
     * Retrieves and removes the first element of this deque.
     *
     * @return the head of this deque
     * @throws NoSuchElementException if this deque is empty
     */
    public int pollFirst() {
        if (isEmpty()) {
            throw new NoSuchElementException("Deque is empty");
        }
        int result = elements[head];
        head = mask(head + 1);
        return result;
    }

    /**
     * Retrieves and removes the last element of this deque.
     *
     * @return the tail of this deque
     * @throws NoSuchElementException if this deque is empty
     */
    public int pollLast() {
        if (isEmpty()) {
            throw new NoSuchElementException("Deque is empty");
        }
        tail = mask(tail - 1);
        return elements[tail];
    }

    /**
     * Retrieves, but does not remove, the first element of this deque.
     *
     * @return the head of this deque
     * @throws NoSuchElementException if this deque is empty
     */
    public int getFirst() {
        if (isEmpty()) {
            throw new NoSuchElementException("Deque is empty");
        }
        return elements[head];
    }

    /**
     * Retrieves, but does not remove, the last element of this deque.
     *
     * @return the tail of this deque
     * @throws NoSuchElementException if this deque is empty
     */
    public int getLast() {
        if (isEmpty()) {
            throw new NoSuchElementException("Deque is empty");
        }
        return elements[mask(tail - 1)];
    }

    /**
     * Returns {@code true} if this deque contains no elements.
     *
     * @return {@code true} if this deque is empty
     */
    public boolean isEmpty() {
        return head == tail;
    }

    /**
     * Returns the number of elements in this deque.
     *
     * @return the number of elements
     */
    public int size() {
        return (tail - head) & (elements.length - 1);
    }

    /**
     * Clears all elements from the deque.
     * After this call, {@code isEmpty()} will return {@code true}.
     */
    public void clear() {
        head = 0;
        tail = 0;
    }

    /**
     * Doubles the capacity of the internal array.
     *
     * @throws IllegalStateException if the deque is too large to expand
     */
    private void expandCapacity() {
        assert head == tail : "expandCapacity called when deque is not full";
        int oldCapacity = elements.length;
        int newCapacity = oldCapacity << 1;
        if (newCapacity < 0) { // Overflow
            throw new IllegalStateException("Deque too big");
        }
        int[] newArray = new int[newCapacity];
        int rightElements = oldCapacity - head;
        System.arraycopy(elements, head, newArray, 0, rightElements);
        System.arraycopy(elements, 0, newArray, rightElements, head);
        elements = newArray;
        head = 0;
        tail = oldCapacity;
    }

    /**
     * Applies bitmasking to wrap the index within the bounds of the internal array.
     *
     * @param index the original index
     * @return the masked index
     */
    private int mask(int index) {
        return index & (elements.length - 1);
    }

    /**
     * Returns an iterator over the elements in this deque from front to back.
     *
     * @return an iterator over the elements in this deque
     */
    @Override
    public Iterator<Integer> iterator() {
        return new Iterator<>() {
            private int current = head;
            private int remaining = size();

            @Override
            public boolean hasNext() {
                return remaining > 0;
            }

            @Override
            public Integer next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                int result = elements[current];
                current = mask(current + 1);
                remaining--;
                return result;
            }
        };
    }
}
