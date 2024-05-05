/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data;

import one.chartsy.Candle;
import one.chartsy.time.Chronological;

import java.time.LocalDateTime;

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
		return getBarsBefore(bars, Chronological.toEpochMicros(dateTime), numBars);
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
		@SuppressWarnings("unchecked") var dataset = (Dataset<E>) bars.getData().take(startIndex, numBars);
		return dataset;
	}

	private CandleSeriesSupport() {} // cannot instantiate
}
