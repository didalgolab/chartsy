/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.packed;

import one.chartsy.SymbolResource;
import one.chartsy.data.Dataset;
import one.chartsy.data.TickSeries;
import one.chartsy.data.market.Tick;

public class PackedTickSeries extends PackedSeries<Tick> implements TickSeries {

    public PackedTickSeries(SymbolResource<Tick> resource, Dataset<Tick> dataset) {
        super(resource, dataset);
    }

    @Override
    public String toString() {
        return getResource() + ": " /*+ getData()*/;
    }
}
