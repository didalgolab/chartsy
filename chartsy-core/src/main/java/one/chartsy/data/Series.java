package one.chartsy.data;

import one.chartsy.SymbolResource;
import one.chartsy.data.packed.PackedDataset;
import one.chartsy.data.packed.PackedSeries;
import one.chartsy.time.Chronological;

import java.util.List;
import java.util.function.ToDoubleFunction;

public interface Series<E extends Chronological> extends IndexedSymbolResourceData<E> {

    static <E extends Chronological> Series<E> empty(SymbolResource<E> resource) {
        return new PackedSeries<>(resource, Dataset.empty());
    }

    static <E extends Chronological> Series<E> of(SymbolResource<E> resource, List<E> data) {
        return new PackedSeries<>(resource, PackedDataset.of(data));
    }

    default <R> R query(Query<? super Series<? extends E>, R> query) {
        return query.queryFrom(this);
    }

    DoubleSeries mapToDouble(ToDoubleFunction<E> mapper);

}
