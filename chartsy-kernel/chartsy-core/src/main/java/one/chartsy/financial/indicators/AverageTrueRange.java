/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.Candle;
import one.chartsy.financial.AbstractCandleIndicator;

public class AverageTrueRange extends AbstractCandleIndicator {
    private final int periods;
    private final double periodsAsDouble;
    private long count;
    private double closePrevious = Double.NaN;
    private double last = Double.NaN;

    public AverageTrueRange(int periods) {
        this.periods = periods;
        this.periodsAsDouble = periods;
    }

    @Override
    public void accept(Candle bar) {
        if (!Double.isNaN(this.closePrevious)) {
            double tr = Math.max(bar.high(), this.closePrevious) - Math.min(bar.low(), this.closePrevious);
            if (++count >= periods) {
                last = (last *(periodsAsDouble - 1.0) + tr)/periodsAsDouble;
            } else {
                last = (count == 1) ? tr : last + (tr - last)/count;
            }
        }
        closePrevious = bar.close();
    }

    @Override
    public final double getLast() {
        return last;
    }

    @Override
    public boolean isReady() {
        return count >= periods;
    }
}
