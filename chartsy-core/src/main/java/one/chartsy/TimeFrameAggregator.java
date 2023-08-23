/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import one.chartsy.data.market.Tick;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public interface TimeFrameAggregator<C extends Candle, T extends Tick> {

    Incomplete<C> addCandle(C candle, Consumer<C> completedItemConsumer);

    Incomplete<C> addTick(T tick, Consumer<C> completedItemConsumer);

    default List<C> aggregate(List<? extends C> elements) {
        return aggregate(elements, true);
    }

    default List<C> aggregate(List<? extends C> elements, boolean emitLast) {
        List<C> aggregated = new ArrayList<>();
        Incomplete<C> last = null;
        for (C element : elements)
            last = addCandle(element, aggregated::add);

        if (emitLast && last != null)
            aggregated.add(last.get());

        return aggregated;
    }
}
