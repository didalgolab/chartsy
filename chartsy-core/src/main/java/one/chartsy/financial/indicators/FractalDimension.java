/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.data.structures.DoubleWindowSummaryStatistics;
import one.chartsy.financial.AbstractDoubleIndicator;

public class FractalDimension extends AbstractDoubleIndicator {
    private static final double LOG2 = Math.log(2);
    private final DoubleWindowSummaryStatistics values;
    private final double DX_POW2;
    private final double LOG_2N;
    private final int periods;
    private double last = 1.5;

    public FractalDimension(int periods) {
        if (periods <= 0)
            throw new IllegalArgumentException("The periods argument " + periods + " must be positive");

        this.values = new DoubleWindowSummaryStatistics(periods);
        this.DX_POW2 = Math.pow(periods - 1.0, -2.0);
        this.LOG_2N = Math.log(2.0 * (periods - 1));
        this.periods = periods;
    }

    @Override
    public void accept(double value) {
        values.add(value);

        var max = values.getMax();
        var min = values.getMin();
        if (values.isFull() && max - min > 0.0) {
            double diffPrevious = 0.0;
            double curveLength = 0.0;
            for (int i = 0; i < periods; i++) {
                double diff = (values.get(i) - min)/(max - min);
                if (i > 0)
                    curveLength += Math.sqrt((diff - diffPrevious)*(diff - diffPrevious) + DX_POW2);
                diffPrevious = diff;
            }

            if (curveLength > 0.0)
                last = 1.0 + (Math.log(curveLength) + LOG2) / LOG_2N;
        }
    }

    @Override
    public final double getLast() {
        return last;
    }

    @Override
    public boolean isReady() {
        return values.isFull();
    }
}
