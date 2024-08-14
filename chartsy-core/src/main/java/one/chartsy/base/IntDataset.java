/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base;

import one.chartsy.base.dataset.AbstractDataset;
import one.chartsy.base.dataset.AbstractDoubleDataset;
import one.chartsy.base.dataset.AbstractIntDataset;
import one.chartsy.base.dataset.AbstractLongDataset;
import one.chartsy.base.dataset.ImmutableIntDataset;

import java.util.Objects;
import java.util.Spliterator;
import java.util.function.IntFunction;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntToLongFunction;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * An ordered sequence of integer-type values.
 *
 * @author Mariusz Bernacki
 */
public interface IntDataset extends PrimitiveDataset<Integer, IntDataset, Spliterator.OfInt> {

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
	int get(int index);

	default IntStream stream() {
		return StreamSupport.intStream(spliterator(), false);
	}

	default IntDataset toImmutable() {
		return ImmutableIntDataset.from(this);
	}

	/**
	 * Returns an array containing the elements of this dataset in matching indexing order.
	 *
	 * @return an array containing the elements of this dataset
	 */
	default int[] toArray() {
		int[] array = new int[length()];
		for (int i = 0; i < array.length; i++)
			array[i] = get(i);
		return array;
	}

	default IntDataset drop(int maxCount) {
		if (maxCount == 0)
			return this;
		if (maxCount < 0)
			throw new IllegalArgumentException("Argument `maxCount` (" + maxCount + ") cannot be negative");

		return AbstractIntDataset.from(this, dataset -> Math.max(0, dataset.length() - maxCount),
				(dataset, index) -> dataset.get(index + maxCount),
				dataset -> dataset.getOrder().drop(maxCount, dataset.stream(), dataset));
	}

	default IntDataset take(int maxCount) {
		if (maxCount <= 0)
			throw new IllegalArgumentException("The `maxCount` argument must be positive");

		return AbstractIntDataset.from(this, dataset -> Math.min(dataset.length(), maxCount),
				(dataset, index) -> dataset.get(Objects.checkIndex(index, maxCount)),
				dataset -> dataset.getOrder().take(maxCount, dataset.stream(), dataset));
	}

	default IntDataset takeExact(int count) {
		if (count > length())
			throw new IllegalArgumentException("The `takeExact` end index cannot exceed dataset length " + length());

		return take(count).toImmutable();
	}

	default IntDataset dropTake(int fromIndex, int maxCount) {
		if (fromIndex < 0)
			throw new IllegalArgumentException("The `fromIndex` (" + fromIndex + ") argument must be non-negative");
		if (maxCount <= 0)
			throw new IllegalArgumentException("The `maxCount` (" + maxCount + ") argument must be positive");

		return AbstractIntDataset.from(this, dataset -> Math.max(0, Math.min(dataset.length() - fromIndex, maxCount)),
				(dataset, index) -> dataset.get(fromIndex + Objects.checkIndex(index, maxCount)),
				dataset -> dataset.getOrder().dropTake(fromIndex, maxCount, dataset.stream(), dataset)
		);
	}

	default IntDataset dropTakeExact(int fromIndex, int count) {
		if (length() < count - fromIndex)
			throw new IllegalArgumentException("The `dropTakeExact` end index cannot exceed dataset length " + length());

		return dropTake(fromIndex, count).toImmutable();
	}

	default IntDataset map(IntUnaryOperator mapper) {
		Objects.requireNonNull(mapper);
		return AbstractIntDataset.from(this,
				(dataset, index) -> mapper.applyAsInt(dataset.get(index)),
				dataset -> dataset.stream().map(mapper));
	}

	default DoubleDataset mapToDouble(IntToDoubleFunction mapper) {
		Objects.requireNonNull(mapper);
		return AbstractDoubleDataset.from(this,
				(dataset, index) -> mapper.applyAsDouble(dataset.get(index)),
				dataset -> dataset.stream().mapToDouble(mapper));
	}

	default LongDataset mapToLong(IntToLongFunction mapper) {
		Objects.requireNonNull(mapper);
		return AbstractLongDataset.from(this,
				(dataset, index) -> mapper.applyAsLong(dataset.get(index)),
				dataset -> dataset.stream().mapToLong(mapper));
	}

	default <E> Dataset<E> mapToObject(IntFunction<E> mapper) {
		Objects.requireNonNull(mapper);
		return AbstractDataset.from(this,
				(dataset, index) -> mapper.apply(dataset.get(index)),
				dataset -> dataset.stream().mapToObj(mapper));
	}

	default IntDataset plus(int x) {
		return map(v -> v + x);
	}

	default IntDataset sub(int x) {
		return plus(-x);
	}

	default IntDataset mul(int x) {
		return map(v -> v * x);
	}

	default IntDataset div(int x) {
		return map(v -> v / x);
	}

	default IntDataset differences() {
		return AbstractIntDataset.from(this, dataset -> Math.max(0, dataset.length() - 1),
				(dataset, index) -> dataset.get(index) - dataset.get(index + 1));
	}

	default IntDataset ratios() {
		return AbstractIntDataset.from(this, dataset -> Math.max(0, dataset.length() - 1),
				(dataset, index) -> dataset.get(index) / dataset.get(index + 1));
	}

	default Dataset<IntDataset> subsequences(int len) {
		if (len <= 0)
			throw new IllegalArgumentException("subsequences length `" + len + "` must be positive");

		return AbstractDataset.from(this, dataset -> Math.max(0, dataset.length() - len + 1),
				(dataset, index) -> dataset.dropTake(index, len));
	}

	default Dataset<Integer> boxed() {
		return mapToObject(Integer::valueOf);
	}

	static IntDataset empty() {
		return ImmutableIntDataset.EMPTY;
	}
}
