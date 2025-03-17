/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.base.Dataset;
import one.chartsy.base.dataset.ImmutableDataset;
import one.chartsy.data.packed.PackedCandleSeries;
import one.chartsy.time.AbstractTimeline;
import one.chartsy.time.Chronological;
import one.chartsy.time.Timeline;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * This class provides utility methods for working with candle series data.
 *
 * @author Mariusz Bernacki
 */
public class CandleSeriesSupport {

	/**
	 * Retrieves a subset of candles from the specified candle series that occur before
	 * the given date and time, up to the specified maximum number of preceding candles.
	 *
	 * @param bars the candle series to extract candles from
	 * @param dateTime the date and time before which to retrieve the candles
	 * @param numBars the maximum number of candles to retrieve
	 * @return a dataset containing the requested candles
	 * @throws IllegalArgumentException if {@code numBars} is negative
	 */
	public static <E extends Candle> Dataset<E> getBarsBefore(Series<? extends E> bars, LocalDateTime dateTime, int numBars) {
		return getBarsBefore(bars, Chronological.toEpochNanos(dateTime), numBars);
	}

	/**
	 * Retrieves a subset of candles from the specified candle series that occur before
	 * the given time, up to the specified maximum number of preceding candles.
	 *
	 * @param bars    the candle series to extract candles from
	 * @param time    the timestamp before which to retrieve the candles
	 * @param numBars the maximum number of candles to retrieve
	 * @return a dataset containing the requested candles
	 * @throws IllegalArgumentException if {@code numBars} is negative
	 */
	public static <E extends Candle> Dataset<E> getBarsBefore(Series<? extends E> bars, long time, int numBars) {
		if (numBars < 0) {
			throw new IllegalArgumentException("Number of bars must be non-negative");
		}

		var timeline = bars.getTimeline();
		int index = timeline.getTimeLocation(time);
		if (index < 0) {
			index = -(index + 1);
		}
		int startIndex = Math.min(index, bars.length());
		@SuppressWarnings("unchecked") var dataset = (Dataset<E>) bars.getData().dropTake(startIndex, numBars);
		return dataset;
	}

	/**
	 * Computes a unified timeline of the given series.
	 *
	 * @param seriesCollection the collection of series
	 * @return a unified timeline
	 */
	public static Timeline getUnifiedTimeline(Iterable<? extends Series<? extends Chronological>> seriesCollection) {
		var order = Chronological.ChronoOrder.REVERSE_CHRONOLOGICAL;
		var times = StreamSupport.stream(seriesCollection.spliterator(), false)
				.mapMulti((Series<? extends Chronological> series, Consumer<Chronological> result) -> series.forEach(result))
				.collect(Collectors.toCollection(() -> new TreeSet<>(order.comparator())))
				.stream()
				.toList();

		return new AbstractTimeline(order) {
			@Override public int length() { return times.size(); }
			@Override public long getTimeAt(int x) { return times.get(x).getTime(); }
		};
	}

	/**
	 * Synchronizes the timelines of multiple candle series to a common timeline. Missing
	 * data points in the original series are filled with candles having the previous
	 * candle's closing price and zero volume.
	 *
	 * <p>The method supports efficient streamed calculation without the need to
	 * materialize the entire series data in memory, however the provided {@code Iterable}
	 * is processed twice: once to compute the unified timeline and second time to iterate
	 * through and calculate the aligned series.
	 *
	 * @param seriesCollection a collection of candle series to synchronize
	 * @return a stream of candle series with synchronized timelines
	 * @see #getUnifiedTimeline(Iterable)
	 */
	public static <E extends Candle> Stream<Series<Candle>> synchronizeTimelines(Iterable<? extends Series<? extends E>> seriesCollection) {
		Timeline timeline = getUnifiedTimeline(seriesCollection);

		return StreamSupport.stream(seriesCollection.spliterator(), false)
				.map(series -> synchronizeTimeline(series, timeline));
	}

	/**
	 * Synchronizes the timeline of a given candle series with a provided desired timeline.
	 * If no candle exists for a particular timestamp in the unified timeline, a new candle
	 * is created with the previous candle's closing price and zero volume.
	 *
	 * @param series    the candle series to synchronize
	 * @param timeline  the timeline to synchronize the series with
	 * @return a new candle series with the desired timeline
	 * @throws AssertionError if the series cannot be aligned, for example if it contains
	 * timestamps which doesn't exist in the {@code timeline}
	 */
	public static Series<Candle> synchronizeTimeline(Series<? extends Candle> series, Timeline timeline) {
		Iterator<? extends Candle> iter = series.iterator();
		if (iter.hasNext()) {
			Candle curr = iter.next(), prev = null;
			int timeIndex = timeline.getTimeLocation(curr.getTime());
			if (timeIndex < 0) {
				timeIndex = -timeIndex - 1;
			}
			List<Candle> bars = new ArrayList<>(timeIndex + 1);

			// Iterate through the timeline
			for (; timeIndex >= 0; timeIndex--) {
				long time = timeline.getTimeAt(timeIndex);

				if (curr.getTime() > time && prev != null) {
					bars.add(Candle.of(time, prev.close()));
				}
				else if (curr.getTime() == time) {
					bars.add(curr);
					if (iter.hasNext()) {
						prev = curr;
						curr = iter.next();
					} else if (curr != MAX) {
						prev = curr;
						curr = MAX;
					}
				}
				else
					throw new AssertionError("Cannot synchronize with this Timeline");
			}
			return createSeries(series, bars, timeline);
		}
		return createSeries(series, List.of(), timeline);
	}

	@SuppressWarnings("unchecked")
	private static PackedCandleSeries createSeries(Series<? extends Candle> series, List<Candle> bars, Timeline timeline) {
		return new PackedCandleSeries((SymbolResource<Candle>) series.getResource(), ImmutableDataset.of(bars, true)) {
			@Override
			public Timeline getTimeline() {
				return timeline;
			}
		};
	}

	private static final Candle MAX = Candle.of(Long.MAX_VALUE, 0);

	private CandleSeriesSupport() {} // cannot instantiate
}
