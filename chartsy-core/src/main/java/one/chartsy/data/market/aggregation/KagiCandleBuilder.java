/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.market.aggregation;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;
import one.chartsy.Incomplete;
import one.chartsy.data.market.Tick;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

import static one.chartsy.data.market.aggregation.Kagi.Trend.*;

public class KagiCandleBuilder<C extends Candle, T extends Tick> implements Incomplete<Kagi<C>> {

    protected final CandleBuilder<C,T> base;
    protected double kagiHigh;
    protected double kagiLow;
    protected double kagiPreviousHigh = Double.NaN;
    protected double kagiPreviousLow = Double.NaN;
    protected int formedElementsCount;
    protected Kagi.Trend kagiTrend = UNSPECIFIED;
    protected Kagi.Trend direction = UNSPECIFIED;
    protected final List<Consumer<CandleBuilder<C,T>>> pendingItems = new LinkedList<>();

    public KagiCandleBuilder(CandleBuilder<C,T> baseBuilder) {
        this.base = baseBuilder;
    }

    @Override
    public final boolean isPresent() {
        return base.isPresent();
    }

    public Kagi.Trend direction() {
        return direction;
    }

    public void assumeDirection(Kagi.Trend direction) {
        this.direction = kagiTrend = direction;
    }

    public void clear() {
        base.clear();
        formedElementsCount = 0;
    }

    protected void addBaseElements(List<Consumer<CandleBuilder<C, T>>> elements) {
        Iterator<Consumer<CandleBuilder<C, T>>> iter = elements.iterator();
        while (iter.hasNext()) {
            iter.next().accept(base);
            formedElementsCount++;
            iter.remove();
        }
    }

    protected void addBaseCandle(C c) {
        if (!pendingItems.isEmpty())
            addBaseElements(pendingItems);
        base.addCandle(c);
        formedElementsCount++;
    }

    protected void addBaseTick(T t) {
        if (!pendingItems.isEmpty())
            addBaseElements(pendingItems);
        base.addTick(t);
        formedElementsCount++;
    }

    public void turnaround() {
        if (direction == BULLISH) {
            direction = BEARISH;
            kagiPreviousLow = kagiLow;
            kagiLow = kagiHigh;
        } else if (direction == BEARISH) {
            direction = BULLISH;
            kagiPreviousHigh = kagiHigh;
            kagiHigh = kagiLow;
        } else
            return;

        clear();
    }

    public void addCandle(C c) {
        if (direction == UNSPECIFIED) {
            if (!base.isPresent())
                kagiHigh = kagiLow = c.open();
            base.addCandle(c);
            formedElementsCount++;
            kagiHigh = Math.max(kagiHigh, c.high());
            kagiLow = Math.min(kagiLow, c.low());
            return;
        }
        else if (direction == BULLISH) {
            if (c.high() >= kagiHigh) {
                if ((kagiHigh = c.high()) > kagiPreviousHigh)
                    kagiTrend = BULLISH;
                addBaseCandle(c);
                return;
            }
        }
        else if (direction == BEARISH) {
            if (c.low() <= kagiLow) {
                if ((kagiLow = c.low()) < kagiPreviousLow)
                    kagiTrend = BEARISH;
                addBaseCandle(c);
                return;
            }
        }

        pendingItems.add(cb -> cb.addCandle(c));
    }

    public void addTick(T t) {
        if (direction == UNSPECIFIED) {
            if (!base.isPresent())
                kagiHigh = kagiLow = t.price();
            base.addTick(t);
            formedElementsCount++;
            kagiHigh = Math.max(kagiHigh, t.price());
            kagiLow = Math.min(kagiLow, t.price());
            return;
        }
        else if (direction == BULLISH) {
            if (t.price() >= kagiHigh) {
                if ((kagiHigh = t.price()) > kagiPreviousHigh)
                    kagiTrend = BULLISH;
                addBaseTick(t);
                return;
            }
        }
        else if (direction == BEARISH) {
            if (t.price() <= kagiLow) {
                if ((kagiLow = t.price()) < kagiPreviousLow)
                    kagiTrend = BEARISH;
                addBaseTick(t);
                return;
            }
        }

        pendingItems.add(cb -> cb.addTick(t));
    }

    @Override
    public Kagi<C> get() {
        if (!isPresent())
            return null;

        var base = this.base.get();
        var bullish = (direction() == BULLISH);
        var openPrice = bullish? kagiLow: kagiHigh;
        var closePrice = bullish? kagiHigh: kagiLow;
        return new Kagi<>(base, openPrice, closePrice, kagiTrend, kagiPreviousLow, kagiPreviousHigh, formedElementsCount);
    }
}
