/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.dataset;

import one.chartsy.base.Dataset;
import one.chartsy.base.DoubleDataset;
import one.chartsy.base.SequenceAlike;
import one.chartsy.base.function.DoubleBiPredicate;
import one.chartsy.base.function.IndexedToDoubleFunction;

import java.util.Objects;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.DoubleStream;

public abstract class AbstractDoubleDataset implements DoubleDataset {

    private final Order order;

    protected AbstractDoubleDataset(Order order) {
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
    public PrimitiveIterator.OfDouble iterator() {
        return stream().iterator();
    }

    @Override
    public Spliterator.OfDouble spliterator() {
        return stream().spliterator();
    }

    public static <T extends SequenceAlike>
    AbstractDoubleDataset from(T origin, IndexedToDoubleFunction<T> getter) {
        return new From<>(origin) {
            @Override
            public double get(int index) {
                return getter.applyAsDouble(origin, index);
            }
        };
    }

    public static <T extends SequenceAlike>
    AbstractDoubleDataset from(T origin, IndexedToDoubleFunction<T> getter, Function<T, DoubleStream> stream) {
        return new From<>(origin) {
            @Override
            public double get(int index) {
                return getter.applyAsDouble(origin, index);
            }

            @Override
            public DoubleStream stream() {
                return stream.apply(origin);
            }
        };
    }

    public static <T extends SequenceAlike>
    AbstractDoubleDataset from(T origin, ToIntFunction<T> length, IndexedToDoubleFunction<T> getter) {
        return new From<>(origin) {
            @Override
            public int length() {
                return length.applyAsInt(origin);
            }

            @Override
            public double get(int index) {
                return getter.applyAsDouble(origin, index);
            }
        };
    }

    public static <T extends SequenceAlike>
    AbstractDoubleDataset from(T origin, ToIntFunction<T> length, IndexedToDoubleFunction<T> getter, Function<T, DoubleStream> stream) {
        return new From<>(origin) {
            @Override
            public int length() {
                return length.applyAsInt(origin);
            }

            @Override
            public double get(int index) {
                return getter.applyAsDouble(origin, index);
            }

            @Override
            public DoubleStream stream() {
                return stream.apply(origin);
            }
        };
    }

    public static abstract class From<T extends SequenceAlike> extends AbstractDoubleDataset {
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
        public DoubleStream stream() {
            return getOrder().indexes(this).mapToDouble(this::get);
        }
    }
}