/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.dataset;

import one.chartsy.base.Dataset;

import java.util.Iterator;
import java.util.Spliterator;

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

    public static abstract class TransformedDataset<E, V> extends AbstractDataset<V> {
        protected final Dataset<E> dataset;

        protected TransformedDataset(Dataset<E> dataset) {
            super(dataset.getOrder());
            this.dataset = dataset;
        }

        @Override
        public int length() {
            return dataset.length();
        }
    }

    @Override
    public Iterator<E> iterator() {
        return stream().iterator();
    }

    @Override
    public Spliterator<E> spliterator() {
        return stream().spliterator();
    }
}