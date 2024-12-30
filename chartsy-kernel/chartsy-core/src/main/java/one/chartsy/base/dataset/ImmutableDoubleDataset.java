/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.base.dataset;

import one.chartsy.base.DoubleDataset;

import java.util.Arrays;
import java.util.stream.DoubleStream;

public class ImmutableDoubleDataset extends AbstractDoubleDataset {

    public static final ImmutableDoubleDataset EMPTY = new ImmutableDoubleDataset(Order.UNSPECIFIED, new double[0]);


    private final double[] values;

    protected ImmutableDoubleDataset(Order order, double[] values) {
        super(order);
        this.values = values;
    }

    public static ImmutableDoubleDataset of(double[] values) {
        return of(Order.INDEX_ASC, values, false);
    }

    public static ImmutableDoubleDataset ofReversedSameEncounterOrder(double[] values) {
        return of(Order.INDEX_DESC, values, true);
    }

    public static ImmutableDoubleDataset ofReversedSameIndexingOrder(double[] values) {
        return of(Order.INDEX_DESC, values, false);
    }

    private static ImmutableDoubleDataset of(Order order, double[] values, boolean reverse) {
        double[] array = Arrays.copyOf(values, values.length);
        if (reverse) Order.reverse(array);
        return new ImmutableDoubleDataset(order, array);
    }

    public static ImmutableDoubleDataset from(DoubleDataset dataset) {
        if (dataset instanceof ImmutableDoubleDataset)
            return (ImmutableDoubleDataset) dataset;

        double[] values = new double[dataset.length()];
        for (int i = 0; i < values.length; i++) {
            values[i] = dataset.get(i);
        }
        return new ImmutableDoubleDataset(dataset.getOrder(), values);
    }

    @Override
    public double get(int index) {
        return values[index];
    }

    @Override
    public int length() {
        return values.length;
    }

    @Override
    public DoubleStream stream() {
        return getOrder().isDescending()
                ? DoubleStream.iterate(values.length - 1, i -> i - 1).limit(values.length).map(i -> values[(int)i])
                : Arrays.stream(values);
    }
}