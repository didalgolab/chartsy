package one.chartsy.data.batch;

import lombok.Getter;
import lombok.experimental.Accessors;
import one.chartsy.time.Chronological;

@Getter @Accessors(fluent = true)
public abstract class AbstractBatch<T extends Chronological> implements Batch<T> {
    private final Comparable<?> batchNumber;
    private final Batcher<T> batcher;
    private final Chronological.Order order;

    protected AbstractBatch(Comparable<?> batchNumber, Batcher<T> batcher, Chronological.Order order) {
        this.batchNumber = batchNumber;
        this.batcher = batcher;
        this.order = order;
    }
}
