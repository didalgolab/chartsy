/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.dataset;

import one.chartsy.base.IntDataset;
import one.chartsy.base.SequenceAlike;
import one.chartsy.base.function.IndexedToIntFunction;

import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.IntStream;

public abstract class AbstractIntDataset implements IntDataset {

    private final Order order;

    protected AbstractIntDataset(Order order) {
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
    AbstractIntDataset from(T origin, IndexedToIntFunction<T> getter) {
        return new From<>(origin) {
            @Override
            public int get(int index) {
                return getter.applyAsInt(origin, index);
            }
        };
    }

    public static <T extends SequenceAlike<?, T>>
    AbstractIntDataset from(T origin, IndexedToIntFunction<T> getter, Function<T, IntStream> stream) {
        return new From<>(origin) {
            @Override
            public int get(int index) {
                return getter.applyAsInt(origin, index);
            }

            @Override
            public IntStream stream() {
                return stream.apply(origin);
            }
        };
    }

    public static <T extends SequenceAlike<?, T>>
    AbstractIntDataset from(T origin, ToIntFunction<T> length, IndexedToIntFunction<T> getter) {
        return new From<>(origin) {
            @Override
            public int length() {
                return length.applyAsInt(origin);
            }

            @Override
            public int get(int index) {
                return getter.applyAsInt(origin, index);
            }
        };
    }

    public static <T extends SequenceAlike<?, T>>
    AbstractIntDataset from(T origin, ToIntFunction<T> length, IndexedToIntFunction<T> getter, Function<T, IntStream> stream) {
        return new From<>(origin) {
            @Override
            public int length() {
                return length.applyAsInt(origin);
            }

            @Override
            public int get(int index) {
                return getter.applyAsInt(origin, index);
            }

            @Override
            public IntStream stream() {
                return stream.apply(origin);
            }
        };
    }

    public static abstract class From<T extends SequenceAlike<?, T>> extends AbstractIntDataset {
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
        public IntStream stream() {
            return getOrder().indexes(this).map(this::get);
        }
    }

    @Override
    public PrimitiveIterator.OfInt iterator() {
        return stream().iterator();
    }

    @Override
    public Spliterator.OfInt spliterator() {
        return stream().spliterator();
    }
}