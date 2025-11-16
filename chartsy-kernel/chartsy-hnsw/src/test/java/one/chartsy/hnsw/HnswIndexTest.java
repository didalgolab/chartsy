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
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import one.chartsy.hnsw.graph.HnswGraph;
import one.chartsy.hnsw.internal.DefaultHnswIndex;
import one.chartsy.hnsw.space.Spaces;
import one.chartsy.hnsw.internal.DefaultHnswIndex;
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

        for (int iteration = 0; iteration < 4000; iteration++) {
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

    private static Set<Long> bruteForceTopK(Map<Long, double[]> active, double[] query, int k) {
        return active.entrySet().stream()
                .map(entry -> new Candidate(entry.getKey(), euclideanDistance(entry.getValue(), query)))
                .sorted(Comparator.comparingDouble(Candidate::distance))
                .limit(k)
                .map(Candidate::id)
                .collect(HashSet::new, HashSet::add, Set::addAll);
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
