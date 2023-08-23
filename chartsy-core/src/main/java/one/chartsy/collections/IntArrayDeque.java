/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.collections;

import java.util.NoSuchElementException;

public class IntArrayDeque {

    /**
     * The minimum capacity that we'll use for a newly created deque.
     * Must be a power of 2.
     */
    private static final int MIN_INITIAL_CAPACITY = 8;
    
    private int[] elements;
    private int head, tail;
    
    public IntArrayDeque(int size) {
        allocateElements(size);
    }
    
    private void allocateElements(int numElements) {
        int initialCapacity = MIN_INITIAL_CAPACITY;
        // Find the best power of two to hold elements.
        // Tests "<=" because arrays aren't kept full.
        if (numElements >= initialCapacity) {
            initialCapacity = numElements;
            initialCapacity |= (initialCapacity >>>  1);
            initialCapacity |= (initialCapacity >>>  2);
            initialCapacity |= (initialCapacity >>>  4);
            initialCapacity |= (initialCapacity >>>  8);
            initialCapacity |= (initialCapacity >>> 16);
            initialCapacity++;
            
            if (initialCapacity < 0)   // Too many elements, must back off
                initialCapacity >>>= 1;// Good luck allocating 2 ^ 30 elements
        }
        elements = new int[initialCapacity];
    }
    
    public int pollLast() {
        if (isEmpty())
            throw new NoSuchElementException();
        
        int t = (tail - 1) & (elements.length - 1);
        int result = elements[t];
        tail = t;
        return result;
    }
    
    public boolean offerLast(int e) {
        elements[tail] = e;
        if ((tail = (tail + 1) & (elements.length - 1)) == head)
            expandCapacity();
        return true;
    }
    
    public int getFirst() {
        if (isEmpty())
            throw new NoSuchElementException();
        
        return elements[head];
    }
    
    /**
     * @throws NoSuchElementException {@inheritDoc}
     */
    public int getLast() {
        if (isEmpty())
            throw new NoSuchElementException();

        return elements[(tail - 1) & (elements.length - 1)];
    }
    
    public int pollFirst() {
        if (isEmpty())
            throw new NoSuchElementException();
        
        int h = head;
        int result = elements[h];
        head = (h + 1) & (elements.length - 1);
        return result;
    }
    
    public boolean isEmpty() {
        return head == tail;
    }
    
    private void expandCapacity() {
        assert head == tail;
        int p = head;
        int n = elements.length;
        int r = n - p; // number of elements to the right of p
        int newCapacity = n << 1;
        if (newCapacity < 0)
            throw new IllegalStateException("Sorry, deque too big");

        int[] a = new int[newCapacity];
        System.arraycopy(elements, p, a, 0, r);
        System.arraycopy(elements, 0, a, r, p);
        elements = a;
        head = 0;
        tail = n;
    }
}