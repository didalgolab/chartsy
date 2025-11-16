package one.chartsy.hnsw.internal;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.locks.ReentrantReadWriteLock;

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
import one.chartsy.hnsw.space.SpaceFactory;
import one.chartsy.hnsw.space.Spaces;
import one.chartsy.hnsw.store.AuxStorage;
import one.chartsy.hnsw.store.VectorStorage;

public final class DefaultHnswIndex implements HnswIndex {
    private static final long MAGIC = 0x484E535730303031L; // "HNSW0001"
    private static final int SERIAL_VERSION = 2;
    private static final int[] EMPTY_INT_ARRAY = new int[0];

    private final HnswConfig config;
    private final VectorStorage vectorStorage;
    private final AuxStorage auxStorage;
    private final Space space;
    private final HnswGraph graph;
    private final Long2IntOpenHashMap idToInternal;
    private long[] internalToId;
    private final BitSet deleted;
    private final IntArrayList freeList;
    private final SplittableRandom random;
    private final ThreadLocal<SearchScratch> searchScratch;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private int nodeCount;
    private int size;

    public DefaultHnswIndex(HnswConfig config) {
        this(config, false);
    }

    private DefaultHnswIndex(HnswConfig config, boolean skipValidation) {
        this.config = new HnswConfig(config);
        if (!skipValidation) {
            this.config.validate();
        }
        int capacity = this.config.initialCapacity;
        this.vectorStorage = new VectorStorage(this.config.dimension, capacity);
        this.auxStorage = new AuxStorage(capacity);
        this.graph = new HnswGraph(this.config.M, this.config.maxM0, capacity);
        this.idToInternal = new Long2IntOpenHashMap(capacity);
        this.idToInternal.defaultReturnValue(-1);
        this.internalToId = new long[Math.max(1, capacity)];
        Arrays.fill(this.internalToId, -1L);
        this.deleted = new BitSet(capacity);
        this.freeList = new IntArrayList();
        this.random = new SplittableRandom(this.config.randomSeed);
        this.space = this.config.spaceFactory.create(this.config, vectorStorage, auxStorage);
        this.searchScratch = ThreadLocal.withInitial(() -> new SearchScratch(capacity, this.config.defaultEfSearch));
        this.nodeCount = 0;
        this.size = 0;
    }

    private SearchScratch scratch() {
        return searchScratch.get();
    }

    @Override
    public void add(long id, double[] vector) {
        Objects.requireNonNull(vector, "vector");
        requireDimension(vector);
        lock.writeLock().lock();
        try {
            int existing = idToInternal.getOrDefault(id, -1);
            if (existing >= 0) {
                if (config.duplicatePolicy == DuplicatePolicy.REJECT) {
                    throw new IllegalArgumentException("Duplicate id " + id);
                }
                removeNode(existing, true);
            }

            int nodeId = allocateNodeId();
            ensureCapacity(nodeId + 1);
            internalToId[nodeId] = id;
            deleted.clear(nodeId);
            idToInternal.put(id, nodeId);
            size++;

            space.onInsert(nodeId, vector);

            int level = sampleLevel();
            graph.setLevelOfNode(nodeId, level);

            int currentMaxLevel = graph.maxLevel();
            if (currentMaxLevel < 0) {
                graph.setEntryPoint(nodeId);
                graph.setMaxLevel(level);
                return;
            }

            QueryContext query = space.prepareQueryForNode(nodeId);
            int entryPoint = graph.entryPoint();
            if (entryPoint == nodeId || entryPoint < 0 || entryPoint >= nodeCount
                    || internalToId[entryPoint] < 0 || deleted.get(entryPoint)) {
                int alternate = selectAlternateEntry(nodeId);
                if (alternate >= 0) {
                    entryPoint = alternate;
                }
            }

            boolean newTopLevel = level > currentMaxLevel;

            for (int levelCursor = currentMaxLevel; levelCursor > level; levelCursor--) {
                entryPoint = greedySearchOnLevel(entryPoint, query, levelCursor);
            }

            for (int levelCursor = Math.min(level, currentMaxLevel); levelCursor >= 0; levelCursor--) {
                entryPoint = connectOnLevel(nodeId, query, entryPoint, levelCursor);
            }

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
    public boolean remove(long id) {
        lock.writeLock().lock();
        try {
            int nodeId = idToInternal.getOrDefault(id, -1);
            if (nodeId < 0 || deleted.get(nodeId)) {
                return false;
            }
            return removeNode(nodeId, true);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean contains(long id) {
        lock.readLock().lock();
        try {
            int nodeId = idToInternal.getOrDefault(id, -1);
            return nodeId >= 0 && !deleted.get(nodeId);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<SearchResult> searchKnn(double[] query, int k) {
        return searchKnn(query, k, config.defaultEfSearch);
    }

    @Override
    public List<SearchResult> searchKnn(double[] query, int k, int efSearch) {
        Objects.requireNonNull(query, "query");
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive");
        }
        lock.readLock().lock();
        try {
            if (size == 0 || graph.entryPoint() < 0) {
                return List.of();
            }
            int effectiveEf = Math.max(efSearch, k);
            QueryContext queryContext = space.prepareQuery(query);
            if (config.exactSearch) {
                return exactSearch(queryContext, k);
            }
            SearchScratch scratch = scratch();
            scratch.reset(Math.max(nodeCount, 1), effectiveEf);

            int entryPoint = graph.entryPoint();
            entryPoint = greedySearchOnAllLevels(queryContext, entryPoint);

            executeBaseLayerSearch(queryContext, k, effectiveEf, entryPoint, scratch);

            return collectResults(k, scratch);
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<SearchResult> exactSearch(QueryContext query, int k) {
        BoundedMaxHeap heap = new BoundedMaxHeap(Math.max(k, 16));
        for (int node = 0; node < nodeCount; node++) {
            long id = internalToId[node];
            if (id < 0 || deleted.get(node)) {
                continue;
            }
            double distance = space.distance(query, node);
            if (!Double.isFinite(distance)) {
                continue;
            }
            heap.insert(node, distance);
        }
        int count = heap.size();
        int[] nodes = new int[count];
        double[] distances = new double[count];
        heap.toArrays(nodes, distances);
        sortByDistance(nodes, distances, count);
        List<SearchResult> results = new ArrayList<>(Math.min(k, count));
        for (int i = 0; i < count && results.size() < k; i++) {
            long id = internalToId[nodes[i]];
            if (id >= 0 && !deleted.get(nodes[i])) {
                results.add(new SearchResult(id, distances[i]));
            }
        }
        return results;
    }

    @Override
    public int size() {
        lock.readLock().lock();
        try {
            return size;
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
                    .size(size)
                    .totalNodes(nodeCount)
                    .maxLevel(graph.maxLevel())
                    .totalEdges(graph.totalEdges())
                    .memoryBytes(vectorStorage.memoryBytes() + auxStorage.memoryBytes());
            int active = 0;
            double levelSum = 0.0;
            double degreeSum = 0.0;
            double degreeLevel0Sum = 0.0;
            for (int node = 0; node < nodeCount; node++) {
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
            idToInternal.clear();
            Arrays.fill(internalToId, -1L);
            deleted.clear();
            freeList.clear();
            nodeCount = 0;
            size = 0;
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
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(path)))) {
            out.writeLong(MAGIC);
            out.writeInt(SERIAL_VERSION);
            writeConfig(out);
            out.writeInt(nodeCount);
            out.writeInt(size);
            out.writeInt(graph.entryPoint());
            out.writeInt(graph.maxLevel());
            for (int i = 0; i < nodeCount; i++) {
                out.writeLong(internalToId[i]);
            }
            byte[] deletedBytes = deleted.toByteArray();
            out.writeInt(deletedBytes.length);
            out.write(deletedBytes);
            for (int i = 0; i < nodeCount; i++) {
                out.writeInt(graph.levelOfNode(i));
            }
            double[] raw = vectorStorage.raw();
            for (int i = 0; i < nodeCount; i++) {
                int offset = vectorStorage.offset(i);
                for (int d = 0; d < config.dimension; d++) {
                    out.writeDouble(raw[offset + d]);
                }
            }
            for (int i = 0; i < nodeCount; i++) {
                out.writeDouble(auxStorage.norm(i));
                out.writeDouble(auxStorage.mean(i));
                out.writeDouble(auxStorage.centeredNorm(i));
            }
            int levelCount = graph.levelCount();
            out.writeInt(levelCount);
            for (int level = 0; level < levelCount; level++) {
                NeighborList[] layer = graph.layers().get(level);
                for (int node = 0; node < nodeCount; node++) {
                    NeighborList list = node < layer.length ? layer[node] : null;
                    int count = list != null ? list.size() : 0;
                    out.writeInt(count);
                    if (count > 0) {
                        int[] elements = list.elements();
                        for (int i = 0; i < count; i++) {
                            out.writeInt(elements[i]);
                        }
                    }
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public static HnswIndex load(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            long magic = in.readLong();
            if (magic != MAGIC) {
                throw new IOException("Invalid HNSW index file");
            }
            int version = in.readInt();
            if (version < 1 || version > SERIAL_VERSION) {
                throw new IOException("Unsupported HNSW index version " + version);
            }
            HnswConfig config = readConfig(in, version);
            config.validate();
            DefaultHnswIndex index = new DefaultHnswIndex(config, true);
            int nodeCount = in.readInt();
            int size = in.readInt();
            int entryPoint = in.readInt();
            int maxLevel = in.readInt();
            index.ensureCapacity(nodeCount);
            index.nodeCount = nodeCount;
            index.size = size;
            index.graph.setEntryPoint(entryPoint);
            index.graph.setMaxLevel(maxLevel);
            for (int i = 0; i < nodeCount; i++) {
                index.internalToId[i] = in.readLong();
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
            for (int level = 0; level < levelCount; level++) {
                for (int node = 0; node < nodeCount; node++) {
                    int neighborCount = in.readInt();
                    if (neighborCount <= 0) {
                        continue;
                    }
                    NeighborList list = index.graph.ensureNeighborList(level, node);
                    int[] buffer = new int[neighborCount];
                    for (int i = 0; i < neighborCount; i++) {
                        buffer[i] = in.readInt();
                    }
                    list.replaceWith(buffer, neighborCount);
                }
            }
            index.rebuildMapsAfterLoad();
            return index;
        }
    }

    private void rebuildMapsAfterLoad() {
        idToInternal.clear();
        freeList.clear();
        for (int node = 0; node < nodeCount; node++) {
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

    private void writeConfig(DataOutputStream out) throws IOException {
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
        if (version >= 2) {
            config.diversificationAlpha = in.readDouble();
            config.exactSearch = in.readBoolean();
        }
        String spaceId = in.readUTF();
        SpaceFactory factory = Spaces.fromId(spaceId, in);
        config.spaceFactory = factory;
        if (version == 1) {
            config.diversificationAlpha = 1.2;
            config.exactSearch = false;
        }
        return config;
    }

    private void ensureCapacity(int capacity) {
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

    private int allocateNodeId() {
        if (!freeList.isEmpty()) {
            return freeList.removeInt(freeList.size() - 1);
        }
        return nodeCount++;
    }

    private int selectAlternateEntry(int excludeNode) {
        int bestNode = -1;
        int bestLevel = -1;
        for (int i = 0; i < nodeCount; i++) {
            if (i == excludeNode) {
                continue;
            }
            if (internalToId[i] < 0 || deleted.get(i)) {
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

    private int sampleLevel() {
        double u = 1.0 - random.nextDouble();
        double value = -Math.log(u) * config.levelLambda;
        return Math.max(0, (int) value);
    }

    private int greedySearchOnAllLevels(QueryContext query, int entryPoint) {
        int current = entryPoint;
        double currentDistance = space.distance(query, current);
        for (int level = graph.maxLevel(); level > 0; level--) {
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
        }
        return current;
    }

    private int greedySearchOnLevel(int entryPoint, QueryContext query, int level) {
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

    private void executeBaseLayerSearch(QueryContext query, int k, int efSearch, int entryPoint, SearchScratch scratch) {
        DoubleIntMinHeap candidates = scratch.candidates();
        BoundedMaxHeap results = scratch.results();
        double entryDistance = space.distance(query, entryPoint);
        candidates.push(entryPoint, entryDistance);
        results.insert(entryPoint, entryDistance);
        scratch.tryVisit(entryPoint);

        while (!candidates.isEmpty()) {
            int current = candidates.peekNode();
            double currentDistance = candidates.peekDistance();
            candidates.popNode();
            if (currentDistance > results.worstDistance() && results.size() >= efSearch) {
                break;
            }
            NeighborList list = graph.neighborList(0, current);
            if (list == null) {
                continue;
            }
            int[] neighbors = list.elements();
            for (int i = 0; i < list.size(); i++) {
                int neighbor = neighbors[i];
                if (deleted.get(neighbor)) {
                    continue;
                }
                if (!scratch.tryVisit(neighbor)) {
                    continue;
                }
                double distance = space.distance(query, neighbor);
                if (!Double.isFinite(distance)) {
                    continue;
                }
                if (results.size() < efSearch || distance < results.worstDistance()) {
                    candidates.push(neighbor, distance);
                    results.insert(neighbor, distance);
                }
            }
        }
    }

    private List<SearchResult> collectResults(int k, SearchScratch scratch) {
        BoundedMaxHeap results = scratch.results();
        int count = results.size();
        if (count == 0) {
            return List.of();
        }
        int[] nodes = scratch.tmpNodes(count);
        double[] distances = scratch.tmpDistances(count);
        results.toArrays(nodes, distances);
        int valid = filterCandidates(nodes, distances, count, -1);
        sortByDistance(nodes, distances, valid);
        List<SearchResult> out = new ArrayList<>(Math.min(k, valid));
        for (int i = 0; i < valid && out.size() < k; i++) {
            int nodeId = nodes[i];
            if (nodeId < 0 || nodeId >= internalToId.length) {
                continue;
            }
            if (deleted.get(nodeId)) {
                continue;
            }
            long id = internalToId[nodeId];
            if (id < 0) {
                continue;
            }
            out.add(new SearchResult(id, distances[i]));
        }
        return out;
    }

    private int connectOnLevel(int nodeId, QueryContext query, int entryPoint, int level) {
        int ef = Math.max(config.efConstruction, config.M);
        SearchScratch scratch = scratch();
        scratch.reset(Math.max(nodeCount, 1), ef);
        DoubleIntMinHeap candidates = scratch.candidates();
        BoundedMaxHeap results = scratch.results();

        double entryDistance = space.distance(query, entryPoint);
        candidates.push(entryPoint, entryDistance);
        results.insert(entryPoint, entryDistance);
        scratch.tryVisit(entryPoint);

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
                if (neighbor == nodeId || deleted.get(neighbor)) {
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

        int candidateCount = results.size();
        int[] candidateNodes = scratch.tmpNodes(candidateCount);
        double[] candidateDistances = scratch.tmpDistances(candidateCount);
        results.toArrays(candidateNodes, candidateDistances);
        candidateCount = filterCandidates(candidateNodes, candidateDistances, candidateCount, nodeId);
        sortByDistance(candidateNodes, candidateDistances, candidateCount);

        int maxDegree = level == 0 ? config.maxM0 : config.M;
        int[] selected = scratch.tmpNodesSecondary(Math.max(candidateCount, maxDegree));
        int selectedCount = selectNeighbors(nodeId, level, candidateNodes, candidateDistances, candidateCount, maxDegree, selected);

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

    private void connectMutual(int level, int source, int target, SearchScratch scratch) {
        NeighborList list = graph.ensureNeighborList(level, target);
        int size = list.size();
        int maxDegree = level == 0 ? config.maxM0 : config.M;
        int[] candidates = scratch.tmpNodes(size + 1);
        double[] distances = scratch.tmpDistances(size + 1);
        int count = 0;
        boolean contains = false;
        int[] elements = list.elements();
        int[] previousNeighbors = Arrays.copyOf(elements, size);
        for (int i = 0; i < size; i++) {
            int neighbor = elements[i];
            if (neighbor == source) {
                contains = true;
            }
            candidates[count] = neighbor;
            distances[count] = space.distanceBetweenNodes(target, neighbor);
            count++;
        }
        if (!contains) {
            candidates[count] = source;
            distances[count] = space.distanceBetweenNodes(target, source);
            count++;
        }
        sortByDistance(candidates, distances, count);
        int[] selected = scratch.tmpNodesSecondary(Math.max(count, maxDegree));
        int selectedCount = selectNeighbors(target, level, candidates, distances, count, maxDegree, selected);
        if (selectedCount > 0 && previousNeighbors.length > 0) {
            boolean retainsPrevious = false;
            for (int prev : previousNeighbors) {
                for (int i = 0; i < selectedCount; i++) {
                    if (selected[i] == prev) {
                        retainsPrevious = true;
                        break;
                    }
                }
                if (retainsPrevious) {
                    break;
                }
            }
            if (!retainsPrevious) {
                int bestPrev = -1;
                double bestPrevDist = Double.POSITIVE_INFINITY;
                for (int prev : previousNeighbors) {
                    if (prev == source) {
                        continue;
                    }
                    double dist = space.distanceBetweenNodes(target, prev);
                    if (dist < bestPrevDist) {
                        bestPrevDist = dist;
                        bestPrev = prev;
                    }
                }
                if (bestPrev >= 0) {
                    if (selectedCount < maxDegree) {
                        selected[selectedCount++] = bestPrev;
                    } else {
                        int farthestIdx = -1;
                        double farthestDist = Double.NEGATIVE_INFINITY;
                        for (int i = 0; i < selectedCount; i++) {
                            double dist = space.distanceBetweenNodes(target, selected[i]);
                            if (dist > farthestDist) {
                                farthestDist = dist;
                                farthestIdx = i;
                            }
                        }
                        if (farthestIdx >= 0 && bestPrevDist < farthestDist) {
                            selected[farthestIdx] = bestPrev;
                        }
                    }
                }
            }
        }
        boolean containsSourceNeighbor = false;
        for (int i = 0; i < selectedCount; i++) {
            if (selected[i] == source) {
                containsSourceNeighbor = true;
                break;
            }
        }
        if (!containsSourceNeighbor) {
            double sourceDistance = space.distanceBetweenNodes(target, source);
            if (selectedCount < maxDegree) {
                selected[selectedCount++] = source;
            } else {
                int farthestIdx = -1;
                double farthestDist = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < selectedCount; i++) {
                    double dist = space.distanceBetweenNodes(target, selected[i]);
                    if (dist > farthestDist) {
                        farthestDist = dist;
                        farthestIdx = i;
                    }
                }
                if (farthestIdx >= 0 && sourceDistance < farthestDist) {
                    selected[farthestIdx] = source;
                }
            }
        }
        list.replaceWith(selected, selectedCount);
    }

    private int selectNeighbors(int nodeId, int level, int[] candidates, double[] distances, int count, int maxDegree, int[] selectedOut) {
        int selected = 0;
        if (config.neighborHeuristic == NeighborSelectHeuristic.SIMPLE) {
            for (int i = 0; i < count && selected < maxDegree; i++) {
                int candidate = candidates[i];
                if (candidate == nodeId) {
                    continue;
                }
                selectedOut[selected++] = candidate;
            }
            return selected;
        }
        double alpha = config.diversificationAlpha;
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
                if (dist <= candidateDistance * alpha) {
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

    private int filterCandidates(int[] nodes, double[] distances, int count, int disallowNode) {
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

    private boolean removeNode(int nodeId, boolean removeFromMap) {
        if (deleted.get(nodeId)) {
            return false;
        }
        deleted.set(nodeId);
        long id = internalToId[nodeId];
        if (removeFromMap && id >= 0) {
            idToInternal.remove(id);
        }
        size--;

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
        internalToId[nodeId] = -1L;
        freeList.add(nodeId);
        if (graph.entryPoint() == nodeId) {
            reselectEntryPoint();
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
                connectMutual(level, neighbor, other, scratch);
            }
            if (config.efRepair > 0) {
                runRepairSearch(level, neighbor, neighbors, scratch);
            }
        }
    }

    private void runRepairSearch(int level, int source, int[] seeds, SearchScratch scratch) {
        QueryContext query = space.prepareQueryForNode(source);
        int ef = Math.max(config.efRepair, config.M);
        scratch.reset(Math.max(nodeCount, 1), ef);
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
                if (neighbor == source || deleted.get(neighbor)) {
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

        int candidateCount = results.size();
        int[] nodes = scratch.tmpNodes(candidateCount);
        double[] distances = scratch.tmpDistances(candidateCount);
        results.toArrays(nodes, distances);
        candidateCount = filterCandidates(nodes, distances, candidateCount, source);
        sortByDistance(nodes, distances, candidateCount);
        int maxDegree = level == 0 ? config.maxM0 : config.M;
        int[] selected = scratch.tmpNodesSecondary(Math.max(candidateCount, maxDegree));
        int selectedCount = selectNeighbors(source, level, nodes, distances, candidateCount, maxDegree, selected);
        NeighborList list = graph.ensureNeighborList(level, source);
        list.replaceWith(selected, selectedCount);
        for (int i = 0; i < selectedCount; i++) {
            connectMutual(level, source, selected[i], scratch);
        }
    }

    private void reselectEntryPoint() {
        int bestNode = -1;
        int bestLevel = -1;
        for (int node = 0; node < nodeCount; node++) {
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

    private void sortByDistance(int[] nodes, double[] distances, int length) {
        if (length <= 1) {
            return;
        }
        quickSort(nodes, distances, 0, length - 1);
    }

    private void quickSort(int[] nodes, double[] distances, int left, int right) {
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
