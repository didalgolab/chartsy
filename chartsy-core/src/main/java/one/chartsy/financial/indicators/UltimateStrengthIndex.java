/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.data.structures.DoubleWindowSummaryStatistics;
import one.chartsy.financial.AbstractDoubleIndicator;

public class UltimateStrengthIndex extends AbstractDoubleIndicator {
    private final int length;
    private final DoubleWindowSummaryStatistics strengthUp;
    private final DoubleWindowSummaryStatistics strengthDown;
    private final UltimateSmoother usuSmoother;
    private final UltimateSmoother usdSmoother;
    private double lastClose = Double.NaN;
    private double last = Double.NaN;
    private int count;

    public UltimateStrengthIndex(int length) {
        this.length = length;
        this.strengthUp = new DoubleWindowSummaryStatistics(4);
        this.strengthDown = new DoubleWindowSummaryStatistics(4);
        this.usuSmoother = new UltimateSmoother(length);
        this.usdSmoother = new UltimateSmoother(length);
    }

    @Override
    public void accept(double close) {
        if (!Double.isNaN(lastClose)) {
            // Calculate strength up and down
            double diff = close - lastClose;
            strengthUp.add(diff > 0 ? diff : 0);
            strengthDown.add(diff < 0 ? -diff : 0);

            if (count >= 3) {
                // Calculate 4-bar averages using DoubleWindowSummaryStatistics's capabilities
                double suAvg = strengthUp.getAverage();
                double sdAvg = strengthDown.getAverage();

                // Apply Ultimate Smoother to the averages
                double usu = usuSmoother.smooth(suAvg);
                double usd = usdSmoother.smooth(sdAvg);

                // Calculate USI
                if (usu + usd != 0.0 && usu > 0.0001 && usd > 0.0001)
                    last = (usu - usd) / (usu + usd);
            }
            count++;
        }
        lastClose = close;
    }

    @Override
    public double getLast() {
        return last;
    }

    @Override
    public boolean isReady() {
        return count >= length;
    }

}