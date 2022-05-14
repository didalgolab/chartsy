/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.Candle;

public abstract class AbstractCandle<C extends Candle> implements Candle {

    private final C baseCandle;

    protected AbstractCandle(C c) {
        this.baseCandle = c;
    }

    public final C baseCandle() {
        return baseCandle;
    }

    @Override
    public long getTime() {
        return baseCandle().getTime();
    }

    @Override
    public double open() {
        return baseCandle().open();
    }

    @Override
    public double high() {
        return baseCandle().high();
    }

    @Override
    public double low() {
        return baseCandle().low();
    }

    @Override
    public double close() {
        return baseCandle().close();
    }

    @Override
    public double volume() {
        return baseCandle().volume();
    }

    @Override
    public int count() {
        return baseCandle().count();
    }
}
