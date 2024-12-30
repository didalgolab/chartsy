/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.dataset;

import one.chartsy.base.LongDataset;

import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

public class ImmutableLongDataset extends AbstractLongDataset {

    public static final ImmutableLongDataset EMPTY = new ImmutableLongDataset(Order.UNSPECIFIED, new long[0]);

    private final long[] values;

    protected ImmutableLongDataset(Order order, long[] values) {
        super(order);
        this.values = values;
    }

    public static ImmutableLongDataset of(long[] values) {
       return of(Order.INDEX_ASC, values, false);
   }

    public static ImmutableLongDataset ofReversedSameEncounterOrder(long[] values) {
        return of(Order.INDEX_DESC, values, true);
    }

    public static ImmutableLongDataset ofReversedSameIndexingOrder(long[] values) {
        return of(Order.INDEX_DESC, values, false);
    }

    private static ImmutableLongDataset of(Order order, long[] values, boolean reverse) {
        long[] array = Arrays.copyOf(values, values.length);
        if (reverse) Order.reverse(array);
        return new ImmutableLongDataset(order, array);
    }

    public static ImmutableLongDataset from(LongDataset dataset) {
        if (dataset instanceof ImmutableLongDataset)
            return (ImmutableLongDataset) dataset;

        long[] values = new long[dataset.length()];
        for (int i = 0; i < values.length; i++) {
            values[i] = dataset.get(i);
        }
        return new ImmutableLongDataset(dataset.getOrder(), values);
    }

    @Override
    public long get(int index) {
       return values[index];
   }

    @Override
    public int length() {
       return values.length;
   }

    @Override
    public LongStream stream() {
        return getOrder().isDescending()
                ? IntStream.iterate(values.length - 1, i -> i - 1).limit(values.length).mapToLong(i -> values[i])
                : Arrays.stream(values);
    }
}