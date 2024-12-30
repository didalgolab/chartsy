/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base;

import one.chartsy.base.dataset.AbstractDataset;
import one.chartsy.base.dataset.AbstractDoubleDataset;
import one.chartsy.base.dataset.AbstractIntDataset;
import one.chartsy.base.dataset.AbstractLongDataset;
import one.chartsy.base.dataset.ImmutableDataset;
import one.chartsy.util.Pair;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Stream;

/**
 * An ordered sequence of arbitrary-type data elements.
 *
 * @author Mariusz Bernacki
 *
 * @param <E> the type of elements stored in this dataset
 */
public interface Dataset<E> extends Iterable<E>, SequenceAlike {

    /**
     * Returns the element at the specified position in the dataset.  Depending
     * on the characteristic of the dataset, the index-ordering may or may not be
     * an iterating order (see {@link #getOrder()}).
     *
     * @param index the index of the element to return
     * @return the element at the specified position
     * @throws IndexOutOfBoundsException if the index is out of range
     *                                   (index < 0 || index >= size())
     */
    E get(int index);

    /**
     * Returns the number of elements in this dataset.
     *
     * @return the dataset's size
     */
    @Override int length();

    Stream<E> stream();

    @Override Iterator<E> iterator();

    @Override Spliterator<E> spliterator();

    default Dataset<E> toImmutable() {
        return ImmutableDataset.from(this);
    }

    default List<E> toImmutableList() {
        List<E> list = new ArrayList<>(length());
        forEach(list::add);
        return Collections.unmodifiableList(list);
    }

    default List<E> values() {
        return new Values<>(this);
    }

    default Dataset<E> drop(int maxCount) {
        if (maxCount == 0)
            return this;
        if (maxCount < 0)
            throw new IllegalArgumentException("Argument `maxCount` (" + maxCount + ") cannot be negative");

        return AbstractDataset.from(this, dataset -> Math.max(0, dataset.length() - maxCount),
                (dataset, index) -> dataset.get(index + maxCount),
                dataset -> dataset.getOrder().drop(maxCount, dataset.stream(), dataset));
    }

    default Dataset<E> take(int maxCount) {
        if (maxCount <= 0)
            throw new IllegalArgumentException("The `maxCount` argument must be positive");

        return AbstractDataset.from(this, dataset -> Math.min(dataset.length(), maxCount),
                (dataset, index) -> dataset.get(Objects.checkIndex(index, maxCount)),
                dataset -> dataset.getOrder().take(maxCount, dataset.stream(), dataset));
    }

    default Dataset<E> takeExact(int count) {
        if (count > length())
            throw new IllegalArgumentException("The `takeExact` end index cannot exceed dataset length " + length());

        return take(count).toImmutable();
    }

    default Dataset<E> dropTake(int fromIndex, int maxCount) {
        if (fromIndex < 0)
            throw new IllegalArgumentException("The `fromIndex` (" + fromIndex + ") argument must be non-negative");
        if (maxCount <= 0)
            throw new IllegalArgumentException("The `maxCount` (" + maxCount + ") argument must be positive");

        return AbstractDataset.from(this, dataset -> Math.max(0, Math.min(dataset.length() - fromIndex, maxCount)),
                (dataset, index) -> dataset.get(fromIndex + Objects.checkIndex(index, maxCount)),
                dataset -> dataset.getOrder().dropTake(fromIndex, maxCount, dataset.stream(), dataset)
        );
    }

    default Dataset<E> dropTakeExact(int fromIndex, int count) {
        if (length() < count - fromIndex)
            throw new IllegalArgumentException("The `dropTakeExact` end index cannot exceed dataset length " + length());

        return dropTake(fromIndex, count).toImmutable();
    }

    default <V> Dataset<V> map(Function<E, V> mapper) {
        Objects.requireNonNull(mapper);
        return AbstractDataset.from(this,
                (dataset, index) -> mapper.apply(dataset.get(index)),
                dataset -> dataset.stream().map(mapper));
    }

    default DoubleDataset mapToDouble(ToDoubleFunction<E> mapper) {
        Objects.requireNonNull(mapper);
        return AbstractDoubleDataset.from(this,
                (dataset, index) -> mapper.applyAsDouble(dataset.get(index)),
                dataset -> dataset.stream().mapToDouble(mapper));
    }

    default IntDataset mapToInt(ToIntFunction<E> mapper) {
        Objects.requireNonNull(mapper);
        return AbstractIntDataset.from(this,
                (dataset, index) -> mapper.applyAsInt(dataset.get(index)),
                dataset -> dataset.stream().mapToInt(mapper));
    }

    default LongDataset mapToLong(ToLongFunction<E> mapper) {
        Objects.requireNonNull(mapper);
        return AbstractLongDataset.from(this,
                (dataset, index) -> mapper.applyAsLong(dataset.get(index)),
                dataset -> dataset.stream().mapToLong(mapper));
    }

    default <R> Dataset<Pair<E, R>> withRight(Dataset<R> right) {
        Objects.requireNonNull(right, "right");
        return AbstractDataset.from(this, left -> Math.min(left.length(), right.length()),
                (left, index) -> Pair.of(left.get(index), right.get(index)));
    }

    default Dataset<Pair<E, Double>> withRight(DoubleDataset right) {
        return withRight(right.boxed());
    }

    default Dataset<Pair<E, Integer>> withRight(IntDataset right) {
        return withRight(right.boxed());
    }

    default Dataset<Dataset<E>> subsequences(int len) {
        if (len <= 0)
            throw new IllegalArgumentException("subsequences length `" + len + "` must be positive");

        return AbstractDataset.from(this, dataset -> Math.max(0, dataset.length() - len + 1),
                (dataset, index) -> dataset.dropTake(index, len));
    }

    @SuppressWarnings("unchecked")
    static <E> Dataset<E> empty() {
        return (Dataset<E>) ImmutableDataset.EMPTY;
    }

    class Values<E> extends AbstractList<E> {
        private final Dataset<E> dataset;

        public Values(Dataset<E> dataset) {
            this.dataset = dataset;
        }

        @Override
        public E get(int index) {
            return dataset.get(index);
        }

        @Override
        public int size() {
            return dataset.length();
        }
    }
}