package one.chartsy.data.packed;

import one.chartsy.HLC;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ByteBufferMutableHLCDatasetTest {

    ByteBufferMutableHLCDataset dataset = new ByteBufferMutableHLCDataset();

    @Test
    void isEmpty_when_virgin() {
        ByteBufferMutableHLCDataset virgin = new ByteBufferMutableHLCDataset();

        assertEquals(0, virgin.length());
        assertTrue(virgin.isEmpty());
        assertThrows(IndexOutOfBoundsException.class, () -> virgin.get(0));
    }

    @Test
    void get_gives_HLC_at_time_slot() {
        long time = 0L;
        dataset.add(time, 0);
        dataset.add(time, -2);
        dataset.add(time, 2);
        dataset.add(time, -1);
        dataset.add(time, 1);

        assertEquals(1, dataset.length());
        assertEquals(new HLC(time, 2, -2, 1), dataset.get(0));
    }

    @Test
    void can_downsample_added_values() {
        ByteBufferMutableHLCDataset downsampling = new ByteBufferMutableHLCDataset(10L);
        // given first time slot
        downsampling.add(1L, 0);
        downsampling.add(2L, -2);
        downsampling.add(3L, 3);
        downsampling.add(9L, -1);
        downsampling.add(10L, 1);
        // given next time slot
        downsampling.add(11L, 11);
        // last time slot end time is: 20

        // verify
        assertEquals(2, downsampling.length());
        assertEquals(new HLC(20L, 11), downsampling.get(0));
        assertEquals(new HLC(10L, 3, -2, 1), downsampling.get(1));
    }

    @Test
    void can_grow_in_length_freely() {
        final int LIMIT = 1_000_000;
        for (int time = 0, val = LIMIT-1; time < LIMIT; time++, val--)
            dataset.add(time, val);

        assertEquals(LIMIT, dataset.length());
        for (int i = 0; i < dataset.length(); i++) {
            assertEquals(i, dataset.get(i).close());
        }
    }
}