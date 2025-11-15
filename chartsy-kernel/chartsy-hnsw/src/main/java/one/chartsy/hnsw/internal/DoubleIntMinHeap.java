package one.chartsy.hnsw.internal;

import java.util.Arrays;

final class DoubleIntMinHeap {
    private int size;
    private int[] nodes;
    private double[] distances;

    DoubleIntMinHeap(int initialCapacity) {
        this.nodes = new int[Math.max(1, initialCapacity)];
        this.distances = new double[Math.max(1, initialCapacity)];
    }

    void ensureCapacity(int capacity) {
        if (capacity <= nodes.length) {
            return;
        }
        int newCapacity = nodes.length;
        while (newCapacity < capacity) {
            newCapacity = Math.max(newCapacity * 2, capacity);
        }
        nodes = Arrays.copyOf(nodes, newCapacity);
        distances = Arrays.copyOf(distances, newCapacity);
    }

    void clear() {
        size = 0;
    }

    boolean isEmpty() {
        return size == 0;
    }

    int size() {
        return size;
    }

    void push(int node, double distance) {
        ensureCapacity(size + 1);
        int i = size++;
        nodes[i] = node;
        distances[i] = distance;
        siftUp(i);
    }

    int peekNode() {
        return nodes[0];
    }

    double peekDistance() {
        return distances[0];
    }

    int popNode() {
        int node = nodes[0];
        double dist = distances[0];
        nodes[0] = nodes[--size];
        distances[0] = distances[size];
        nodes[size] = 0;
        distances[size] = 0.0;
        if (size > 0) {
            siftDown(0);
        }
        return node;
    }

    double popDistance() {
        double dist = distances[0];
        nodes[0] = nodes[--size];
        distances[0] = distances[size];
        nodes[size] = 0;
        distances[size] = 0.0;
        if (size > 0) {
            siftDown(0);
        }
        return dist;
    }

    double popDistance(int[] outNode) {
        outNode[0] = nodes[0];
        double dist = distances[0];
        nodes[0] = nodes[--size];
        distances[0] = distances[size];
        nodes[size] = 0;
        distances[size] = 0.0;
        if (size > 0) {
            siftDown(0);
        }
        return dist;
    }

    private void siftUp(int idx) {
        while (idx > 0) {
            int parent = (idx - 1) >>> 1;
            if (distances[parent] <= distances[idx]) {
                break;
            }
            swap(idx, parent);
            idx = parent;
        }
    }

    private void siftDown(int idx) {
        while (true) {
            int left = (idx << 1) + 1;
            if (left >= size) {
                break;
            }
            int right = left + 1;
            int smallest = left;
            if (right < size && distances[right] < distances[left]) {
                smallest = right;
            }
            if (distances[idx] <= distances[smallest]) {
                break;
            }
            swap(idx, smallest);
            idx = smallest;
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

    int[] nodes() {
        return nodes;
    }

    double[] distances() {
        return distances;
    }
}
