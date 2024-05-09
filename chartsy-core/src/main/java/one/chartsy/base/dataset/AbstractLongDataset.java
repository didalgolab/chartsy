/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.dataset;

import one.chartsy.base.LongDataset;
import one.chartsy.base.SequenceAlike;
import one.chartsy.base.function.IndexedToLongFunction;

import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.LongStream;

public abstract class AbstractLongDataset implements LongDataset {

    private final Order order;

    protected AbstractLongDataset(Order order) {
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

    public static <T extends SequenceAlike<?, T>>
    AbstractLongDataset from(T origin, IndexedToLongFunction<T> getter) {
        return new From<>(origin) {
            @Override
            public long get(int index) {
                return getter.applyAsLong(origin, index);
            }
        };
    }

    public static <T extends SequenceAlike<?, T>>
    AbstractLongDataset from(T origin, IndexedToLongFunction<T> getter, Function<T, LongStream> stream) {
        return new From<>(origin) {
            @Override
            public long get(int index) {
                return getter.applyAsLong(origin, index);
            }

            @Override
            public LongStream stream() {
                return stream.apply(origin);
            }
        };
    }

    public static <T extends SequenceAlike<?, T>>
    AbstractLongDataset from(T origin, ToLongFunction<T> length, IndexedToLongFunction<T> getter) {
        return new From<>(origin) {
            @Override
            public int length() {
                return (int) length.applyAsLong(origin);
            }

            @Override
            public long get(int index) {
                return getter.applyAsLong(origin, index);
            }
        };
    }

    public static <T extends SequenceAlike<?, T>>
    AbstractLongDataset from(T origin, ToLongFunction<T> length, IndexedToLongFunction<T> getter, Function<T, LongStream> stream) {
        return new From<>(origin) {
            @Override
            public int length() {
                return (int) length.applyAsLong(origin);
            }

            @Override
            public long get(int index) {
                return getter.applyAsLong(origin, index);
            }

            @Override
            public LongStream stream() {
                return stream.apply(origin);
            }
        };
    }

    public static abstract class From<T extends SequenceAlike<?, T>> extends AbstractLongDataset {
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
        public LongStream stream() {
            return getOrder().indexes(this).mapToLong(this::get);
        }
    }

    @Override
    public PrimitiveIterator.OfLong iterator() {
        return stream().iterator();
    }

    @Override
    public Spliterator.OfLong spliterator() {
        return stream().spliterator();
    }
}