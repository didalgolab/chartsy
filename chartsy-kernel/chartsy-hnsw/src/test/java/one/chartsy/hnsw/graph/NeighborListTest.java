package one.chartsy.hnsw.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NeighborListTest {

    @Test
    void addRespectsCapacityAndUniqueness() {
        NeighborList list = new NeighborList(2);

        assertTrue(list.addIfAbsent(7));
        assertTrue(list.addIfAbsent(11));

        assertFalse(list.addIfAbsent(7), "duplicate additions must be ignored");
        assertFalse(list.addIfAbsent(3), "capacity should not be exceeded");
        assertEquals(2, list.size());
        assertTrue(list.contains(7));
        assertTrue(list.contains(11));

        assertTrue(list.removeIfPresent(7));
        assertFalse(list.contains(7));
        assertEquals(1, list.size());

        assertTrue(list.addIfAbsent(5), "removal should free a slot");
        assertEquals(2, list.size());
    }
}
