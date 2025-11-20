package one.chartsy.hnsw.internal;

import java.util.Arrays;

final class BoundedMaxHeap {
    private int size;
    private int maxSize;
    private int[] nodes;
    private double[] distances;

    BoundedMaxHeap(int initialCapacity) {
        this.nodes = new int[Math.max(1, initialCapacity)];
        this.distances = new double[Math.max(1, initialCapacity)];
        this.maxSize = initialCapacity;
    }

    void ensureCapacity(int capacity) {
        if (capacity <= nodes.length) {
            maxSize = capacity;
            return;
        }
        int newCapacity = nodes.length;
        while (newCapacity < capacity) {
            newCapacity = Math.max(newCapacity * 2, capacity);
        }
        nodes = Arrays.copyOf(nodes, newCapacity);
        distances = Arrays.copyOf(distances, newCapacity);
        maxSize = capacity;
    }

    void reset(int capacity) {
        ensureCapacity(capacity);
        size = 0;
        maxSize = capacity;
    }

    boolean isEmpty() {
        return size == 0;
    }

    int size() {
        return size;
    }

    double worstDistance() {
        if (size == 0) {
            return Double.POSITIVE_INFINITY;
        }
        return distances[0];
    }

    void insert(int node, double distance) {
        if (size < maxSize) {
            ensureCapacity(maxSize);
            nodes[size] = node;
            distances[size] = distance;
            siftUp(size++);
            return;
        }
        if (distance >= distances[0]) {
            return;
        }
        nodes[0] = node;
        distances[0] = distance;
        siftDown(0);
    }

    void trimToMaxSize() {
        while (size > maxSize) {
            removeWorst();
        }
    }

    void removeWorst() {
        if (size == 0) {
            return;
        }
        nodes[0] = nodes[--size];
        distances[0] = distances[size];
        nodes[size] = 0;
        distances[size] = 0.0;
        if (size > 0) {
            siftDown(0);
        }
    }

    void toArrays(int[] outNodes, double[] outDistances) {
        System.arraycopy(nodes, 0, outNodes, 0, size);
        System.arraycopy(distances, 0, outDistances, 0, size);
    }

    private void siftUp(int index) {
        while (index > 0) {
            int parent = (index - 1) >>> 1;
            if (distances[parent] >= distances[index]) {
                break;
            }
            swap(index, parent);
            index = parent;
        }
    }

    private void siftDown(int index) {
        while (true) {
            int left = (index << 1) + 1;
            if (left >= size) {
                break;
            }
            int right = left + 1;
            int largest = left;
            if (right < size && distances[right] > distances[left]) {
                largest = right;
            }
            if (distances[index] >= distances[largest]) {
                break;
            }
            swap(index, largest);
            index = largest;
        }
    }

    private void swap(int a, int b) {
        int nodeA = nodes[a];
        nodes[a] = nodes[b];
        nodes[b] = nodeA;
        double distA = distances[a];
        distances[a] = distances[b];
        distances[b] = distA;
    }
}
