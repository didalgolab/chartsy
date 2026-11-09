package one.chartsy.financial.indicators;

import one.chartsy.Candle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AverageTrueRangeTest {
    @Test
    void periodOne_returns_each_completed_true_range() {
        var atr = new AverageTrueRange(1);
        atr.accept(Candle.of(1, 10, 11, 9, 10));
        assertFalse(atr.isReady());
        atr.accept(Candle.of(2, 10, 12, 9, 11));
        assertTrue(atr.isReady());
        assertEquals(3, atr.getLast());
        atr.accept(Candle.of(3, 14, 16, 14, 15));
        assertEquals(5, atr.getLast());
        atr.accept(Candle.of(4, 14, 14, 13, 13));
        assertEquals(2, atr.getLast());
    }

    @Test
    void periodTwenty_preserves_initial_mean_and_wilder_smoothing() {
        var atr = new AverageTrueRange(20);
        atr.accept(Candle.of(0, 100));
        for (int range = 1; range <= 19; range++)
            atr.accept(Candle.of(range, 100, 100 + range, 100, 100));
        assertFalse(atr.isReady());
        atr.accept(Candle.of(20, 100, 120, 100, 100));
        assertTrue(atr.isReady());
        assertEquals(10.5, atr.getLast(), 1e-12);
        atr.accept(Candle.of(21, 100, 121, 100, 100));
        assertEquals(11.025, atr.getLast(), 1e-12);
    }
}
