/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
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
    public long time() {
        return baseCandle().time();
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
}
