/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.packed;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.data.*;

import java.util.function.ToDoubleFunction;

public class PackedCandleSeries extends AbstractCandleSeries<Candle, PackedDoubleSeries> implements CandleSeries {

    public PackedCandleSeries(SymbolResource<Candle> resource, Dataset<Candle> dataset) {
        super(resource, dataset);
    }

    public static PackedCandleSeries from(Series<? extends Candle> series) {
        if (series instanceof PackedCandleSeries)
            return (PackedCandleSeries) series;

        @SuppressWarnings("unchecked")
        var cs = (Series<Candle>) series;
        return new PackedCandleSeries(cs.getResource(), PackedDataset.from(cs.getData()));
    }

    @Override
    public String toString() {
        return getResource() + ": " /*+ getData()*/;
    }

    @Override
    public PackedDoubleSeries trueRange() {
        int newLength = length() - 1;
        if (newLength <= 0)
            return DoubleSeries.empty(getTimeline());

        Candle c2 = get(newLength);
        double[] result = new double[newLength];
        for (int i = newLength - 1; i >= 0; i--) {
            Candle c1 = get(i);
            result[i] = Math.max(c1.high(), c2.close()) - Math.min(c1.low(), c2.close());
            c2 = c1;
        }
        return DoubleSeries.of(result, getTimeline());
    }
}
