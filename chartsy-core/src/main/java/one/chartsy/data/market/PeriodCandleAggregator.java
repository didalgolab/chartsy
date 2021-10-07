package one.chartsy.data.market;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;

import java.time.Period;

public class PeriodCandleAggregator<E extends Candle> extends AbstractCandleAggregator<E> {

    protected final Period period;
    protected final PeriodCandleAlignment alignment;
    protected long candleCloseTime = Long.MIN_VALUE;

    public PeriodCandleAggregator(CandleBuilder<Candle, E> builder, Period period, PeriodCandleAlignment alignment) {
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
