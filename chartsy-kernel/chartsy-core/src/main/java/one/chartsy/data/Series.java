/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data;

import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.base.Dataset;
import one.chartsy.base.dataset.ImmutableDataset;
import one.chartsy.data.packed.PackedSeries;
import one.chartsy.time.Chronological;
import reactor.core.publisher.Mono;

import java.util.Iterator;
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
public interface Series<E extends Chronological> extends IndexedSymbolResourceData<E>, Iterable<E>, TimeSeriesAlike {

    DoubleSeries mapToDouble(ToDoubleFunction<E> mapper);

    ChronologicalIterator<E> chronologicalIterator(ChronologicalIteratorContext context);

    default Iterator<E> iterator() {
        return chronologicalIterator(iteratorContext());
    }

    Dataset<E> getData();

    private static ChronologicalIteratorContext iteratorContext() {
        class Holder {
            private static final ChronologicalIteratorContext INSTANCE = new ChronologicalIteratorContext(-1);
            private Holder() { }
        }
        return Holder.INSTANCE;
    }

    /**
     * Queries this series using the provided {@code SeriesQuery}.
     * <p>
     * This method allows for the application of a {@code SeriesQuery} strategy
     * to this series. The query encapsulates the logic for extracting
     * information from the series and returns a result.
     * <p>
     * Implementations of {@code SeriesQuery} are responsible for defining the
     * query logic. This method simply delegates to the {@link SeriesQuery#queryFrom}
     * method of the provided {@code SeriesQuery} instance.
     *
     * @param <R> the type of the result returned by the query
     * @param query the {@code SeriesQuery} to apply to this series
     * @return the result of the query
     * @throws IllegalArgumentException if the query cannot be applied to this series
     */
    default <R> R query(SeriesQuery<? super Series<E>, R, ? super E> query) {
        return query.queryFrom(this);
    }

    @Override
    default SymbolIdentity getSymbol() {
        return getResource().symbol();
    }

    @Override
    default TimeFrame getTimeFrame() {
        return getResource().timeFrame();
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
        boolean reverse = (Chronological.Order.CHRONOLOGICAL.isOrdered(data));
        return new PackedSeries<>(resource, ImmutableDataset.of(data, reverse));
    }

    /*----------------------------------------- PARTITIONING HELPER METHODS ------------------------------------------*/
    Function<Series<?>, Object> PARTITION_BY_SYMBOL = Series::getSymbol;

    Function<Series<?>, Object> PARTITION_BY_TIME_FRAME = Series::getTimeFrame;

    Function<Series<?>, Series<?>> PARTITION_BY_SERIES = Function.identity();

}
