/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.dataset;

import one.chartsy.base.Dataset;
import one.chartsy.base.SequenceAlike;
import one.chartsy.base.function.IndexedFunction;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

public abstract class AbstractDataset<E> implements Dataset<E> {

    private final Order order;

    protected AbstractDataset(Order order) {
        this.order = order;
    }

    @Override
    public boolean isEmpty() {
        return length() == 0;
    }

    @Override
    public Order getOrder() {
        return order;
    }

    @Override
    public Iterator<E> iterator() {
        return stream().iterator();
    }

    @Override
    public Spliterator<E> spliterator() {
        return stream().spliterator();
    }

    public static <E, T extends SequenceAlike<?, T>>
    AbstractDataset<E> from(T origin, IndexedFunction<T, E> getter) {
        return new From<>(origin) {
            @Override
            public E get(int index) {
                return getter.apply(origin, index);
            }
        };
    }

    public static <E, T extends SequenceAlike<?, T>>
    AbstractDataset<E> from(T origin, IndexedFunction<T, E> getter, Function<T, Stream<E>> stream) {
        return new From<>(origin) {
            @Override
            public E get(int index) {
                return getter.apply(origin, index);
            }

            @Override
            public Stream<E> stream() {
                return stream.apply(origin);
            }
        };
    }

    public static <E, T extends SequenceAlike<?, T>>
    AbstractDataset<E> from(T origin, ToIntFunction<T> length, IndexedFunction<T, E> getter) {
        return new From<>(origin) {
            @Override
            public int length() {
                return length.applyAsInt(origin);
            }

            @Override
            public E get(int index) {
                return getter.apply(origin, index);
            }
        };
    }

    public static <E, T extends SequenceAlike<?, T>>
    AbstractDataset<E> from(T origin, ToIntFunction<T> length, IndexedFunction<T, E> getter, Function<T, Stream<E>> stream) {
        return new From<>(origin) {
            @Override
            public int length() {
                return length.applyAsInt(origin);
            }

            @Override
            public E get(int index) {
                return getter.apply(origin, index);
            }

            @Override
            public Stream<E> stream() {
                return stream.apply(origin);
            }
        };
    }

    public static abstract class From<E, T extends SequenceAlike<?, T>> extends AbstractDataset<E> {
        protected final T origin;

        public From(T origin) {
            super(origin.getOrder());
            this.origin = origin;
        }

        @Override
        public int length() {
            return origin.length();
        }

        @Override
        public Stream<E> stream() {
            return getOrder().indexes(this).mapToObj(this::get);
        }
    }
}