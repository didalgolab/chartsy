package one.chartsy.simulation.data;

import one.chartsy.collections.PriorityMap;
import one.chartsy.data.ChronologicalIterator;
import one.chartsy.data.ChronologicalIteratorContext;
import one.chartsy.data.IndexedSymbolResourceData;
import one.chartsy.data.Series;
import one.chartsy.time.Chronological;
import one.chartsy.trade.TradingStrategyContext;

import java.util.Collection;

public class TradingData {

    public static PriorityMap<Chronological, ChronologicalIterator<?>> priorityMap(
            TradingStrategyContext context,
            Collection<? extends Series<?>> datasets)
    {
        PriorityMap<Chronological, ChronologicalIterator<?>> map = new PriorityMap<>(datasets.size());

        int iteratorId = 0;
        for (Series<?> dataset : datasets) {
            ChronologicalIterator<?> iter = dataset.chronologicalIterator(new ChronologicalIteratorContext(iteratorId++));
            if (iter.hasNext())
                map.put(iter.peek(), iter);
        }
        return map;
    }
}
