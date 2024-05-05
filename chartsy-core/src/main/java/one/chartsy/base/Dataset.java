/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base;

import one.chartsy.base.dataset.AbstractDataset;
import one.chartsy.base.dataset.ImmutableDataset;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface Dataset<E> extends SequenceAlike<E, Dataset<E>> {

    /**
     * Returns the element at the specified position in the dataset.  Depending
     * on the characteristic of the dataset, the index-ordering may or may not be
     * an iterating order.
     *
     * @param index the index of the element to return
     * @return the element at the specified position
     * @throws IndexOutOfBoundsException if the index is out of range
     *                                   (index < 0 || index >= size())
     */
    E get(int index);

    @Override Stream<E> stream();

    @Override Iterator<E> iterator();

    @Override Spliterator<E> spliterator();

    default Dataset<E> toImmutable() {
        return ImmutableDataset.from(this);
    }

    default List<E> values() {
        return new Values<>(this);
    }

    default Dataset<E> ref(int n) {
        if (n == 0)
            return this;
        if (n > 0)
            throw new IllegalArgumentException("Periods `n` (" + n + ") cannot be positive");

        return new AbstractDataset.TransformedDataset<>(this) {
            @Override
            public E get(int index) {
                return dataset.get(index - n);
            }

            @Override
            public int length() {
                return Math.max(0, dataset.length() + n);
            }

            @Override
            public Stream<E> stream() {
                return getOrder().shift(-n, dataset.stream(), dataset);
            }
        };
    }

    default Dataset<E> take(int count) {
        if (count <= 0)
            throw new IllegalArgumentException("The `count` argument must be positive");

        return new AbstractDataset.TransformedDataset<>(this) {
            @Override
            public int length() {
                return Math.min(dataset.length(), count);
            }

            @Override
            public E get(int index) {
                return dataset.get(Objects.checkIndex(index, count));
            }

            @Override
            public Stream<E> stream() {
                return getOrder().take(count, dataset.stream(), dataset);
            }
        };
    }

    default Dataset<E> take(int maxCount, int fromIndex) {
        if (maxCount <= 0)
            throw new IllegalArgumentException("The `maxCount` (" + maxCount + ") argument must be positive");
        if (fromIndex < 0)
            throw new IllegalArgumentException("The `fromIndex` (" + fromIndex + ") argument must be non-negative");

        return new AbstractDataset.TransformedDataset<>(this) {
            @Override
            public int length() {
                return Math.max(0, Math.min(dataset.length() - fromIndex, maxCount));
            }

            @Override
            public E get(int index) {
                return dataset.get(fromIndex + Objects.checkIndex(index, maxCount));
            }

            @Override
            public Stream<E> stream() {
                return getOrder().take(maxCount, fromIndex, dataset.stream(), dataset);
            }
        };
    }

    default <V> Dataset<V> map(Function<E, V> mapper) {
        Objects.requireNonNull(mapper);
        return new AbstractDataset.TransformedDataset<>(this) {
            @Override
            public V get(int index) {
                return mapper.apply(dataset.get(index));
            }

            @Override
            public Stream<V> stream() {
                return dataset.stream().map(mapper);
            }
        };
    }

    default Dataset<Dataset<E>> subsequences(int len) {
        if (len <= 0)
            throw new IllegalArgumentException("subsequences length `" + len + "` must be positive");

        return new AbstractDataset.TransformedDataset<>(this) {
            @Override
            public Dataset<E> get(int index) {
                return dataset.take(len, index);
            }

            @Override
            public int length() {
                return Math.max(0, dataset.length() - len + 1);
            }

            @Override
            public Stream<Dataset<E>> stream() {
                return IntStream.range(0, length()).mapToObj(index -> dataset.take(len, index));
            }
        };
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

    interface OfPrimitive<E,
            T_SEQ extends OfPrimitive<E, T_SEQ, T_SPLITR>,
            T_SPLITR extends Spliterator.OfPrimitive<E, ?, T_SPLITR>>
            extends SequenceAlike<E, T_SEQ> {

        /**
         * Returns a primitive spliterator over the elements in the window.
         *
         * @return a primitive spliterator
         */
        @Override
        T_SPLITR spliterator();

    }

    /**
     * An ordered, sliding window of primitive {@code int} values.
     */
    interface OfInt extends OfPrimitive<Integer, OfInt, Spliterator.OfInt> {

        /**
         * Returns the element at the specified position in the window.
         *
         * @param index the index of the element to return
         * @return the element at the specified position in the window
         * @throws IndexOutOfBoundsException if the index is out of range
         */
        int get(int index);

        /**
         * Returns a sequential {@code IntStream} with the specified number of elements in the window.
         *
         * @return a sequential {@code IntStream} over the elements in the window
         */
        @Override
        default IntStream stream() {
            return StreamSupport.intStream(spliterator(), false);
        }
    }

    /**
     * An ordered, sliding window of primitive {@code long} values.
     */
    interface OfLong extends OfPrimitive<Long, OfLong, Spliterator.OfLong> {

        /**
         * Returns the element at the specified position in the window.
         *
         * @param index the index of the element to return
         * @return the element at the specified position in the window
         * @throws IndexOutOfBoundsException if the index is out of range
         */
        long get(int index);

        /**
         * Returns a sequential {@code LongStream} with the specified number of elements in the window.
         *
         * @return a sequential {@code LongStream} over the elements in the window
         */
        @Override
        default LongStream stream() {
            return StreamSupport.longStream(spliterator(), false);
        }
    }

    /**
     * An ordered, sliding window of primitive {@code double} values.
     */
    interface OfDouble extends OfPrimitive<Double, OfDouble, Spliterator.OfDouble> {

        /**
         * Returns the element at the specified position in the window.
         *
         * @param index the index of the element to return
         * @return the element at the specified position in the window
         * @throws IndexOutOfBoundsException if the index is out of range
         */
        double get(int index);

        /**
         * Returns a sequential {@code DoubleStream} with the specified number of elements in the window.
         *
         * @return a sequential {@code DoubleStream} over the elements in the window
         */
        @Override
        default DoubleStream stream() {
            return StreamSupport.doubleStream(spliterator(), false);
        }
    }
}