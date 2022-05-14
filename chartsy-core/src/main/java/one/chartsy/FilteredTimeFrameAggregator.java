/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import one.chartsy.data.market.Tick;
import one.chartsy.time.Chronological;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class FilteredTimeFrameAggregator<C extends Candle, T extends Tick> implements TimeFrameAggregator<C, T> {

    private final Predicate<? super C> candleFilter;
    private final Predicate<? super T> tickFilter;
    private final TimeFrameAggregator<C, T> target;
    private Incomplete<C> result = Incomplete.empty();

    public FilteredTimeFrameAggregator(Predicate<? super Chronological> filter, TimeFrameAggregator<C, T> target) {
        this(filter, filter, target);
    }

    public FilteredTimeFrameAggregator(Predicate<? super C> candleFilter, Predicate<? super T> tickFilter, TimeFrameAggregator<C, T> target) {
        this.candleFilter = candleFilter;
        this.tickFilter = tickFilter;
        this.target = target;
    }

    @Override
    public Incomplete<C> addCandle(C candle, Consumer<C> completedItemConsumer) {
        return candleFilter.test(candle)? (result = target.addCandle(candle, completedItemConsumer)) : result;
    }

    @Override
    public Incomplete<C> addTick(T tick, Consumer<C> completedItemConsumer) {
        return tickFilter.test(tick)? (result = target.addTick(tick, completedItemConsumer)) : result;
    }
}
