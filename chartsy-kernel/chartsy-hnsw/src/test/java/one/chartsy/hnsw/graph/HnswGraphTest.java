package one.chartsy.hnsw.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HnswGraphTest {

    @Test
    void neighborListsHonorDegreeBoundsPerLevel() {
        HnswGraph graph = new HnswGraph(/*maxM=*/2, /*maxM0=*/4, /*initialCapacity=*/2);

        NeighborList levelZero = graph.ensureNeighborList(0, 0);
        assertEquals(4, levelZero.capacity(), "level 0 should honor maxM0");

        NeighborList levelTwo = graph.ensureNeighborList(2, 5);
        assertEquals(2, levelTwo.capacity(), "levels above 0 should honor maxM");
        assertTrue(graph.levelCount() >= 3, "ensureNeighborList should grow the level array");
    }

    @Test
    void expandingNodeCapacityPreservesExistingNeighborLists() {
        HnswGraph graph = new HnswGraph(/*maxM=*/2, /*maxM0=*/3, /*initialCapacity=*/1);
        NeighborList existing = graph.ensureNeighborList(1, 0);
        assertTrue(existing.addIfAbsent(42));

        graph.ensureNeighborList(1, 10); // forces internal arrays to grow

        NeighborList retained = graph.neighborList(1, 0);
        assertNotNull(retained);
        assertTrue(retained.contains(42), "existing neighbor list entries must survive expansions");
        assertNotNull(graph.neighborList(1, 10), "newly requested node should also exist");
    }
}
