package one.chartsy.core;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;
import one.chartsy.TimeFrameAggregator;
import one.chartsy.data.market.*;
import one.chartsy.time.Chronological;
import org.openide.util.Lookup;

import java.time.Duration;
import java.time.temporal.TemporalAmount;

public interface TimeFrameServices {

    static TimeFrameServices getDefault() {
        return Lookup.getDefault().lookup(TimeFrameServices.class);
    }

    TimeFrameAggregator<Candle, Tick> createTickOnlyAggregator();

    <E extends Chronological> TimeCandleAggregator<E> createTimeCandleAggregator(
            Duration granularity,
            CandleBuilder<Candle, E> builder,
            TimeCandleAlignment alignment);

    <E extends Chronological> PeriodCandleAggregator<E> createPeriodCandleAggregator(
            TemporalAmount periodicity,
            CandleBuilder<Candle, E> builder,
            DateCandleAlignment alignment);

    //<E> TimeCandleAggregator<E> createTimeCandleAggregator(CandleBuilder<Candle,E> cb, Duration dur, TimeCandleAlignment align);
}
