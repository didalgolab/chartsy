package one.chartsy.hnsw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import one.chartsy.hnsw.graph.HnswGraph;
import one.chartsy.hnsw.graph.NeighborList;
import one.chartsy.hnsw.internal.DefaultHnswIndex;
import one.chartsy.hnsw.space.Spaces;
import one.chartsy.hnsw.store.VectorStorage;

class HnswIndexTest {

    @Test
    void shouldAddAndSearch() {
        HnswConfig config = new HnswConfig();
        config.dimension = 2;
        config.spaceFactory = Spaces.euclidean();
        config.initialCapacity = 8;
        config.M = 4;
        config.maxM0 = 6;
        config.levelLambda = 1.0;

        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{0.0, 0.0});
        index.add(2L, new double[]{1.0, 0.0});
        index.add(3L, new double[]{0.0, 1.0});

        List<SearchResult> results = index.searchKnn(new double[]{0.1, 0.1}, 2);
        assertThat(results).hasSize(2);
        assertThat(results.getFirst().id()).isEqualTo(1L);
    }

    @Test
    void addRejectsMismatchedDimensionsWithoutCorruptingState() {
        HnswConfig config = new HnswConfig();
        config.dimension = 3;
        config.spaceFactory = Spaces.euclidean();

        HnswIndex index = Hnsw.build(config);

        assertThatThrownBy(() -> index.add(7L, new double[]{1.0, 2.0}))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(index.size()).isZero();
        assertThat(index.contains(7L)).isFalse();
    }

    @Test
    void searchRecallMeetsTargetOnRandomData() {
        HnswConfig config = new HnswConfig();
        config.dimension = 32;
        config.spaceFactory = Spaces.euclidean();
        config.initialCapacity = 12_000;
        config.M = 16;
        config.maxM0 = 32;
        config.efConstruction = 200;
        config.defaultEfSearch = 100;

        HnswIndex index = Hnsw.build(config);
        Map<Long, double[]> dataset = new HashMap<>();
        Random random = new Random(42L);
        int pointCount = 10_000;
        for (int i = 0; i < pointCount; i++) {
            double[] vector = randomVector(random, config.dimension);
            long id = i + 1L;
            dataset.put(id, vector);
            index.add(id, vector);
        }

        Random queryRandom = new Random(777L);
        int queryCount = 25;
        int k = 10;
        int efSearch = k * 10;
        double totalRecall = 0.0;
        for (int q = 0; q < queryCount; q++) {
            double[] query = randomVector(queryRandom, config.dimension);
            List<SearchResult> results = index.searchKnn(query, k, efSearch);
            assertThat(results.size()).isGreaterThanOrEqualTo(k);

            Set<Long> expected = bruteForceTopK(dataset, query, k);
            long hits = results.stream()
                    .limit(k)
                    .map(SearchResult::id)
                    .filter(expected::contains)
                    .count();
            totalRecall += ((double) hits) / k;
        }

        double averageRecall = totalRecall / queryCount;
        assertThat(averageRecall).isGreaterThanOrEqualTo(0.9);
    }

    @Test
    void shouldRespectDuplicatePolicy() {
        HnswConfig config = new HnswConfig();
        config.dimension = 3;
        config.spaceFactory = Spaces.euclidean();
        config.duplicatePolicy = DuplicatePolicy.REJECT;

        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{0.0, 0.0, 0.0});
        assertThatThrownBy(() -> index.add(1L, new double[]{1.0, 0.0, 0.0}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRemoveVectors() {
        HnswConfig config = new HnswConfig();
        config.dimension = 2;
        config.spaceFactory = Spaces.euclidean();
        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{0.0, 0.0});
        index.add(2L, new double[]{2.0, 2.0});
        assertThat(index.remove(1L)).isTrue();
        assertThat(index.contains(1L)).isFalse();
        List<SearchResult> results = index.searchKnn(new double[]{0.0, 0.0}, 1);
        assertThat(results).extracting(SearchResult::id).doesNotContain(1L);
    }

    @Test
    void concurrentSearchesYieldDeterministicResults() throws Exception {
        HnswConfig config = new HnswConfig();
        config.dimension = 8;
        config.spaceFactory = Spaces.euclidean();
        config.initialCapacity = 512;
        config.M = 8;
        config.maxM0 = 12;
        config.defaultEfSearch = 32;
        config.efConstruction = 64;

        HnswIndex index = Hnsw.build(config);
        Random random = new Random(123L);
        for (int i = 0; i < 256; i++) {
            index.add(i + 1L, randomVector(random, config.dimension));
        }

        int queryCount = 32;
        List<double[]> queries = new ArrayList<>(queryCount);
        List<List<SearchResult>> expectedResults = new ArrayList<>(queryCount);
        for (int i = 0; i < queryCount; i++) {
            double[] query = randomVector(random, config.dimension);
            queries.add(query);
            expectedResults.add(index.searchKnn(query, 5, 32));
        }

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int i = 0; i < queryCount; i++) {
                double[] query = queries.get(i);
                List<SearchResult> expected = expectedResults.get(i);
                tasks.add(() -> {
                    for (int round = 0; round < 5; round++) {
                        List<SearchResult> actual = index.searchKnn(query, 5, 32);
                        assertThat(actual).containsExactlyElementsOf(expected);
                    }
                    return null;
                });
            }
            executor.invokeAll(tasks);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void removeDoesNotLeakDeletedNodesDuringConcurrentReads() throws Exception {
        HnswConfig config = new HnswConfig();
        config.dimension = 1;
        config.spaceFactory = Spaces.euclidean();
        config.M = 4;
        config.maxM0 = 6;
        config.defaultEfSearch = 16;
        config.efConstruction = 32;

        HnswIndex index = Hnsw.build(config);
        long removedId = 1L;
        index.add(removedId, new double[]{0.0});
        for (int i = 1; i <= 64; i++) {
            index.add(removedId + i, new double[]{10.0 + i});
        }
        double[] query = new double[]{0.05};

        int readerCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(readerCount + 1);
        CountDownLatch readersReady = new CountDownLatch(readerCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicBoolean removed = new AtomicBoolean(false);

        List<Future<?>> readers = new ArrayList<>();
        for (int i = 0; i < readerCount; i++) {
            readers.add(executor.submit(() -> {
                readersReady.countDown();
                start.await();
                while (running.get()) {
                    List<SearchResult> results = index.searchKnn(query, 1, 16);
                    if (removed.get()) {
                        assertThat(results)
                                .extracting(SearchResult::id)
                                .doesNotContain(removedId);
                    }
                }
                return null;
            }));
        }

        assertThat(readersReady.await(2, TimeUnit.SECONDS)).isTrue();
        Future<Boolean> removalFuture = executor.submit(() -> {
            start.countDown();
            return index.remove(removedId);
        });

        boolean removedNow = removalFuture.get(5, TimeUnit.SECONDS);
        assertThat(removedNow).isTrue();
        removed.set(true);

        // Allow readers to observe the removal while still running.
        Thread.sleep(50L);
        running.set(false);
        for (Future<?> reader : readers) {
            reader.get(5, TimeUnit.SECONDS);
        }
        executor.shutdownNow();

        List<SearchResult> resultsAfterRemoval = index.searchKnn(query, 1, 16);
        assertThat(resultsAfterRemoval)
                .extracting(SearchResult::id)
                .doesNotContain(removedId);
    }

    @Test
    void removalReconnectsNeighborsWhenRepairDisabled() throws Exception {
        HnswConfig config = new HnswConfig();
        config.dimension = 1;
        config.spaceFactory = Spaces.euclidean();
        config.M = 2;
        config.maxM0 = 2;
        config.efConstruction = 8;
        config.defaultEfSearch = 2;
        config.randomSeed = 1234L;
        config.efRepair = 0;

        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{0.0});
        index.add(2L, new double[]{0.1});
        index.add(3L, new double[]{0.2});

        DefaultHnswIndex internal = (DefaultHnswIndex) index;
        Field mapField = DefaultHnswIndex.class.getDeclaredField("idToInternal");
        mapField.setAccessible(true);
        Long2IntOpenHashMap idToInternal = (Long2IntOpenHashMap) mapField.get(internal);
        int firstNode = idToInternal.get(1L);
        int middleNode = idToInternal.get(2L);
        int thirdNode = idToInternal.get(3L);

        Field graphField = DefaultHnswIndex.class.getDeclaredField("graph");
        graphField.setAccessible(true);
        HnswGraph graph = (HnswGraph) graphField.get(internal);
        NeighborList middleNeighbors = graph.neighborList(0, middleNode);
        assertThat(middleNeighbors).isNotNull();
        assertThat(middleNeighbors.contains(firstNode)).isTrue();
        assertThat(middleNeighbors.contains(thirdNode)).isTrue();

        assertThat(index.remove(2L)).isTrue();
        NeighborList firstNeighbors = graph.neighborList(0, firstNode);
        NeighborList thirdNeighbors = graph.neighborList(0, thirdNode);

        assertThat(firstNeighbors).isNotNull();
        assertThat(thirdNeighbors).isNotNull();
        assertThat(firstNeighbors.contains(thirdNode)).isTrue();
        assertThat(thirdNeighbors.contains(firstNode)).isTrue();
    }

    @Test
    void shouldSaveAndLoadIndex() throws IOException {
        HnswConfig config = new HnswConfig();
        config.dimension = 2;
        config.spaceFactory = Spaces.euclidean();
        config.initialCapacity = 4;
        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{0.0, 0.0});
        index.add(2L, new double[]{1.0, 1.0});

        Path path = Files.createTempFile("hnsw-index", ".bin");
        index.save(path);

        HnswIndex loaded = Hnsw.load(path);
        List<SearchResult> results = loaded.searchKnn(new double[]{0.0, 0.0}, 1);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().id()).isEqualTo(1L);
    }

    @Test
    void loadFailsWhenChecksumIsCorrupted() throws IOException {
        HnswConfig config = new HnswConfig();
        config.dimension = 2;
        config.spaceFactory = Spaces.euclidean();
        config.initialCapacity = 4;

        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{0.0, 0.0});
        index.add(2L, new double[]{1.0, 1.0});

        Path path = Files.createTempFile("hnsw-index", ".bin");
        try {
            index.save(path);

            byte[] bytes = Files.readAllBytes(path);
            int corruptIndex = Math.max(0, bytes.length - Long.BYTES - 1);
            bytes[corruptIndex] ^= 0xFF;
            Files.write(path, bytes);

            assertThatThrownBy(() -> Hnsw.load(path))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("Checksum");
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void cosineSpaceShouldNormaliseVectors() {
        HnswConfig config = new HnswConfig();
        config.dimension = 3;
        config.spaceFactory = Spaces.cosineNormalized();
        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{1.0, 0.0, 0.0});
        index.add(2L, new double[]{0.0, 1.0, 0.0});

        List<SearchResult> results = index.searchKnn(new double[]{1.0, 0.0, 0.0}, 1);
        assertThat(results).extracting(SearchResult::id).containsExactly(1L);
    }

    @Test
    void searchMatchesBruteForceOnLineDataset() {
        HnswConfig config = new HnswConfig();
        config.dimension = 1;
        config.spaceFactory = Spaces.euclidean();
        config.M = 4;
        config.maxM0 = 6;
        config.efConstruction = 64;
        config.defaultEfSearch = 32;

        HnswIndex index = Hnsw.build(config);
        Map<Long, double[]> dataset = new LinkedHashMap<>();
        for (int i = 0; i < 8; i++) {
            long id = i;
            double[] vector = new double[]{i};
            dataset.put(id, vector);
            index.add(id, vector);
        }

        double[] query = new double[]{2.2};
        List<SearchResult> results = index.searchKnn(query, 3, 32);

        assertThat(results).hasSize(3);
        List<Long> expectedOrder = bruteForceOrderedIds(dataset, query, 3);
        assertThat(results).extracting(SearchResult::id).containsExactlyElementsOf(expectedOrder);
    }

    @Test
    void searchClampsEfSearchWhenBelowRequestedK() {
        HnswConfig config = new HnswConfig();
        config.dimension = 2;
        config.spaceFactory = Spaces.euclidean();
        config.M = 4;
        config.maxM0 = 6;
        config.defaultEfSearch = 2;

        HnswIndex index = Hnsw.build(config);
        Map<Long, double[]> dataset = new LinkedHashMap<>();
        double[][] points = {
                {0.0, 0.0},
                {0.1, 0.0},
                {0.0, 0.1},
                {0.2, 0.0},
                {0.0, 0.2}
        };
        for (int i = 0; i < points.length; i++) {
            long id = i + 1L;
            dataset.put(id, points[i]);
            index.add(id, points[i]);
        }

        double[] query = new double[]{0.05, 0.05};
        List<SearchResult> results = index.searchKnn(query, 4, 1);

        assertThat(results).hasSize(4);
        List<Long> ids = results.stream().map(SearchResult::id).toList();
        assertThat(ids).contains(1L, 2L, 3L);
        assertThat(new HashSet<>(ids)).hasSize(4);
        assertThat(results).extracting(SearchResult::distance).isSorted();
    }

    @Test
    void correlationSpaceHandlesConstantVectors() {
        HnswConfig config = new HnswConfig();
        config.dimension = 3;
        config.spaceFactory = Spaces.correlationDirect();
        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{2.0, 2.0, 2.0});
        index.add(2L, new double[]{1.0, 2.0, 3.0});

        List<SearchResult> results = index.searchKnn(new double[]{1.0, 2.0, 3.0}, 1);
        assertThat(results).extracting(SearchResult::id).containsExactly(2L);
    }

    @Test
    void sizeTracksNonDeletedVectors() {
        HnswConfig config = new HnswConfig();
        config.dimension = 2;
        config.spaceFactory = Spaces.euclidean();
        config.initialCapacity = 2;

        HnswIndex index = Hnsw.build(config);

        assertThat(index.size()).isZero();

        index.add(1L, new double[]{0.0, 0.0});
        index.add(2L, new double[]{1.0, 1.0});
        index.add(3L, new double[]{2.0, 2.0});

        assertThat(index.size()).isEqualTo(3);
        assertThat(index.contains(2L)).isTrue();

        assertThat(index.remove(2L)).isTrue();
        assertThat(index.size()).isEqualTo(2);
        assertThat(index.contains(2L)).isFalse();

        index.add(4L, new double[]{3.0, 3.0});
        assertThat(index.size()).isEqualTo(3);
        assertThat(index.contains(4L)).isTrue();
    }

    @Test
    void expandingCapacityKeepsPreviouslyInsertedVectors() throws Exception {
        HnswConfig config = new HnswConfig();
        config.dimension = 3;
        config.spaceFactory = Spaces.euclidean();
        config.initialCapacity = 1;
        config.M = 4;
        config.maxM0 = 6;

        HnswIndex index = Hnsw.build(config);
        long anchorId = 99L;
        double[] anchorVector = new double[]{10.0, 20.0, 30.0};
        index.add(anchorId, anchorVector);

        for (int i = 0; i < 10; i++) {
            index.add(200L + i, new double[]{i, i + 1.0, i + 2.0});
        }

        assertThat(index.size()).isEqualTo(11);
        assertThat(index.contains(anchorId)).isTrue();

        DefaultHnswIndex internal = (DefaultHnswIndex) index;
        Field vectorField = DefaultHnswIndex.class.getDeclaredField("vectorStorage");
        vectorField.setAccessible(true);
        VectorStorage vectorStorage = (VectorStorage) vectorField.get(internal);

        Field mapField = DefaultHnswIndex.class.getDeclaredField("idToInternal");
        mapField.setAccessible(true);
        Long2IntOpenHashMap idToInternal = (Long2IntOpenHashMap) mapField.get(internal);
        int nodeId = idToInternal.get(anchorId);

        assertThat(nodeId).isGreaterThanOrEqualTo(0);
        assertThat(vectorStorage.copy(nodeId)).containsExactly(anchorVector);
    }

    @Test
    void exactSearchModeReturnsBruteForceResults() {
        HnswConfig config = new HnswConfig();
        config.dimension = 2;
        config.spaceFactory = Spaces.euclidean();
        config.exactSearch = true;

        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{0.0, 0.0});
        index.add(2L, new double[]{1.0, 0.0});
        index.add(3L, new double[]{0.0, 1.0});

        List<SearchResult> results = index.searchKnn(new double[]{0.8, 0.1}, 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isEqualTo(2L);
        assertThat(results.get(1).id()).isEqualTo(1L);
        assertThat(results.get(0).distance()).isLessThan(results.get(1).distance());
    }

    @Test
    void randomizedInsertDeleteMaintainsHighRecall() {
        HnswConfig config = new HnswConfig();
        config.dimension = 8;
        config.spaceFactory = Spaces.euclidean();
        config.initialCapacity = 512;
        config.M = 12;
        config.maxM0 = 24;
        config.efConstruction = 200;
        config.defaultEfSearch = 100;
        config.levelLambda = 1.0 / Math.log(config.M);

        HnswIndex index = Hnsw.build(config);

        Random random = new Random(123456789L);
        Map<Long, double[]> active = new HashMap<>();
        long nextId = 1L;

        double totalRecall = 0.0;
        int recallChecks = 0;

        for (int iteration = 0; iteration < 5000; iteration++) {
            boolean shouldInsert = active.isEmpty() || active.size() < 64 || random.nextDouble() < 0.6;
            if (shouldInsert) {
                double[] vector = randomVector(random, config.dimension);
                long id = nextId++;
                index.add(id, vector);
                active.put(id, vector);
            } else {
                List<Long> ids = new ArrayList<>(active.keySet());
                Long id = ids.get(random.nextInt(ids.size()));
                assertThat(index.remove(id)).isTrue();
                active.remove(id);
                assertThat(index.contains(id)).isFalse();
            }

            if (!active.isEmpty() && iteration % 10 == 0) {
                double[] query = randomVector(random, config.dimension);
                int requestedK = Math.min(8, active.size());
                List<SearchResult> results = index.searchKnn(query, requestedK, Math.max(requestedK, 200));

                assertThat(results).isNotEmpty();
                int evaluatedK = Math.min(requestedK, results.size());

                Set<Long> expectedTopK = bruteForceTopK(active, query, evaluatedK);
                Set<Long> returnedIds = new HashSet<>();
                for (int i = 0; i < evaluatedK; i++) {
                    SearchResult result = results.get(i);
                    returnedIds.add(result.id());
                }

                long intersection = returnedIds.stream().filter(expectedTopK::contains).count();
                double recall = ((double) intersection) / evaluatedK;
                totalRecall += recall;
                recallChecks++;

                assertThat(returnedIds)
                        .as("search results must not include deleted ids")
                        .allMatch(active::containsKey);
            }
        }

        assertThat(recallChecks).isGreaterThan(0);
        double averageRecall = totalRecall / recallChecks;
        assertThat(averageRecall).isGreaterThanOrEqualTo(0.98);
        System.out.printf("Average recall over %d checks: %.8f%n", recallChecks, averageRecall);
    }

    @Test
    void removingEntryPointReselectsNewEntryPoint() throws Exception {
        HnswConfig config = new HnswConfig();
        config.dimension = 2;
        config.spaceFactory = Spaces.euclidean();
        config.M = 4;
        config.maxM0 = 6;

        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{0.0, 0.0});
        index.add(2L, new double[]{1.0, 0.0});
        index.add(3L, new double[]{0.0, 1.0});

        DefaultHnswIndex internal = (DefaultHnswIndex) index;
        HnswGraph graph = extractGraph(internal);
        int entryNode = graph.entryPoint();
        assertThat(entryNode).isGreaterThanOrEqualTo(0);
        long entryId = extractInternalToId(internal)[entryNode];

        assertThat(index.remove(entryId)).isTrue();
        assertThat(index.contains(entryId)).isFalse();

        int newEntry = graph.entryPoint();
        assertThat(newEntry).isGreaterThanOrEqualTo(0);
        long newEntryId = extractInternalToId(internal)[newEntry];
        assertThat(newEntryId).isNotEqualTo(-1L);
        assertThat(newEntryId).isNotEqualTo(entryId);
        assertThat(graph.maxLevel()).isEqualTo(graph.levelOfNode(newEntry));

        List<SearchResult> results = index.searchKnn(new double[]{0.2, 0.2}, 2);
        assertThat(results)
                .as("search should remain operational after removing the entry point")
                .extracting(SearchResult::id)
                .doesNotContain(entryId)
                .containsAnyOf(2L, 3L);
    }

    @Test
    void removingNodeReturnsSlotToFreeList() throws Exception {
        HnswConfig config = new HnswConfig();
        config.dimension = 2;
        config.spaceFactory = Spaces.euclidean();
        config.M = 4;
        config.maxM0 = 6;

        HnswIndex index = Hnsw.build(config);
        index.add(1L, new double[]{0.0, 0.0});
        index.add(2L, new double[]{1.0, 0.0});
        index.add(3L, new double[]{0.0, 1.0});

        DefaultHnswIndex internal = (DefaultHnswIndex) index;
        int initialNodeCount = extractNodeCount(internal);
        assertThat(initialNodeCount).isEqualTo(3);

        assertThat(index.remove(2L)).isTrue();
        assertThat(extractNodeCount(internal))
                .as("removing should not shrink nodeCount because slots are recycled lazily")
                .isEqualTo(initialNodeCount);

        index.add(4L, new double[]{1.0, 1.0});
        assertThat(extractNodeCount(internal))
                .as("freed slots should be reused without growing the node pool")
                .isEqualTo(initialNodeCount);
        assertThat(index.contains(4L)).isTrue();
    }

    @Test
    void querySpecificEfSearchLowerThanDefaultIsHonored() throws Exception {
        HnswConfig config = new HnswConfig();
        config.dimension = 1;
        config.spaceFactory = Spaces.euclidean();
        config.defaultEfSearch = 32;
        config.efConstruction = 40;
        config.M = 6;
        config.maxM0 = 6;

        HnswIndex index = Hnsw.build(config);
        for (int i = 0; i < 16; i++) {
            index.add(i, new double[]{i});
        }

        DefaultHnswIndex internal = (DefaultHnswIndex) index;
        Field graphField = DefaultHnswIndex.class.getDeclaredField("graph");
        graphField.setAccessible(true);
        HnswGraph graph = (HnswGraph) graphField.get(internal);
        graph.setEntryPoint(0);
        graph.setMaxLevel(0);

        List<SearchResult> results = index.searchKnn(new double[]{15.2}, 1, 1);
        assertThat(results).isNotEmpty();

        int resultHeapSize = extractResultHeapSize((DefaultHnswIndex) index);
        assertThat(resultHeapSize)
                .as("per-query efSearch should cap the number of retained candidates")
                .isLessThanOrEqualTo(1);
    }

    private static double[] randomVector(Random random, int dimension) {
        double[] vector = new double[dimension];
        for (int i = 0; i < dimension; i++) {
            vector[i] = random.nextDouble() * 2.0 - 1.0;
        }
        return vector;
    }

    private static int extractResultHeapSize(DefaultHnswIndex index) throws Exception {
        Field scratchField = DefaultHnswIndex.class.getDeclaredField("searchScratch");
        scratchField.setAccessible(true);
        ThreadLocal<?> scratchLocal = (ThreadLocal<?>) scratchField.get(index);
        Object scratch = scratchLocal.get();
        Field resultsField = scratch.getClass().getDeclaredField("results");
        resultsField.setAccessible(true);
        Object heap = resultsField.get(scratch);
        Field sizeField = heap.getClass().getDeclaredField("size");
        sizeField.setAccessible(true);
        return sizeField.getInt(heap);
    }

    private static HnswGraph extractGraph(DefaultHnswIndex index) throws Exception {
        Field graphField = DefaultHnswIndex.class.getDeclaredField("graph");
        graphField.setAccessible(true);
        return (HnswGraph) graphField.get(index);
    }

    private static long[] extractInternalToId(DefaultHnswIndex index) throws Exception {
        Field field = DefaultHnswIndex.class.getDeclaredField("internalToId");
        field.setAccessible(true);
        return (long[]) field.get(index);
    }

    private static int extractNodeCount(DefaultHnswIndex index) throws Exception {
        Field field = DefaultHnswIndex.class.getDeclaredField("nodeCount");
        field.setAccessible(true);
        return field.getInt(index);
    }

    private static Set<Long> bruteForceTopK(Map<Long, double[]> active, double[] query, int k) {
        return active.entrySet().stream()
                .map(entry -> new Candidate(entry.getKey(), euclideanDistance(entry.getValue(), query)))
                .sorted(Comparator.comparingDouble(Candidate::distance))
                .limit(k)
                .map(Candidate::id)
                .collect(HashSet::new, HashSet::add, Set::addAll);
    }

    private static List<Long> bruteForceOrderedIds(Map<Long, double[]> active, double[] query, int k) {
        return active.entrySet().stream()
                .map(entry -> new Candidate(entry.getKey(), euclideanDistance(entry.getValue(), query)))
                .sorted(Comparator.comparingDouble(Candidate::distance))
                .limit(k)
                .map(Candidate::id)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    private static double euclideanDistance(double[] vector, double[] query) {
        double sum = 0.0;
        for (int i = 0; i < vector.length; i++) {
            double diff = vector[i] - query[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    private record Candidate(long id, double distance) {}
}
