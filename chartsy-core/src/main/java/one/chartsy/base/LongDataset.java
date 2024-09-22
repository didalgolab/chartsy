/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base;

import one.chartsy.base.dataset.AbstractDataset;
import one.chartsy.base.dataset.AbstractDoubleDataset;
import one.chartsy.base.dataset.AbstractIntDataset;
import one.chartsy.base.dataset.AbstractLongDataset;
import one.chartsy.base.dataset.ImmutableLongDataset;

import java.util.Objects;
import java.util.Spliterator;
import java.util.function.LongFunction;
import java.util.function.LongToDoubleFunction;
import java.util.function.LongToIntFunction;
import java.util.function.LongUnaryOperator;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

/**
 * An ordered sequence of long-type values.
 *
 * @author Mariusz Bernacki
 */
public interface LongDataset extends PrimitiveDataset<Long, LongDataset, Spliterator.OfLong> {

	/**
	 * Returns the value at the specified position in the dataset. Depending
	 * on the characteristic of the dataset, the index-ordering may or may not be
	 * an iterating order (see {@link #getOrder()}).
	 *
	 * @param index the index of the value to return
	 * @return the value at the specified position
	 * @throws IndexOutOfBoundsException if the index is out of range
	 *                                   (index < 0 || index >= size())
	 */
	long get(int index);

	/**
	 * Returns the number of elements in this dataset.
	 *
	 * @return the dataset's size
	 */
	@Override int length();

	default LongStream stream() {
		return StreamSupport.longStream(spliterator(), false);
	}

	default LongDataset toImmutable() {
		return ImmutableLongDataset.from(this);
	}

	/**
	 * Returns an array containing the elements of this dataset in matching indexing order.
	 *
	 * @return an array containing the elements of this dataset
	 */
	default long[] toArray() {
		long[] array = new long[length()];
		for (int i = 0; i < array.length; i++)
			array[i] = get(i);
		return array;
	}

	default LongDataset drop(int maxCount) {
		if (maxCount == 0)
			return this;
		if (maxCount < 0)
			throw new IllegalArgumentException("Argument `maxCount` (" + maxCount + ") cannot be negative");

		return AbstractLongDataset.from(this, dataset -> Math.max(0, dataset.length() - maxCount),
				(dataset, index) -> dataset.get(index + maxCount),
				dataset -> dataset.getOrder().drop(maxCount, dataset.stream(), dataset));
	}

	default LongDataset take(int maxCount) {
		if (maxCount <= 0)
			throw new IllegalArgumentException("The `maxCount` argument must be positive");

		return AbstractLongDataset.from(this, dataset -> Math.min(dataset.length(), maxCount),
				(dataset, index) -> dataset.get(Objects.checkIndex(index, maxCount)),
				dataset -> dataset.getOrder().take(maxCount, dataset.stream(), dataset));
	}

	default LongDataset takeExact(int count) {
		if (count > length())
			throw new IllegalArgumentException("The `takeExact` end index cannot exceed dataset length " + length());

		return take(count).toImmutable();
	}

	default LongDataset dropTake(int fromIndex, int maxCount) {
		if (fromIndex < 0)
			throw new IllegalArgumentException("The `fromIndex` (" + fromIndex + ") argument must be non-negative");
		if (maxCount <= 0)
			throw new IllegalArgumentException("The `maxCount` (" + maxCount + ") argument must be positive");

		return AbstractLongDataset.from(this, dataset -> Math.max(0, Math.min(dataset.length() - fromIndex, maxCount)),
				(dataset, index) -> dataset.get(fromIndex + Objects.checkIndex(index, maxCount)),
				dataset -> dataset.getOrder().dropTake(fromIndex, maxCount, dataset.stream(), dataset)
		);
	}

	default LongDataset dropTakeExact(int fromIndex, int count) {
		if (length() < count - fromIndex)
			throw new IllegalArgumentException("The `dropTakeExact` end index cannot exceed dataset length " + length());

		return dropTake(fromIndex, count).toImmutable();
	}

	default LongDataset map(LongUnaryOperator mapper) {
		Objects.requireNonNull(mapper);
		return AbstractLongDataset.from(this,
				(dataset, index) -> mapper.applyAsLong(dataset.get(index)),
				dataset -> dataset.stream().map(mapper));
	}

	default DoubleDataset mapToDouble(LongToDoubleFunction mapper) {
		Objects.requireNonNull(mapper);
		return AbstractDoubleDataset.from(this,
				(dataset, index) -> mapper.applyAsDouble(dataset.get(index)),
				dataset -> dataset.stream().mapToDouble(mapper));
	}

	default IntDataset mapToInt(LongToIntFunction mapper) {
		Objects.requireNonNull(mapper);
		return AbstractIntDataset.from(this,
				(dataset, index) -> mapper.applyAsInt(dataset.get(index)),
				dataset -> dataset.stream().mapToInt(mapper));
	}

	default <E> Dataset<E> mapToObject(LongFunction<E> mapper) {
		Objects.requireNonNull(mapper);
		return AbstractDataset.from(this,
				(dataset, index) -> mapper.apply(dataset.get(index)),
				dataset -> dataset.stream().mapToObj(mapper));
	}

	default LongDataset plus(long x) {
		return map(v -> v + x);
	}

	default LongDataset sub(long x) {
		return plus(-x);
	}

	default LongDataset mul(long x) {
		return map(v -> v * x);
	}

	default LongDataset div(long x) {
		return map(v -> v / x);
	}

	default LongDataset differences() {
		return AbstractLongDataset.from(this, dataset -> Math.max(0, dataset.length() - 1),
				(dataset, index) -> dataset.get(index) - dataset.get(index + 1));
	}

	default LongDataset ratios() {
		return AbstractLongDataset.from(this, dataset -> Math.max(0, dataset.length() - 1),
				(dataset, index) -> dataset.get(index) / dataset.get(index + 1));
	}

	default Dataset<LongDataset> subsequences(int len) {
		if (len <= 0)
			throw new IllegalArgumentException("subsequences length `" + len + "` must be positive");

		return AbstractDataset.from(this, dataset -> Math.max(0, dataset.length() - len + 1),
				(dataset, index) -> dataset.dropTake(index, len));
	}

	default Dataset<Long> boxed() {
		return mapToObject(Long::valueOf);
	}

	static LongDataset empty() {
		return ImmutableLongDataset.EMPTY;
	}
}