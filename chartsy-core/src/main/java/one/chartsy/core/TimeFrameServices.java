package one.chartsy.core;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;
import one.chartsy.TimeFrameAggregator;
import one.chartsy.data.market.*;
import org.openide.util.Lookup;

import java.time.Duration;
import java.time.temporal.TemporalAmount;

public interface TimeFrameServices {

    static TimeFrameServices getDefault() {
        return Lookup.getDefault().lookup(TimeFrameServices.class);
    }

    TimeFrameAggregator<Candle, Tick> createTickOnlyAggregator();

    <C extends Candle, T extends Tick> TimeCandleAggregator<C,T> createTimeCandleAggregator(
            Duration granularity,
            CandleBuilder<C,T> builder,
            TimeCandleAlignment alignment);

    <C extends Candle, T extends Tick> PeriodCandleAggregator<C,T> createPeriodCandleAggregator(
            TemporalAmount periodicity,
            CandleBuilder<C,T> builder,
            DateCandleAlignment alignment);

    //<E> TimeCandleAggregator<E> createTimeCandleAggregator(CandleBuilder<Candle,E> cb, Duration dur, TimeCandleAlignment align);
}
