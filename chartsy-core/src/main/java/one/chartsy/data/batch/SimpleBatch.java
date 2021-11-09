package one.chartsy.data.batch;

import one.chartsy.time.Chronological;

import java.util.List;

public class SimpleBatch<T extends Chronological> extends AbstractBatch<T> {

    private final List<T> list;

    public SimpleBatch(Batch<T> batch, Long batchNumber, List<T> list) {
        this(batch.batcher(), batch.order(), batchNumber, list);
    }

    public SimpleBatch(Batcher<T> batcher, Chronological.Order order, Long batchNumber, List<T> list) {
        super(batchNumber, batcher, order);
        this.list = list;
    }

    @Override
    public List<T> list() {
        return list;
    }
}
