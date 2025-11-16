package one.chartsy.hnsw.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Core adjacency structure for the HNSW index.
 */
public final class HnswGraph {
    private final int maxM;
    private final int maxM0;
    private int[] levelOfNode;
    private final List<NeighborList[]> layers;
    private int entryPoint = -1;
    private int maxLevel = -1;

    public HnswGraph(int maxM, int maxM0, int initialCapacity) {
        this.maxM = maxM;
        this.maxM0 = maxM0;
        this.layers = new ArrayList<>();
        reset(initialCapacity);
    }

    public int entryPoint() {
        return entryPoint;
    }

    public void setEntryPoint(int entryPoint) {
        this.entryPoint = entryPoint;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public void setMaxLevel(int level) {
        this.maxLevel = level;
    }

    public void ensureNodeCapacity(int capacity) {
        if (capacity <= levelOfNode.length) {
            return;
        }
        int oldLength = levelOfNode.length;
        int newLength = levelOfNode.length;
        while (newLength < capacity) {
            newLength = Math.max(newLength * 2, capacity);
        }
        levelOfNode = Arrays.copyOf(levelOfNode, newLength);
        Arrays.fill(levelOfNode, oldLength, newLength, -1);
        for (int level = 0; level < layers.size(); level++) {
            NeighborList[] layer = layers.get(level);
            NeighborList[] expanded = Arrays.copyOf(layer, newLength);
            layers.set(level, expanded);
        }
    }

    public void ensureLevel(int level, int capacity) {
        ensureNodeCapacity(capacity);
        while (layers.size() <= level) {
            NeighborList[] layer = new NeighborList[levelOfNode.length];
            layers.add(layer);
        }
    }

    public NeighborList ensureNeighborList(int level, int nodeId) {
        ensureLevel(level, nodeId + 1);
        NeighborList[] layer = layers.get(level);
        NeighborList list = layer[nodeId];
        if (list == null) {
            list = new NeighborList(level == 0 ? maxM0 : maxM);
            layer[nodeId] = list;
        }
        return list;
    }

    public NeighborList neighborList(int level, int nodeId) {
        if (level >= layers.size()) {
            return null;
        }
        NeighborList[] layer = layers.get(level);
        if (nodeId >= layer.length) {
            return null;
        }
        return layer[nodeId];
    }

    public void setLevelOfNode(int nodeId, int level) {
        ensureNodeCapacity(nodeId + 1);
        levelOfNode[nodeId] = level;
    }

    public int levelOfNode(int nodeId) {
        if (nodeId >= levelOfNode.length) {
            return -1;
        }
        return levelOfNode[nodeId];
    }

    public void clearNode(int nodeId) {
        int max = levelOfNode(nodeId);
        if (max < 0) {
            return;
        }
        for (int level = 0; level <= max && level < layers.size(); level++) {
            NeighborList list = neighborList(level, nodeId);
            if (list != null) {
                list.clear();
            }
        }
        levelOfNode[nodeId] = -1;
    }

    public long totalEdges() {
        long adjacencyEntries = 0L;
        for (NeighborList[] layer : layers) {
            for (NeighborList list : layer) {
                if (list != null) {
                    adjacencyEntries += list.size();
                }
            }
        }
        return adjacencyEntries / 2;
    }

    public int levelCount() {
        return layers.size();
    }

    public List<NeighborList[]> layers() {
        return layers;
    }

    public void reset(int initialCapacity) {
        this.entryPoint = -1;
        this.maxLevel = -1;
        this.levelOfNode = new int[Math.max(1, initialCapacity)];
        Arrays.fill(levelOfNode, -1);
        this.layers.clear();
        ensureLevel(0, initialCapacity);
    }
}
