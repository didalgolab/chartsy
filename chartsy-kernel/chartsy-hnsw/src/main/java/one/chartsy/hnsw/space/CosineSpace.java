package one.chartsy.hnsw.space;

import java.util.Arrays;

import one.chartsy.hnsw.HnswConfig;
import one.chartsy.hnsw.store.AuxStorage;
import one.chartsy.hnsw.store.VectorStorage;

/**
 * Cosine distance space storing normalised vectors.
 */
public final class CosineSpace implements Space {

    private final int dimension;
    private final VectorStorage vectorStorage;
    private final AuxStorage auxStorage;

    CosineSpace(HnswConfig config, VectorStorage vectorStorage, AuxStorage auxStorage) {
        this.dimension = config.dimension;
        this.vectorStorage = vectorStorage;
        this.auxStorage = auxStorage;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public void onInsert(int nodeId, double[] vector) {
        requireDimension(vector);
        double norm = norm(vector);
        double[] normalised = vector;
        if (norm > 0.0) {
            normalised = Arrays.copyOf(vector, vector.length);
            scale(normalised, 1.0 / norm);
        } else {
            normalised = new double[dimension];
        }
        vectorStorage.set(nodeId, normalised);
        auxStorage.setNorm(nodeId, norm);
    }

    @Override
    public void onRemove(int nodeId) {
        vectorStorage.clear(nodeId);
        auxStorage.clear(nodeId);
    }

    @Override
    public void onClear() {
    }

    @Override
    public QueryContext prepareQuery(double[] queryVector) {
        requireDimension(queryVector);
        double norm = norm(queryVector);
        double[] normalised = queryVector;
        if (norm > 0.0) {
            normalised = Arrays.copyOf(queryVector, queryVector.length);
            scale(normalised, 1.0 / norm);
        } else {
            normalised = new double[dimension];
        }
        return new ArrayQueryContext(normalised, norm);
    }

    @Override
    public QueryContext prepareQueryForNode(int nodeId) {
        return new StoredQueryContext(nodeId);
    }

    @Override
    public double distance(QueryContext query, int nodeId) {
        return switch (query) {
            case ArrayQueryContext array -> cosineDistance(array.normalised(), array.originalNorm(), nodeId);
            case StoredQueryContext stored -> distanceBetweenNodes(stored.nodeId(), nodeId);
            default -> throw new IllegalStateException("Unknown query context: " + query);
        };
    }

    @Override
    public double distanceBetweenNodes(int nodeA, int nodeB) {
        double dot = vectorStorage.dotBetween(nodeA, nodeB);
        double normA = auxStorage.norm(nodeA);
        double normB = auxStorage.norm(nodeB);
        if (normA == 0.0 || normB == 0.0) {
            return 1.0;
        }
        double value = 1.0 - dot;
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.POSITIVE_INFINITY;
        }
        return value;
    }

    private double cosineDistance(double[] normalisedQuery, double queryNorm, int nodeId) {
        double normStored = auxStorage.norm(nodeId);
        if (normStored == 0.0 || queryNorm == 0.0) {
            return 1.0;
        }
        double dot = vectorStorage.dot(nodeId, normalisedQuery);
        double value = 1.0 - dot;
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.POSITIVE_INFINITY;
        }
        return value;
    }

    private double norm(double[] vector) {
        double sum = 0.0;
        for (double v : vector) {
            sum += v * v;
        }
        return Math.sqrt(sum);
    }

    private void scale(double[] vector, double factor) {
        for (int i = 0; i < vector.length; i++) {
            vector[i] *= factor;
        }
    }

    private void requireDimension(double[] vector) {
        if (vector.length != dimension) {
            throw new IllegalArgumentException("Expected vector dimension " + dimension + " but was " + vector.length);
        }
    }

    record ArrayQueryContext(double[] normalised, double originalNorm) implements QueryContext {
    }

    record StoredQueryContext(int nodeId) implements QueryContext {
    }

    public static final class Factory implements SpaceFactory {
        @Override
        public Space create(HnswConfig config, VectorStorage vectorStorage, AuxStorage auxStorage) {
            return new CosineSpace(config, vectorStorage, auxStorage);
        }

        @Override
        public String typeId() {
            return "cosine";
        }
    }
}
