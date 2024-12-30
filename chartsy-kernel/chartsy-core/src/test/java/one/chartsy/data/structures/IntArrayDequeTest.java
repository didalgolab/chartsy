/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.structures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class IntArrayDequeTest {

    private IntArrayDeque deque;

    @BeforeEach
    void setUp() {
        deque = new IntArrayDeque(4);
    }

    @Test
    void isEmpty_upon_creation() {
        var emptyDeque = new IntArrayDeque(0);
        assertTrue(emptyDeque.isEmpty());
        assertEquals(0, emptyDeque.size());
    }

    @Test
    void constructor_throws_exception_for_negative_capacity() {
        assertThrows(IllegalArgumentException.class, () -> new IntArrayDeque(-1));
    }

    @Test
    void offerLast_adds_element_to_tail() {
        deque.offerLast(10);

        assertFalse(deque.isEmpty());
        assertEquals(1, deque.size());
        assertEquals(10, deque.getLast());
        assertEquals(10, deque.getFirst());
    }

    @Test
    void offerFirst_adds_element_to_head() {
        deque.offerFirst(20);

        assertFalse(deque.isEmpty());
        assertEquals(1, deque.size());
        assertEquals(20, deque.getFirst());
        assertEquals(20, deque.getLast());
    }

    @Test
    void offerLast_after_many_adds_and_pollFirst_gives_elements_in_FIFO_order() {
        deque.offerLast(30);
        deque.offerLast(40);

        assertEquals(2, deque.size());
        assertEquals(30, deque.pollFirst());
        assertEquals(1, deque.size());
        assertEquals(40, deque.getFirst());
    }

    @Test
    void offerFirst_after_many_adds_and_poll_last_gives_elements_in_LIFO_order() {
        deque.offerFirst(50);
        deque.offerFirst(60);

        assertEquals(2, deque.size());
        assertEquals(50, deque.pollLast());
        assertEquals(1, deque.size());
        assertEquals(60, deque.getLast());
    }

    @Test
    void offerLast_when_exceeds_initialCapacity_triggers_expansion() {
        // Initial capacity is 4
        deque.offerLast(1);
        deque.offerLast(2);
        deque.offerLast(3);
        deque.offerLast(4);

        assertEquals(4, deque.size(), "Size should be 4 before expansion");
        deque.offerLast(5); // This should trigger expansion
        assertEquals(5, deque.size(), "Size should be 5 after expansion");
        assertEquals(1, deque.pollFirst(), "First element should be still 1");
        assertEquals(5, deque.getLast(), "Last element should be 5");
    }

    @Test
    void clear_discards_content() {
        deque.offerFirst(1);
        deque.offerLast(2);
        deque.offerFirst(3);
        deque.clear();

        assertTrue(deque.isEmpty());
        assertEquals(0, deque.size());
        // Ensure that poll operations throw exceptions after clearing
        assertThrows(NoSuchElementException.class, () -> deque.pollFirst());
        assertThrows(NoSuchElementException.class, () -> deque.pollLast());
    }

    @Test
    void iterator_traverses_elements_in_order() {
        deque.offerLast(10);
        deque.offerLast(20);
        deque.offerFirst(5);
        deque.offerLast(30);
        // Current deque: 5,10,20,30

        var iterator = deque.iterator();
        assertTrue(iterator.hasNext(), "Iterator should have elements");
        assertEquals(5, iterator.next());
        assertEquals(10, iterator.next());
        assertEquals(20, iterator.next());
        assertEquals(30, iterator.next());
        assertFalse(iterator.hasNext(), "Iterator should have no more elements");
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void offerLast_when_adding_many_elements_handles_wraparound_properly() {
        // Add elements to exceed initial capacity and cause wrap-around
        for (int i = 1; i <= 211; i++)
            deque.offerLast(i);
        assertEquals(211, deque.size(), "Size should be 211 after adding 211 elements");

        // Verify all elements are present in order
        int expected = 1;
        for (int value : deque)
            assertEquals(expected++, value, "Element should be " + (expected - 1));
    }

    @Test
    void pollFirst_throws_exception_when_deque_is_empty() {
        assertThrows(NoSuchElementException.class, () -> deque.pollFirst());
    }

    @Test
    void pollLast_throws_exception_when_deque_is_empty() {
        assertThrows(NoSuchElementException.class, () -> deque.pollLast());
    }

    @Test
    void getFirst_throws_exception_when_deque_is_empty() {
        assertThrows(NoSuchElementException.class, () -> deque.getFirst());
    }

    @Test
    void getLast_throws_exception_when_deque_is_empty() {
        assertThrows(NoSuchElementException.class, () -> deque.getLast());
    }

    @Test
    void offerFirst_and_pollLast_can_handle_mixed_operations() {
        deque.offerLast(1);
        deque.offerFirst(2);
        deque.offerLast(3);
        deque.offerFirst(4);

        // Current deque: 4,2,1,3
        assertEquals(4, deque.size());
        assertEquals(4, deque.getFirst());
        assertEquals(3, deque.getLast());

        assertEquals(4, deque.pollFirst());
        assertEquals(3, deque.pollLast());
        // Current deque: 2,1
        assertEquals(2, deque.size());
        assertEquals(2, deque.getFirst());
        assertEquals(1, deque.getLast());
    }

    @Test
    void size_gives_correct_number_of_elements() {
        assertEquals(0, deque.size());
        deque.offerFirst(10);
        assertEquals(1, deque.size());
        deque.offerLast(20);
        assertEquals(2, deque.size());
        deque.pollFirst();
        assertEquals(1, deque.size());
        deque.pollLast();
        assertEquals(0, deque.size());
    }

    @Test
    void clear_on_non_empty_deque_empties_deque() {
        deque.offerFirst(1);
        deque.offerLast(2);
        deque.offerFirst(3);
        assertEquals(3, deque.size());

        deque.clear();
        assertTrue(deque.isEmpty());
        assertEquals(0, deque.size());
    }

    @Test
    void offerFirst_and_offerLast_after_clear_adds_elements_correctly() {
        deque.offerLast(1);
        deque.offerLast(2);
        deque.clear();
        assertTrue(deque.isEmpty(), "Deque should be empty after clearing");

        deque.offerFirst(3);
        deque.offerLast(4);
        assertEquals(2, deque.size());
        assertEquals(3, deque.getFirst());
        assertEquals(4, deque.getLast());
    }

    @Test
    void iterator_after_clear_has_no_elements() {
        deque.offerLast(10);
        deque.offerLast(20);
        deque.clear();

        var iterator = deque.iterator();
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void offerLast_when_added_large_number_of_elements_triggers_multiple_expansions() {
        int initialCapacity = 4;
        int totalElements = 1000;
        var largeDeque = new IntArrayDeque(initialCapacity);
        for (int i = 0; i < totalElements; i++) {
            largeDeque.offerLast(i);
        }
        assertEquals(totalElements, largeDeque.size(), "Size should be " + totalElements + " after adding elements");
        // Verify first and last elements
        assertEquals(0, largeDeque.getFirst(), "First element should be 0");
        assertEquals(totalElements - 1, largeDeque.getLast(), "Last element should be " + (totalElements - 1));
        // Poll some elements
        for (int i = 0; i < 500; i++) {
            assertEquals(i, largeDeque.pollFirst(), "Polled element should be " + i);
        }
        assertEquals(500, largeDeque.size(), "Size should be 500 after polling 500 elements");
        assertEquals(500, largeDeque.getFirst(), "First element should be 500");
        assertEquals(totalElements - 1, largeDeque.getLast(), "Last element should be " + (totalElements - 1));
    }

    @Test
    void iterator_handles_concurrent_modifications_correctly() {
        deque.offerLast(1);
        deque.offerLast(2);
        deque.offerFirst(0);
        // Current deque: 0,1,2

        var iterator = deque.iterator();
        assertTrue(iterator.hasNext());
        assertEquals(0, iterator.next());

        deque.offerLast(3); // Modify deque after iterator creation
        // Depending on implementation, iterator may or may not reflect this change
        // Here, our iterator was created before the addition, so it should not see '3'
        assertEquals(1, iterator.next());
        assertEquals(2, iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    void toString_gives_standardized_textual_representation() {
        assertEquals("[]", deque.toString());

        deque.offerLast(1);
        assertEquals("[1]", deque.toString());

        deque.offerFirst(2);
        assertEquals("[2, 1]", deque.toString());

        deque.offerLast(3);
        assertEquals("[2, 1, 3]", deque.toString());

        deque.pollFirst();
        assertEquals("[1, 3]", deque.toString());

        deque.pollLast();
        assertEquals("[1]", deque.toString());

        deque.clear();
        assertEquals("[]", deque.toString());
    }
}
