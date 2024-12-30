/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.market;

import one.chartsy.Candle;
import one.chartsy.CandleBuilder;
import one.chartsy.TradingDay;

import java.time.Duration;

public class TimeCandleAggregator<C extends Candle, T extends Tick> extends TimePeriodCandleAggregator<C, T> {

    protected final Duration granularity;
    protected final TimeCandleAlignment alignment;
    protected TradingDay day;
    protected long candleCloseTime = Long.MIN_VALUE;


    public TimeCandleAggregator(CandleBuilder<C, T> builder, Duration granularity, TimeCandleAlignment alignment) {
        super(builder);
        this.granularity = granularity;
        this.alignment = alignment;
    }

    @Override
    protected boolean isCompletedOn(long time) {
        if (time <= candleCloseTime)
            return false;

        if (day == null || !day.contains(time))
            day = alignment.getTradingDay(time);
        candleCloseTime = day.getRegularCandleCloseTime(time, granularity);
        return true;
    }
}
