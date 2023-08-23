/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import java.util.function.IntFunction;

public abstract class AbstractIntDataset implements IntDataset {

    @Override
    public IntDataset ref(int n) {
        if (n == 0)
            return this;
        if (n > 0)
            throw new IllegalArgumentException("Periods `n` ("+n+") cannot be positive");

        IntDataset dataset = this;
        return new AbstractIntDataset() {
            @Override
            public int get(int index) {
                return dataset.get(index - n);
            }

            @Override
            public int length() {
                return Math.max(0, dataset.length() + n);
            }
        };
    }

    @Override
    public IntDataset take(int start, int count) {
        if (start < 0)
            throw new IllegalArgumentException("The `start` argument cannot be negative");
        if (count <= 0)
            throw new IllegalArgumentException("The `count` argument must be positive");
        if (length() < count - start)
            throw new IllegalArgumentException("The take end index cannot exceed dataset length " + length());

        return new AbstractIntDataset() {
            @Override
            public int length() {
                return count;
            }

            @Override
            public int get(int index) {
                return AbstractIntDataset.this.get(start + Datasets.requireValidIndex(index, this));
            }
        };
    }

    @Override
    public <E> Dataset<E> mapToObject(IntFunction<E> mapper) {
        return new AbstractDataset<>() {
            @Override
            public E get(int index) {
                return mapper.apply(AbstractIntDataset.this.get(index));
            }

            @Override
            public int length() {
                return AbstractIntDataset.this.length();
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
