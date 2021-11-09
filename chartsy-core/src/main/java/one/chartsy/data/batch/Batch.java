package one.chartsy.data.batch;

import one.chartsy.time.Chronological;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Stream;

public interface Batch<T extends Chronological> extends Comparable<Batch<T>>, Iterator<Batch<T>>, Iterable<T> {

    List<T> list();

    Batcher<T> batcher();

    Comparable<?> batchNumber();

    Chronological.Order order();

    @Override
    default boolean hasNext() {
        return batcher().hasNext(this);
    }

    @Override
    default Batch<T> next() {
        return batcher().getNext(this);
    }

    @Override
    default Iterator<T> iterator() {
        return list().iterator();
    }

    default boolean isEmpty() {
        return list().isEmpty();
    }

    default int size() {
        return list().size();
    }

    default List<T> listOrdered() {
        List<T> list = list();
        if (order().isReversed()) {
            list = new ArrayList<>(list);
            Collections.reverse(list);
        }
        return list;
    }

    default Stream<Batch<T>> stream() {
        return Stream.concat(Stream.of(this), Stream.iterate(this, Batch::hasNext, Batch::next));
    }

    default Stream<T> flatten() {
        return stream().sorted()
                .map(Batch::listOrdered)
                .flatMap(List::stream);
    }

    default <R, A> R collect(Collector<? super Batch<T>, A, R> collector) {
        return stream().collect(collector);
    }
}
