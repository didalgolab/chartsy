package one.chartsy.data;

import one.chartsy.time.Chronological;
import one.chartsy.time.Timeline;

import java.util.List;

public interface IndexedSymbolResourceData<E extends Chronological> extends SymbolResourceData<E, List<E>> {

    int length();

    E get(int index);

    ChronologicalIterator<E> chronologicalIterator(ChronologicalIteratorContext context);

    Timeline getTimeline();
}
