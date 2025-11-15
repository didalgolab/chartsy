package one.chartsy.hnsw.graph;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HnswGraphCapacityTest {

    @Test
    void ensureNodeCapacity_fillsNewSliceWithMinusOne() {
        HnswGraph graph = new HnswGraph(4, 6, 4);
        for (int i = 0; i < 4; i++) {
            assertThat(graph.levelOfNode(i)).as("initial i=%d", i).isEqualTo(-1);
        }

        graph.ensureNodeCapacity(10);

        for (int i = 4; i < 10; i++) {
            assertThat(graph.levelOfNode(i)).as("expanded i=%d", i).isEqualTo(-1);
        }
    }
}
