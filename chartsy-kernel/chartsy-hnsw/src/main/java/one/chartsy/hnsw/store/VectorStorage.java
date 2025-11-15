package one.chartsy.hnsw.store;

import java.util.Arrays;

/**
 * Row-major storage of dense vectors backed by a single double array.
 */
public final class VectorStorage {
    private final int dimension;
    private double[] data;
    private int capacity;

    public VectorStorage(int dimension, int initialCapacity) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive");
        }
        this.dimension = dimension;
        this.capacity = Math.max(1, initialCapacity);
        this.data = new double[this.capacity * this.dimension];
    }

    public int dimension() {
        return dimension;
    }

    public int capacity() {
        return capacity;
    }

    public void ensureCapacity(int requested) {
        if (requested <= capacity) {
            return;
        }
        int newCapacity = capacity;
        while (newCapacity < requested) {
            newCapacity = Math.max(newCapacity * 2, requested);
        }
        data = Arrays.copyOf(data, newCapacity * dimension);
        capacity = newCapacity;
    }

    public void set(int nodeId, double[] vector) {
        System.arraycopy(vector, 0, data, offset(nodeId), dimension);
    }

    public void set(int nodeId, double value) {
        Arrays.fill(data, offset(nodeId), offset(nodeId) + dimension, value);
    }

    public double[] copy(int nodeId) {
        return Arrays.copyOfRange(data, offset(nodeId), offset(nodeId) + dimension);
    }

    public void clear(int nodeId) {
        Arrays.fill(data, offset(nodeId), offset(nodeId) + dimension, 0.0);
    }

    public double dot(int nodeId, double[] other) {
        return dot(data, offset(nodeId), other, 0, dimension);
    }

    public double dotBetween(int nodeA, int nodeB) {
        return dot(data, offset(nodeA), data, offset(nodeB), dimension);
    }

    public double l2(int nodeId, double[] other) {
        return Math.sqrt(l2Squared(data, offset(nodeId), other, 0, dimension));
    }

    public double l2Between(int nodeA, int nodeB) {
        return Math.sqrt(l2Squared(data, offset(nodeA), data, offset(nodeB), dimension));
    }

    public double l2Squared(int nodeId, double[] other) {
        return l2Squared(data, offset(nodeId), other, 0, dimension);
    }

    public double l2SquaredBetween(int nodeA, int nodeB) {
        return l2Squared(data, offset(nodeA), data, offset(nodeB), dimension);
    }

    public double[] raw() {
        return data;
    }

    public int offset(int nodeId) {
        return nodeId * dimension;
    }

    public long memoryBytes() {
        return (long) data.length * Double.BYTES;
    }

    public void reset(int initialCapacity) {
        this.capacity = Math.max(1, initialCapacity);
        this.data = new double[capacity * dimension];
    }

    private static double dot(double[] a, int offsetA, double[] b, int offsetB, int length) {
        double sum0 = 0.0;
        double sum1 = 0.0;
        double sum2 = 0.0;
        double sum3 = 0.0;
        int i = 0;
        int limit = length - (length % 4);
        for (; i < limit; i += 4) {
            sum0 += a[offsetA + i] * b[offsetB + i];
            sum1 += a[offsetA + i + 1] * b[offsetB + i + 1];
            sum2 += a[offsetA + i + 2] * b[offsetB + i + 2];
            sum3 += a[offsetA + i + 3] * b[offsetB + i + 3];
        }
        double sum = (sum0 + sum1) + (sum2 + sum3);
        for (; i < length; i++) {
            sum += a[offsetA + i] * b[offsetB + i];
        }
        return sum;
    }

    private static double l2Squared(double[] a, int offsetA, double[] b, int offsetB, int length) {
        double sum0 = 0.0;
        double sum1 = 0.0;
        double sum2 = 0.0;
        double sum3 = 0.0;
        int i = 0;
        int limit = length - (length % 4);
        for (; i < limit; i += 4) {
            double d0 = a[offsetA + i] - b[offsetB + i];
            double d1 = a[offsetA + i + 1] - b[offsetB + i + 1];
            double d2 = a[offsetA + i + 2] - b[offsetB + i + 2];
            double d3 = a[offsetA + i + 3] - b[offsetB + i + 3];
            sum0 += d0 * d0;
            sum1 += d1 * d1;
            sum2 += d2 * d2;
            sum3 += d3 * d3;
        }
        double sum = (sum0 + sum1) + (sum2 + sum3);
        for (; i < length; i++) {
            double d = a[offsetA + i] - b[offsetB + i];
            sum += d * d;
        }
        return sum;
    }
}
