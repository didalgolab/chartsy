/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.dataset;

import one.chartsy.base.IntDataset;

import java.util.Arrays;
import java.util.stream.IntStream;

public class ImmutableIntDataset extends AbstractIntDataset {

    public static final ImmutableIntDataset EMPTY = new ImmutableIntDataset(Order.UNSPECIFIED, new int[0]);


    private final int[] values;

    protected ImmutableIntDataset(Order order, int[] values) {
        super(order);
        this.values = values;
    }

    public static ImmutableIntDataset of(int[] values) {
       return of(Order.INDEX_ASC, values, false);
   }

    public static ImmutableIntDataset ofReversedSameEncounterOrder(int[] values) {
        return of(Order.INDEX_DESC, values, true);
    }

    public static ImmutableIntDataset ofReversedSameIndexingOrder(int[] values) {
        return of(Order.INDEX_DESC, values, false);
    }

    private static ImmutableIntDataset of(Order order, int[] values, boolean reverse) {
        int[] array = Arrays.copyOf(values, values.length);
        if (reverse) Order.reverse(array);
        return new ImmutableIntDataset(order, array);
    }

    public static ImmutableIntDataset from(IntDataset dataset) {
        if (dataset instanceof ImmutableIntDataset)
            return (ImmutableIntDataset) dataset;

        int[] values = new int[dataset.length()];
        for (int i = 0; i < values.length; i++) {
            values[i] = dataset.get(i);
        }
        return new ImmutableIntDataset(dataset.getOrder(), values);
    }

    @Override
    public int get(int index) {
       return values[index];
   }

    @Override
    public int length() {
       return values.length;
   }

    @Override
    public IntStream stream() {
        return getOrder().isDescending()
                ? IntStream.iterate(values.length - 1, i -> i - 1).limit(values.length).map(i -> values[i])
                : Arrays.stream(values);
    }
}