/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.SymbolResource;
import one.chartsy.data.market.Tick;
import one.chartsy.data.packed.PackedDataset;
import one.chartsy.data.packed.PackedTickSeries;
import one.chartsy.time.Chronological;

import java.util.Collection;

public interface TickSeries extends Series<Tick> {

    static TickSeries of(SymbolResource<Tick> resource, Collection<? extends Tick> values) {
        boolean reverse = (Chronological.Order.CHRONOLOGICAL.isOrdered(values));
        return new PackedTickSeries(resource, PackedDataset.of(values, reverse));
    }

    static <T extends Tick> TickSeries from(Series<T> series) {
        if (series instanceof TickSeries ts)
            return ts;

        return new PackedTickSeries((SymbolResource<Tick>) series.getResource(), (Dataset<Tick>) series.getData());
    }

    default DoubleSeries prices() {
        return mapToDouble(Tick::price);
    }

    default DoubleSeries volumes() {
        return mapToDouble(Tick::size);
    }

}
