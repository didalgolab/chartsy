package one.chartsy.hnsw.internal;

import java.util.BitSet;

import one.chartsy.hnsw.NeighborSelectHeuristic;
import one.chartsy.hnsw.graph.HnswGraph;
import one.chartsy.hnsw.graph.NeighborList;
import one.chartsy.hnsw.space.QueryContext;
import one.chartsy.hnsw.space.Space;

final class HnswInternalUtil {
    private HnswInternalUtil() {}

    static int greedySearchOnLevel(HnswGraph graph, Space space, BitSet deleted, QueryContext query,
            int entryPoint, int level) {
        int current = entryPoint;
        double currentDistance = space.distance(query, current);
        boolean changed;
        do {
            changed = false;
            NeighborList list = graph.neighborList(level, current);
            if (list == null) {
                break;
            }
            int[] neighbors = list.elements();
            for (int i = 0; i < list.size(); i++) {
                int neighbor = neighbors[i];
                if (deleted.get(neighbor)) {
                    continue;
                }
                double distance = space.distance(query, neighbor);
                if (distance < currentDistance) {
                    currentDistance = distance;
                    current = neighbor;
                    changed = true;
                }
            }
        } while (changed);
        return current;
    }

    static int filterCandidates(BitSet deleted, long[] internalToId, int[] nodes, double[] distances,
            int count, int disallowNode) {
        int write = 0;
        for (int i = 0; i < count; i++) {
            int node = nodes[i];
            if (node == disallowNode || node < 0) {
                continue;
            }
            if (deleted.get(node)) {
                continue;
            }
            if (internalToId[node] < 0) {
                continue;
            }
            double distance = distances[i];
            if (!Double.isFinite(distance)) {
                continue;
            }
            boolean duplicate = false;
            for (int j = 0; j < write; j++) {
                if (nodes[j] == node) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) {
                continue;
            }
            nodes[write] = node;
            distances[write] = distance;
            write++;
        }
        return write;
    }

    static int selectNeighbors(Space space, NeighborSelectHeuristic heuristic, double diversificationAlpha,
            int nodeId, int level, int[] candidates, double[] distances, int count, int maxDegree,
            int[] selectedOut) {
        int selected = 0;
        if (heuristic == NeighborSelectHeuristic.SIMPLE) {
            for (int i = 0; i < count && selected < maxDegree; i++) {
                int candidate = candidates[i];
                if (candidate == nodeId) {
                    continue;
                }
                selectedOut[selected++] = candidate;
            }
            return selected;
        }
        for (int i = 0; i < count && selected < maxDegree; i++) {
            int candidate = candidates[i];
            if (candidate == nodeId) {
                continue;
            }
            double candidateDistance = distances[i];
            boolean occluded = false;
            for (int j = 0; j < selected; j++) {
                int other = selectedOut[j];
                double dist = space.distanceBetweenNodes(candidate, other);
                if (!Double.isFinite(dist)) {
                    continue;
                }
                if (dist <= candidateDistance * diversificationAlpha) {
                    occluded = true;
                    break;
                }
            }
            if (!occluded) {
                selectedOut[selected++] = candidate;
            }
        }
        if (selected < maxDegree) {
            for (int i = 0; i < count && selected < maxDegree; i++) {
                int candidate = candidates[i];
                if (candidate == nodeId) {
                    continue;
                }
                boolean alreadySelected = false;
                for (int j = 0; j < selected; j++) {
                    if (selectedOut[j] == candidate) {
                        alreadySelected = true;
                        break;
                    }
                }
                if (!alreadySelected) {
                    selectedOut[selected++] = candidate;
                }
            }
        }
        return selected;
    }

    static void sortByDistance(int[] nodes, double[] distances, int length) {
        if (length <= 1) {
            return;
        }
        quickSort(nodes, distances, 0, length - 1);
    }

    private static void quickSort(int[] nodes, double[] distances, int left, int right) {
        int i = left;
        int j = right;
        double pivot = distances[(left + right) >>> 1];
        while (i <= j) {
            while (distances[i] < pivot) {
                i++;
            }
            while (distances[j] > pivot) {
                j--;
            }
            if (i <= j) {
                double tmpDist = distances[i];
                distances[i] = distances[j];
                distances[j] = tmpDist;
                int tmpNode = nodes[i];
                nodes[i] = nodes[j];
                nodes[j] = tmpNode;
                i++;
                j--;
            }
        }
        if (left < j) {
            quickSort(nodes, distances, left, j);
        }
        if (i < right) {
            quickSort(nodes, distances, i, right);
        }
    }
}
