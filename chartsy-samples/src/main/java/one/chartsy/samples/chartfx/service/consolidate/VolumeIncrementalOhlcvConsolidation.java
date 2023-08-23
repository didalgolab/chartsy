/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.chartfx.service.consolidate;

import java.util.Calendar;

import one.chartsy.samples.chartfx.dos.Interval;
import one.chartsy.samples.chartfx.dos.OHLCVItem;
import one.chartsy.samples.chartfx.service.consolidate.OhlcvTimeframeConsolidation.OhlcvConsolidationComputation;
import one.chartsy.samples.chartfx.service.period.IntradayPeriod;

/**
 * Volume based financial charts
 *
 * @author afischer
 */
public class VolumeIncrementalOhlcvConsolidation extends AbstractIncrementalOhlcvConsolidation {
    private double volumeDiff;
    private final double volumePeriod;

    public VolumeIncrementalOhlcvConsolidation(OhlcvConsolidationComputation consolidationComputation,
            IntradayPeriod period, Interval<Calendar> tt,
            OhlcvConsolidationAddon[] _ohlcvConsolidationAddons) {
        super(consolidationComputation, period, tt, _ohlcvConsolidationAddons);
        this.volumePeriod = period.getPeriodValue();
    }

    @Override
    protected void defineConsolidationConditionAfterAddition(OHLCVItem finalItem) {
        defineConsolidationCondition(finalItem);
    }

    @Override
    protected void defineConsolidationConditionAfterUpdate(OHLCVItem finalItem) {
        defineConsolidationCondition(finalItem);
    }

    @Override
    protected boolean checkConsolidationCondition(OHLCVItem lastItem, OHLCVItem incrementItem) {
        return incrementItem.getVolume() <= volumeDiff;
    }

    private void defineConsolidationCondition(OHLCVItem finalItem) {
        volumeDiff = volumePeriod - finalItem.getVolume();
    }
}
