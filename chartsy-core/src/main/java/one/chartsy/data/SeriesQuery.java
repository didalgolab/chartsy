/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data;

import one.chartsy.Candle;
import one.chartsy.time.Chronological;

/**
 * Strategy for querying chronological data series.
 * <p>
 * This interface defines a framework for querying series of chronological data,
 * such as financial time series. It allows for the encapsulation of query logic,
 * following the strategy design pattern, enabling various query operations to be
 * performed on a series without the need to alter the series itself. Implementations
 * of this interface can perform a wide range of queries, such as extracting the
 * highest high price within a certain period or calculating the average volume over
 * a specified range of data points.
 * <p>
 * The {@code Series<E>} interface provides the context for the query. A typical
 * series, such as {@code Series<Candle>} or {@code CandleSeries}, contains
 * chronological data where the zeroth element ({@code series.get(0)}) is the most
 * recent (latest) data point, {@code Series.get(1)} is the next most recent, and so on.
 * In case of {@code Series<Candle>} series, accessing further individual candle's
 * prices can be accomplished using methods like {@link Candle#high()},
 * {@link Candle#low()}, or {@link Candle#close()}.
 * <p>
 * An example of using a {@code SeriesQuery} might look as follows:
 * <pre>
 *   SeriesQuery&lt;Double&gt; highestHighQuery = series -&gt; {
 *     return series.stream()
 *                  .mapToDouble(Candle::high)
 *                  .max()
 *                  .orElse(Double.NaN);
 *   };
 *   double highestHigh = highestHighQuery.queryFrom(candleSeries);
 *   // or alternatively
 *   double highestHigh = candleSeries.query(highestHighQuery);
 * </pre>
 * This example defines a query to find the highest high price in a {@code CandleSeries}.
 * <p>
 * It is recommended to use lambda expressions or method references to create
 * instances of {@code SeriesQuery} due to their succinctness and clarity.
 * <p>
 * It is recommended that implementations are immutable and thread-safe.
 *
 * @param <S> the type of the {@code Series} being queried, extending from {@code Series<E>}
 * @param <R> the type of the result returned by the query
 * @param <E> the type of the data elements in the series, extending from {@code Chronological}
 *
 * @author Mariusz Bernacki
 *
 */
@FunctionalInterface
public interface SeriesQuery<S extends Series<E>, R, E extends Chronological> {

    /**
     * Queries the specified series object.
     * <p>
     * This method applies the query logic encapsulated in the implementing class
     * to the provided series. The series is not modified by this operation.
     *
     * @param series the series to query
     * @return the queried result
     * @throws IllegalArgumentException if the query cannot be performed on the provided series
     */
    R queryFrom(S series);
}