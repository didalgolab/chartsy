package one.chartsy.data.market;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;
import one.chartsy.time.Chronological;

import java.time.Period;
import java.time.temporal.TemporalAmount;

public class PeriodCandleAggregator<E extends Chronological> extends AbstractCandleAggregator<Candle, E> {

    protected final TemporalAmount period;
    protected final PeriodCandleAlignment alignment;
    protected long candleCloseTime = Long.MIN_VALUE;


    public PeriodCandleAggregator(CandleBuilder<Candle, E> builder, TemporalAmount period, PeriodCandleAlignment alignment) {
        super(builder);
        this.period = period;
        this.alignment = alignment;
    }

    @Override
    protected boolean isCompletedBy(E candle) {
        long time = candle.getTime();
        if (time <= candleCloseTime)
            return false;

        candleCloseTime = alignment.getRegularCandleCloseTime(time, period);
        return true;
    }
}
