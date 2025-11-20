package one.chartsy.hnsw.internal;

import java.util.Arrays;

public class SearchScratch {
    private int[] visitMark;
    private int visitToken = 1;
    private final DoubleIntMinHeap candidates;
    private final BoundedMaxHeap results;
    private int[] tmpNodes;
    private double[] tmpDistances;
    private int[] tmpNodesSecondary;

    protected SearchScratch(int initialNodes, int initialEf) {
        this.visitMark = new int[Math.max(1, initialNodes)];
        this.candidates = new DoubleIntMinHeap(initialEf);
        this.results = new BoundedMaxHeap(initialEf);
        this.tmpNodes = new int[Math.max(16, initialEf)];
        this.tmpNodesSecondary = new int[Math.max(16, initialEf)];
        this.tmpDistances = new double[Math.max(16, initialEf)];
    }

    protected void ensure(int nodeCapacity, int bufferCapacity) {
        ensureVisitMark(nodeCapacity);
        ensureBuffers(bufferCapacity);
    }

    protected void reset(int nodeCapacity, int efSearch) {
        ensure(nodeCapacity, efSearch);
        candidates.ensureCapacity(efSearch);
        results.reset(efSearch);
        candidates.clear();
        if (++visitToken == 0) {
            Arrays.fill(visitMark, 0);
            visitToken = 1;
        }
    }

    protected boolean tryVisit(int nodeId) {
        if (nodeId >= visitMark.length) {
            ensureVisitMark(nodeId + 1);
        }
        if (visitMark[nodeId] == visitToken) {
            return false;
        }
        visitMark[nodeId] = visitToken;
        return true;
    }

    protected final DoubleIntMinHeap candidates() {
        return candidates;
    }

    protected final BoundedMaxHeap results() {
        return results;
    }

    protected int[] tmpNodes(int required) {
        ensureBuffers(required);
        return tmpNodes;
    }

    protected double[] tmpDistances(int required) {
        ensureBuffers(required);
        return tmpDistances;
    }

    protected int[] tmpNodesSecondary(int required) {
        ensureBuffers(required);
        return tmpNodesSecondary;
    }

    private void ensureVisitMark(int capacity) {
        if (capacity <= visitMark.length) {
            return;
        }
        int newCapacity = visitMark.length;
        while (newCapacity < capacity) {
            newCapacity = Math.max(newCapacity * 2, capacity);
        }
        visitMark = Arrays.copyOf(visitMark, newCapacity);
    }

    private void ensureBuffers(int capacity) {
        if (capacity <= tmpNodes.length) {
            return;
        }
        int newCapacity = tmpNodes.length;
        while (newCapacity < capacity) {
            newCapacity = Math.max(newCapacity * 2, capacity);
        }
        tmpNodes = Arrays.copyOf(tmpNodes, newCapacity);
        tmpNodesSecondary = Arrays.copyOf(tmpNodesSecondary, newCapacity);
        tmpDistances = Arrays.copyOf(tmpDistances, newCapacity);
    }
}
