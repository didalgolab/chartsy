package one.chartsy.data;

import one.chartsy.SymbolResource;
import one.chartsy.collections.ReversedView;
import one.chartsy.data.packed.PackedDataset;
import one.chartsy.data.packed.PackedSeries;
import one.chartsy.time.Chronological;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

/**
 * Defines a data series whose elements are represented, among others, by a point
 * in time of its particular elements.
 * <p>
 * The order of elements in the series <b><i>must be</i></b> provided from the
 * newest element being at <b>{@code index = 0}</b> to the oldest element at
 * index <b> {@link #length()} - 1</b> unless specified otherwise by the
 * {@link #getTimeline() timeline}'s order.
 *
 * @author Mariusz Bernacki
 *
 */
public interface Series<E extends Chronological> extends IndexedSymbolResourceData<E>, TimeSeriesAlike {

    DoubleSeries mapToDouble(ToDoubleFunction<E> mapper);

    ChronologicalIterator<E> chronologicalIterator(ChronologicalIteratorContext context);

    default <R> R query(Query<? super Series<? extends E>, R> query) {
        return query.queryFrom(this);
    }


    /*-------------------------------------------- STATIC FACTORY METHODS --------------------------------------------*/
    static <E extends Chronological> Series<E> empty(SymbolResource<E> resource) {
        return new PackedSeries<>(resource, Dataset.empty());
    }

    /**
     * Gives a transformer capable of transforming a mono list of into the {@code Series} of the given resource.
     *
     * @param resource the target series symbol resource
     * @return the transformer
     */
    static <E extends Chronological> Function<? super Mono<List<E>>, Series<E>> of(SymbolResource<E> resource) {
        return mono -> of(resource, mono.block());
    }

    static <E extends Chronological> Series<E> of(SymbolResource<E> resource, List<E> data) {
        if (Chronological.Order.CHRONOLOGICAL.isOrdered(data))
            data = ReversedView.of(data);

        return new PackedSeries<>(resource, PackedDataset.of(data));
    }
}
