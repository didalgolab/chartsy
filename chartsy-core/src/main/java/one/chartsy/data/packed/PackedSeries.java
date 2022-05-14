/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.packed;

import lombok.Getter;
import one.chartsy.SymbolResource;
import one.chartsy.data.*;
import one.chartsy.time.Chronological;
import one.chartsy.time.Timeline;

import java.util.Collections;
import java.util.function.ToDoubleFunction;

@Getter
public class PackedSeries<E extends Chronological> implements Series<E>, Timeline {

    private final SymbolResource<E> resource;
    private final Dataset<E> data;


    public PackedSeries(SymbolResource<E> resource, Dataset<E> dataset) {
        this.resource = resource;
        this.data = dataset;

        int length = length();
        if (length > 0) {
            E first = get(length - 1), last = get(0);
            boolean reversed = getOrder().isReversed();
            if (reversed && first.isAfter(last) || !reversed && first.isBefore(last))
                throw new IllegalArgumentException("Given values aren't in " + getOrder() + " order");
        }
    }

    @Override
    public int length() {
        return data.length();
    }

    @Override
    public E get(int index) {
        return data.get(index);
    }

    @Override
    public boolean isEmpty() {
        return data.isEmpty();
    }

    @Override
    public E getFirst() {
        return get(length() - 1);
    }

    @Override
    public E getLast() {
        return get(0);
    }

    @Override
    public DoubleSeries mapToDouble(ToDoubleFunction<E> mapper) {
        return new PackedDoubleSeries(getTimeline(), getData().mapToDouble(mapper));
    }

    @Override
    public ChronologicalIterator<E> chronologicalIterator(ChronologicalIteratorContext context) {
        return new AbstractChronologicalIterator<>(this, context) {
            @Override
            public E peek() {
                return get(index - 1);
            }

            @Override
            public E next() {
                return (value = get(--index));
            }
        };
    }

    @Override
    public final Timeline getTimeline() {
        return this;
    }

    @Override
    public final Chronological.Order getOrder() {
        return Chronological.Order.REVERSE_CHRONOLOGICAL;
    }

    @Override
    public final long getTimeAt(int index) {
        return get(index).getTime();
    }

    @Override
    public int getTimeLocation(long time) {
        Chronological forTime = () -> time;
        return Collections.binarySearch(data.values(), forTime, getOrder().comparator());
    }
}
