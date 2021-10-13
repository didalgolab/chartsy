package one.chartsy.data;

import one.chartsy.When;
import one.chartsy.time.Chronological;

import java.util.Iterator;
import java.util.NoSuchElementException;

public interface ChronologicalIterator<E extends Chronological> extends Iterator<E>, When {
    /**
     * Gives the underlying dataset being iterated.
     */
    IndexedSymbolResourceData<E> getDataset();

    /**
     * Peeks for the next value available in the iteration sequence
     * without moving the actual iterator pointer.
     *
     * @return the next value in the iteration
     * @throws NoSuchElementException
     *             if the iteration has no more elements
     */
    E peek();
}
