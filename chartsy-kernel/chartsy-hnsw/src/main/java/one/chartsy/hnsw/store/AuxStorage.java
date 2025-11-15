package one.chartsy.hnsw.store;

import java.util.Arrays;

/**
 * Auxiliary per-node scalar storage used by some {@link one.chartsy.hnsw.space.Space} variants.
 */
public final class AuxStorage {
    private double[] norms;
    private double[] means;
    private double[] centeredNorms;
    private int capacity;

    public AuxStorage(int initialCapacity) {
        this.capacity = Math.max(1, initialCapacity);
    }

    public void ensureCapacity(int required) {
        if (required <= capacity) {
            return;
        }
        int newCapacity = capacity;
        while (newCapacity < required) {
            newCapacity = Math.max(newCapacity * 2, required);
        }
        if (norms != null) {
            norms = Arrays.copyOf(norms, newCapacity);
        }
        if (means != null) {
            means = Arrays.copyOf(means, newCapacity);
        }
        if (centeredNorms != null) {
            centeredNorms = Arrays.copyOf(centeredNorms, newCapacity);
        }
        capacity = newCapacity;
    }

    public void setNorm(int index, double value) {
        if (value == 0.0 && norms == null) {
            return;
        }
        ensureNorms();
        norms[index] = value;
    }

    public double norm(int index) {
        return norms != null ? norms[index] : 0.0;
    }

    public void setMean(int index, double value) {
        if (value == 0.0 && means == null) {
            return;
        }
        ensureMeans();
        means[index] = value;
    }

    public double mean(int index) {
        return means != null ? means[index] : 0.0;
    }

    public void setCenteredNorm(int index, double value) {
        if (value == 0.0 && centeredNorms == null) {
            return;
        }
        ensureCenteredNorms();
        centeredNorms[index] = value;
    }

    public double centeredNorm(int index) {
        return centeredNorms != null ? centeredNorms[index] : 0.0;
    }

    public void clear(int index) {
        if (norms != null) {
            norms[index] = 0.0;
        }
        if (means != null) {
            means[index] = 0.0;
        }
        if (centeredNorms != null) {
            centeredNorms[index] = 0.0;
        }
    }

    public long memoryBytes() {
        long sum = 0L;
        if (norms != null) {
            sum += (long) norms.length * Double.BYTES;
        }
        if (means != null) {
            sum += (long) means.length * Double.BYTES;
        }
        if (centeredNorms != null) {
            sum += (long) centeredNorms.length * Double.BYTES;
        }
        return sum;
    }

    public void reset(int initialCapacity) {
        this.capacity = Math.max(1, initialCapacity);
        this.norms = null;
        this.means = null;
        this.centeredNorms = null;
    }

    /**
     * Eagerly materialises all auxiliary arrays to {@code capacity}. This avoids racy lazy
     * allocations when the structures are written to from multiple worker threads.
     */
    public void preallocateAll(int capacity) {
        ensureCapacity(capacity);
        ensureNorms();
        ensureMeans();
        ensureCenteredNorms();
    }

    private void ensureNorms() {
        if (norms == null) {
            norms = new double[capacity];
        }
    }

    private void ensureMeans() {
        if (means == null) {
            means = new double[capacity];
        }
    }

    private void ensureCenteredNorms() {
        if (centeredNorms == null) {
            centeredNorms = new double[capacity];
        }
    }
}
