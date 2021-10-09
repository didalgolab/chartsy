package one.chartsy.data;

import one.chartsy.Candle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleCandleBuilderTest {

    @Test void isEmpty_when_newly_created() {
        SimpleCandleBuilder newlyCreated = new SimpleCandleBuilder();

        assertFalse(newlyCreated.isPresent(), "isPresent");
        assertNull(newlyCreated.get(), "get");
    }

    @Test void get_gives_aggregated_Candle() {
        SimpleCandleBuilder builder = new SimpleCandleBuilder();
        builder.merge(Candle.of(1L, 1));
        builder.merge(Candle.of(2L, 2));
        builder.merge(Candle.of(3L, 3));

        assertTrue(builder.isPresent(), "isPresent");

        Candle aggregatedCandle = builder.get();
        assertNotNull(aggregatedCandle);
        assertEquals(Candle.of(3L, 1, 3, 1, 3), aggregatedCandle, "aggregatedCandle");
    }
}