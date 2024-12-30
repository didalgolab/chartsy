/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.data.structures.DoubleWindowSummaryStatistics;
import one.chartsy.financial.ValueIndicator;

import java.util.function.DoubleConsumer;

public class ExponentialMovingAverage implements ValueIndicator.OfDouble, DoubleConsumer {
    private final int length;
    private final double multiplier;
    private DoubleWindowSummaryStatistics sma;
    private double last = Double.NaN;

    public ExponentialMovingAverage(int length) {
        this.length = length;
        this.multiplier = 2.0/(length + 1.0);
        this.sma = new DoubleWindowSummaryStatistics(length);
    }

    @Override
    public void accept(double value) {
        if (sma == null) {
            last = value*multiplier + last*(1.0 - multiplier);
        } else {
            sma.accept(value);
            if (sma.isFull()) {
                last = sma.getAverage();
                sma = null;
            }
        }
    }

    @Override
    public double getLast() {
        return last;
    }

    @Override
    public boolean isReady() {
        return sma == null;
    }
}
