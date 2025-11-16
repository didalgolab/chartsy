package one.chartsy.hnsw.store;

import java.util.Arrays;

/**
 * Row-major float32 variant of {@link VectorStorage}.
 */
public final class VectorStorageF32 extends VectorStorage {
    private float[] dataF32;
    private double[] doubleView;
    private boolean doubleViewDirty = true;

    public VectorStorageF32(int dimension, int initialCapacity) {
        super(dimension, initialCapacity, true);
        this.dataF32 = new float[Math.max(1, initialCapacity) * dimension];
    }

    @Override
    public void ensureCapacity(int requested) {
        if (requested <= capacity) {
            return;
        }
        int newCapacity = capacity;
        while (newCapacity < requested) {
            newCapacity = Math.max(newCapacity * 2, requested);
        }
        dataF32 = Arrays.copyOf(dataF32, newCapacity * dimension);
        if (doubleView != null) {
            doubleView = Arrays.copyOf(doubleView, newCapacity * dimension);
        }
        capacity = newCapacity;
    }

    @Override
    public void set(int nodeId, double[] vector) {
        int offset = offset(nodeId);
        for (int i = 0; i < dimension; i++) {
            dataF32[offset + i] = (float) vector[i];
        }
        markDirty();
    }

    @Override
    public void set(int nodeId, double value) {
        int offset = offset(nodeId);
        Arrays.fill(dataF32, offset, offset + dimension, (float) value);
        markDirty();
    }

    @Override
    public double[] copy(int nodeId) {
        double[] copy = new double[dimension];
        int offset = offset(nodeId);
        for (int i = 0; i < dimension; i++) {
            copy[i] = dataF32[offset + i];
        }
        return copy;
    }

    @Override
    public void clear(int nodeId) {
        int offset = offset(nodeId);
        Arrays.fill(dataF32, offset, offset + dimension, 0f);
        markDirty();
    }

    @Override
    public double dot(int nodeId, double[] other) {
        return dot(dataF32, offset(nodeId), other, 0, dimension);
    }

    @Override
    public double dotBetween(int nodeA, int nodeB) {
        return dot(dataF32, offset(nodeA), dataF32, offset(nodeB), dimension);
    }

    @Override
    public double l2(int nodeId, double[] other) {
        return Math.sqrt(l2Squared(dataF32, offset(nodeId), other, 0, dimension));
    }

    @Override
    public double l2Between(int nodeA, int nodeB) {
        return Math.sqrt(l2Squared(dataF32, offset(nodeA), dataF32, offset(nodeB), dimension));
    }

    @Override
    public double l2Squared(int nodeId, double[] other) {
        return l2Squared(dataF32, offset(nodeId), other, 0, dimension);
    }

    @Override
    public double l2SquaredBetween(int nodeA, int nodeB) {
        return l2Squared(dataF32, offset(nodeA), dataF32, offset(nodeB), dimension);
    }

    @Override
    public double[] raw() {
        if (doubleView == null || doubleView.length < dataF32.length) {
            doubleView = new double[dataF32.length];
            doubleViewDirty = true;
        }
        if (doubleViewDirty) {
            for (int i = 0; i < dataF32.length; i++) {
                doubleView[i] = dataF32[i];
            }
            doubleViewDirty = false;
        }
        return doubleView;
    }

    @Override
    public long memoryBytes() {
        long bytes = (long) dataF32.length * Float.BYTES;
        if (doubleView != null) {
            bytes += (long) doubleView.length * Double.BYTES;
        }
        return bytes;
    }

    @Override
    public void reset(int initialCapacity) {
        int cap = Math.max(1, initialCapacity);
        this.capacity = cap;
        this.dataF32 = new float[cap * dimension];
        this.doubleView = null;
        this.doubleViewDirty = true;
    }

    private void markDirty() {
        doubleViewDirty = true;
    }

    private static double dot(float[] a, int offsetA, double[] b, int offsetB, int length) {
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

    private static double dot(float[] a, int offsetA, float[] b, int offsetB, int length) {
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

    private static double l2Squared(float[] a, int offsetA, double[] b, int offsetB, int length) {
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

    private static double l2Squared(float[] a, int offsetA, float[] b, int offsetB, int length) {
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
