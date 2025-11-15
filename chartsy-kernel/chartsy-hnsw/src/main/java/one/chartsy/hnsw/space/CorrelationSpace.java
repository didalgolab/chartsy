package one.chartsy.hnsw.space;

import java.util.Arrays;

import one.chartsy.hnsw.HnswConfig;
import one.chartsy.hnsw.store.AuxStorage;
import one.chartsy.hnsw.store.VectorStorage;

/**
 * Correlation distance space implemented via explicit mean centring.
 */
public final class CorrelationSpace implements Space {

    private final int dimension;
    private final VectorStorage vectorStorage;
    private final AuxStorage auxStorage;

    CorrelationSpace(HnswConfig config, VectorStorage vectorStorage, AuxStorage auxStorage) {
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
        double mean = mean(vector);
        double[] centered = Arrays.copyOf(vector, vector.length);
        centre(centered, mean);
        double norm = norm(centered);
        if (norm == 0.0) {
            Arrays.fill(centered, 0.0);
        } else {
            scale(centered, 1.0); // no-op but emphasises intentionality
        }
        vectorStorage.set(nodeId, centered);
        auxStorage.setMean(nodeId, mean);
        auxStorage.setCenteredNorm(nodeId, norm);
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
        double mean = mean(queryVector);
        double[] centered = Arrays.copyOf(queryVector, queryVector.length);
        centre(centered, mean);
        double norm = norm(centered);
        return new ArrayQueryContext(centered, norm);
    }

    @Override
    public QueryContext prepareQueryForNode(int nodeId) {
        return new StoredQueryContext(nodeId);
    }

    @Override
    public double distance(QueryContext query, int nodeId) {
        return switch (query) {
            case ArrayQueryContext array -> correlationDistance(array.centered(), array.norm(), nodeId);
            case StoredQueryContext stored -> distanceBetweenNodes(stored.nodeId(), nodeId);
            default -> throw new IllegalStateException("Unknown query context: " + query);
        };
    }

    @Override
    public double distanceBetweenNodes(int nodeA, int nodeB) {
        double normA = auxStorage.centeredNorm(nodeA);
        double normB = auxStorage.centeredNorm(nodeB);
        if (normA == 0.0 || normB == 0.0) {
            return 1.0;
        }
        double dot = vectorStorage.dotBetween(nodeA, nodeB);
        double value = 1.0 - (dot / (normA * normB));
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.POSITIVE_INFINITY;
        }
        return value;
    }

    private double correlationDistance(double[] centeredQuery, double queryNorm, int nodeId) {
        double normStored = auxStorage.centeredNorm(nodeId);
        if (queryNorm == 0.0 || normStored == 0.0) {
            return 1.0;
        }
        double dot = vectorStorage.dot(nodeId, centeredQuery);
        double value = 1.0 - (dot / (queryNorm * normStored));
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Double.POSITIVE_INFINITY;
        }
        return value;
    }

    private double mean(double[] vector) {
        double sum = 0.0;
        for (double v : vector) {
            sum += v;
        }
        return sum / vector.length;
    }

    private void centre(double[] vector, double mean) {
        for (int i = 0; i < vector.length; i++) {
            vector[i] -= mean;
        }
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

    record ArrayQueryContext(double[] centered, double norm) implements QueryContext {
    }

    record StoredQueryContext(int nodeId) implements QueryContext {
    }

    public static final class Factory implements SpaceFactory {
        @Override
        public Space create(HnswConfig config, VectorStorage vectorStorage, AuxStorage auxStorage) {
            return new CorrelationSpace(config, vectorStorage, auxStorage);
        }

        @Override
        public String typeId() {
            return "correlation";
        }
    }
}
