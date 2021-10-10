package one.chartsy.data.market;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;
import one.chartsy.TradingDay;
import one.chartsy.time.Chronological;

import java.time.Duration;

public class TimeCandleAggregator<E extends Chronological> extends AbstractCandleAggregator<Candle, E> {

    protected final Duration granularity;
    protected final TimeCandleAlignment alignment;
    protected TradingDay day;
    protected long candleCloseTime = Long.MIN_VALUE;


    public TimeCandleAggregator(CandleBuilder<Candle, E> builder, Duration granularity, TimeCandleAlignment alignment) {
        super(builder);
        this.granularity = granularity;
        this.alignment = alignment;
    }

    @Override
    protected boolean isCompletedBy(E candle) {
        long time = candle.getTime();
        if (time <= candleCloseTime)
            return false;

        if (day == null || !day.contains(time))
            day = alignment.getTradingDay(time);
        candleCloseTime = day.getRegularCandleCloseTime(time, granularity);
        return true;
    }
}
