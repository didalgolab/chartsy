/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.structures;

import one.chartsy.base.SequenceAlike;

public abstract class AbstractRingBuffer implements SequenceAlike {

	protected final int capacity;
	protected final int mask;
	protected long nextWrite;


	protected AbstractRingBuffer(int capacity) {
		if (capacity <= 0)
			throw new IllegalArgumentException("Capacity must be positive");

		this.capacity = capacity;
		this.mask = nextPowerOfTwo(capacity) - 1;
	}

	static int nextPowerOfTwo(int x) {
		return 1 << -Integer.numberOfLeadingZeros(x - 1);
	}

	@Override
	public final SequenceAlike.Order getOrder() {
		return SequenceAlike.Order.INDEX_DESC;
	}

	/**
	 * Gets the number of elements currently held in the buffer.
	 *
	 * @return the number of elements held in the buffer
	 */
	@Override
	public int length() {
		return (int) Math.min(capacity(), nextWrite);
	}

	/**
	 * Is the buffer currently empty?
	 */
	@Override
	public boolean isEmpty() {
		return length() == 0;
	}

	/**
	 * Returns the buffer's capacity, or {@code Integer.MAX_VALUE} if there is no intrinsic limit.
	 *
	 * @return the buffer's capacity
	 */
	public int capacity() {
		return capacity;
	}

	/**
	 * Returns the number of additional elements that this queue can ideally
	 * accept without discarding the history.
	 *
	 * @return the remaining capacity
	 */
	public int remainingCapacity() {
		int capacity = capacity();
		return (capacity == Integer.MAX_VALUE) ? Integer.MAX_VALUE : capacity - length();
	}

	/**
	 * Is the buffer currently empty?
	 */
	public boolean isFull() {
		return length() == capacity();
	}

	/**
	 * Remove all data from the buffer
	 */
	public abstract void clear();

	protected final int arrayIndex(int offset) {
		return ((int)nextWrite - offset - 1) & mask;
	}

	protected final void checkIndex(int index) {
		if (index >= capacity())
			throw new BufferTooSmallException("RingBuffer too small", capacity(), index + 1);
		if (index < 0 || index >= length())
			throw new IndexOutOfBoundsException(index);
	}
}
