/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.Candle;
import one.chartsy.data.structures.RingBuffer;
import one.chartsy.financial.AbstractCandleIndicator;
import one.chartsy.math.MovingMedian;

public class MedianRange extends AbstractCandleIndicator {

    private final int periods;
    private final RingBuffer<Candle> window;
    private double lastValue = Double.NaN;

    public MedianRange(int periods) {
        if (periods <= 0)
            throw new IllegalArgumentException("Periods must be positive");
        
        this.periods = periods;
        this.window = new RingBuffer<>(periods);
    }

    @Override
    public void accept(Candle candle) {
        window.add(candle);

        if (window.isFull())
            lastValue = calculate();
    }

    protected double calculate() {
        double highestHigh = Double.NEGATIVE_INFINITY;
        double lowestLow = Double.POSITIVE_INFINITY;

        MovingMedian.OfDouble median = new MovingMedian.OfDouble(periods);
        for (int i = 0; i < periods; i++) {
            Candle bar = window.get(i);
            highestHigh = Math.max(highestHigh, bar.high());
            lowestLow = Math.min(lowestLow, bar.low());

            double range = highestHigh - lowestLow;
            median.accept(range * range);
        }

        return Math.sqrt(median.getMedian());
    }

    @Override
    public double getLast() {
        return lastValue;
    }

    @Override
    public boolean isReady() {
        return window.isFull();
    }
}
