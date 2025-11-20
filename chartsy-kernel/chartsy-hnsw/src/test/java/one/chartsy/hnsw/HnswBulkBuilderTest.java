package one.chartsy.hnsw;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.SplittableRandom;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import one.chartsy.hnsw.space.SpaceFactory;
import one.chartsy.hnsw.space.Spaces;

class HnswBulkBuilderTest {

    @Test
    void bulkBuildMatchesSerialResultsCosine() {
        HnswConfig config = defaultConfig(Spaces.cosineNormalized());
        Dataset dataset = dataset(256, config.dimension, 42L);
        assertBulkMatchesSerial(config, dataset, 5);
    }

    @Test
    void streamingBatchesAreSupported() {
        HnswConfig config = defaultConfig(Spaces.cosineNormalized());
        Dataset dataset = dataset(120, config.dimension, 7L);
        HnswBulkBuilder builder = new HnswBulkBuilder(config);
        builder.addBatch(slice(dataset.ids(), 0, 40), slice(dataset.vectors(), 0, 40));
        builder.addBatch(slice(dataset.ids(), 40, 80), slice(dataset.vectors(), 40, 80));
        builder.addBatch(slice(dataset.ids(), 80, 120), slice(dataset.vectors(), 80, 120));

        HnswIndex index = builder.build();
        HnswIndex serial = serial(config, dataset);
        assertThat(index.stats().size()).isEqualTo(serial.stats().size());
        assertThat(ids(index.nearestNeighbors(dataset.vectors()[0], 5)))
                .containsExactlyElementsOf(ids(serial.nearestNeighbors(dataset.vectors()[0], 5)));
    }

    @Test
    void bulkBuildMatchesSerialResultsEuclidean() {
        HnswConfig config = defaultConfig(Spaces.euclidean());
        Dataset dataset = dataset(200, config.dimension, 123L);
        assertBulkMatchesSerial(config, dataset, 10);
    }

    @Test
    void bulkBuildMaintainsRecallOnLargeDataset() {
        int dimension = 64;
        Dataset dataset = dataset(10_000, dimension, 99L);
        HnswConfig config = HnswConfig.builder()
                .dimension(dimension)
                .spaceFactory(Spaces.cosineNormalized())
                .efConstruction(200)
                .defaultEfSearch(200)
                .build();
        HnswBulkBuilder.Options options = new HnswBulkBuilder.Options();
        options.blockSizeL0 = 50_000;
        options.seedBlockL0 = 512;
        HnswIndex bulk = new HnswBulkBuilder(config, options).build(dataset.ids(), dataset.vectors());
        HnswIndex serial = serial(config, dataset);

        double averageRecall = averageRecall(bulk, serial, dataset, 16, 80);
        assertThat(averageRecall).isGreaterThan(0.9929);
    }

    @Test
    void bulkBuilderRejectsDuplicateIds() {
        HnswConfig config = defaultConfig(Spaces.cosineNormalized());
        long[] ids = {1L, 2L, 2L};
        double[][] vectors = new double[ids.length][config.dimension];
        for (int i = 0; i < vectors.length; i++) {
            for (int d = 0; d < config.dimension; d++) {
                vectors[i][d] = 0.1 * (i + d + 1);
            }
        }
        assertThatThrownBy(() -> new HnswBulkBuilder(config).build(ids, vectors))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate id");
    }

    private static HnswIndex serial(HnswConfig config, Dataset dataset) {
        HnswIndex index = Hnsw.build(config);
        for (int i = 0; i < dataset.ids().length; i++) {
            index.add(dataset.ids()[i], dataset.vectors()[i]);
        }
        return index;
    }

    private static Dataset dataset(int size, int dimension, long seed) {
        long[] ids = new long[size];
        double[][] vectors = new double[size][dimension];
        SplittableRandom random = new SplittableRandom(seed);
        for (int i = 0; i < size; i++) {
            ids[i] = i;
            for (int d = 0; d < dimension; d++) {
                vectors[i][d] = random.nextDouble(-1.0, 1.0);
            }
        }
        return new Dataset(ids, vectors);
    }

    private static long[] slice(long[] ids, int from, int to) {
        long[] out = new long[to - from];
        System.arraycopy(ids, from, out, 0, out.length);
        return out;
    }

    private static double[][] slice(double[][] vectors, int from, int to) {
        double[][] out = new double[to - from][];
        System.arraycopy(vectors, from, out, 0, out.length);
        return out;
    }

    private static List<Long> ids(List<SearchResult> results) {
        return results.stream().map(SearchResult::id).collect(Collectors.toList());
    }

    private static void assertBulkMatchesSerial(HnswConfig config, Dataset dataset, int step) {
        HnswIndex bulkIndex = new HnswBulkBuilder(config).build(dataset.ids(), dataset.vectors());
        HnswIndex serialIndex = serial(config, dataset);
        for (int i = 0; i < dataset.ids().length; i += step) {
            double[] query = dataset.vectors()[i];
            assertThat(ids(bulkIndex.nearestNeighbors(query, 8)))
                    .containsExactlyElementsOf(ids(serialIndex.nearestNeighbors(query, 8)));
        }
    }

    private static double averageRecall(HnswIndex bulk, HnswIndex serial, Dataset dataset, int k, int samples) {
        int step = Math.max(1, dataset.ids().length / samples);
        double total = 0.0;
        int count = 0;
        for (int i = 0; i < dataset.ids().length && count < samples; i += step, count++) {
            double[] query = dataset.vectors()[i];
            List<SearchResult> truth = serial.nearestNeighborsExact(query, k);
            List<SearchResult> approx = bulk.nearestNeighbors(query, k);
            total += recallAtK(approx, truth);
        }
        return count == 0 ? 1.0 : total / count;
    }

    private static double recallAtK(List<SearchResult> approx, List<SearchResult> truth) {
        LongOpenHashSet truthIds = new LongOpenHashSet();
        for (SearchResult result : truth) {
            truthIds.add(result.id());
        }
        if (truthIds.isEmpty()) {
            return 1.0;
        }
        int hits = 0;
        for (SearchResult result : approx) {
            if (truthIds.contains(result.id())) {
                hits++;
            }
        }
        return (double) hits / truthIds.size();
    }

    private static HnswConfig defaultConfig(SpaceFactory factory) {
        return HnswConfig.builder()
                .dimension(8)
                .spaceFactory(factory)
                .build();
    }

    private record Dataset(long[] ids, double[][] vectors) {}
}
