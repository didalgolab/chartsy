/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider.file;

import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.AdjustmentMethod;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.structures.AdaptiveHistogram;
import one.chartsy.random.RandomWalk;

public class RandomCandlesBootstrap {

    public static void main(String[] args) {
        CandleSeries series = RandomWalk.candleSeries(1000, SymbolResource.of("RANDOM", TimeFrame.Period.DAILY));
        System.out.println(series.length());
        System.out.println("Last: " + series.getLast());

        double orgClose = series.getLast().close();
        long n = 1000_000, cnt = 0;
        //System.in.read();
        long startTime = System.nanoTime();
        AdaptiveHistogram hist = new AdaptiveHistogram();
        for (int i = 0; i < n; i++) {
            CandleSeries series2 = RandomWalk.candleSeries(1000, SymbolResource.of("RANDOM", TimeFrame.Period.DAILY));
            series2 = series2.resample(AdjustmentMethod.ABSOLUTE);
            double newClose = series2.getLast().close();
            hist.addValue(newClose);
            //double newClose = series.bootstrap(AdjustmentMethod.ABSOLUTE).getLast().close();
            if (newClose <= 0)
                cnt++;
        }
        long elapsedTime = System.nanoTime() - startTime;
        System.out.println("PCT = " + (cnt*10_000/n)/100.0 + "% - TIME INFO: " + (elapsedTime/1000_000L)/1000.0);
        //hist.show();
        System.out.println(hist.getValueForPercentile(50));
    }
}
