/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.market;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;
import one.chartsy.Incomplete;
import one.chartsy.TimeFrameAggregator;

import java.util.function.Consumer;

public abstract class AbstractCandleAggregator<C extends Candle, T extends Tick> implements TimeFrameAggregator<C, T> {

    protected final CandleBuilder<C, T> candle;


    public AbstractCandleAggregator(CandleBuilder<C, T> builder) {
        this.candle = builder;
    }

    public void complete(Consumer<C> completedItemConsumer) {
        if (candle.isPresent()) {
            completedItemConsumer.accept(candle.get());
            candle.clear();
        }
    }

    @Override
    public Incomplete<C> addCandle(C c, Consumer<C> completedItemConsumer) {
        candle.addCandle(c);
        return candle;
    }

    @Override
    public Incomplete<C> addTick(T tick, Consumer<C> completedItemConsumer) {
        candle.addTick(tick);
        return candle;
    }
}
