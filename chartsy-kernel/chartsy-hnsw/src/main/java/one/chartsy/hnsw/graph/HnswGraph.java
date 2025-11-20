package one.chartsy.hnsw.graph;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Core adjacency structure for the HNSW index.
 */
public class HnswGraph {
    private final int maxM;
    private final int maxM0;
    private int[] levelOfNode;
    private NeighborList[] baseLayer;
    private final List<SparseLayer> upperLayers;
    private int entryPoint = -1;
    private int maxLevel = -1;

    public HnswGraph(int maxM, int maxM0, int initialCapacity) {
        this.maxM = maxM;
        this.maxM0 = maxM0;
        this.upperLayers = new ArrayList<>();
        reset(initialCapacity);
    }

    public final int entryPoint() {
        return entryPoint;
    }

    public void setEntryPoint(int entryPoint) {
        this.entryPoint = entryPoint;
    }

    public final int maxLevel() {
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
        baseLayer = Arrays.copyOf(baseLayer, newLength);
    }

    public void ensureLevel(int level, int capacity) {
        ensureNodeCapacity(capacity);
        if (level != 0) {
            while (upperLayers.size() < level) {
                upperLayers.add(new SparseLayer());
            }
        }
    }

    public NeighborList ensureNeighborList(int level, int nodeId) {
        ensureLevel(level, nodeId + 1);
        if (level == 0) {
            NeighborList list = baseLayer[nodeId];
            if (list == null) {
                list = new NeighborList(maxM0);
                baseLayer[nodeId] = list;
            }
            return list;
        }
        SparseLayer layer = upperLayers.get(level - 1);
        return layer.map().computeIfAbsent(nodeId, _ -> new NeighborList(maxM));
    }

    public NeighborList neighborList(int level, int nodeId) {
        if (level == 0) {
            if (nodeId >= baseLayer.length) {
                return null;
            }
            return baseLayer[nodeId];
        }
        int idx = level - 1;
        if (idx >= upperLayers.size() || idx < 0) {
            return null;
        }
        return upperLayers.get(idx).map().get(nodeId);
    }

    public void setLevelOfNode(int nodeId, int level) {
        ensureNodeCapacity(nodeId + 1);
        levelOfNode[nodeId] = level;
    }

    public int levelOfNode(int nodeId) {
        return nodeId < levelOfNode.length ? levelOfNode[nodeId] : -1;
    }

    public void clearNode(int nodeId) {
        int max = levelOfNode(nodeId);
        if (max < 0) {
            return;
        }
        for (int level = 0; level <= max; level++) {
            NeighborList list = neighborList(level, nodeId);
            if (list != null) {
                list.clear();
            }
            if (level > 0) {
                SparseLayer sparse = level - 1 < upperLayers.size() ? upperLayers.get(level - 1) : null;
                if (sparse != null) {
                    sparse.map().remove(nodeId);
                }
            }
        }
        if (nodeId < baseLayer.length) {
            baseLayer[nodeId] = null;
        }
        levelOfNode[nodeId] = -1;
    }

    public long totalEdges() {
        long edges = 0L;
        for (NeighborList list : baseLayer) {
            if (list != null) {
                edges += list.size();
            }
        }
        for (SparseLayer layer : upperLayers) {
            for (NeighborList list : layer.map().values()) {
                if (list != null) {
                    edges += list.size();
                }
            }
        }
        return edges;
    }

    public final int levelCount() {
        return 1 + upperLayers.size();
    }

    public void reset(int initialCapacity) {
        this.entryPoint = -1;
        this.maxLevel = -1;
        int cap = Math.max(1, initialCapacity);
        this.levelOfNode = new int[cap];
        Arrays.fill(levelOfNode, -1);
        this.baseLayer = new NeighborList[cap];
        this.upperLayers.clear();
    }

    public NeighborList[] level0() {
        return baseLayer;
    }

    public List<SparseLayer> upperLayers() {
        return upperLayers;
    }

    public static final class SparseLayer {

        private final Int2ObjectOpenHashMap<NeighborList> map = new Int2ObjectOpenHashMap<>();

        public Int2ObjectOpenHashMap<NeighborList> map() {
            return map;
        }
    }
}
