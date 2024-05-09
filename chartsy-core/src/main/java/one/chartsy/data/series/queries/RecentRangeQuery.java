/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.series.queries;

import one.chartsy.Candle;
import one.chartsy.base.Dataset;
import one.chartsy.base.dataset.ImmutableDataset;
import one.chartsy.core.Range;
import one.chartsy.data.Series;
import one.chartsy.data.SeriesQuery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * A query that computes the recent range (highest high and lowest low) for each candle in a series.
 */
public class RecentRangeQuery implements SeriesQuery<Series<Candle>, Dataset<Range>, Candle> {
    private final int nthExtrema;

    public RecentRangeQuery() {
        this(1);
    }

    public RecentRangeQuery(int nthExtrema) {
        this.nthExtrema = nthExtrema;
    }

    @Override
    public Dataset<Range> queryFrom(Series<Candle> series) {
        List<Range> ranges = new ArrayList<>();
        Calculator tracker = new Calculator(nthExtrema);

        for (int i = 0; i < series.length(); i++) {
            Candle candle = series.get(i);
            tracker.add(candle.low(), candle.high());
            ranges.add(Range.of(tracker.getMin(), tracker.getMax()));
        }

        return ImmutableDataset.of(ranges);
    }

    /**
     * A helper class to efficiently track the highest and lowest values in a series.
     */
    private static class Calculator {
        private final PriorityQueue<Double> minHeap; // For n-th highest
        private final PriorityQueue<Double> maxHeap; // For n-th lowest
        private final int n;

        public Calculator(int nthExtrema) {
            if (nthExtrema <= 0) {
                throw new IllegalArgumentException("n must be greater than 0");
            }
            this.n = nthExtrema;
            this.minHeap = new PriorityQueue<>();
            this.maxHeap = new PriorityQueue<>(Collections.reverseOrder());
        }

        public void add(double minValue, double maxValue) {
            if (minHeap.size() < n || maxValue > getMax()) {
                minHeap.offer(maxValue);
                if (minHeap.size() > n) {
                    minHeap.poll();
                }
            }

            if (maxHeap.size() < n || minValue < getMin()) {
                maxHeap.offer(minValue);
                if (maxHeap.size() > n) {
                    maxHeap.poll();
                }
            }
        }

        public void add(double value) {
            add(value, value);
        }

        public double getMax() {
            return minHeap.peek();
        }

        public double getMin() {
            return maxHeap.peek();
        }
    }
}