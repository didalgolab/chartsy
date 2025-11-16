package one.chartsy.hnsw.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

import one.chartsy.hnsw.HnswBulkBuilder;
import one.chartsy.hnsw.HnswConfig;
import one.chartsy.hnsw.HnswIndex;
import one.chartsy.hnsw.graph.HnswGraph;
import one.chartsy.hnsw.graph.NeighborList;
import one.chartsy.hnsw.space.Spaces;

class BulkBuildEngineConnectivityTest {

    @Test
    void entryPointReceivesSeedEdgesOnAllPopulatedLevels() {
        int size = 200;
        int dimension = 8;
        int seedBlock = 2;
        long builderSeed = findSeedWithEntryPointOutsideSeedBlock(size, seedBlock);
        HnswConfig config = HnswConfig.builder()
                .dimension(dimension)
                .spaceFactory(Spaces.cosineNormalized())
                .randomSeed(builderSeed)
                .build();
        HnswBulkBuilder.Options options = new HnswBulkBuilder.Options();
        options.seedBlockL0 = seedBlock;
        options.seedBlockUpper = 2;
        options.blockSizeL0 = size;
        options.blockSizeUpper = size;
        Dataset dataset = dataset(size, dimension, 123L);

        HnswIndex built = new HnswBulkBuilder(config, options).build(dataset.ids(), dataset.vectors());
        DefaultHnswIndex internal = (DefaultHnswIndex) built;
        HnswGraph graph = internal.graph();
        int entryPoint = graph.entryPoint();
        for (int level = 0; level <= graph.maxLevel(); level++) {
            int population = countNodesAtLeast(graph, size, level);
            if (population <= 1) {
                continue;
            }
            NeighborList neighbors = graph.neighborList(level, entryPoint);
            assertThat(neighbors)
                    .as("entry point has neighbors on level %s", level)
                    .isNotNull();
            assertThat(neighbors.size()).isGreaterThan(0);
        }
    }

    private static int countNodesAtLeast(HnswGraph graph, int size, int level) {
        int count = 0;
        for (int node = 0; node < size; node++) {
            if (graph.levelOfNode(node) >= level) {
                count++;
            }
        }
        return count;
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

    private static long findSeedWithEntryPointOutsideSeedBlock(int size, int seedBlock) {
        for (long seed = 1; seed < 10_000; seed++) {
            int[] levels = sampleLevels(size, seed, 1.0);
            int entryPoint = selectEntryPoint(levels);
            int position = positionInShuffledOrder(levels, seed, 0, entryPoint);
            if (position >= seedBlock) {
                return seed;
            }
        }
        throw new IllegalStateException("Unable to locate builder seed for test");
    }

    private static int[] sampleLevels(int size, long randomSeed, double levelLambda) {
        int[] levels = new int[size];
        for (int node = 0; node < size; node++) {
            long seed = randomSeed ^ node;
            SplittableRandom random = new SplittableRandom(seed);
            double u = Math.max(1e-12, 1.0 - random.nextDouble());
            double value = -Math.log(u) * levelLambda;
            levels[node] = Math.max(0, (int) value);
        }
        return levels;
    }

    private static int selectEntryPoint(int[] levels) {
        int bestNode = 0;
        int bestLevel = -1;
        for (int i = 0; i < levels.length; i++) {
            if (levels[i] > bestLevel) {
                bestLevel = levels[i];
                bestNode = i;
            }
        }
        return bestNode;
    }

    private static int positionInShuffledOrder(int[] levels, long randomSeed, int level, int target) {
        int count = 0;
        for (int node = 0; node < levels.length; node++) {
            if (levels[node] >= level) {
                count++;
            }
        }
        int[] nodes = new int[count];
        int cursor = 0;
        for (int node = 0; node < levels.length; node++) {
            if (levels[node] >= level) {
                nodes[cursor++] = node;
            }
        }
        SplittableRandom random = new SplittableRandom(randomSeed + level * 31L + 17);
        for (int i = nodes.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = nodes[i];
            nodes[i] = nodes[j];
            nodes[j] = tmp;
        }
        for (int i = 0; i < nodes.length; i++) {
            if (nodes[i] == target) {
                return i;
            }
        }
        return -1;
    }

    private record Dataset(long[] ids, double[][] vectors) {
    }
}
