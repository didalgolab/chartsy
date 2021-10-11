package one.chartsy.data;

import one.chartsy.SymbolResource;
import one.chartsy.time.Chronological;

public abstract class AbstractChronologicalIterator<E extends Chronological> implements ChronologicalIterator<E> {
    private final ChronologicalIteratorContext context;
    private final IndexedSymbolResourceData<E> dataset;
    public int index;
    public E value;

    public AbstractChronologicalIterator(IndexedSymbolResourceData<E> dataset, ChronologicalIteratorContext context) {
        this(dataset, context, dataset.length(), null);
    }

    public AbstractChronologicalIterator(IndexedSymbolResourceData<E> dataset, ChronologicalIteratorContext context, int index, E value) {
        this.context = context;
        this.dataset = dataset;
        this.index = index;
        this.value = value;
    }

    @Override
    public SymbolResource<E> getResource() {
        return dataset.getResource();
    }

    @Override
    public final Integer getId() {
        return context.getId();
    }

    @Override
    public IndexedSymbolResourceData<E> getDataset() {
        return dataset;
    }

    @Override
    public int index() {
        return index;
    }

    @Override
    public E current() {
        return value;
    }

    @Override
    public boolean hasNext() {
        return index > 0;
    }
}
