package one.chartsy.hnsw.internal;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

import one.chartsy.hnsw.HnswBulkBuilder;
import one.chartsy.hnsw.HnswConfig;
import one.chartsy.hnsw.HnswIndex;
import one.chartsy.hnsw.graph.HnswGraph;
import one.chartsy.hnsw.graph.NeighborList;
import one.chartsy.hnsw.space.QueryContext;
import one.chartsy.hnsw.space.Space;

public final class BulkBuildEngine {
    private final HnswConfig config;
    private final HnswBulkBuilder.Options options;
    private final List<long[]> idBatches = new ArrayList<>();
    private final List<double[][]> vectorBatches = new ArrayList<>();
    private final Long2IntOpenHashMap seenIds = new Long2IntOpenHashMap();
    private boolean built;

    public BulkBuildEngine(HnswConfig config, HnswBulkBuilder.Options options) {
        this.config = new HnswConfig(Objects.requireNonNull(config));
        this.config.validate();
        this.options = options;
        this.seenIds.defaultReturnValue(-1);
    }

    public synchronized void addBatch(long[] ids, double[][] vectors) {
        ensureNotBuilt();
        validateBatch(ids, vectors);
        idBatches.add(ids);
        vectorBatches.add(vectors);
        registerIds(ids);
    }

    public synchronized HnswIndex build() {
        ensureNotBuilt();
        HnswIndex index = buildFromBatches(idBatches, vectorBatches);
        built = true;
        idBatches.clear();
        vectorBatches.clear();
        seenIds.clear();
        return index;
    }

    public synchronized HnswIndex build(long[] ids, double[][] vectors) {
        ensureNotBuilt();
        validateBatch(ids, vectors);
        List<long[]> singleIds = new ArrayList<>(1);
        singleIds.add(ids);
        List<double[][]> singleVectors = new ArrayList<>(1);
        singleVectors.add(vectors);
        HnswIndex index = buildFromBatches(singleIds, singleVectors);
        built = true;
        seenIds.clear();
        return index;
    }

    private void ensureNotBuilt() {
        if (built) {
            throw new IllegalStateException("Builder already used");
        }
    }

    private HnswIndex buildFromBatches(List<long[]> idsList, List<double[][]> vectorsList) {
        int total = totalCount(idsList);
        DefaultHnswIndex index = DefaultHnswIndex.newForBulk(config, options.useFloatStorage);
        if (total == 0) {
            index.bulkSetCounts(0, 0);
            index.bulkPublishIdMap(new Long2IntOpenHashMap(), new long[0]);
            return index;
        }
        index.ensureCapacity(total);
        HnswGraph graph = index.graph();
        graph.ensureNodeCapacity(total);
        Space space = index.space();
        index.space().preallocate(index.auxStorage(), total);

        FlattenedInput input = flatten(idsList, vectorsList, total);
        ParallelExecutor executor = new ParallelExecutor(Math.max(1, options.concurrency));
        try {
            ingest(space, input.vectors, executor);
            Arrays.fill(input.vectors, null);
            LevelAssignment assignment = assignLevels(graph, input.internalToId, executor);
            BulkBuilder builder = new BulkBuilder(index, config, options, input, assignment, executor);
            builder.build();
        } finally {
            executor.shutdown();
        }
        index.bulkPublishIdMap(input.idToInternal, input.internalToId);
        index.bulkSetCounts(total, total);
        return index;
    }

    private FlattenedInput flatten(List<long[]> idsList, List<double[][]> vectorsList, int total) {
        long[] internalToId = new long[total];
        double[][] vectors = new double[total][];
        Long2IntOpenHashMap idToInternal = new Long2IntOpenHashMap(total * 2);
        idToInternal.defaultReturnValue(-1);
        int cursor = 0;
        for (int batch = 0; batch < idsList.size(); batch++) {
            long[] ids = idsList.get(batch);
            double[][] vs = vectorsList.get(batch);
            for (int i = 0; i < ids.length; i++) {
                internalToId[cursor] = ids[i];
                vectors[cursor] = vs[i];
                idToInternal.put(ids[i], cursor);
                cursor++;
            }
        }
        return new FlattenedInput(internalToId, vectors, idToInternal);
    }

    private void ingest(Space space, double[][] vectors, ParallelExecutor executor) {
        int total = vectors.length;
        executor.parallelFor(0, total, index -> space.onInsert(index, vectors[index]));
    }

    private LevelAssignment assignLevels(HnswGraph graph, long[] internalToId, ParallelExecutor executor) {
        int total = internalToId.length;
        int[] levels = new int[total];
        AtomicInteger maxLevel = new AtomicInteger(-1);
        executor.parallelFor(0, total, node -> {
            int level = sampleLevel(internalToId[node]);
            levels[node] = level;
            graph.setLevelOfNode(node, level);
            maxLevel.accumulateAndGet(level, Math::max);
        });
        return new LevelAssignment(levels, Math.max(0, maxLevel.get()));
    }

    private int sampleLevel(long nodeId) {
        long seed = config.randomSeed ^ nodeId;
        SplittableRandom random = new SplittableRandom(seed);
        double u = Math.max(1e-12, 1.0 - random.nextDouble());
        double value = -Math.log(u) * config.levelLambda;
        return Math.max(0, (int) value);
    }

    private void validateBatch(long[] ids, double[][] vectors) {
        Objects.requireNonNull(ids, "ids");
        Objects.requireNonNull(vectors, "vectors");
        if (ids.length != vectors.length) {
            throw new IllegalArgumentException("ids and vectors must have the same length");
        }
        for (int i = 0; i < ids.length; i++) {
            if (vectors[i] == null) {
                throw new NullPointerException("Vector at index " + i + " is null");
            }
            if (vectors[i].length != config.dimension) {
                throw new IllegalArgumentException(
                        "Vector at index " + i + " has dimension " + vectors[i].length + " expected " + config.dimension);
            }
        }
        ensureNoDuplicates(ids);
    }

    private void ensureNoDuplicates(long[] ids) {
        Long2IntOpenHashMap local = new Long2IntOpenHashMap();
        local.defaultReturnValue(-1);
        for (long id : ids) {
            if (seenIds.get(id) >= 0 || local.get(id) >= 0) {
                throw new IllegalArgumentException("Duplicate id " + id + " in bulk build input");
            }
            local.put(id, 1);
        }
    }

    private void registerIds(long[] ids) {
        for (long id : ids) {
            seenIds.put(id, 1);
        }
    }

    private int totalCount(List<long[]> idsList) {
        int total = 0;
        for (long[] ids : idsList) {
            total += ids.length;
        }
        return total;
    }

    private record FlattenedInput(long[] internalToId, double[][] vectors, Long2IntOpenHashMap idToInternal) {
    }

    private record LevelAssignment(int[] levels, int maxLevel) {
    }

    private static final class ParallelExecutor {
        private final ExecutorService executor;
        private final int threads;

        ParallelExecutor(int threads) {
            this.threads = Math.max(1, threads);
            this.executor = this.threads > 1 ? Executors.newFixedThreadPool(this.threads) : null;
        }

        void parallelFor(int fromInclusive, int toExclusive, IntConsumer consumer) {
            int length = toExclusive - fromInclusive;
            if (length <= 0) {
                return;
            }
            if (executor == null || length <= 1) {
                for (int i = fromInclusive; i < toExclusive; i++) {
                    consumer.accept(i);
                }
                return;
            }
            int chunk = Math.max(1, (int) Math.ceil(length / (double) threads));
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                int start = fromInclusive + t * chunk;
                if (start >= toExclusive) {
                    break;
                }
                int end = Math.min(toExclusive, start + chunk);
                futures.add(executor.submit((Callable<Void>) () -> {
                    for (int i = start; i < end; i++) {
                        consumer.accept(i);
                    }
                    return null;
                }));
            }
            waitAll(futures);
        }

        private void waitAll(List<Future<?>> futures) {
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(e.getCause());
                }
            }
        }

        void shutdown() {
            if (executor != null) {
                executor.shutdown();
            }
        }
    }

    private static final class BulkBuilder {
        private final DefaultHnswIndex index;
        private final HnswConfig config;
        private final HnswBulkBuilder.Options options;
        private final FlattenedInput input;
        private final LevelAssignment assignment;
        private final ParallelExecutor executor;
        private final Space space;
        private final HnswGraph graph;
        private final BitSet deleted;
        private final int nodeCount;
        private final int efConstruction;
        private final ThreadLocal<SearchScratch> scratch;
        private final int[] levelEntryPoints;
        private final int[][] levelSeedBlocks;

        BulkBuilder(DefaultHnswIndex index, HnswConfig config, HnswBulkBuilder.Options options,
                FlattenedInput input, LevelAssignment assignment, ParallelExecutor executor) {
            this.index = index;
            this.config = config;
            this.options = options;
            this.input = input;
            this.assignment = assignment;
            this.executor = executor;
            this.space = index.space();
            this.graph = index.graph();
            this.deleted = index.deleted();
            this.nodeCount = input.internalToId().length;
            this.efConstruction = options.efConstruction > 0 ? options.efConstruction : config.efConstruction;
            this.scratch = ThreadLocal.withInitial(() -> new SearchScratch(nodeCount, efConstruction));
            int levels = assignment.maxLevel() + 1;
            this.levelEntryPoints = new int[levels];
            Arrays.fill(this.levelEntryPoints, -1);
            this.levelSeedBlocks = new int[levels][];
        }

        void build() {
            if (nodeCount == 0) {
                return;
            }
            int maxLevel = assignment.maxLevel();
            int entryPoint = selectEntryPoint();
            graph.setEntryPoint(entryPoint);
            graph.setMaxLevel(maxLevel);
            int[][] nodesByLevel = nodesAtLeastPerLevel();
            for (int level = maxLevel; level >= 0; level--) {
                int[] nodes = nodesByLevel[level];
                if (nodes.length == 0) {
                    continue;
                }
                buildLevel(level, nodes);
            }
        }

        private int selectEntryPoint() {
            int bestNode = 0;
            int bestLevel = -1;
            int[] levels = assignment.levels();
            for (int i = 0; i < levels.length; i++) {
                int level = levels[i];
                if (level > bestLevel) {
                    bestLevel = level;
                    bestNode = i;
                }
            }
            return bestNode;
        }

        private int[][] nodesAtLeastPerLevel() {
            int maxLevel = assignment.maxLevel();
            IntArrayList[] perLevel = new IntArrayList[maxLevel + 1];
            for (int level = 0; level <= maxLevel; level++) {
                perLevel[level] = new IntArrayList();
            }
            int[] levels = assignment.levels();
            for (int node = 0; node < levels.length; node++) {
                int level = levels[node];
                for (int l = 0; l <= level; l++) {
                    perLevel[l].add(node);
                }
            }
            int[][] result = new int[maxLevel + 1][];
            for (int level = 0; level <= maxLevel; level++) {
                int[] nodes = perLevel[level].toIntArray();
                shuffle(nodes, config.randomSeed + level * 31L + 17);;;
                result[level] = nodes;
            }
            return result;
        }

        private void shuffle(int[] array, long seed) {
            SplittableRandom random = new SplittableRandom(seed);
            for (int i = array.length - 1; i > 0; i--) {
                int j = random.nextInt(i + 1);
                int tmp = array[i];
                array[i] = array[j];
                array[j] = tmp;
            }
        }

        private void buildLevel(int level, int[] nodes) {
            graph.ensureLevel(level, nodeCount);
            int seedSize = levelSeedSize(level, nodes.length);
            int[] seedBlock = null;
            if (seedSize > 0) {
                ensureEntryPointSeeded(nodes, seedSize);
                seedBlock = Arrays.copyOfRange(nodes, 0, seedSize);
                int entry = graph.entryPoint();
                levelEntryPoints[level] = entry >= 0 ? entry : seedBlock[0];
                levelSeedBlocks[level] = seedBlock;
                processBlock(level, seedBlock, true);
            } else {
                levelEntryPoints[level] = graph.entryPoint();
                levelSeedBlocks[level] = new int[0];
            }
            int blockSize = levelBlockSize(level, nodes.length - seedSize);
            if (blockSize <= 0) {
                blockSize = nodes.length;
            }
            for (int start = seedSize; start < nodes.length; start += blockSize) {
                int end = Math.min(nodes.length, start + blockSize);
                processBlock(level, Arrays.copyOfRange(nodes, start, end), false);
            }
        }

        private void processBlock(int level, int[] blockNodes, boolean seed) {
            if (blockNodes.length == 0) {
                return;
            }
            BlockResult result = new BlockResult(level, blockNodes, level == 0 ? config.maxM0 : config.M);
            if (seed || graph.entryPoint() < 0) {
                bruteForceBlock(result);
            } else {
                phaseA(result);
            }
            phaseB(result);
        }

        private void bruteForceBlock(BlockResult result) {
            int degree = result.maxDegree;
            executor.parallelFor(0, result.nodes.length, idx -> {
                int nodeId = result.nodes[idx];
                SearchScratch scratch = this.scratch.get();
                int candidateCount = result.nodes.length - 1;
                int[] candidates = scratch.tmpNodes(candidateCount);
                double[] distances = scratch.tmpDistances(candidateCount);
                int cursor = 0;
                for (int other : result.nodes) {
                    if (other == nodeId) {
                        continue;
                    }
                    candidates[cursor] = other;
                    distances[cursor] = space.distanceBetweenNodes(nodeId, other);
                    cursor++;
                }
                int filtered = HnswInternalUtil.filterCandidates(deleted, input.internalToId(), candidates, distances, cursor,
                        nodeId);
                HnswInternalUtil.sortByDistance(candidates, distances, filtered);
                int[] selected = scratch.tmpNodesSecondary(degree);
                int selectedCount = HnswInternalUtil.selectNeighbors(space, config.neighborHeuristic,
                        config.diversificationAlpha, nodeId, result.level, candidates, distances, filtered, degree,
                        selected);
                result.store(idx, selected, selectedCount);
            });
        }

        private void phaseA(BlockResult result) {
            executor.parallelFor(0, result.nodes.length, idx -> {
                int nodeId = result.nodes[idx];
                QueryContext query = space.prepareQueryForNode(nodeId);
                SearchScratch scratch = this.scratch.get();
                scratch.reset(nodeCount, efConstruction);
                int entry = entryPointForLevel(query, result.level, nodeId);
                int candidateCount = searchOnLevel(result.level, query, entry, scratch);
                int seedAugment = seedAugmentCapacity(result.level, candidateCount);
                int localAugment = localConnectivitySample(result.level, result.nodes.length);
                int[] candidates = scratch.tmpNodes(candidateCount + seedAugment + localAugment);
                double[] distances = scratch.tmpDistances(candidateCount + seedAugment + localAugment);
                scratch.results().toArrays(candidates, distances);
                if (seedAugment > 0 && candidateCount < Math.min(4, result.maxDegree)) {
                    candidateCount += addSeedCandidates(result.level, query, candidates, distances, candidateCount,
                            seedAugment);
                }
                if (localAugment > 0) {
                    candidateCount += addLocalBlockCandidates(result, idx, candidates, distances, candidateCount,
                            localAugment);
                }
                int filtered = HnswInternalUtil.filterCandidates(deleted, input.internalToId(), candidates, distances,
                        candidateCount, nodeId);
                HnswInternalUtil.sortByDistance(candidates, distances, filtered);
                int[] selected = scratch.tmpNodesSecondary(result.maxDegree);
                int selectedCount = HnswInternalUtil.selectNeighbors(space, config.neighborHeuristic,
                        config.diversificationAlpha, nodeId, result.level, candidates, distances, filtered,
                        result.maxDegree, selected);
                result.store(idx, selected, selectedCount);
            });
        }

        private int entryPointForLevel(QueryContext query, int level, int fallback) {
            int entry = graph.entryPoint();
            if (entry >= 0) {
                for (int current = graph.maxLevel(); current > level; current--) {
                    entry = HnswInternalUtil.greedySearchOnLevel(graph, space, deleted, query, entry, current);
                }
                if (hasAdjacency(level, entry)) {
                    return entry;
                }
            }
            int perLevelEntry = levelEntryPoint(level);
            if (hasAdjacency(level, perLevelEntry)) {
                return perLevelEntry;
            }
            int nearSeed = pickBuiltEntryNear(query, level);
            if (nearSeed >= 0) {
                return nearSeed;
            }
            return fallback;
        }

        private int levelEntryPoint(int level) {
            if (level < 0 || level >= levelEntryPoints.length) {
                return -1;
            }
            return levelEntryPoints[level];
        }

        private int searchOnLevel(int level, QueryContext query, int entryPoint, SearchScratch scratch) {
            DoubleIntMinHeap candidates = scratch.candidates();
            BoundedMaxHeap results = scratch.results();
            candidates.clear();
            results.reset(efConstruction);
            double entryDistance = space.distance(query, entryPoint);
            candidates.push(entryPoint, entryDistance);
            results.insert(entryPoint, entryDistance);
            scratch.tryVisit(entryPoint);
            while (!candidates.isEmpty()) {
                int current = candidates.peekNode();
                double currentDistance = candidates.peekDistance();
                candidates.popNode();
                if (currentDistance > results.worstDistance() && results.size() >= efConstruction) {
                    break;
                }
                NeighborList list = graph.neighborList(level, current);
                if (list == null) {
                    continue;
                }
                int[] neighbors = list.elements();
                for (int i = 0; i < list.size(); i++) {
                    int neighbor = neighbors[i];
                    if (deleted.get(neighbor) || !scratch.tryVisit(neighbor))
                        continue;

                    double distance = space.distance(query, neighbor);
                    if (!Double.isFinite(distance))
                        continue;

                    if (results.size() < efConstruction || distance < results.worstDistance()) {
                        candidates.push(neighbor, distance);
                        results.insert(neighbor, distance);
                    }
                }
            }
            return results.size();
        }

        private void phaseB(BlockResult result) {
            Int2ObjectOpenHashMap<IntArrayList> incoming = new Int2ObjectOpenHashMap<>();
            BitSet touched = new BitSet(nodeCount);
            IntArrayList touchedList = new IntArrayList();
            for (int i = 0; i < result.nodes.length; i++) {
                int nodeId = result.nodes[i];
                markTouched(nodeId, touched, touchedList);
                int count = result.counts[i];
                int offset = i * result.maxDegree;
                for (int j = 0; j < count; j++) {
                    int neighbor = result.neighbors[offset + j];
                    if (neighbor < 0) {
                        continue;
                    }
                    markTouched(neighbor, touched, touchedList);
                    incoming.computeIfAbsent(neighbor, key -> new IntArrayList()).add(nodeId);
                }
            }
            int[] touchedNodes = touchedList.toIntArray();
            NeighborList[] neighborRefs = new NeighborList[touchedNodes.length];
            for (int i = 0; i < touchedNodes.length; i++) {
                neighborRefs[i] = graph.ensureNeighborList(result.level, touchedNodes[i]);
            }
            executor.parallelFor(0, touchedNodes.length, idx -> {
                int nodeId = touchedNodes[idx];
                SearchScratch scratch = this.scratch.get();
                NeighborList previous = graph.neighborList(result.level, nodeId);
                IntArrayList incomingSources = incoming.get(nodeId);
                int ownIndex = result.indexOf(nodeId);
                int ownCount = ownIndex >= 0 ? result.counts[ownIndex] : 0;
                int totalCandidates = (previous != null ? previous.size() : 0)
                        + (incomingSources != null ? incomingSources.size() : 0) + ownCount;
                if (totalCandidates == 0) {
                    return;
                }
                int[] candidates = scratch.tmpNodes(totalCandidates);
                double[] distances = scratch.tmpDistances(totalCandidates);
                int cursor = 0;
                if (previous != null) {
                    int[] prevNodes = previous.elements();
                    for (int i = 0; i < previous.size(); i++) {
                        candidates[cursor] = prevNodes[i];
                        distances[cursor] = space.distanceBetweenNodes(nodeId, prevNodes[i]);
                        cursor++;
                    }
                }
                if (ownIndex >= 0) {
                    int offset = ownIndex * result.maxDegree;
                    for (int i = 0; i < ownCount; i++) {
                        int neighbor = result.neighbors[offset + i];
                        candidates[cursor] = neighbor;
                        distances[cursor] = space.distanceBetweenNodes(nodeId, neighbor);
                        cursor++;
                    }
                }
                if (incomingSources != null) {
                    for (int i = 0; i < incomingSources.size(); i++) {
                        int source = incomingSources.getInt(i);
                        candidates[cursor] = source;
                        distances[cursor] = space.distanceBetweenNodes(nodeId, source);
                        cursor++;
                    }
                }
                int filtered = HnswInternalUtil.filterCandidates(deleted, input.internalToId(), candidates, distances,
                        cursor, nodeId);
                if (filtered == 0) {
                    return;
                }
                HnswInternalUtil.sortByDistance(candidates, distances, filtered);
                int[] selected = scratch.tmpNodesSecondary(result.maxDegree);
                int selectedCount = HnswInternalUtil.selectNeighbors(space, config.neighborHeuristic,
                        config.diversificationAlpha, nodeId, result.level, candidates, distances, filtered,
                        result.maxDegree, selected);
                neighborRefs[idx].replaceWith(selected, selectedCount);
            });
        }

        private void markTouched(int nodeId, BitSet touched, IntArrayList list) {
            if (!touched.get(nodeId)) {
                touched.set(nodeId);
                list.add(nodeId);
            }
        }

        private void ensureEntryPointSeeded(int[] nodes, int seedSize) {
            if (seedSize <= 0) {
                return;
            }
            int entryPoint = graph.entryPoint();
            if (entryPoint < 0) {
                return;
            }
            int idx = indexOf(nodes, entryPoint);
            if (idx >= seedSize && idx >= 0) {
                int swapPos = seedSize - 1;
                int tmp = nodes[swapPos];
                nodes[swapPos] = nodes[idx];
                nodes[idx] = tmp;
            }
        }

        private int indexOf(int[] nodes, int target) {
            for (int i = 0; i < nodes.length; i++) {
                if (nodes[i] == target) {
                    return i;
                }
            }
            return -1;
        }

        private int seedAugmentCapacity(int level, int currentCount) {
            int[] seeds = seedBlock(level);
            if (seeds == null || seeds.length == 0 || currentCount >= efConstruction) {
                return 0;
            }
            return Math.min(32, seeds.length);
        }

        private int localConnectivitySample(int level, int blockSize) {
            if (blockSize <= 1) {
                return 0;
            }
            if (level != 0) {
                return 0;
            }
            int configured = Math.max(0, options.localConnectivitySampleL0);
            return Math.min(configured, blockSize - 1);
        }

        private int addLocalBlockCandidates(BlockResult result, int blockIndex, int[] candidates, double[] distances,
                int offset, int capacity) {
            if (capacity <= 0) {
                return 0;
            }
            int nodeId = result.nodes[blockIndex];
            int added = 0;
            int[] block = result.nodes;
            int total = block.length;
            for (int step = 1; step < total && added < capacity; step++) {
                int candidate = block[(blockIndex + step) % total];
                if (candidate == nodeId) {
                    continue;
                }
                candidates[offset + added] = candidate;
                distances[offset + added] = space.distanceBetweenNodes(nodeId, candidate);
                added++;
            }
            return added;
        }

        private int addSeedCandidates(int level, QueryContext query, int[] candidates, double[] distances,
                int offset, int capacity) {
            int[] seeds = seedBlock(level);
            if (seeds == null || seeds.length == 0) {
                return 0;
            }
            int limit = Math.min(seeds.length, capacity);
            int added = 0;
            for (int i = 0; i < limit; i++) {
                int candidate = seeds[i];
                if (!hasAdjacency(level, candidate)) {
                    continue;
                }
                double distance = space.distance(query, candidate);
                candidates[offset + added] = candidate;
                distances[offset + added] = distance;
                added++;
            }
            return added;
        }

        private boolean hasAdjacency(int level, int nodeId) {
            if (nodeId < 0) {
                return false;
            }
            NeighborList list = graph.neighborList(level, nodeId);
            return list != null && list.size() > 0;
        }

        private int pickBuiltEntryNear(QueryContext query, int level) {
            int[] seeds = seedBlock(level);
            if (seeds == null || seeds.length == 0) {
                return -1;
            }
            int limit = Math.min(seeds.length, 128);
            double bestDistance = Double.POSITIVE_INFINITY;
            int bestNode = -1;
            for (int i = 0; i < limit; i++) {
                int candidate = seeds[i];
                if (!hasAdjacency(level, candidate)) {
                    continue;
                }
                double distance = space.distance(query, candidate);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestNode = candidate;
                }
            }
            return bestNode;
        }

        private int[] seedBlock(int level) {
            if (level < 0 || level >= levelSeedBlocks.length) {
                return null;
            }
            return levelSeedBlocks[level];
        }

        private int levelSeedSize(int level, int nodesAtLevel) {
            if (nodesAtLevel <= 0) {
                return 0;
            }
            if (level == 0) {
                int dynamic = Math.min(options.seedBlockL0, Math.min(nodesAtLevel, Math.max(256, nodeCount / 32)));
                return Math.max(1, dynamic);
            }
            return Math.min(nodesAtLevel, options.seedBlockUpper);
        }

        private int levelBlockSize(int level, int remainingNodes) {
            if (remainingNodes <= 0) {
                return 0;
            }
            if (level == 0) {
                int dynamic = Math.max(8_000, nodeCount / 6);
                return Math.min(options.blockSizeL0, Math.max(1, dynamic));
            }
            return options.blockSizeUpper;
        }
    }

    private static final class BlockResult {
        final int level;
        final int[] nodes;
        final int maxDegree;
        final int[] neighbors;
        final int[] counts;
        final Int2IntOpenHashMap nodeToIndex;

        BlockResult(int level, int[] nodes, int maxDegree) {
            this.level = level;
            this.nodes = nodes;
            this.maxDegree = maxDegree;
            this.neighbors = new int[nodes.length * maxDegree];
            this.counts = new int[nodes.length];
            this.nodeToIndex = new Int2IntOpenHashMap(nodes.length * 2);
            this.nodeToIndex.defaultReturnValue(-1);
            for (int i = 0; i < nodes.length; i++) {
                nodeToIndex.put(nodes[i], i);
            }
        }

        void store(int blockIndex, int[] selected, int selectedCount) {
            counts[blockIndex] = selectedCount;
            int offset = blockIndex * maxDegree;
            Arrays.fill(neighbors, offset, offset + maxDegree, -1);
            System.arraycopy(selected, 0, neighbors, offset, Math.min(selectedCount, maxDegree));
        }

        int indexOf(int nodeId) {
            return nodeToIndex.get(nodeId);
        }
    }
}
