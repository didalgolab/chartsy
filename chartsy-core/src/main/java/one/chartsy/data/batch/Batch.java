package one.chartsy.data.batch;

import one.chartsy.time.Chronological;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import static one.chartsy.time.Chronological.Order.REVERSE_CHRONOLOGICAL;

public interface Batch<T extends Chronological> extends Iterable<T> {

    List<T> list();

    Batcher<T> batcher();

    Comparable<?> batchNumber();

    Chronological.Order order();

    default boolean hasNext() {
        return batcher().hasNext(this);
    }

    default Batch<T> next() {
        return batcher().getNext(this);
    }

    default boolean isEmpty() {
        return list().isEmpty();
    }

    @Override
    default Iterator<T> iterator() {
        return list().iterator();
    }

    default Stream<Batch<T>> stream() {
        return Stream.iterate(this, Batch::hasNext, Batch::next);
    }

    default Stream<T> streamAll() {
        return stream().map(Batch::list)
                .map((order() == REVERSE_CHRONOLOGICAL)? Batch::reverseList: Function.identity())
                .flatMap(List::stream);
    }

    private static <T> List<T> reverseList(List<T> list) {
        list = new ArrayList<>(list);
        Collections.reverse(list);
        return list;
    }
}
