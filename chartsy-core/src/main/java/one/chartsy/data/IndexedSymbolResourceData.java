package one.chartsy.data;

import one.chartsy.time.Chronological;

public interface IndexedSymbolResourceData<E extends Chronological> extends SymbolResourceData<E, Dataset<E>> {

    int length();

    E get(int index);

    E getFirst();

    E getLast();

    boolean isEmpty();
}
