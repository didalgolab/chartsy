package one.chartsy.hnsw.space;

/**
 * Functional interface describing a distance function over dense vectors stored in flat arrays.
 */
@FunctionalInterface
public interface VectorDistance {
    double distance(double[] a, int offsetA, double[] b, int offsetB, int length);
}
