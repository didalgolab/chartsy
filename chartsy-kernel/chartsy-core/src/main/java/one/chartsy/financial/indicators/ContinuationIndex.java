/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.data.structures.DoubleWindowSummaryStatistics;
import one.chartsy.financial.AbstractDoubleIndicator;
import org.apache.commons.math3.util.FastMath;

/**
 * Continuation Index indicator providing early indication of trend onset and exhaustion.
 *
 * <p>The indicator is based on the difference between an {@link UltimateSmoother}
 * filter and a Laguerre filter. The difference is normalized by its average absolute
 * deviation and compressed using an inverse Fisher transform to produce values
 * oscillating around +1 and -1.</p>
 *
 * <p>Implementation inspired by John F. Ehlers' article "The Continuation Index" in
 * <i>Stocks & Commodities</i> (September 2025).</p>
 */
public class ContinuationIndex extends AbstractDoubleIndicator {

    private final int length;
    private final UltimateSmoother smoother;
    private final LaguerreFilter laguerre;
    private final DoubleWindowSummaryStatistics diffStats;
    private double last = Double.NaN;

    /**
     * Creates a new Continuation Index indicator.
     *
     * @param gamma  smoothing factor of the Laguerre filter (0 &le; gamma &lt; 1)
     * @param order  order of the Laguerre filter (typically 1..10)
     * @param length smoothing length controlling responsiveness
     */
    public ContinuationIndex(double gamma, int order, int length) {
        this.length = length;
        this.smoother = new UltimateSmoother(Math.max(1, length / 2));
        this.laguerre = new LaguerreFilter(gamma, order, length);
        this.diffStats = new DoubleWindowSummaryStatistics(length);
    }

    @Override
    public void accept(double price) {
        double us = smoother.smooth(price);
        double lg = laguerre.filter(price);
        double diff = us - lg;
        diffStats.add(FastMath.abs(diff));
        double avgDiff = diffStats.getAverage();
        double ref = (avgDiff != 0.0) ? 2.0 * diff / avgDiff : 0.0;
        last = FastMath.tanh(ref);
    }

    @Override
    public double getLast() {
        return last;
    }

    @Override
    public boolean isReady() {
        return diffStats.getCount() >= length;
    }

    /**
     * The Laguerre filter implementation used by the Continuation Index.
     */
    static class LaguerreFilter {
        private final int order;
        private final double gamma;
        private final UltimateSmoother smoother;
        private final double[] curr;
        private final double[] prev;
        private double last = Double.NaN;

        LaguerreFilter(double gamma, int order, int length) {
            if (gamma < 0.0 || gamma >= 1.0)
                throw new IllegalArgumentException("gamma must be in [0,1)");
            if (order < 1)
                throw new IllegalArgumentException("order must be >= 1");
            this.gamma = gamma;
            this.order = order;
            this.smoother = new UltimateSmoother(length);
            this.curr = new double[order + 1];
            this.prev = new double[order + 1];
        }

        double filter(double price) {
            // Shift previous values
            System.arraycopy(curr, 0, prev, 0, curr.length);
            // Compute Laguerre components
            for (int i = 2; i <= order; i++) {
                curr[i] = -gamma * prev[i - 1] + prev[i - 1] + gamma * prev[i];
            }
            curr[1] = smoother.smooth(price);
            double fir = 0.0;
            for (int i = 1; i <= order; i++) {
                fir += curr[i];
            }
            last = fir / order;
            return last;
        }

        double getLast() {
            return last;
        }
    }
}

