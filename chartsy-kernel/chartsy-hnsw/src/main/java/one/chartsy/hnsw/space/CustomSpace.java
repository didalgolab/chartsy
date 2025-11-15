package one.chartsy.hnsw.space;

import one.chartsy.hnsw.HnswConfig;
import one.chartsy.hnsw.store.AuxStorage;
import one.chartsy.hnsw.store.VectorStorage;

/**
 * Space delegating distance computations to a user provided {@link VectorDistance}.
 */
public final class CustomSpace implements Space {

    private final int dimension;
    private final VectorStorage vectorStorage;
    private final VectorDistance distance;

    CustomSpace(HnswConfig config, VectorStorage vectorStorage, VectorDistance distance) {
        this.dimension = config.dimension;
        this.vectorStorage = vectorStorage;
        this.distance = distance;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public void preallocate(int capacity) {
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
        return new StoredNodeQueryContext(nodeId);
    }

    @Override
    public double distance(QueryContext query, int nodeId) {
        return switch (query) {
            case ArrayQueryContext array -> distance.distance(vectorStorage.raw(), vectorStorage.offset(nodeId), array.vector(), 0, dimension);
            case StoredNodeQueryContext stored -> distanceBetweenNodes(stored.nodeId(), nodeId);
            default -> throw new IllegalStateException("Unknown query context: " + query);
        };
    }

    @Override
    public double distanceBetweenNodes(int nodeA, int nodeB) {
        return distance.distance(vectorStorage.raw(), vectorStorage.offset(nodeA),
                vectorStorage.raw(), vectorStorage.offset(nodeB), dimension);
    }

    private void requireDimension(double[] vector) {
        if (vector.length != dimension) {
            throw new IllegalArgumentException("Expected vector dimension " + dimension + " but was " + vector.length);
        }
    }

    record ArrayQueryContext(double[] vector) implements QueryContext {
    }

    public static final class Factory implements SpaceFactory {
        private final VectorDistance distance;

        public Factory(VectorDistance distance) {
            this.distance = distance;
        }

        @Override
        public Space create(HnswConfig config, VectorStorage vectorStorage, AuxStorage auxStorage) {
            return new CustomSpace(config, vectorStorage, distance);
        }

        @Override
        public String typeId() {
            return "custom";
        }
    }
}
