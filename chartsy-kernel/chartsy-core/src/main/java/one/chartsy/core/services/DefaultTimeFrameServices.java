/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.core.services;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;
import one.chartsy.Incomplete;
import one.chartsy.TimeFrameAggregator;
import one.chartsy.core.TimeFrameServices;
import one.chartsy.data.market.*;
import org.openide.util.lookup.ServiceProvider;

import java.time.Duration;
import java.time.temporal.TemporalAmount;
import java.util.function.Consumer;

@ServiceProvider(service = TimeFrameServices.class)
public class DefaultTimeFrameServices implements TimeFrameServices {

    private static final TimeFrameAggregator<Candle, Tick> tickOnlyAggregator = new TimeFrameAggregator<>() {
        @Override
        public Incomplete<Candle> addCandle(Candle candle, Consumer<Candle> completedItemConsumer) {
            throw new UnsupportedOperationException("Tick-only TimeFrameAggregator");
        }

        @Override
        public Incomplete<Candle> addTick(Tick tick, Consumer<Candle> completedItemConsumer) {
            completedItemConsumer.accept(tick.toCandle());
            return Incomplete.empty();
        }
    };

    @Override
    public TimeFrameAggregator<Candle, Tick> createTickOnlyAggregator() {
        return tickOnlyAggregator;
    }

    @Override
    public <C extends Candle, T extends Tick> TimeCandleAggregator<C,T> createTimeCandleAggregator(
            Duration granularity,
            CandleBuilder<C,T> builder,
            TimeCandleAlignment alignment) {

        return new TimeCandleAggregator<>(builder, granularity, alignment);
    }

    @Override
    public <C extends Candle, T extends Tick> PeriodCandleAggregator<C,T> createPeriodCandleAggregator(
            TemporalAmount periodicity,
            CandleBuilder<C,T> builder,
            DateCandleAlignment alignment) {

        return new PeriodCandleAggregator<>(builder, periodicity, alignment);
    }
}
