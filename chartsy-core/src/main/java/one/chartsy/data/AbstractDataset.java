/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.util.Pair;

import java.util.Objects;
import java.util.function.IntToDoubleFunction;

public abstract class AbstractDataset<E> implements Dataset<E> {

    @Override
    public Dataset<E> ref(int n) {
        if (n == 0)
            return this;
        if (n > 0)
            throw new IllegalArgumentException("Periods `n` ("+n+") cannot be positive");

        Dataset<E> dataset = this;
        return new AbstractDataset<>() {
            @Override
            public E get(int index) {
                return dataset.get(index - n);
            }

            @Override
            public int length() {
                return Math.max(0, dataset.length() + n);
            }
        };
    }

    @Override
    public Dataset<E> take(int start, int count) {
        if (start < 0)
            throw new IllegalArgumentException("The `start` argument cannot be negative");
        if (count <= 0)
            throw new IllegalArgumentException("The `count` argument must be positive");
        if (length() < count - start)
            throw new IllegalArgumentException("The take end index cannot exceed dataset length " + length());

        return new AbstractDataset<>() {
            @Override
            public int length() {
                return count;
            }

            @Override
            public E get(int index) {
                return AbstractDataset.this.get(start + Datasets.requireValidIndex(index, this));
            }
        };
    }

    @Override
    public DoubleDataset mapToDouble(IntToDoubleFunction mapper) {
        return new AbstractDoubleDataset() {
            @Override
            public double get(int index) {
                return mapper.applyAsDouble(index);
            }

            @Override
            public int length() {
                return AbstractDataset.this.length();
            }
        };
    }

    @Override
    public <R> Dataset<Pair<E, R>> withRight(Dataset<R> right) {
        Objects.requireNonNull(right, "right");
        Dataset<E> left = this;
        return new AbstractDataset<>() {
            @Override
            public Pair<E, R> get(int index) {
                return Pair.of(left.get(index), right.get(index));
            }

            @Override
            public int length() {
                return Math.min(left.length(), right.length());
            }
        };
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append('[');
        int count = length();
        if (count > 0) {
            int i = 0;
            buf.append(get(i++));
            while (i < count)
                buf.append(", ").append(get(i++));
        }
        return buf.append(']').toString();
    }
}
