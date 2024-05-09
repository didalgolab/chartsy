/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.packed;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.base.Dataset;
import one.chartsy.base.dataset.ImmutableDataset;
import one.chartsy.data.*;

public class PackedCandleSeries extends AbstractCandleSeries<Candle, PackedDoubleSeries> implements CandleSeries {

    public PackedCandleSeries(SymbolResource<Candle> resource, Dataset<Candle> dataset) {
        super(resource, dataset);
    }

    public static PackedCandleSeries from(Series<? extends Candle> series) {
        if (series instanceof PackedCandleSeries ps)
            return ps;

        @SuppressWarnings("unchecked")
        var cs = (Series<Candle>) series;
        return new PackedCandleSeries(cs.getResource(), ImmutableDataset.from(cs.getData()));
    }

    @Override
    public String toString() {
        return getResource() + ": <<" + getData().length() + ">>";
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

    @Override
    public CandleSeries take(int count) {
        if (count <= 0)
            throw new IllegalArgumentException("The `count` argument must be positive");
        if (count > length())
            throw new IllegalArgumentException("The `count` argument cannot exceed series length " + length());

        Dataset<Candle> subDataset = getData().take(count);
        return new PackedCandleSeries(getResource(), subDataset);
    }
}
