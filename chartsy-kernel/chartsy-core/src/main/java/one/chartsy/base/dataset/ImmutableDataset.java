/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.dataset;

import one.chartsy.base.Dataset;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public class ImmutableDataset<E> extends AbstractDataset<E> {

    public static final ImmutableDataset<?> EMPTY = new ImmutableDataset<>(Order.UNSPECIFIED, new Object[0]);

    private final E[] values;

    protected ImmutableDataset(Order order, E[] values) {
        super(order);
        this.values = values;
    }

    public static <E> ImmutableDataset<E> of(E[] values) {
        return of(Arrays.asList(values));
    }

    public static <E> ImmutableDataset<E> ofReversedSameEncounterOrder(E[] values) {
        return ofReversedSameEncounterOrder(Arrays.asList(values));
    }

    public static <E> ImmutableDataset<E> ofReversedSameIndexingOrder(E[] values) {
        return ofReversedSameIndexingOrder(Arrays.asList(values));
    }

    public static <E> ImmutableDataset<E> of(Collection<? extends E> values) {
        return of(Order.INDEX_ASC, values, false);
    }

    public static <E> ImmutableDataset<E> of(Collection<? extends E> values, boolean reverse) {
        return of(reverse? Order.INDEX_DESC: Order.INDEX_ASC, values, reverse);
    }

    public static <E> ImmutableDataset<E> ofReversedSameEncounterOrder(Collection<? extends E> values) {
        return of(Order.INDEX_DESC, values, true);
    }

    public static <E> ImmutableDataset<E> ofReversedSameIndexingOrder(List<? extends E> values) {
        return of(Order.INDEX_DESC, values, false);
    }

    @SuppressWarnings("unchecked")
    private static <E> ImmutableDataset<E> of(Order order, Collection<? extends E> values, boolean reverse) {
        E[] array = values.toArray((E[]) new Object[values.size()]);
        if (reverse) Order.reverse(array);
        return new ImmutableDataset<>(order, array);
    }

    public static <E> ImmutableDataset<E> from(Dataset<E> dataset) {
        if (dataset instanceof ImmutableDataset)
            return (ImmutableDataset<E>) dataset;

        @SuppressWarnings("unchecked")
        E[] values = (E[]) new Object[dataset.length()];
        for (int i = 0; i < values.length; i++) {
            var value = dataset.get(i);
            if (value instanceof Dataset<?> valueDataset)
                value = (E) valueDataset.toImmutable();
            values[i] = value;
        }
        return new ImmutableDataset<>(dataset.getOrder(), values);
    }

    @Override
    public E get(int index) {
        return values[index];
    }

    @Override
    public int length() {
        return values.length;
    }

    @Override
    public Stream<E> stream() {
        return getOrder().isDescending() ? Arrays.asList(values).reversed().stream() : Arrays.stream(values);
    }
}
