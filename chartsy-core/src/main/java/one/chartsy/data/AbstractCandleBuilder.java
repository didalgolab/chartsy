/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;
import one.chartsy.data.market.Tick;

import javax.annotation.Nullable;

public abstract class AbstractCandleBuilder<C extends Candle, T extends Tick> implements CandleBuilder<C, T> {

    private boolean present;
    protected long time;
    protected double open;
    protected double high;
    protected double low;
    protected double close;
    protected double volume;
    protected int count;
    protected int formedElementsCount;

    public final double highPrice() {
        return high;
    }

    public final double lowPrice() {
        return low;
    }

    @Override
    public final void clear() {
        this.present = false;
    }

    @Override
    public final boolean isPresent() {
        return present;
    }

    protected void setPresent() {
        this.present = true;
    }

    @Nullable
    public final SimpleCandle getAsSimpleCandle() {
        if (isPresent())
            return SimpleCandle.of(time, open, high, low, close, volume, count);
        else
            return null;
    }

    public int getFormedElementsCount() {
        return formedElementsCount;
    }

    protected void putCandle(C c) {
        clear();
        addCandle(c);
    }

    protected void putTick(T t) {
        clear();
        addTick(t);
    }

    @Override
    public void addCandle(C c) {
        time = c.getTime();
        if (!isPresent()) {
            open = c.open();
            high = c.high();
            low = c.low();
            close = c.close();
            volume = c.volume();
            count = c.count();
            formedElementsCount = 1;
            setPresent();
        } else {
            high = Math.max(high, c.high());
            low = Math.min(low, c.low());
            close = c.close();
            volume += c.volume();
            count += c.count();
            formedElementsCount++;
        }
    }

    @Override
    public void addTick(Tick t) {
        time = t.getTime();
        if (!isPresent()) {
            open = high = low = close = t.price();
            volume = t.size();
            count = 1;
            formedElementsCount = 1;
            setPresent();
        } else {
            var price = t.price();
            high = Math.max(high, price);
            low = Math.min(low, price);
            close = price;
            volume += t.size();
            formedElementsCount++;
            count++;
        }
    }
}
