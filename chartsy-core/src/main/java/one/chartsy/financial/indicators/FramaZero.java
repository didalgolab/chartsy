/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.Candle;
import one.chartsy.financial.AbstractCandleIndicator;

public class FramaZero extends AbstractCandleIndicator {
    public static int DEFAULT_FRAMA_PERIOD = 45;
    public static int DEFAULT_ATR_PERIOD = 15;
    private final Frama frama;
    private final AverageTrueRange atr;
    private double last;

    public FramaZero() {
        this(DEFAULT_FRAMA_PERIOD, DEFAULT_ATR_PERIOD);
    }

    public FramaZero(int framaPeriods) {
        this(framaPeriods, DEFAULT_ATR_PERIOD);
    }

    public FramaZero(int framaPeriods, int atrPeriods) {
        this.frama = new Frama(framaPeriods);
        this.atr = new AverageTrueRange(atrPeriods);
    }

    @Override
    public void accept(Candle bar) {
        frama.accept(bar.close());
        atr.accept(bar);
        last = frama.getLast() + atr.getLast();
    }

    @Override
    public double getLast() {
        return last;
    }

    @Override
    public boolean isReady() {
        return frama.isReady() && atr.isReady();
    }
}
