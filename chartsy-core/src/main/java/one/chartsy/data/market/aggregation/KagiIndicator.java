/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.market.aggregation;

import one.chartsy.Candle;
import one.chartsy.Incomplete;
import one.chartsy.data.ReversalAmount;
import one.chartsy.data.ReversalType;
import one.chartsy.data.SimpleCandleBuilder;
import one.chartsy.data.market.Tick;

import java.util.ArrayList;
import java.util.List;

import static one.chartsy.data.market.aggregation.Kagi.Trend.*;

public class KagiIndicator implements Incomplete<Kagi<Candle>> {

    private final ReversalAmount reversalAmount;
    private final KagiCandleBuilder<Candle, Tick> kagi;
    private final List<Kagi<Candle>> output = new ArrayList<>();

    private double kagiOpen;
    private double kagiHigh;
    private double kagiLow;
    private double kagiTurnaroundPrice;


    public KagiIndicator(ReversalAmount reversalAmount) {
        this.reversalAmount = reversalAmount;
        this.kagi = new KagiCandleBuilder<>(new SimpleCandleBuilder());
    }

    public final ReversalAmount getReversalAmount() {
        return reversalAmount;
    }

    public final KagiCandleBuilder<Candle, Tick> getKagiCandleBuilder() {
        return kagi;
    }

    public int length() {
        return output.size();
    }

    public Kagi<Candle> get(int index) {
        var mappedIndex = output.size() - 1 - index;
        return output.get(mappedIndex);
    }

    public void addOutput(Kagi<Candle> candle) {
        output.add(candle);
    }

    public void addOutputUpdate(Kagi<Candle> candle) {
        if (output.isEmpty())
            addOutput(candle);
        else
            output.set(output.size() - 1, candle);
    }

    public void addOutputUpdate(Incomplete<Kagi<Candle>> candle) {
        addOutputUpdate(candle.get());
    }

    public Kagi.Trend getKagiDirection() {
        var direction = kagi.direction();
        if (direction == UNSPECIFIED && kagi.isPresent()) {

            if (kagiLow <= reversalAmount.getTurnaroundPrice(kagiOpen, ReversalType.BEARISH))
                direction = DOWN;
            else if (kagiHigh >= reversalAmount.getTurnaroundPrice(kagiOpen, ReversalType.BULLISH))
                direction = UP;

            if (direction != UNSPECIFIED) {
                kagi.assumeDirection(direction);
                if (direction == UP)
                    kagiTurnaroundPrice = reversalAmount.getTurnaroundPrice(kagiHigh, ReversalType.BEARISH);
                else
                    kagiTurnaroundPrice = reversalAmount.getTurnaroundPrice(kagiLow, ReversalType.BULLISH);
            }
        }
        return direction;
    }

    @Override
    public Kagi<Candle> get() {
        return getKagiCandleBuilder().get();
    }

    public void onCandle(Candle c) {
        var trend = getKagiDirection();
        if (trend == UNSPECIFIED) {
            if (!kagi.isPresent())
                kagiOpen = kagiHigh = kagiLow = c.open();

            kagi.addCandle(c);
            kagiHigh = Math.max(kagiHigh, c.high());
            kagiLow = Math.min(kagiLow, c.low());
            return;
        }

        if (trend == UP) { // currently building candle is bullish
            if (c.low() <= kagiTurnaroundPrice) {
                // top-type reversal
                if (c.isBearish() && c.high() > kagiHigh) {
                    kagi.addCandle(c);
                    kagiHigh = c.high();
                }
                addOutputUpdate(get());
                kagi.turnaround();
                kagi.addCandle(c);
                kagiLow = c.low();
                kagiTurnaroundPrice = reversalAmount.getTurnaroundPrice(kagiLow, ReversalType.BULLISH);
                addOutput(get());
                return;
            }
            if (c.high() > kagiHigh) {
                kagiHigh = c.high();
                kagiTurnaroundPrice = reversalAmount.getTurnaroundPrice(kagiHigh, ReversalType.BEARISH);

                if (c.close() <= kagiTurnaroundPrice) {
                    kagi.addCandle(c);
                    addOutputUpdate(get());
                    kagi.turnaround();
                    kagi.addCandle(c);
                    kagiLow = c.low();
                    kagiTurnaroundPrice = reversalAmount.getTurnaroundPrice(kagiLow, ReversalType.BULLISH);
                    addOutput(get());
                    return;
                }
            }
        }
        if (trend == DOWN) { // currently building candle is bearish
            if (c.high() >= kagiTurnaroundPrice) {
                // bottom-type reversal
                if (c.isBullish() && c.low() < kagiLow) {
                    kagi.addCandle(c);
                    kagiLow = c.low();
                }
                addOutputUpdate(get());
                kagi.turnaround();
                kagi.addCandle(c);
                kagiHigh = c.high();
                kagiTurnaroundPrice = reversalAmount.getTurnaroundPrice(kagiHigh, ReversalType.BEARISH);
                addOutput(get());
                return;
            }
            if (c.low() < kagiLow) {
                kagiLow = c.low();
                kagiTurnaroundPrice = reversalAmount.getTurnaroundPrice(kagiLow, ReversalType.BULLISH);

                if (c.close() >= kagiTurnaroundPrice) {
                    kagi.addCandle(c);
                    addOutputUpdate(get());
                    kagi.turnaround();
                    kagi.addCandle(c);
                    kagiHigh = c.high();
                    kagiTurnaroundPrice = reversalAmount.getTurnaroundPrice(kagiHigh, ReversalType.BEARISH);
                    addOutput(get());
                    return;
                }
            }
        }
        kagi.addCandle(c);
        addOutputUpdate(this);
    }

    public void onTick(Tick t) {
        var trend = getKagiDirection();
        if (trend == UNSPECIFIED) {
            if (!kagi.isPresent())
                kagiOpen = kagiHigh = kagiLow = t.price();

            kagi.addTick(t);
            kagiHigh = Math.max(kagiHigh, t.price());
            kagiLow = Math.min(kagiLow, t.price());
            return;
        }

        if (trend == UP) { // currently building candle is bullish
            if (t.price() <= kagiTurnaroundPrice) {
                // top-type reversal
                addOutputUpdate(get());
                kagi.turnaround();
                kagi.addTick(t);
                kagiLow = t.price();
                kagiTurnaroundPrice = reversalAmount.getTurnaroundPrice(kagiLow, ReversalType.BULLISH);
                addOutput(get());
                return;
            }
            if (t.price() > kagiHigh) {
                kagiHigh = t.price();
                kagiTurnaroundPrice = reversalAmount.getTurnaroundPrice(kagiHigh, ReversalType.BEARISH);
            }
        }
        if (trend == DOWN) { // currently building candle is bearish
            if (t.price() >= kagiTurnaroundPrice) {
                // bottom-type reversal
                addOutputUpdate(get());
                kagi.turnaround();
                kagi.addTick(t);
                kagiHigh = t.price();
                kagiTurnaroundPrice = reversalAmount.getTurnaroundPrice(kagiHigh, ReversalType.BEARISH);
                addOutput(get());
                return;
            }
            if (t.price() < kagiLow) {
                kagiLow = t.price();
                kagiTurnaroundPrice = reversalAmount.getTurnaroundPrice(kagiLow, ReversalType.BULLISH);
            }
        }
        kagi.addTick(t);
        addOutputUpdate(this);
    }
}
