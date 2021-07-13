package one.chartsy.data.batch;

import one.chartsy.data.DataQuery;
import one.chartsy.time.Chronological;

public abstract class Batcher<T extends Chronological> {
    private final DataQuery<T> query;

    protected Batcher(DataQuery<T> query) {
        this.query = query;
    }

    public abstract Batch<T> getNext(Batch<T> prevBatch);

    public boolean hasNext(Batch<T> prevBatch) {
        return !getNext(prevBatch).isEmpty();
    }

    public final DataQuery<T> getQuery() {
        return query;
    }

}
