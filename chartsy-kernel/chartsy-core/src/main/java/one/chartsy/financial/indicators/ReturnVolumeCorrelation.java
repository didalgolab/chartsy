/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.Candle;
import one.chartsy.data.structures.RingBuffer;
import one.chartsy.financial.AbstractCandleIndicator;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.util.FastMath;

/**
 * Computes the rolling Pearson correlation between logarithmic price returns
 * and logarithmic volume ratios.
 *
 * <p>The indicator first transforms price closes and volumes into
 * first‑order differences expressed as natural logarithms:
 * {@code log(close_t / close_{t-1})} and {@code log(volume_t / volume_{t-1})}.
 * The correlation is then evaluated over a sliding window of the most recent
 * {@code periods} observations.
 */
public class ReturnVolumeCorrelation extends AbstractCandleIndicator {

    /** Default number of periods used for correlation calculation. */
    public static final int DEFAULT_PERIODS = 30;

    private final int periods;
    private final RingBuffer.OfDouble returns;
    private final RingBuffer.OfDouble volumeRatios;
    private final PearsonsCorrelation pearsons = new PearsonsCorrelation();
    private Candle previous;
    private double last = Double.NaN;

    /**
     * Constructs the indicator with {@link #DEFAULT_PERIODS} look‑back window.
     */
    public ReturnVolumeCorrelation() {
        this(DEFAULT_PERIODS);
    }

    /**
     * Constructs the indicator with a custom look‑back window.
     *
     * @param periods number of periods over which the correlation is computed
     */
    public ReturnVolumeCorrelation(int periods) {
        if (periods <= 1)
            throw new IllegalArgumentException("Periods must be greater than 1");
        this.periods = periods;
        this.returns = new RingBuffer.OfDouble(periods);
        this.volumeRatios = new RingBuffer.OfDouble(periods);
    }

    @Override
    public void accept(Candle candle) {
        if (previous != null) {
            double prevClose = previous.close();
            double prevVolume = previous.volume();
            double close = candle.close();
            double volume = candle.volume();
            if (prevClose > 0 && close > 0 && prevVolume > 0 && volume > 0) {
                double r = FastMath.log(close / prevClose);
                double v = FastMath.log(volume / prevVolume);
                returns.add(r);
                volumeRatios.add(v);
                last = isReady() ? pearsons.correlation(returns.toPrimitiveArray(), volumeRatios.toPrimitiveArray()) : Double.NaN;
            }
        }
        previous = candle;
    }

    @Override
    public double getLast() {
        return last;
    }

    @Override
    public boolean isReady() {
        return returns.length() == periods;
    }
}
