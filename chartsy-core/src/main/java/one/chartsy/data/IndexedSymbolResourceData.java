package one.chartsy.data;

import one.chartsy.time.Chronological;
import one.chartsy.time.Timeline;

public interface IndexedSymbolResourceData<E extends Chronological> extends SymbolResourceData<E, Dataset<E>> {

    int length();

    E get(int index);

    default E getFirst() {
        return get(length() - 1);
    }

    default E getLast() {
        return get(0);
    }

    ChronologicalIterator<E> chronologicalIterator(ChronologicalIteratorContext context);

    Timeline getTimeline();
}
