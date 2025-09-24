/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.market;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;
import one.chartsy.Incomplete;

import java.util.function.Consumer;

public abstract class TimePeriodCandleAggregator<C extends Candle, T extends Tick> extends AbstractCandleAggregator<C, T> {

    protected TimePeriodCandleAggregator(CandleBuilder<C, T> builder) {
        super(builder);
    }

    protected abstract boolean isCompletedOn(long time);

    @Override
    public Incomplete<C> addCandle(C c, Consumer<C> completedItemConsumer) {
        if (isCompletedOn(c.time()))
            complete(completedItemConsumer);
        return super.addCandle(c, completedItemConsumer);
    }

    @Override
    public Incomplete<C> addTick(T t, Consumer<C> completedItemConsumer) {
        if (isCompletedOn((t.time() + 1)))
            complete(completedItemConsumer);
        return super.addTick(t, completedItemConsumer);
    }
}
