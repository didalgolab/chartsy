/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.data.packed.PackedDoubleDataset;

import java.util.function.DoubleFunction;
import java.util.function.DoubleToIntFunction;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/**
 * A dataset specialized for double values.
 *
 * @author Mariusz Bernacki
 */
public interface DoubleDataset extends SequenceAlike<Double, DoubleDataset> {

    double get(int index);

    @Override
    default DoubleStream stream() {
        return IntStream.range(0, length()).mapToDouble(this::get);
    }

    default DoubleDataset map(DoubleUnaryOperator valueMapping) {
        return new MappedDoubleDataset(this, DoubleDataset::length,
                (ds, idx) -> valueMapping.applyAsDouble(ds.get(idx)));
    }

    default DoubleDataset add(double x) {
        return map(v -> v + x);
    }

    default DoubleDataset sub(double x) {
        return add(-x);
    }

    default DoubleDataset mul(double x) {
        return map(v -> v * x);
    }

    default DoubleDataset div(double x) {
        return map(v -> v / x);
    }

    default DoubleDataset differences() {
        return new MappedDoubleDataset(this, ds -> ds.length()-1, (ds, idx) -> ds.get(idx) - ds.get(idx + 1));
    }

    default DoubleDataset ratios() {
        return new MappedDoubleDataset(this, ds -> ds.length()-1, (ds, idx) -> ds.get(idx) / ds.get(idx + 1));
    }

    default DoubleDataset toDirect() {
        return PackedDoubleDataset.from(this);
    }

    default double[] toArray() {
        double[] array = new double[length()];
        for (int i = 0; i < array.length; i++)
            array[i] = get(i);
        return array;
    }

    DoubleDataset ref(int n);

    IntDataset mapToInt(DoubleToIntFunction mapper);

    <E> Dataset<E> mapToObject(DoubleFunction<E> mapper);

    default Dataset<Double> boxed() {
        return mapToObject(Double::valueOf);
    }
}
