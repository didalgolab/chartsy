/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.data.structures.RingBuffer;
import one.chartsy.financial.ValueIndicator;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;

public class FramaTrendEquilibrium implements ValueIndicator, DoubleConsumer {
    private final List<ValuePath> valuePaths = new ArrayList<>();
    private double min = Double.NaN;
    private double max = Double.NaN;

    record ValuePath(
            Frama frama,
            RingBuffer.OfDouble delayedValues
    ) {
    }

    public FramaTrendEquilibrium() {
        for (int i = 0; i < 5; i++) {
            valuePaths.add(new ValuePath(
                    new Frama(13, 63 + 15*i, 9.0),
                    new RingBuffer.OfDouble(41)
            ));
        }
    }

    @Override
    public void accept(double value) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (var val : valuePaths) {
            val.frama().accept(value);
            if (val.frama().isReady())
                val.delayedValues().add(val.frama().getLast());

            if (val.delayedValues().length() > 40) {
                for (int shift = 15; shift <= 40; shift += 5) {
                    var delayedValue = val.delayedValues().get(shift);
                    min = Math.min(min, delayedValue);
                    max = Math.max(max, delayedValue);
                }
            }
        }

        if (max > min) {
            this.min = min;
            this.max = max;
        }
    }

    public final double getMin() {
        return min;
    }

    public final double getMax() {
        return max;
    }

    public final double getAverage() {
        return (getMax() + getMin()) / 2.0;
    }

    @Override
    public boolean isReady() {
        return max > min;
    }
}
