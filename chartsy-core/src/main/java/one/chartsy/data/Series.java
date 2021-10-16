package one.chartsy.data;

import one.chartsy.SymbolResource;
import one.chartsy.data.collections.PackedDataset;
import one.chartsy.time.Chronological;

import java.util.List;

public interface Series<E extends Chronological> extends IndexedSymbolResourceData<E> {

    static <E extends Chronological> Series<E> of(SymbolResource<E> resource) {
        return new StandardIndexedSymbolResourceData<>(resource, Dataset.empty());
    }

    static <E extends Chronological> Series<E> of(SymbolResource<E> resource, List<E> data) {
        return new StandardIndexedSymbolResourceData<>(resource, PackedDataset.of(data));
    }
}
