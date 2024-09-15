/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.financial.AbstractDoubleIndicator;

public class Frama extends AbstractDoubleIndicator {
    private final double compliance;
    private final double alpha;
    private final FractalDimension fdi;
    private double last = Double.NaN;

    public Frama(int length) {
        this(length, length * 3, 5.0);
    }

    public Frama(int length, int averagingLength, double compliance) {
        this.fdi = new FractalDimension(length);
        this.alpha = 2.0/(1.0 + averagingLength);
        this.compliance = compliance;
    }

    @Override
    public void accept(double value) {
        fdi.accept(value);

        if (fdi.isReady()) {
            double weight = fdi.getLast();
            if (weight > 1.7)
                weight = 1.7;
            else if (weight < 1.3)
                weight = 1.3;

            double coeff = alpha*Math.exp(-compliance*(weight - 1.5));
            last = Double.isNaN(last) ? value : (value - last)*coeff + last;
        }
    }

    @Override
    public final double getLast() {
        return last;
    }

    @Override
    public boolean isReady() {
        return fdi.isReady();
    }
}
