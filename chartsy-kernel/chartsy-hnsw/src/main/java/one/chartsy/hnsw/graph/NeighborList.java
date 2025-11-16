package one.chartsy.hnsw.graph;

import java.util.Arrays;

/**
 * Fixed capacity adjacency list for a node within a specific level.
 */
public final class NeighborList {
    private final int capacity;
    private final int[] ids;
    private int size;

    public NeighborList(int capacity) {
        this.capacity = capacity;
        this.ids = new int[capacity];
        this.size = 0;
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return capacity;
    }

    public boolean contains(int id) {
        for (int i = 0; i < size; i++) {
            if (ids[i] == id) {
                return true;
            }
        }
        return false;
    }

    public boolean addIfAbsent(int id) {
        if (contains(id)) {
            return false;
        }
        if (size >= capacity) {
            return false;
        }
        ids[size++] = id;
        return true;
    }

    public boolean removeIfPresent(int id) {
        for (int i = 0; i < size; i++) {
            if (ids[i] == id) {
                int last = ids[--size];
                ids[i] = last;
                ids[size] = 0;
                return true;
            }
        }
        return false;
    }

    public int[] elements() {
        return ids;
    }

    public void replaceWith(int[] nodes, int count) {
        size = Math.min(count, capacity);
        Arrays.fill(ids, 0);
        System.arraycopy(nodes, 0, ids, 0, size);
    }

    public void clear() {
        Arrays.fill(ids, 0);
        size = 0;
    }
}
