package one.chartsy.hnsw.space;

import java.util.Objects;

import one.chartsy.hnsw.HnswConfig;
import one.chartsy.hnsw.store.AuxStorage;
import one.chartsy.hnsw.store.VectorStorage;

/**
 * Plain Euclidean distance space.
 */
public final class EuclideanSpace implements Space {

    private final int dimension;
    private final VectorStorage vectorStorage;

    EuclideanSpace(HnswConfig config, VectorStorage vectorStorage, AuxStorage auxStorage) {
        this.dimension = config.dimension;
        this.vectorStorage = Objects.requireNonNull(vectorStorage);
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public void preallocate(AuxStorage auxStorage, int capacity) {
        vectorStorage.ensureCapacity(capacity);
    }

    @Override
    public void onInsert(int nodeId, double[] vector) {
        requireDimension(vector);
        vectorStorage.set(nodeId, vector);
    }

    @Override
    public void onRemove(int nodeId) {
        vectorStorage.clear(nodeId);
    }

    @Override
    public void onClear() {
    }

    @Override
    public QueryContext prepareQuery(double[] queryVector) {
        requireDimension(queryVector);
        return new ArrayQueryContext(queryVector);
    }

    @Override
    public QueryContext prepareQueryForNode(int nodeId) {
        return new StoredQueryContext(nodeId);
    }

    @Override
    public double distance(QueryContext query, int nodeId) {
        return switch (query) {
            case ArrayQueryContext array -> vectorStorage.l2(nodeId, array.vector());
            case StoredQueryContext stored -> vectorStorage.l2Between(stored.nodeId(), nodeId);
            default -> throw new IllegalStateException("Unknown query context: " + query);
        };
    }

    @Override
    public double distanceBetweenNodes(int nodeA, int nodeB) {
        return vectorStorage.l2Between(nodeA, nodeB);
    }

    private void requireDimension(double[] vector) {
        if (vector.length != dimension) {
            throw new IllegalArgumentException("Expected vector dimension " + dimension + " but was " + vector.length);
        }
    }

    record ArrayQueryContext(double[] vector) implements QueryContext {
    }

    record StoredQueryContext(int nodeId) implements QueryContext {
    }

    public static final class Factory implements SpaceFactory {
        @Override
        public Space create(HnswConfig config, VectorStorage vectorStorage, AuxStorage auxStorage) {
            return new EuclideanSpace(config, vectorStorage, auxStorage);
        }

        @Override
        public String typeId() {
            return "euclidean";
        }
    }
}
