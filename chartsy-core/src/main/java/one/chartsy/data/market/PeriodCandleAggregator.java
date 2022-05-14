/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.market;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;

import java.time.temporal.TemporalAmount;

public class PeriodCandleAggregator<C extends Candle, T extends Tick> extends TimePeriodCandleAggregator<C, T> {

    protected final TemporalAmount period;
    protected final DateCandleAlignment alignment;
    protected long candleCloseTime = Long.MIN_VALUE;


    public PeriodCandleAggregator(CandleBuilder<C, T> builder, TemporalAmount period, DateCandleAlignment alignment) {
        super(builder);
        this.period = period;
        this.alignment = alignment;
    }

    @Override
    protected boolean isCompletedOn(long time) {
        if (time <= candleCloseTime)
            return false;

        candleCloseTime = alignment.getRegularCandleCloseTime(time, period);
        return true;
    }
}
