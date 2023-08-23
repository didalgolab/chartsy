/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.data.packed.PackedDataset;
import one.chartsy.data.function.IndexedToDoubleFunction;
import one.chartsy.util.Pair;

import java.util.AbstractList;
import java.util.List;
import java.util.function.IntToDoubleFunction;
import java.util.function.ToDoubleFunction;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * An ordered sequence of arbitrary-type data elements.
 *
 * @author Mariusz Bernacki
 *
 * @param <E> the type of elements stored in this dataset
 */
public interface Dataset<E> extends SequenceAlike<E, Dataset<E>> {

    E get(int index);

    @Override
    default Stream<E> stream() {
        return IntStream.range(0, length()).mapToObj(this::get);
    }

    default Dataset<E> toDirect() {
        return PackedDataset.from(this);
    }

    default List<E> values() {
        return new Values<>(this);
    }

    Dataset<E> ref(int n);

    DoubleDataset mapToDouble(IntToDoubleFunction mapper);

    default DoubleDataset mapToDouble(ToDoubleFunction<E> mapper) {
        return mapToDouble((int index) -> mapper.applyAsDouble(get(index)));
    }

    default DoubleDataset mapToDouble(IndexedToDoubleFunction<E> mapper) {
        return mapToDouble((int index) -> mapper.applyAsDouble(get(index), index));
    }

    <R> Dataset<Pair<E, R>> withRight(Dataset<R> right);

    default Dataset<Pair<E, Double>> withRight(DoubleDataset right) {
        return withRight(right.boxed());
    }

    default Dataset<Pair<E, Integer>> withRight(IntDataset right) {
        return withRight(right.boxed());
    }

    @SuppressWarnings("unchecked")
    static <E> Dataset<E> empty() {
        return (Dataset<E>) PackedDataset.EMPTY;
    }

    class Values<E> extends AbstractList<E> {
        private final Dataset<E> dataset;

        public Values(Dataset<E> dataset) {
            this.dataset = dataset;
        }

        @Override
        public E get(int index) {
            return dataset.get(index);
        }

        @Override
        public int size() {
            return dataset.length();
        }
    }
}
