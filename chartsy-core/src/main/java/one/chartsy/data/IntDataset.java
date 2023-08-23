/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.data.packed.PackedIntDataset;

import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

/**
 * A dataset specialized for int values.
 *
 * @author Mariusz Bernacki
 */
public interface IntDataset extends SequenceAlike<Integer, IntDataset> {

    int get(int index);

    @Override
    default IntStream stream() {
        return IntStream.range(0, length()).map(this::get);
    }

    default IntDataset map(IntUnaryOperator valueMapping) {
        return new MappedIntDataset(this, IntDataset::length,
                (ds, idx) -> valueMapping.applyAsInt(ds.get(idx)));
    }

    default IntDataset add(int x) {
        return map(v -> v + x);
    }

    default IntDataset sub(int x) {
        return add(-x);
    }

    default IntDataset mul(int x) {
        return map(v -> v * x);
    }

    default IntDataset div(int x) {
        return map(v -> v / x);
    }

    default IntDataset differences() {
        return new MappedIntDataset(this, ds -> ds.length()-1, (ds, idx) -> ds.get(idx) - ds.get(idx + 1));
    }

    default IntDataset ratios() {
        return new MappedIntDataset(this, ds -> ds.length()-1, (ds, idx) -> ds.get(idx) / ds.get(idx + 1));
    }

    default IntDataset toDirect() {
        return PackedIntDataset.from(this);
    }

    default double[] toArray() {
        double[] array = new double[length()];
        for (int i = 0; i < array.length; i++)
            array[i] = get(i);
        return array;
    }

    IntDataset ref(int n);

    <E> Dataset<E> mapToObject(IntFunction<E> mapper);

    default Dataset<Integer> boxed() {
        return mapToObject(Integer::valueOf);
    }
}
