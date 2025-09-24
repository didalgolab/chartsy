/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.market.aggregation;

import one.chartsy.Candle;
import one.chartsy.data.AbstractCandle;

public class Kagi<C extends Candle> extends AbstractCandle<C> {
    public enum Trend {
        UNSPECIFIED, DOWN, UP
    }

    private final double open;
    private final double close;
    private final double yangLevel;
    private final double yinLevel;
    private final Trend trend;
    private final int formedElementsCount;

    protected Kagi(C baseCandle, double open, double close, Trend trend, double yinLevel, double yangLevel, int formedElementsCount) {
        super(baseCandle);
        if (!Double.isNaN(yangLevel) && !Double.isNaN(yinLevel) && yangLevel <= yinLevel) {
            throw new IllegalArgumentException(
                    String.format("Yang level cannot be less than or equal to Yin level, was: %s vs %s", yangLevel, yinLevel));
        }
        this.open = open;
        this.close = close;
        this.trend = trend;
        this.yinLevel = yinLevel;
        this.yangLevel = yangLevel;
        this.formedElementsCount = formedElementsCount;
    }

    public static <C extends Candle> Kagi<C> of(C base, double open, double close, Trend trend, double yinLevel, double yangLevel, int formedElementsCount) {
        return new Kagi<>(base, open, close, trend, yinLevel, yangLevel, formedElementsCount);
    }

    public Trend trend() {
        return trend;
    }

    public double yangLevel() {
        return yangLevel;
    }

    public double yinLevel() {
        return yinLevel;
    }

    @Override
    public double open() {
        return open;
    }

    @Override
    public double high() {
        return Math.max(open, close);
    }

    @Override
    public double low() {
        return Math.min(open, close);
    }

    @Override
    public double close() {
        return close;
    }

    /**
     * The number of ticks or candles which were used to form the current bar.
     */
    public int formedElementsCount() {
        return formedElementsCount;
    }

    @Override
    public String toString() {
        return "{\"" + instant() + "\": {\"Kagi\": {\"OC\":[" + open() + "," + close() + "], \"trend\":\"" + trend() + "\"}}}";
    }
}
