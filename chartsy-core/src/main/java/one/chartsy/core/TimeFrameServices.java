package one.chartsy.core;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;
import one.chartsy.TimeFrameAggregator;
import one.chartsy.data.market.*;
import one.chartsy.time.Chronological;

import java.time.Duration;
import java.time.temporal.TemporalAmount;

public interface TimeFrameServices {

    TimeFrameAggregator<Candle, Tick> createTickOnlyAggregator();

    <E extends Chronological> TimeCandleAggregator<E> createTimeCandleAggregator(
            Duration granularity,
            CandleBuilder<Candle, E> builder,
            TimeCandleAlignment alignment);

    <E extends Chronological> PeriodCandleAggregator<E> createPeriodCandleAggregator(
            TemporalAmount periodicity,
            CandleBuilder<Candle, E> builder,
            PeriodCandleAlignment alignment);

    //<E> TimeCandleAggregator<E> createTimeCandleAggregator(CandleBuilder<Candle,E> cb, Duration dur, TimeCandleAlignment align);
}
