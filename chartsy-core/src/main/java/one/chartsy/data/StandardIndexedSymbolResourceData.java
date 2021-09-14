package one.chartsy.data;

import lombok.Getter;
import one.chartsy.SymbolResource;
import one.chartsy.time.Chronological;
import one.chartsy.time.Timeline;

import java.util.Collections;

@Getter
public class StandardIndexedSymbolResourceData<E extends Chronological> implements IndexedSymbolResourceData<E>, Timeline {

    private final SymbolResource<E> resource;
    private final Dataset<E> data;

    public StandardIndexedSymbolResourceData(SymbolResource<E> resource, Dataset<E> dataset) {
        this.resource = resource;
        this.data = dataset;
        if (!getOrder().isOrdered(dataset.values()))
            throw new IllegalArgumentException("Given values aren't in " + getOrder() + " order");
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
    public ChronologicalIterator<E> chronologicalIterator(ChronologicalIteratorContext context) {
        throw new UnsupportedOperationException();
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
