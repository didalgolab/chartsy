package one.chartsy.financial.indicators;

import one.chartsy.Candle;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RangeInversionSigma}.
 */
@Disabled
@DisplayName("RangeInversionSigma indicator")
class RangeInversionSigmaTest {

    private static long currentTime = 1L;

    static Candle candleOfRange(double highLow) {
        var rnd = ThreadLocalRandom.current();
        var open = rnd.nextDouble();
        return Candle.of(currentTime++, open, open + highLow, open, open + highLow);
    }

    /* ------ helper: expected |z| for a *fixed* window length w ------ */
    static double expectedAbsZScore(int w, long inversions) {
        double mu   = w * (w - 1) / 4.0;
        double var  = w * (w - 1) * (2.0 * w + 5.0) / 72.0;
        return Math.abs((inversions - mu) / Math.sqrt(var));
    }

    @Test
    void constructor_rejects_small_periods() {
        assertThrows(IllegalArgumentException.class, () -> new RangeInversionSigma(6));
    }

    @Test
    void is_not_ready_before_some_bars() {
        var ind = new RangeInversionSigma(10);
        for (int i = 1; i <= 6; i++)
            ind.accept(Candle.of(i, i));

        assertFalse(ind.isReady(), "indicator should not be ready");
        assertTrue(Double.isNaN(ind.getLast()), "getLast() must be NaN when not ready");
        assertEquals(0, ind.getLastWindowLength(), "window length should be 0 while not ready");
    }

    @Nested
    @DisplayName("Ascending ranges 1..10 (zero inversions)")
    class AscendingSequence {

        private final int PERIODS = 10;
        private final RangeInversionSigma ind = new RangeInversionSigma(PERIODS);

        @Test
        void ascending_produces_correct_sigma_distance_and_window_length() {
            for (int range = 1; range <= 10; range++)
                ind.accept(candleOfRange(range));

            assertTrue(ind.isReady(), "indicator must be ready (>= 7 bars)");
            // The absolute z‑score grows monotonically with window size,
            // so the maximum must come from the *largest* window (10).
            double expectedAbsZ = expectedAbsZScore(10, 0L);
            assertEquals(10, ind.getLastWindowLength(), "best window length must be 10");
            assertEquals(expectedAbsZ, ind.getLast(), 1e-12, "sigma multiplier mismatch (ascending case)");
        }
    }

    @Nested
    @DisplayName("Descending ranges 10..1 (max inversions)")
    class DescendingSequence {

        private final int PERIODS = 10;
        private final RangeInversionSigma ind = new RangeInversionSigma(PERIODS);

        @Test
        @DisplayName("Produces same |sigma| as ascending (symmetry check)")
        void descending_produces_same_absolute_sigma() {
            // Feed descending ranges –> *maximum* inversions in any window
            for (int range = 10; range >= 1; range--)
                ind.accept(candleOfRange(range));

            assertTrue(ind.isReady(), "indicator must be ready (>= 7 bars)");
            long maxInv10 = 10L * 9 / 2; // 45 inversions
            double expectedAbsZ = expectedAbsZScore(10, maxInv10);

            assertEquals(10, ind.getLastWindowLength(), "best window length must be 10");
            assertEquals(expectedAbsZ, ind.getLast(), 1e-12, "sigma multiplier mismatch (descending case)");
        }
    }

    @Test
    @DisplayName("Indicator updates as buffer slides forward")
    void testSlidingWindowUpdate() {
        int periods = 8;
        RangeInversionSigma ind = new RangeInversionSigma(periods);

        // Make ranges 8..1 (descending): max inversions = C(8,2)=28
        for (int range = 8; range >= 1; range--)
            ind.accept(candleOfRange(range));

        assertTrue(ind.isReady(), "should be ready");

        /* Capture initial state (window length must be 8) */
        double firstSigma = ind.getLast();
        int    firstW     = ind.getLastWindowLength();
        assertEquals(periods, firstW, "initial best window length");

        // Append a *very large* range so buffer slides and becomes mixed.
        // The new last 8 ranges are: 100,8,7,6,5,4,3,2
        // => still descending for 7 steps, then highest at front – inversions drop. */
        ind.accept(candleOfRange(100));

        assertTrue(ind.isReady(), "should stay ready");
        /* Sigma must either stay the same or change but certainly not become NaN */
        assertFalse(Double.isNaN(ind.getLast()), "sigma should remain defined");
        /* The best window length must remain within [7..8] */
        int w = ind.getLastWindowLength();
        assertTrue(w >= 7 && w <= periods,
                "window length must lie in permitted range");
    }

    @Nested
    @DisplayName("Constant ranges (all equal)")
    class ConstantRanges {

        private final int PERIODS = 12;
        private final RangeInversionSigma ind = new RangeInversionSigma(PERIODS);

        /**
         * Return |z| for zero inversions on window w.
         */
        private double expectedAbsZ(int w) {
            double mu   = w * (w - 1) / 4.0;
            double var  = w * (w - 1) * (2.0 * w + 5.0) / 72.0;
            // zero inversions => z = -mu/sqrt(var), so |z| = mu/sigma
            return mu / Math.sqrt(var);
        }

        @Test
        void constant_ranges_act_like_ascending() {
            // feed PERIODS candles each with identical high-low range
            for (int i = 0; i < PERIODS; i++)
                ind.accept(candleOfRange(5.0));

            assertTrue(ind.isReady(), "indicator must be ready after PERIODS bars");

            // should pick the largest window = PERIODS with zero inversions
            assertEquals(PERIODS, ind.getLastWindowLength(), "best window length must be full buffer length");
            assertEquals(expectedAbsZ(PERIODS), ind.getLast(), 1e-12, "sigma multiplier should match zero-inversion case");
        }
    }
}
