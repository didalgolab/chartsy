package one.chartsy.hnsw;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Random;
import one.chartsy.hnsw.graph.HnswGraph;
import one.chartsy.hnsw.graph.NeighborList;
import one.chartsy.hnsw.internal.DefaultHnswIndex;
import one.chartsy.hnsw.space.Spaces;
import org.junit.jupiter.api.Test;

class TopLevelInsertionConnectivityTest {

    @Test
    void nodePromotedAboveCurrentMaxLevelObtainsBaseNeighbors() throws Exception {
        HnswConfig config = new HnswConfig();
        config.dimension = 8;
        config.spaceFactory = Spaces.euclidean();
        config.M = 12;
        config.maxM0 = 24;
        config.efConstruction = 200;
        config.defaultEfSearch = 120;
        config.levelLambda = 1.0;

        HnswIndex index = Hnsw.build(config);
        DefaultHnswIndex internal = (DefaultHnswIndex) index;

        Field graphField = DefaultHnswIndex.class.getDeclaredField("graph");
        graphField.setAccessible(true);
        HnswGraph graph = (HnswGraph) graphField.get(internal);

        Random random = new Random(123456789L);
        long nextId = 1L;
        int previousMaxLevel = -1;
        boolean verifiedPromotion = false;

        for (int iteration = 0; iteration < 500 && !verifiedPromotion; iteration++) {
            double[] vector = new double[config.dimension];
            for (int d = 0; d < config.dimension; d++) {
                vector[d] = random.nextDouble() * 2.0 - 1.0;
            }
            long id = nextId++;
            index.add(id, vector);

            HnswStats stats = index.stats();
            int maxLevel = stats.maxLevel();
            if (previousMaxLevel >= 0 && maxLevel > previousMaxLevel) {
                int internalId = internal.getNodeId(id);
                NeighborList levelZero = graph.neighborList(0, internalId);
                assertThat(levelZero)
                        .as("level-0 neighbor list for promoted node")
                        .isNotNull();
                assertThat(levelZero.size())
                        .as("promoted node retains level-0 neighbors")
                        .isGreaterThan(0);
                verifiedPromotion = true;
                break;
            }
            previousMaxLevel = Math.max(previousMaxLevel, maxLevel);
        }

        assertThat(verifiedPromotion)
                .as("expected at least one node to be promoted above the previous max level")
                .isTrue();
    }
}
