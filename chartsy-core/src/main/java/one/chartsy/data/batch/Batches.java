package one.chartsy.data.batch;

import one.chartsy.SymbolResource;
import one.chartsy.data.Series;
import one.chartsy.time.Chronological;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class Batches {

    public static <T extends Chronological> Collector<Batch<T>, ?, Series<T>> toSeries() {
        return Collectors.collectingAndThen(Collectors.toList(),
                (List<Batch<T>> batches) -> {
                    Collections.sort(batches);

                    int itemCount = 0;
                    for (Batch<T> batch : batches)
                        itemCount += batch.size();

                    List<T> list = new ArrayList<>(itemCount);
                    for (Batch<T> batch : batches)
                        list.addAll(batch.listOrdered());

                    SymbolResource<T> resource = batches.get(0).batcher().getQuery().resource();
                    return Series.of(resource, list);
                });
    }

    private Batches() {
        throw new AssertionError("Cannot instantiate");
    }
}
