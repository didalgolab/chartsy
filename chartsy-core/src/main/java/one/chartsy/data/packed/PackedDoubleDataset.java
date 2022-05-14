/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.packed;

import one.chartsy.data.AbstractDoubleDataset;
import one.chartsy.data.DoubleDataset;

import java.util.Arrays;
import java.util.stream.DoubleStream;

public class PackedDoubleDataset extends AbstractDoubleDataset {
    private final double[] values;

    protected PackedDoubleDataset(double[] values) {
        this.values = values;
    }

    public static PackedDoubleDataset of(double[] values) {
        return new PackedDoubleDataset(values.clone());
    }

    public static PackedDoubleDataset from(DoubleDataset dataset) {
        if (dataset instanceof PackedDoubleDataset)
            return (PackedDoubleDataset) dataset;

        double[] values = new double[dataset.length()];
        Arrays.setAll(values, dataset::get);
        return new PackedDoubleDataset(values);
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
        return Arrays.stream(values);
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }
}
