/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base;

import one.chartsy.base.dataset.AbstractDataset;
import one.chartsy.base.dataset.AbstractDoubleDataset;
import one.chartsy.base.dataset.AbstractIntDataset;
import one.chartsy.base.dataset.AbstractLongDataset;
import one.chartsy.base.dataset.ImmutableDoubleDataset;
import one.chartsy.base.function.DoubleBiPredicate;

import java.util.Objects;
import java.util.Spliterator;
import java.util.function.DoubleFunction;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleToLongFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.DoubleStream;
import java.util.stream.StreamSupport;

/**
 * An ordered sequence of double-type values.
 *
 * @author Mariusz Bernacki
 */
public interface DoubleDataset extends PrimitiveDataset<Double, DoubleDataset, Spliterator.OfDouble> {

	/**
	 * Returns the value at the specified position in the dataset.  Depending
	 * on the characteristic of the dataset, the index-ordering may or may not be
	 * an iterating order (see {@link #getOrder()}).
	 *
	 * @param index the index of the value to return
	 * @return the value at the specified position
	 * @throws IndexOutOfBoundsException if the index is out of range
	 *                                   (index < 0 || index >= size())
	 */
	double get(int index);

    /**
     * Returns a dataset indicating where the series crosses over the specified value.
     *
     * @param value the value to check crossings against
     * @return a {@code Dataset} of {@code Boolean} indicating crossings over the value
     */
    default Dataset<Boolean> crossesOver(double value) {
        return crosses((prev, curr) -> prev <= value && curr > value);
    }

    /**
     * Returns a dataset indicating where the series crosses under the specified value.
     *
     * @param value the value to check crossings against
     * @return a {@code Dataset} of {@code Boolean} indicating crossings over the value
     */
    default Dataset<Boolean> crossesUnder(double value) {
        return crosses((prev, curr) -> prev >= value && curr < value);
    }

    /**
     * Determines the dataset's crossings based on the provided crossing condition.
     *
     * @param crossingCondition a {@code DoubleBiPredicate} that defines the crossing condition
     * @return a {@code Dataset} of {@code Boolean} indicating crossings based on the condition
     */
    Dataset<Boolean> crosses(DoubleBiPredicate crossingCondition);

	default DoubleStream stream() {
		return StreamSupport.doubleStream(spliterator(), false);
	}

    default DoubleDataset toImmutable() {
        return ImmutableDoubleDataset.from(this);
    }

    /**
     * Returns an array containing the elements of this dataset in matching indexing order.
     *
     * @return an array containing the elements of this dataset
     */
    default double[] toArray() {
        double[] array = new double[length()];
        for (int i = 0; i < array.length; i++)
            array[i] = get(i);
        return array;
    }

    default DoubleDataset drop(int maxCount) {
        if (maxCount == 0)
            return this;
        if (maxCount < 0)
            throw new IllegalArgumentException("Argument `maxCount` (" + maxCount + ") cannot be negative");

        return AbstractDoubleDataset.from(this, dataset -> Math.max(0, dataset.length() - maxCount),
                (dataset, index) -> dataset.get(index + maxCount),
                dataset -> dataset.getOrder().drop(maxCount, dataset.stream(), dataset));
    }

    default DoubleDataset take(int maxCount) {
        if (maxCount <= 0)
            throw new IllegalArgumentException("The `maxCount` argument must be positive");

        return AbstractDoubleDataset.from(this, dataset -> Math.min(dataset.length(), maxCount),
                (dataset, index) -> dataset.get(Objects.checkIndex(index, maxCount)),
                dataset -> dataset.getOrder().take(maxCount, dataset.stream(), dataset));
    }

    default DoubleDataset takeExact(int count) {
        if (count > length())
            throw new IllegalArgumentException("The `takeExact` end index cannot exceed dataset length " + length());

        return take(count).toImmutable();
    }

    default DoubleDataset dropTake(int fromIndex, int maxCount) {
        if (fromIndex < 0)
            throw new IllegalArgumentException("The `fromIndex` (" + fromIndex + ") argument must be non-negative");
        if (maxCount <= 0)
            throw new IllegalArgumentException("The `maxCount` (" + maxCount + ") argument must be positive");

        return AbstractDoubleDataset.from(this,
                dataset -> Math.max(0, Math.min(dataset.length() - fromIndex, maxCount)),
                (dataset, index) -> dataset.get(fromIndex + Objects.checkIndex(index, maxCount)),
                dataset -> dataset.getOrder().dropTake(fromIndex, maxCount, dataset.stream(), dataset)
        );
    }

    default DoubleDataset dropTakeExact(int fromIndex, int count) {
        if (length() < count - fromIndex)
            throw new IllegalArgumentException("The `dropTakeExact` end index cannot exceed dataset length " + length());

        return dropTake(fromIndex, count).toImmutable();
    }

    default DoubleDataset map(DoubleUnaryOperator mapper) {
        Objects.requireNonNull(mapper);
        return AbstractDoubleDataset.from(this,
                (dataset, index) -> mapper.applyAsDouble(dataset.get(index)),
                dataset -> dataset.stream().map(mapper));
    }

    default IntDataset mapToInt(DoubleToIntFunction mapper) {
        Objects.requireNonNull(mapper);
        return AbstractIntDataset.from(this,
                (dataset, index) -> mapper.applyAsInt(dataset.get(index)),
                dataset -> dataset.stream().mapToInt(mapper));
    }

    default LongDataset mapToLong(DoubleToLongFunction mapper) {
        Objects.requireNonNull(mapper);
        return AbstractLongDataset.from(this,
                (dataset, index) -> mapper.applyAsLong(dataset.get(index)),
                dataset -> dataset.stream().mapToLong(mapper));
    }

    default <E> Dataset<E> mapToObject(DoubleFunction<E> mapper) {
        Objects.requireNonNull(mapper);
        return AbstractDataset.from(this,
                (dataset, index) -> mapper.apply(dataset.get(index)),
                dataset -> dataset.stream().mapToObj(mapper));
    }

    default DoubleDataset plus(double x) {
        return map(v -> v + x);
    }

    default DoubleDataset sub(double x) {
        return plus(-x);
    }

    default DoubleDataset mul(double x) {
        return map(v -> v * x);
    }

    default DoubleDataset div(double x) {
        return map(v -> v / x);
    }

    default DoubleDataset differences() {
        return AbstractDoubleDataset.from(this, dataset -> Math.max(0, dataset.length()-1),
                (dataset, index) -> dataset.get(index) - dataset.get(index + 1));
    }

    default DoubleDataset ratios() {
        return AbstractDoubleDataset.from(this, dataset -> Math.max(0, dataset.length()-1),
                (dataset, index) -> dataset.get(index) / dataset.get(index + 1));
    }

    default Dataset<DoubleDataset> subsequences(int len) {
        if (len <= 0)
            throw new IllegalArgumentException("subsequences length `" + len + "` must be positive");

        return AbstractDataset.from(this, dataset -> Math.max(0, dataset.length() - len + 1),
                (dataset, index) -> dataset.dropTake(index, len));
    }

    default Dataset<Double> boxed() {
        return mapToObject(Double::valueOf);
    }

    /**
     * Calculates the fractal dimension index (FDI) at a specific position in the series.
     *
     * @param n the number of periods to use in the calculation
     * @param index the index at which to calculate the FDI
     * @return the FDI value at the specified index
     * @throws IllegalArgumentException if periods is not positive or index is out of bounds
     */
    default double fdi(int n, int index) {
        if (n <= 0)
            throw new IllegalArgumentException("The period-n argument " + n + " must be positive");

        double max = Double.NEGATIVE_INFINITY, min = Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            max = Math.max(max, get(index + i));
            min = Math.min(min, get(index + i));
        }
        if (max - min == 0.0)
            return 1.5; // Default value when there's no price movement

        double prev = 0.0;
        double length = 0.0;
        double nPow = 1.0 / Math.pow(n - 1.0, 2.0);
        for (int i = 0; i < n; i++) {
            double diff = (get(index + i) - min) / (max - min);
            if (i > 0) {
                length += Math.sqrt((diff - prev) * (diff - prev) + nPow);
            }
            prev = diff;
        }

        return 1.0 + (Math.log(length) + Math.log(2)) / Math.log(2.0 * (n - 1));
    }

    static DoubleDataset empty() {
        return ImmutableDoubleDataset.EMPTY;
    }
}
