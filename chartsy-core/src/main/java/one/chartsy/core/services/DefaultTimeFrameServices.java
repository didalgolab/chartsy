package one.chartsy.core.services;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;
import one.chartsy.Incomplete;
import one.chartsy.TimeFrameAggregator;
import one.chartsy.core.TimeFrameServices;
import one.chartsy.data.market.*;
import one.chartsy.time.Chronological;

import java.time.Duration;
import java.time.temporal.TemporalAmount;

public class DefaultTimeFrameServices implements TimeFrameServices {

    private static final TimeFrameAggregator<Candle, Tick> tickOnlyAggregator = (sourceTick, completedItemConsumer) -> {
        completedItemConsumer.accept(sourceTick.toCandle());
        return Incomplete.empty();
    };

    @Override
    public TimeFrameAggregator<Candle, Tick> createTickOnlyAggregator() {
        return tickOnlyAggregator;
    }

    @Override
    public <E extends Chronological> TimeCandleAggregator<E> createTimeCandleAggregator(
            Duration granularity,
            CandleBuilder<Candle, E> builder,
            TimeCandleAlignment alignment) {

        return new TimeCandleAggregator<>(builder, granularity, alignment);
    }

    @Override
    public <E extends Chronological> PeriodCandleAggregator<E> createPeriodCandleAggregator(
            TemporalAmount periodicity,
            CandleBuilder<Candle, E> builder,
            PeriodCandleAlignment alignment) {

        return new PeriodCandleAggregator<>(builder, periodicity, alignment);
    }
}
