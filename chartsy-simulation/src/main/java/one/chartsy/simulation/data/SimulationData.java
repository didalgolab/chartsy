/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.data;

import one.chartsy.collections.PriorityMap;
import one.chartsy.data.ChronologicalIterator;
import one.chartsy.data.ChronologicalIteratorContext;
import one.chartsy.data.Series;
import one.chartsy.data.structures.IntHashMap;
import one.chartsy.data.structures.IntMap;
import one.chartsy.time.Chronological;

import java.util.Collection;

public class SimulationData {

    public static IntMap<Series<?>> seriesMap(
            Collection<? extends Series<?>> datasets)
    {
        IntMap<Series<?>> seriesMap = new IntHashMap<>(datasets.size());

        int iteratorId = 0;
        for (Series<?> dataset : datasets)
            seriesMap.put(iteratorId++, dataset);
        return seriesMap;
    }

    public static PriorityMap<Chronological, ChronologicalIterator<?>> priorityMap(
            IntMap<Series<?>> datasets)
    {
        PriorityMap<Chronological, ChronologicalIterator<?>> map = new PriorityMap<>(datasets.size());

        datasets.forEach((datasetId, dataset) -> {
            ChronologicalIterator<?> iter = dataset.chronologicalIterator(new ChronologicalIteratorContext(datasetId));
            if (iter.hasNext())
                map.put(iter.peek(), iter);
        });
        return map;
    }
}
