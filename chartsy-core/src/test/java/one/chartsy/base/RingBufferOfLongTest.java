/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingBufferOfLongTest {

	@Test
	void testEmptyBuffer() {
		var empty = new RingBuffer.OfLong();

		assertTrue(empty.isEmpty(), "Buffer should be empty");
		assertFalse(empty.isFull(), "Buffer shouldn't be full");
		assertEquals(0, empty.length());
		assertEquals(RingBuffer.DEFAULT_CAPACITY, empty.remainingCapacity());
		assertThrows(IndexOutOfBoundsException.class, () -> empty.get(0));
		assertEquals(0, empty.stream().count());
		assertEquals(0, empty.stream().parallel().count());
		assertArrayEquals(new long[0], empty.toPrimitiveArray());
		assertFalse(empty.iterator().hasNext(), "Buffer's Iterator should have no elements");
	}

	@Test
	void testAddElement() {
		var buffer = new RingBuffer.OfLong(3);
		buffer.add(1);

		assertFalse(buffer.isEmpty());
		assertFalse(buffer.isFull());
		assertEquals(1, buffer.length());
		assertEquals(1, buffer.get(0));
		assertEquals(1, buffer.iterator().next());
		assertEquals(1, buffer.stream().count());
		assertEquals(1, buffer.stream().findFirst().orElseThrow());
		assertEquals(1, buffer.stream().parallel().findFirst().orElseThrow());
		assertArrayEquals(new long[] {1}, buffer.toPrimitiveArray());
		var content = new ArrayList<Long>();
		buffer.forEach((long v) -> content.add(v));
		assertEquals(List.of(1L), content);
	}

	@Test
	void testGetOutOfBounds() {
		var buffer = new RingBuffer.OfLong(3);
		buffer.add(1);
		buffer.add(2);
		assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(-1));
		assertThrows(IndexOutOfBoundsException.class, () -> buffer.get(2));
	}

	@Test
	void testGetOutOfCapacity() {
		@SuppressWarnings("UnnecessaryLocalVariable")
		final int CAPACITY = 3, INDEX_ONE_PAST_LAST = CAPACITY;
		var buffer = new RingBuffer.OfLong(CAPACITY);
		buffer.add(1);
		buffer.add(2);
		var exception = assertThrows(BufferTooSmallException.class, () -> buffer.get(INDEX_ONE_PAST_LAST));
		assertEquals(buffer.capacity, exception.getActualCapacity());
		assertEquals(INDEX_ONE_PAST_LAST + 1, exception.getRequiredCapacity());
	}

	@Test
	void testOverwrite() {
		var buffer = new RingBuffer.OfLong(3);
		buffer.add(1);
		buffer.add(2);
		buffer.add(3);
		buffer.add(4); // Overwrites 1
		assertEquals(3, buffer.length());
		assertEquals(2, buffer.get(2)); // Oldest element
		assertEquals(4, buffer.get(0)); // Newest element
	}

	@Test
	void testWrapAround() {
		var buffer = new RingBuffer.OfLong(2);
		buffer.add(1);
		buffer.add(2);
		buffer.add(3); // Overwrites 1
		buffer.add(4); // Overwrites 2
		buffer.add(5); // Overwrites 3
		assertEquals(2, buffer.length());
		assertEquals(4, buffer.get(1));
		assertEquals(5, buffer.get(0));
	}

	@Test
	void testSet() {
		final int CAPACITY = 5, NUM_ELEMENTS = 3;
		var buffer = new RingBuffer.OfLong(CAPACITY);
		for (int i = 0; i < NUM_ELEMENTS; i++) {
			buffer.add(i);
		}

		buffer.set(1, 100);
		assertEquals(100, buffer.get(1));
		assertThrows(IndexOutOfBoundsException.class, () -> buffer.set(-1, 0), "Setting out of bounds should throw an exception");
		assertThrows(IndexOutOfBoundsException.class, () -> buffer.set(NUM_ELEMENTS, 0), "Setting out of bounds should throw an exception");
		assertThrows(BufferTooSmallException.class, () -> buffer.set(CAPACITY, 0), "Setting out of capacity should throw an exception");
	}

	@Test
	void testClear() {
		var buffer = new RingBuffer.OfLong(2);
		buffer.add(1);
		buffer.clear();
		assertTrue(buffer.isEmpty(), "Buffer should be empty after `clear`");
	}

	@Test
	void testNonPositiveCapacity() {
		assertThrows(IllegalArgumentException.class, () -> new RingBuffer.OfLong(-1));
		assertThrows(IllegalArgumentException.class, () -> new RingBuffer.OfLong(0));
	}

	@Test
	void testForEach() {
		var buffer = new RingBuffer.OfLong(3);
		buffer.add(1);
		buffer.add(2);
		buffer.add(3); // Buffer is now full

		var elements = new ArrayList<Long>();
		buffer.forEach((long v) -> elements.add(v));

		assertEquals(List.of(1L, 2L, 3L), elements);

		// Overwrite and test again
		buffer.add(4);
		elements.clear();
		buffer.forEach((long v) -> elements.add(v));

		assertEquals(List.of(2L, 3L, 4L), elements);
	}

	@Test
	void testSpliterator() {
		var buffer = new RingBuffer.OfLong(1000);
		for (int i = 0; i < buffer.capacity(); i++)
			buffer.add(i + 1);

		List<Long> countAll = LongStream.rangeClosed(1, buffer.capacity()).boxed().toList();
		assertEquals(countAll, buffer.stream().parallel().boxed().toList());
	}
}