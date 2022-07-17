/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.packed;

import one.chartsy.data.AbstractIntDataset;
import one.chartsy.data.IntDataset;

import java.util.Arrays;
import java.util.stream.IntStream;

public class PackedIntDataset extends AbstractIntDataset {
    private final int[] values;

    protected PackedIntDataset(int[] values) {
        this.values = values;
    }

    public static PackedIntDataset of(int[] values) {
        return new PackedIntDataset(values.clone());
    }

    public static PackedIntDataset from(IntDataset dataset) {
        if (dataset instanceof PackedIntDataset)
            return (PackedIntDataset) dataset;

        int[] values = new int[dataset.length()];
        Arrays.setAll(values, dataset::get);
        return new PackedIntDataset(values);
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
        return Arrays.stream(values);
    }

    @Override
    public String toString() {
        return Arrays.toString(values);
    }
}
