package one.chartsy.hnsw.internal;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

import one.chartsy.hnsw.DeletionPolicy;
import one.chartsy.hnsw.DuplicatePolicy;
import one.chartsy.hnsw.HnswConfig;
import one.chartsy.hnsw.HnswIndex;
import one.chartsy.hnsw.HnswStats;
import one.chartsy.hnsw.NeighborSelectHeuristic;
import one.chartsy.hnsw.SearchResult;
import one.chartsy.hnsw.graph.HnswGraph;
import one.chartsy.hnsw.graph.NeighborList;
import one.chartsy.hnsw.space.QueryContext;
import one.chartsy.hnsw.space.Space;
import one.chartsy.hnsw.space.Spaces;
import one.chartsy.hnsw.store.AuxStorage;
import one.chartsy.hnsw.store.VectorStorage;

public class DefaultHnswIndex implements HnswIndex {
    private static final long MAGIC = 0x484E535730303031L; // "HNSW0001"
    private static final int SERIAL_VERSION = 4;
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    private final HnswConfig config;
    private final VectorStorage vectorStorage;
    private final AuxStorage auxStorage;
    private final Space space;
    private final HnswGraph graph;
    private final BitSet deleted;
    private final NodeDirectory directory;
    private final EntryPointPolicy entryPoints;
    private final ResultCollector resultCollector;
    private final Searcher searcher;
    private final GraphConnector connector;
    private final RepairEngine repairEngine;
    private final IndexPersistence persistence;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ThreadLocal<SearchScratch> searchScratch;

    public DefaultHnswIndex(HnswConfig config) {
        this(config, false);
    }

    private DefaultHnswIndex(HnswConfig config, boolean skipValidation) {
        this(config, skipValidation, new VectorStorage(config.dimension, config.initialCapacity));
    }

    private DefaultHnswIndex(HnswConfig config, boolean skipValidation, VectorStorage storage) {
        this.config = new HnswConfig(config);
        if (!skipValidation) {
            this.config.validate();
        }
        int capacity = this.config.initialCapacity;
        this.vectorStorage = storage;
        this.vectorStorage.ensureCapacity(capacity);
        this.auxStorage = new AuxStorage(capacity);
        this.graph = new HnswGraph(this.config.M, this.config.maxM0, capacity);
        this.space = this.config.spaceFactory.create(this.config, vectorStorage, auxStorage);
        this.space.preallocate(capacity);
        this.deleted = new BitSet(capacity);
        this.searchScratch = ThreadLocal.withInitial(() -> new SearchScratch(capacity, this.config.defaultEfSearch));
        this.directory = new NodeDirectory(capacity);
        this.entryPoints = new EntryPointPolicy(config, directory, deleted, graph);
        this.resultCollector = new ResultCollector(directory, deleted);
        this.searcher = new Searcher();
        this.connector = new GraphConnector();
        this.repairEngine = new RepairEngine();
        this.persistence = new SerializerV4();
    }

    static DefaultHnswIndex newForBulk(HnswConfig config) {
        VectorStorage storage = new VectorStorage(config.dimension, config.initialCapacity);
        return new DefaultHnswIndex(config, false, storage);
    }

    public VectorStorage getVectorStorage() {
        return vectorStorage;
    }

    public int getNodeCount() {
        lock.readLock().lock();
        try {
            return directory.nodeCount();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int getNodeId(long key) {
        lock.readLock().lock();
        try {
            return directory.lookup(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public long getKey(int nodeId) {
        lock.readLock().lock();
        try {
            return directory.idOf(nodeId);
        } finally {
            lock.readLock().unlock();
        }
    }

    private SearchScratch scratch() {
        return searchScratch.get();
    }

    Space space() {
        return space;
    }

    HnswGraph graph() {
        return graph;
    }

    BitSet deleted() {
        return deleted;
    }

    void bulkSetCounts(int nodeCount, int size) {
        directory.bulkSetCounts(nodeCount, size);
    }

    void bulkPublishIdMap(Long2IntOpenHashMap map, long[] newInternalToId) {
        directory.bulkPublishIdMap(map, newInternalToId);
    }

    void ensureCapacity(int capacity) {
        directory.ensureCapacity(capacity);
    }

    @Override
    public void add(long key, double[] vector) {
        Objects.requireNonNull(vector, "vector");
        requireDimension(vector);
        lock.writeLock().lock();
        try {
            int existing = directory.lookup(key);
            if (existing >= 0) {
                if (config.duplicatePolicy == DuplicatePolicy.REJECT) {
                    throw new IllegalArgumentException("Duplicate id " + key);
                }
                repairEngine.removeNode(existing, true);
            }

            int nodeId = directory.allocateNodeId();
            directory.ensureCapacity(nodeId + 1);
            deleted.clear(nodeId);
            directory.recordInsertion(key, nodeId);

            space.onInsert(nodeId, vector);

            int level = entryPoints.sampleLevel();
            graph.setLevelOfNode(nodeId, level);

            int currentMaxLevel = graph.maxLevel();
            if (currentMaxLevel < 0) {
                graph.setEntryPoint(nodeId);
                graph.setMaxLevel(level);
                return;
            }

            QueryContext query = space.prepareQueryForNode(nodeId);
            int entryPoint = graph.entryPoint();
            if (entryPoint == nodeId || entryPoint < 0 || entryPoint >= directory.nodeCount()
                    || directory.idOf(entryPoint) < 0 || deleted.get(entryPoint)) {
                int alternate = entryPoints.selectAlternateEntry(nodeId);
                if (alternate >= 0) {
                    entryPoint = alternate;
                }
            }

            boolean newTopLevel = level > currentMaxLevel;
            connector.linkNode(nodeId, query, level, entryPoint);

            if (newTopLevel) {
                graph.setEntryPoint(nodeId);
                graph.setMaxLevel(level);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void requireDimension(double[] vector) {
        if (vector.length != config.dimension) {
            throw new IllegalArgumentException(
                    "Expected vector dimension " + config.dimension + " but was " + vector.length);
        }
    }

    @Override
    public boolean remove(long key) {
        lock.writeLock().lock();
        try {
            int nodeId = directory.lookup(key);
            if (nodeId < 0 || deleted.get(nodeId)) {
                return false;
            }
            return repairEngine.removeNode(nodeId, true);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean contains(long key) {
        lock.readLock().lock();
        try {
            int nodeId = directory.lookup(key);
            return nodeId >= 0 && !deleted.get(nodeId);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<SearchResult> nearestNeighbors(double[] query, int k) {
        return nearestNeighbors(query, k, config.defaultEfSearch);
    }

    @Override
    public List<SearchResult> nearestNeighbors(double[] query, int k, int efSearch) {
        Objects.requireNonNull(query, "query");
        if (k <= 0)
            throw new IllegalArgumentException("k must be positive");

        lock.readLock().lock();
        try {
            if (directory.size() <= 0 || graph.entryPoint() < 0)
                return List.of();

            return searcher.searchKnn(query, k, efSearch);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<SearchResult> nearestNeighborsExact(double[] query, int k) {
        Objects.requireNonNull(query, "query");
        if (k <= 0)
            throw new IllegalArgumentException("k must be positive");

        lock.readLock().lock();
        try {
            return searcher.exactSearch(space.prepareQuery(query), k);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return directory.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int dimension() {
        return config.dimension;
    }

    @Override
    public HnswStats stats() {
        lock.readLock().lock();
        try {
            HnswStats.Builder builder = HnswStats.builder()
                    .size(directory.size())
                    .totalNodes(directory.nodeCount())
                    .maxLevel(graph.maxLevel())
                    .totalEdges(graph.totalEdges())
                    .memoryBytes(vectorStorage.memoryBytes() + auxStorage.memoryBytes());
            int active = 0;
            double levelSum = 0.0;
            double degreeSum = 0.0;
            double degreeLevel0Sum = 0.0;
            long[] internalToId = directory.internalIds();
            for (int node = 0; node < directory.nodeCount(); node++) {
                if (internalToId[node] < 0 || deleted.get(node)) {
                    continue;
                }
                active++;
                int nodeLevel = graph.levelOfNode(node);
                levelSum += nodeLevel;
                NeighborList base = graph.neighborList(0, node);
                if (base != null) {
                    degreeLevel0Sum += base.size();
                    degreeSum += base.size();
                }
                for (int level = 1; level <= nodeLevel; level++) {
                    NeighborList list = graph.neighborList(level, node);
                    if (list != null) {
                        degreeSum += list.size();
                    }
                }
            }
            if (active > 0) {
                builder.averageLevel(levelSum / active);
                builder.averageDegree(degreeSum / active);
                builder.averageDegreeLevel0(degreeLevel0Sum / active);
            }
            return builder.build();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void clear() {
        lock.writeLock().lock();
        try {
            directory.clear();
            deleted.clear();
            vectorStorage.reset(config.initialCapacity);
            auxStorage.reset(config.initialCapacity);
            graph.reset(config.initialCapacity);
            space.onClear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void save(Path path) throws IOException {
        lock.readLock().lock();
        try {
            persistence.save(this, path);
        } finally {
            lock.readLock().unlock();
        }
    }

    public static HnswIndex load(Path path) throws IOException {
        return new SerializerV4().load(path);
    }

    protected class NodeDirectory {

        private final Long2IntOpenHashMap idToInternal;
        private long[] internalToId;
        private final IntArrayList freeList = new IntArrayList();
        private int nodeCount;
        private int size;

        protected NodeDirectory(int capacity) {
            this.internalToId = new long[Math.max(1, capacity)];
            Arrays.fill(this.internalToId, -1L);
            this.idToInternal = new Long2IntOpenHashMap(Math.max(1, capacity));
            this.idToInternal.defaultReturnValue(-1);
        }

        int allocateNodeId() {
            if (!freeList.isEmpty()) {
                return freeList.removeInt(freeList.size() - 1);
            }
            return nodeCount++;
        }

        void ensureCapacity(int capacity) {
            if (capacity <= internalToId.length) {
                vectorStorage.ensureCapacity(capacity);
                auxStorage.ensureCapacity(capacity);
                graph.ensureNodeCapacity(capacity);
                return;
            }
            int newCapacity = internalToId.length;
            while (newCapacity < capacity) {
                newCapacity = Math.max(newCapacity * 2, capacity);
            }
            int oldLength = internalToId.length;
            internalToId = Arrays.copyOf(internalToId, newCapacity);
            Arrays.fill(internalToId, oldLength, newCapacity, -1L);
            vectorStorage.ensureCapacity(newCapacity);
            auxStorage.ensureCapacity(newCapacity);
            graph.ensureNodeCapacity(newCapacity);
        }

        int lookup(long id) {
            return idToInternal.getOrDefault(id, -1);
        }

        long idOf(int nodeId) {
            return nodeId >= 0 && nodeId < internalToId.length ? internalToId[nodeId] : -1L;
        }

        long[] internalIds() {
            return internalToId;
        }

        int nodeCount() {
            return nodeCount;
        }

        int size() {
            return size;
        }

        void recordInsertion(long id, int nodeId) {
            internalToId[nodeId] = id;
            idToInternal.put(id, nodeId);
            size++;
        }

        void clearMapping(int nodeId, boolean removeFromMap) {
            long existingId = internalToId[nodeId];
            if (removeFromMap && existingId >= 0) {
                idToInternal.remove(existingId);
            }
            if (existingId >= 0) {
                size--;
            }
            internalToId[nodeId] = -1L;
        }

        void releaseNodeId(int nodeId) {
            freeList.add(nodeId);
        }

        void bulkSetCounts(int nodeCount, int size) {
            this.nodeCount = nodeCount;
            this.size = size;
            freeList.clear();
        }

        void bulkPublishIdMap(Long2IntOpenHashMap map, long[] newInternalToId) {
            idToInternal.clear();
            idToInternal.putAll(map);
            idToInternal.defaultReturnValue(-1);
            internalToId = Arrays.copyOf(newInternalToId, newInternalToId.length);
        }

        void clear() {
            idToInternal.clear();
            Arrays.fill(internalToId, -1L);
            freeList.clear();
            nodeCount = 0;
            size = 0;
        }

        void rebuildAfterLoad(BitSet deleted, HnswGraph graph) {
            idToInternal.clear();
            freeList.clear();
            int totalNodes = this.nodeCount;
            for (int node = 0; node < totalNodes; node++) {
                long id = internalToId[node];
                boolean isDeleted = deleted.get(node);
                if (id >= 0 && !isDeleted) {
                    idToInternal.put(id, node);
                } else {
                    internalToId[node] = -1L;
                    freeList.add(node);
                    graph.clearNode(node);
                }
            }
            this.size = idToInternal.size();
        }
    }

    protected static class EntryPointPolicy {

        private final SplittableRandom random;
        private final HnswConfig config;
        private final NodeDirectory directory;
        private final BitSet deleted;
        private final HnswGraph graph;

        protected EntryPointPolicy(HnswConfig config, NodeDirectory directory, BitSet deleted, HnswGraph graph) {
            this.random = new SplittableRandom(config.randomSeed);
            this.config = config;
            this.directory = directory;
            this.deleted = deleted;
            this.graph = graph;
        }

        int sampleLevel() {
            double u = 1.0 - random.nextDouble();
            double value = -Math.log(u) * config.levelLambda;
            return Math.max(0, (int) value);
        }

        int selectAlternateEntry(int excludeNode) {
            int bestNode = -1;
            int bestLevel = -1;
            for (int i = 0; i < directory.nodeCount(); i++) {
                if (i == excludeNode) {
                    continue;
                }
                if (directory.internalIds()[i] < 0 || deleted.get(i)) {
                    continue;
                }
                int level = graph.levelOfNode(i);
                if (level > bestLevel) {
                    bestLevel = level;
                    bestNode = i;
                }
            }
            return bestNode;
        }

        void reselectEntryPoint() {
            int bestNode = -1;
            int bestLevel = -1;
            for (int node = 0; node < directory.nodeCount(); node++) {
                if (deleted.get(node)) {
                    continue;
                }
                int nodeLevel = graph.levelOfNode(node);
                if (nodeLevel > bestLevel) {
                    bestLevel = nodeLevel;
                    bestNode = node;
                }
            }
            graph.setEntryPoint(bestNode);
            graph.setMaxLevel(bestLevel);
        }
    }

    protected class Searcher {

        List<SearchResult> searchKnn(double[] query, int k, int efSearch) {
            int ef = Math.max(efSearch, k);
            QueryContext queryContext = space.prepareQuery(query);
            if (config.exactSearch) {
                return exactSearch(queryContext, k);
            }
            SearchScratch scratch = scratch();
            scratch.reset(Math.max(directory.nodeCount(), 1), ef);

            int entryPoint = graph.entryPoint();
            entryPoint = greedySearchOnAllLevels(queryContext, entryPoint);

            executeBaseLayerSearch(queryContext, k, ef, entryPoint, scratch);

            return resultCollector.collectResults(k, scratch);
        }

        List<SearchResult> exactSearch(QueryContext query, int k) {
            BoundedMaxHeap heap = new BoundedMaxHeap(Math.max(k, 16));

            long[] internalToId = directory.internalIds();
            for (int node = 0; node < directory.nodeCount(); node++) {
                long id = internalToId[node];
                if (id < 0 || deleted.get(node))
                    continue;

                double distance = space.distance(query, node);
                if (!Double.isFinite(distance))
                    continue;

                heap.insert(node, distance);
            }

            int count = heap.size();
            int[] nodes = new int[count];
            double[] distances = new double[count];
            heap.toArrays(nodes, distances);
            HnswInternalUtil.sortByDistance(nodes, distances, count);

            List<SearchResult> results = new ArrayList<>(Math.min(k, count));
            for (int i = 0; i < count && results.size() < k; i++) {
                long id = internalToId[nodes[i]];
                if (id >= 0 && !deleted.get(nodes[i])) {
                    results.add(new SearchResult(id, distances[i]));
                }
            }
            return results;
        }

        protected int greedySearchOnAllLevels(QueryContext query, int entryPoint) {
            int current = entryPoint;
            for (int level = graph.maxLevel(); level > 0; level--)
                current = greedySearchOnLevel(query, current, level);

            return current;
        }

        protected int greedySearchOnLevel(QueryContext query, int entryPoint, int level) {
            return HnswInternalUtil.greedySearchOnLevel(graph, space, deleted, query, entryPoint, level);
        }

        protected void executeBaseLayerSearch(QueryContext query, int k, int efSearch, int entryPoint, SearchScratch scratch) {
            DoubleIntMinHeap candidates = scratch.candidates();
            BoundedMaxHeap results = scratch.results();
            double entryDistance = space.distance(query, entryPoint);
            candidates.push(entryPoint, entryDistance);
            results.insert(entryPoint, entryDistance);
            scratch.tryVisit(entryPoint);

            executeSearchOnLevel(query, 0, efSearch, -1, candidates, results, scratch);
        }
    }

    protected static class ResultCollector {

        private final NodeDirectory directory;
        private final BitSet deleted;

        protected ResultCollector(NodeDirectory directory, BitSet deleted) {
            this.directory = directory;
            this.deleted = deleted;
        }

        protected List<SearchResult> collectResults(int k, SearchScratch scratch) {
            BoundedMaxHeap results = scratch.results();
            int count = results.size();
            if (count == 0) {
                return List.of();
            }
            int[] nodes = scratch.tmpNodes(count);
            double[] distances = scratch.tmpDistances(count);
            results.toArrays(nodes, distances);
            int valid = HnswInternalUtil.filterCandidates(deleted, directory.internalIds(), nodes, distances, count, -1);
            HnswInternalUtil.sortByDistance(nodes, distances, valid);
            List<SearchResult> out = new ArrayList<>(Math.min(k, valid));
            for (int i = 0; i < valid && out.size() < k; i++) {
                int nodeId = nodes[i];
                if (nodeId < 0 || nodeId >= directory.internalIds().length || deleted.get(nodeId))
                    continue;

                long id = directory.internalIds()[nodeId];
                if (id < 0)
                    continue;

                out.add(new SearchResult(id, distances[i]));
            }
            return out;
        }
    }

    private final class GraphConnector {

        void linkNode(int nodeId, QueryContext query, int level, int entryPoint) {
            int currentMaxLevel = graph.maxLevel();
            int point = entryPoint;
            for (int levelCursor = currentMaxLevel; levelCursor > level; levelCursor--) {
                point = searcher.greedySearchOnLevel(query, point, levelCursor);
            }
            for (int levelCursor = Math.min(level, currentMaxLevel); levelCursor >= 0; levelCursor--) {
                point = connectOnLevel(nodeId, query, point, levelCursor);
            }
        }

        int connectOnLevel(int nodeId, QueryContext query, int entryPoint, int level) {
            int ef = Math.max(config.efConstruction, config.M);
            SearchScratch scratch = scratch();
            scratch.reset(Math.max(directory.nodeCount(), 1), ef);
            DoubleIntMinHeap candidates = scratch.candidates();
            BoundedMaxHeap results = scratch.results();

            double entryDistance = space.distance(query, entryPoint);
            candidates.push(entryPoint, entryDistance);
            results.insert(entryPoint, entryDistance);
            scratch.tryVisit(entryPoint);

            int candidateCount = executeSearchOnLevel(query, level, ef, nodeId, candidates, results, scratch);

            int[] candidateNodes = scratch.tmpNodes(candidateCount);
            double[] candidateDistances = scratch.tmpDistances(candidateCount);
            results.toArrays(candidateNodes, candidateDistances);
            candidateCount = HnswInternalUtil.filterCandidates(
                    deleted, directory.internalIds(), candidateNodes, candidateDistances, candidateCount, nodeId);
            HnswInternalUtil.sortByDistance(candidateNodes, candidateDistances, candidateCount);

            int maxDegree = level == 0 ? config.maxM0 : config.M;
            int[] selected = scratch.tmpNodesSecondary(Math.max(candidateCount, maxDegree));
            int selectedCount = HnswInternalUtil.selectNeighbors(
                    space, config.neighborHeuristic, config.diversificationAlpha,
                    nodeId, level, candidateNodes, candidateDistances, candidateCount, maxDegree, selected);

            int[] neighbors = selectedCount > 0 ? Arrays.copyOf(selected, selectedCount) : EMPTY_INT_ARRAY;
            NeighborList nodeList = graph.ensureNeighborList(level, nodeId);
            nodeList.replaceWith(neighbors, selectedCount);

            for (int i = 0; i < selectedCount; i++) {
                connectMutual(level, nodeId, neighbors[i], scratch);
            }

            if (selectedCount > 0) {
                return neighbors[0];
            }
            return entryPoint;
        }

        void connectMutual(int level, int source, int target, SearchScratch scratch) {
            NeighborList list = graph.ensureNeighborList(level, target);
            int maxDegree = level == 0 ? config.maxM0 : config.M;

            int[] elements = list.elements();
            int initialSize = list.size();
            int[] previousNeighbors = Arrays.copyOf(elements, initialSize);

            int[] candidates = scratch.tmpNodes(initialSize + 1);
            double[] distances = scratch.tmpDistances(initialSize + 1);
            int candidateCount = collectCandidates(source, target, elements, initialSize, candidates, distances);

            HnswInternalUtil.sortByDistance(candidates, distances, candidateCount);
            int[] selected = scratch.tmpNodesSecondary(Math.max(candidateCount, maxDegree));

            int selectedCount = HnswInternalUtil.selectNeighbors(
                    space, config.neighborHeuristic, config.diversificationAlpha,
                    target, level, candidates, distances, candidateCount, maxDegree, selected
            );

            selectedCount = retainBestPreviousNeighbor(target, source, previousNeighbors, selected, selectedCount, maxDegree);
            selectedCount = ensureSourceInList(target, source, selected, selectedCount, maxDegree);
            list.replaceWith(selected, selectedCount);
        }

        private int collectCandidates(int source, int target, int[] currentNeighbors, int size, int[] candidates, double[] distances) {
            int count = 0;
            boolean containsSource = false;

            for (int i = 0; i < size; i++) {
                int neighbor = currentNeighbors[i];
                if (neighbor == source)
                    containsSource = true;

                candidates[count] = neighbor;
                distances[count] = space.distanceBetweenNodes(target, neighbor);
                count++;
            }

            if (!containsSource) {
                candidates[count] = source;
                distances[count] = space.distanceBetweenNodes(target, source);
                count++;
            }
            return count;
        }

        private int retainBestPreviousNeighbor(int target, int source, int[] previousNeighbors, int[] selected, int selectedCount, int maxDegree) {
            if (selectedCount == 0 || previousNeighbors.length == 0)
                return selectedCount;

            for (int prev : previousNeighbors)
                for (int i = 0; i < selectedCount; i++)
                    if (selected[i] == prev)
                        return selectedCount;

            int bestPrev = -1;
            double bestPrevDist = Double.POSITIVE_INFINITY;

            for (int prev : previousNeighbors) {
                if (prev == source)
                    continue;

                double dist = space.distanceBetweenNodes(target, prev);
                if (dist < bestPrevDist) {
                    bestPrevDist = dist;
                    bestPrev = prev;
                }
            }
            return bestPrev < 0 ? selectedCount : insertOrRefineNeighbor(target, bestPrev, bestPrevDist, selected, selectedCount, maxDegree);
        }

        private int ensureSourceInList(int target, int source, int[] selected, int selectedCount, int maxDegree) {
            for (int i = 0; i < selectedCount; i++)
                if (selected[i] == source)
                    return selectedCount;

            double sourceDist = space.distanceBetweenNodes(target, source);
            return insertOrRefineNeighbor(target, source, sourceDist, selected, selectedCount, maxDegree);
        }

        /**
         * Core logic for "Force Insert":
         * If list < maxDegree: Append.
         * If list full: Find farthest node. If new node is closer than farthest, replace it.
         */
        private int insertOrRefineNeighbor(int target, int candidateNode, double candidateDist, int[] selected, int currentCount, int maxDegree) {
            if (currentCount < maxDegree) {
                selected[currentCount] = candidateNode;
                return currentCount + 1;
            }

            int farthestIdx = -1;
            double farthestDist = Double.NEGATIVE_INFINITY;

            for (int i = 0; i < currentCount; i++) {
                double dist = space.distanceBetweenNodes(target, selected[i]);
                if (dist > farthestDist) {
                    farthestDist = dist;
                    farthestIdx = i;
                }
            }

            if (farthestIdx >= 0 && candidateDist < farthestDist)
                selected[farthestIdx] = candidateNode;

            return currentCount;
        }
    }

    protected class RepairEngine {

        protected boolean removeNode(int nodeId, boolean removeFromMap) {
            if (deleted.get(nodeId)) {
                return false;
            }
            deleted.set(nodeId);
            directory.clearMapping(nodeId, removeFromMap);
            int level = graph.levelOfNode(nodeId);
            for (int lvl = 0; lvl <= level; lvl++) {
                NeighborList list = graph.neighborList(lvl, nodeId);
                if (list == null) {
                    continue;
                }
                int[] neighbors = Arrays.copyOf(list.elements(), list.size());
                for (int neighbor : neighbors) {
                    NeighborList otherList = graph.neighborList(lvl, neighbor);
                    if (otherList != null) {
                        otherList.removeIfPresent(nodeId);
                    }
                }
                if (config.deletionPolicy == DeletionPolicy.LAZY_WITH_REPAIR) {
                    reconnectNeighborsAfterRemoval(lvl, neighbors);
                }
                list.clear();
            }
            graph.setLevelOfNode(nodeId, -1);
            space.onRemove(nodeId);
            directory.releaseNodeId(nodeId);
            if (graph.entryPoint() == nodeId) {
                entryPoints.reselectEntryPoint();
            }
            return true;
        }

        private void reconnectNeighborsAfterRemoval(int level, int[] neighbors) {
            SearchScratch scratch = scratch();
            for (int neighbor : neighbors) {
                if (neighbor < 0 || deleted.get(neighbor)) {
                    continue;
                }
                for (int other : neighbors) {
                    if (other <= neighbor || other < 0 || deleted.get(other)) {
                        continue;
                    }
                    connector.connectMutual(level, neighbor, other, scratch);
                    connector.connectMutual(level, other, neighbor, scratch);
                }
                if (config.efRepair > 0) {
                    runRepairSearch(level, neighbor, neighbors, scratch);
                }
            }
        }

        private void runRepairSearch(int level, int source, int[] seeds, SearchScratch scratch) {
            QueryContext query = space.prepareQueryForNode(source);
            int ef = Math.max(config.efRepair, config.M);
            scratch.reset(Math.max(directory.nodeCount(), 1), ef);
            DoubleIntMinHeap candidates = scratch.candidates();
            BoundedMaxHeap results = scratch.results();

            for (int seed : seeds) {
                if (seed == source || seed < 0 || deleted.get(seed)) {
                    continue;
                }
                double dist = space.distance(query, seed);
                candidates.push(seed, dist);
                results.insert(seed, dist);
                scratch.tryVisit(seed);
            }

            int candidateCount = executeSearchOnLevel(query, level, ef, source, candidates, results, scratch);

            int[] nodes = scratch.tmpNodes(candidateCount);
            double[] distances = scratch.tmpDistances(candidateCount);
            results.toArrays(nodes, distances);
            candidateCount = HnswInternalUtil.filterCandidates(
                    deleted, directory.internalIds(), nodes, distances, candidateCount, source);
            HnswInternalUtil.sortByDistance(nodes, distances, candidateCount);
            int maxDegree = level == 0 ? config.maxM0 : config.M;
            int[] selected = scratch.tmpNodesSecondary(Math.max(candidateCount, maxDegree));
            int selectedCount = HnswInternalUtil.selectNeighbors(
                    space, config.neighborHeuristic, config.diversificationAlpha,
                    source, level, nodes, distances, candidateCount, maxDegree, selected);
            NeighborList list = graph.ensureNeighborList(level, source);
            list.replaceWith(selected, selectedCount);
            for (int i = 0; i < selectedCount; i++) {
                connector.connectMutual(level, source, selected[i], scratch);
            }
        }
    }

    protected int executeSearchOnLevel(QueryContext query, int level, int ef, int excludeNode, DoubleIntMinHeap candidates, BoundedMaxHeap results, SearchScratch scratch) {
        while (!candidates.isEmpty()) {
            int current = candidates.peekNode();
            double distance = candidates.peekDistance();
            candidates.popNode();
            if (distance > results.worstDistance() && results.size() >= ef) {
                break;
            }
            NeighborList list = graph.neighborList(level, current);
            if (list == null) {
                continue;
            }
            int[] neighbors = list.elements();
            for (int i = 0; i < list.size(); i++) {
                int neighbor = neighbors[i];
                if (neighbor == excludeNode || deleted.get(neighbor)) {
                    continue;
                }
                if (!scratch.tryVisit(neighbor)) {
                    continue;
                }
                double dist = space.distance(query, neighbor);
                if (!Double.isFinite(dist)) {
                    continue;
                }
                if (results.size() < ef || dist < results.worstDistance()) {
                    candidates.push(neighbor, dist);
                    results.insert(neighbor, dist);
                }
            }
        }
        return results.size();
    }

    private sealed interface IndexPersistence permits SerializerV4 {
        void save(DefaultHnswIndex index, Path path) throws IOException;
    }

    private static final class SerializerV4 implements IndexPersistence {
        @Override
        public void save(DefaultHnswIndex index, Path path) throws IOException {
            try (OutputStream fileOut = Files.newOutputStream(path);
                 BufferedOutputStream bufferedOut = new BufferedOutputStream(fileOut);
                 CheckedOutputStream checkedOut = new CheckedOutputStream(bufferedOut, new CRC32());
                 DataOutputStream out = new DataOutputStream(checkedOut)) {
                out.writeLong(MAGIC);
                out.writeInt(SERIAL_VERSION);
                writeConfig(out, index.config);
                out.writeInt(index.directory.nodeCount());
                out.writeInt(index.directory.size());
                out.writeInt(index.graph.entryPoint());
                out.writeInt(index.graph.maxLevel());
                for (int i = 0; i < index.directory.nodeCount(); i++) {
                    out.writeLong(index.directory.internalIds()[i]);
                }
                byte[] deletedBytes = index.deleted.toByteArray();
                out.writeInt(deletedBytes.length);
                out.write(deletedBytes);
                for (int i = 0; i < index.directory.nodeCount(); i++) {
                    out.writeInt(index.graph.levelOfNode(i));
                }
                double[] raw = index.vectorStorage.raw();
                for (int i = 0; i < index.directory.nodeCount(); i++) {
                    int offset = index.vectorStorage.offset(i);
                    for (int d = 0; d < index.config.dimension; d++) {
                        out.writeDouble(raw[offset + d]);
                    }
                }
                for (int i = 0; i < index.directory.nodeCount(); i++) {
                    out.writeDouble(index.auxStorage.norm(i));
                    out.writeDouble(index.auxStorage.mean(i));
                    out.writeDouble(index.auxStorage.centeredNorm(i));
                }
                int levelCount = index.graph.levelCount();
                out.writeInt(levelCount);
                NeighborList[] levelZero = index.graph.level0();
                for (int node = 0; node < index.directory.nodeCount(); node++) {
                    NeighborList list = node < levelZero.length ? levelZero[node] : null;
                    int count = list != null ? list.size() : 0;
                    out.writeInt(count);
                    if (count > 0) {
                        int[] elements = list.elements();
                        for (int i = 0; i < count; i++) {
                            out.writeInt(elements[i]);
                        }
                    }
                }
                for (int level = 1; level < levelCount; level++) {
                    HnswGraph.SparseLayer layer = index.graph.upperLayers().get(level - 1);
                    out.writeInt(layer.map().size());
                    for (Int2ObjectMap.Entry<NeighborList> entry : layer.map().int2ObjectEntrySet()) {
                        int nodeId = entry.getIntKey();
                        NeighborList list = entry.getValue();
                        int count = list != null ? list.size() : 0;
                        out.writeInt(nodeId);
                        out.writeInt(count);
                        if (count > 0) {
                            int[] elements = list.elements();
                            for (int i = 0; i < count; i++) {
                                out.writeInt(elements[i]);
                            }
                        }
                    }
                }
                out.flush();
                long checksum = checkedOut.getChecksum().getValue();
                checkedOut.getChecksum().reset();
                out.writeLong(checksum);
            }
        }

        HnswIndex load(Path path) throws IOException {
            try (InputStream fileIn = Files.newInputStream(path);
                 BufferedInputStream bufferedIn = new BufferedInputStream(fileIn);
                 CheckedInputStream checkedIn = new CheckedInputStream(bufferedIn, new CRC32());
                 DataInputStream in = new DataInputStream(checkedIn)) {
                long magic = in.readLong();
                if (magic != MAGIC) {
                    throw new IOException("Invalid HNSW index file");
                }
                int version = in.readInt();
                if (version != SERIAL_VERSION) {
                    throw new IOException("Unsupported HNSW index version " + version);
                }
                HnswConfig config = readConfig(in, version);
                config.validate();
                DefaultHnswIndex index = new DefaultHnswIndex(config, true);
                int nodeCount = in.readInt();
                int size = in.readInt();
                int entryPoint = in.readInt();
                int maxLevel = in.readInt();
                index.directory.ensureCapacity(nodeCount);
                index.directory.bulkSetCounts(nodeCount, size);
                index.graph.setEntryPoint(entryPoint);
                index.graph.setMaxLevel(maxLevel);
                for (int i = 0; i < nodeCount; i++) {
                    index.directory.internalIds()[i] = in.readLong();
                }
                int deletedLength = in.readInt();
                byte[] deletedBytes = in.readNBytes(deletedLength);
                BitSet deleted = BitSet.valueOf(deletedBytes);
                index.deleted.clear();
                for (int bit = deleted.nextSetBit(0); bit >= 0; bit = deleted.nextSetBit(bit + 1)) {
                    index.deleted.set(bit);
                }
                for (int i = 0; i < nodeCount; i++) {
                    int level = in.readInt();
                    index.graph.setLevelOfNode(i, level);
                }
                double[] raw = index.vectorStorage.raw();
                for (int i = 0; i < nodeCount; i++) {
                    int offset = index.vectorStorage.offset(i);
                    for (int d = 0; d < config.dimension; d++) {
                        raw[offset + d] = in.readDouble();
                    }
                }
                for (int i = 0; i < nodeCount; i++) {
                    double norm = in.readDouble();
                    double mean = in.readDouble();
                    double centeredNorm = in.readDouble();
                    if (norm != 0.0) {
                        index.auxStorage.setNorm(i, norm);
                    }
                    if (mean != 0.0) {
                        index.auxStorage.setMean(i, mean);
                    }
                    if (centeredNorm != 0.0) {
                        index.auxStorage.setCenteredNorm(i, centeredNorm);
                    }
                }
                int levelCount = in.readInt();
                for (int node = 0; node < nodeCount; node++) {
                    int neighborCount = in.readInt();
                    if (neighborCount <= 0) {
                        continue;
                    }
                    NeighborList list = index.graph.ensureNeighborList(0, node);
                    int[] buffer = new int[neighborCount];
                    for (int i = 0; i < neighborCount; i++) {
                        buffer[i] = in.readInt();
                    }
                    list.replaceWith(buffer, neighborCount);
                }
                for (int level = 1; level < levelCount; level++) {
                    int mapSize = in.readInt();
                    for (int i = 0; i < mapSize; i++) {
                        int nodeId = in.readInt();
                        int neighborCount = in.readInt();
                        NeighborList list = index.graph.ensureNeighborList(level, nodeId);
                        int[] buffer = new int[neighborCount];
                        for (int j = 0; j < neighborCount; j++) {
                            buffer[j] = in.readInt();
                        }
                        list.replaceWith(buffer, neighborCount);
                    }
                }
                long computedChecksum = checkedIn.getChecksum().getValue();
                checkedIn.getChecksum().reset();
                long storedChecksum = in.readLong();
                if (computedChecksum != storedChecksum) {
                    throw new IOException("Checksum mismatch while loading index");
                }
                index.directory.rebuildAfterLoad(index.deleted, index.graph);
                return index;
            }
        }
    }

    private static void writeConfig(DataOutputStream out, HnswConfig config) throws IOException {
        out.writeInt(config.dimension);
        out.writeInt(config.M);
        out.writeInt(config.maxM0);
        out.writeInt(config.efConstruction);
        out.writeInt(config.defaultEfSearch);
        out.writeDouble(config.levelLambda);
        out.writeLong(config.randomSeed);
        out.writeInt(config.duplicatePolicy.ordinal());
        out.writeInt(config.deletionPolicy.ordinal());
        out.writeInt(config.neighborHeuristic.ordinal());
        out.writeInt(config.initialCapacity);
        out.writeInt(config.efRepair);
        out.writeDouble(config.diversificationAlpha);
        out.writeBoolean(config.exactSearch);
        out.writeUTF(config.spaceFactory.typeId());
        config.spaceFactory.write(out);
    }

    private static HnswConfig readConfig(DataInputStream in, int version) throws IOException {
        HnswConfig config = new HnswConfig();
        config.dimension = in.readInt();
        config.M = in.readInt();
        config.maxM0 = in.readInt();
        config.efConstruction = in.readInt();
        config.defaultEfSearch = in.readInt();
        config.levelLambda = in.readDouble();
        config.randomSeed = in.readLong();
        config.duplicatePolicy = DuplicatePolicy.values()[in.readInt()];
        config.deletionPolicy = DeletionPolicy.values()[in.readInt()];
        config.neighborHeuristic = NeighborSelectHeuristic.values()[in.readInt()];
        config.initialCapacity = in.readInt();
        config.efRepair = in.readInt();
        config.diversificationAlpha = in.readDouble();
        config.exactSearch = in.readBoolean();
        String spaceId = in.readUTF();
        config.spaceFactory = Spaces.fromId(spaceId, in);
        return config;
    }
}
