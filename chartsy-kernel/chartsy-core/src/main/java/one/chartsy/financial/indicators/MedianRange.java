/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.Candle;
import one.chartsy.data.structures.RingBuffer;
import one.chartsy.financial.AbstractCandleIndicator;
import one.chartsy.math.MovingMedian;
import one.chartsy.study.ChartStudy;
import one.chartsy.study.LinePlotSpec;
import one.chartsy.study.StudyFactory;
import one.chartsy.study.StudyInputKind;
import one.chartsy.study.StudyKind;
import one.chartsy.study.StudyOutput;
import one.chartsy.study.StudyParameter;
import one.chartsy.study.StudyParameterScope;
import one.chartsy.study.StudyParameterType;
import one.chartsy.study.StudyPlacement;

@ChartStudy(
        name = "Median Range",
        label = "MedianRange({periods})",
        category = "Volatility",
        kind = StudyKind.INDICATOR,
        placement = StudyPlacement.OWN_PANEL
)
@StudyParameter(id = "lineColor", name = "Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#D33682", order = 100)
@StudyParameter(id = "lineStyle", name = "Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "THIN_SOLID", order = 110)
@LinePlotSpec(id = "medianRange", label = "MedianRange", output = "value", colorParameter = "lineColor", strokeParameter = "lineStyle", order = 10)
public class MedianRange extends AbstractCandleIndicator {

    private final int periods;
    private final RingBuffer<Candle> window;
    private double lastValue = Double.NaN;

    @StudyFactory(input = StudyInputKind.CANDLES)
    public static MedianRange study(
            @StudyParameter(id = "periods", name = "Periods", scope = StudyParameterScope.COMPUTATION, defaultValue = "14", order = 10) int periods
    ) {
        return new MedianRange(periods);
    }

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
    @StudyOutput(id = "value", name = "MedianRange", order = 10)
    public double getLast() {
        return lastValue;
    }

    @Override
    public boolean isReady() {
        return window.isFull();
    }
}
