/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.simulation.reporting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EquitySummaryStatisticsTest {

    @Test
    void annualSharpeRatio() {
        var stats = aStatistics(2, 4, 4, 3, 1, 2, 6, 5);
        assertEquals(7.024760386350532788, stats.getAnnualSharpeRatio(),
                "Annual Sharpe ratio should match the hand-calculated value");
    }

    @Test
    void annualSortinoRatio() {
        var stats = aStatistics(2, 4, 4, 3, 1, 2, 6, 5);
        assertEquals(23.93172105652397, stats.getAnnualSortinoRatio(),
                "Annual Sortino ratio should match the hand-calculated value");
    }

    @Test
    void coreStatistics() {
        var stats = aStatistics(2, 4, 4, 3, 1, 2, 6, 5);
        assertAll(
                () -> assertEquals(2.0, stats.getStartingEquity()),
                () -> assertEquals(6.0, stats.getTotalEquityHigh()),
                () -> assertEquals(1.0, stats.getTotalEquityLow()),
                () -> assertEquals(5.0, stats.getEndingEquity()),
                () -> assertEquals(-3.0, stats.getMaxDrawdown()),
                () -> assertEquals(-75.0, stats.getMaxDrawdownPercent()),
                () -> assertEquals(-1.0, stats.getAverageDrawdown()),
                () -> assertEquals(-23.809523809523807, stats.getAverageDrawdownPercent()),
                () -> assertEquals(day(6)-day(3), stats.getLongestDrawdownDuration()),
                () -> assertEquals(day(6), stats.getCurrentDrawdownStartTime()),
                () -> assertEquals(day(6), stats.getTotalEquityHighTime()),
                () -> assertEquals(day(4), stats.getMaxDrawdownTime()),
                () -> assertEquals(day(4), stats.getMaxDrawdownPercentTime()),
                () -> assertEquals(7, stats.getDataPoints()),
                () -> assertFalse(stats.toString().isBlank())
        );
    }

    @Test
    void getTimeEquityCorrelation_gives_maximum_correlation_when_equity_grows_linearly_over_time() {
        var stats = aStatistics(100, 110, 120, 130, 140);

        assertEquals(1.0, stats.getTimeEquityCorrelation());
        assertEquals(0.9993551143064855, stats.getTimeLogEquityCorrelation());
    }

    @Test
    void getTimeEquityCorrelation_gives_maximum_correlation_when_equity_grows_exponentially_over_time() {
        var stats = aStatistics(100, 200, 400, 800);

        assertEquals(0.9819805060619657, stats.getTimeEquityCorrelation());
        assertEquals(1.0, stats.getTimeLogEquityCorrelation());
    }

    @Test
    void getTimeEquityCorrelation_gives_minimum_correlation_when_equity_decreases_linearly_over_time() {
        var stats = aStatistics(130, 120, 110, 100);

        assertEquals(-1.0, stats.getTimeEquityCorrelation());
        assertEquals(-0.9996548731684168, stats.getTimeLogEquityCorrelation());
    }

    @Test
    void defaultCollector_still_rejects_negative_equity_before_updating_state() {
        var stats = aStatistics(100, 110);
        assertThrows(IllegalArgumentException.class, () -> stats.add(-1, day(2)));
        assertEquals(110, stats.getEndingEquity());
        assertEquals(100, stats.getTotalEquityLow());
        assertEquals(1, stats.getDataPoints());
        assertEquals(aStatistics(100, 110).getEndTime(), stats.getEndTime());
    }

    @Test
    void defaultCollector_keeps_its_zero_skipping_log_correlation_and_recovery_behavior() {
        var stats = aStatistics(100, 110, 0, 120, 130);
        assertTrue(Double.isFinite(stats.getTimeLogEquityCorrelation()));
        assertTrue(Double.isFinite(stats.getCAGR()));
        assertEquals(-100, stats.getMaxDrawdownPercent());
    }

    @Test
    void signedCollector_retains_loss_beyond_capital_and_does_not_restore_relative_metrics_after_recovery() {
        var stats = EquitySummaryStatistics.forSignedEquity(100, .01);
        stats.add(120, day(1));
        stats.add(-30, day(2));
        assertUndefinedWealthMetrics(stats);
        assertEquals(-30, stats.getEndingEquity());
        stats.add(60, day(3));
        stats.add(150, day(4));
        assertAll(
                () -> assertEquals(100, stats.getStartingEquity()),
                () -> assertEquals(-30, stats.getTotalEquityLow()),
                () -> assertEquals(150, stats.getTotalEquityHigh()),
                () -> assertEquals(150, stats.getEndingEquity()),
                () -> assertEquals(50, stats.getNetProfit()),
                () -> assertEquals(-150, stats.getMaxDrawdown()),
                () -> assertEquals(-125, stats.getMaxDrawdownPercent()),
                () -> assertEquals(-52.5, stats.getAverageDrawdown()),
                () -> assertEquals(-43.75, stats.getAverageDrawdownPercent()),
                () -> assertEquals(day(2), stats.getMaxDrawdownTime()),
                () -> assertEquals(day(2), stats.getMaxDrawdownPercentTime()),
                () -> assertEquals(4, stats.getDataPoints()),
                () -> assertTrue(Double.isFinite(stats.getTimeEquityCorrelation())),
                () -> assertTrue(stats.getYearsElapsed() > 0));
        assertTrue(stats.toString().contains("timeEquityCorrelation="
                + String.format("%.4f", stats.getTimeEquityCorrelation())));
        assertUndefinedWealthMetrics(stats);
    }

    @Test
    void signedCollector_nonpositive_intraday_observation_invalidates_even_if_daily_close_recovers() {
        var stats = EquitySummaryStatistics.forSignedEquity(100, 0);
        stats.add(110, day(1));
        stats.add(0, day(1) + 1);
        stats.add(120, day(1) + 2);
        stats.add(130, day(2));
        assertEquals(130, stats.getEndingEquity());
        assertEquals(-100, stats.getMaxDrawdownPercent());
        assertUndefinedWealthMetrics(stats);
    }

    @Test
    void signedCollector_undefined_peak_percentage_remains_undefined_after_positive_peak_appears() {
        var stats = EquitySummaryStatistics.forSignedEquity(0, 0);
        assertTrue(Double.isNaN(stats.getMaxDrawdownPercent()));
        stats.add(-10, day(1));
        stats.add(20, day(2));
        stats.add(10, day(3));
        assertEquals(-10, stats.getTotalEquityLow());
        assertEquals(20, stats.getTotalEquityHigh());
        assertEquals(10, stats.getEndingEquity());
        assertEquals(-10, stats.getMaxDrawdown());
        assertEquals(-20.0 / 3, stats.getAverageDrawdown());
        assertTrue(Double.isNaN(stats.getMaxDrawdownPercent()));
        assertTrue(Double.isNaN(stats.getAverageDrawdownPercent()));
        assertEquals(-1, stats.getMaxDrawdownPercentTime());
        assertTrue(stats.toString().contains("maxDrawdownPercent=NaN"));
        assertUndefinedWealthMetrics(stats);
    }

    @Test
    void signedCollector_accepts_negative_initial_money_without_inventing_capital() {
        var stats = EquitySummaryStatistics.forSignedEquity(-20, 0);
        stats.add(-30, day(1));
        stats.add(-10, day(2));
        stats.add(20, day(3));
        assertEquals(-20, stats.getStartingEquity());
        assertEquals(-30, stats.getTotalEquityLow());
        assertEquals(20, stats.getEndingEquity());
        assertEquals(40, stats.getNetProfit());
        assertEquals(-10, stats.getMaxDrawdown());
        assertTrue(Double.isNaN(stats.getMaxDrawdownPercent()));
        assertUndefinedWealthMetrics(stats);
    }

    @Test
    void signedCollector_rejects_nonfinite_construction_arguments() {
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class,
                    () -> EquitySummaryStatistics.forSignedEquity(invalid, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> EquitySummaryStatistics.forSignedEquity(100, invalid));
        }
    }

    @Test
    void signedCollector_rejects_nonfinite_observations_before_mutating_statistics() {
        var stats = EquitySummaryStatistics.forSignedEquity(100, 0);
        var unchanged = new EquitySummaryStatistics(100, 0);
        stats.add(110, day(1));
        unchanged.add(110, day(1));
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> stats.add(invalid, day(20)));
            assertSameStatistics(unchanged, stats);
        }
        stats.add(120, day(2));
        unchanged.add(120, day(2));
        assertSameStatistics(unchanged, stats);
    }

    @Test
    void signedCollector_positive_observations_exactly_preserve_all_existing_statistics() {
        for (double[] values : new double[][] {{2, 4, 4, 3, 1, 2, 6, 5}, {100, 120, 115, 125, 117, 135}}) {
            var expected = new EquitySummaryStatistics(values[0], .01);
            var signed = EquitySummaryStatistics.forSignedEquity(values[0], .01);
            for (int i = 1; i < values.length; i++) {
                long time = day(i / 2 + 1) + (i % 2) * 3_600_000_000_000L;
                expected.add(values[i], time);
                signed.add(values[i], time);
                assertSameStatistics(expected, signed);
            }
        }
    }

    private static void assertUndefinedWealthMetrics(EquitySummaryStatistics stats) {
        assertAll(
                () -> assertTrue(Double.isNaN(stats.getLastDayReturn())),
                () -> assertTrue(Double.isNaN(stats.getAverageDailyReturn())),
                () -> assertTrue(Double.isNaN(stats.getCAGR())),
                () -> assertTrue(Double.isNaN(stats.getAnnualizedVolatility())),
                () -> assertTrue(Double.isNaN(stats.getCalmarRatio())),
                () -> assertTrue(Double.isNaN(stats.getAnnualSharpeRatio())),
                () -> assertTrue(Double.isNaN(stats.getAnnualSortinoRatio())),
                () -> assertTrue(Double.isNaN(stats.getTimeLogEquityCorrelation())));
    }

    private static void assertSameStatistics(EquitySummaryStatistics expected, EquitySummaryStatistics actual) {
        assertAll(
                () -> assertEquals(expected.getStartingEquity(), actual.getStartingEquity()),
                () -> assertEquals(expected.getEndingEquity(), actual.getEndingEquity()),
                () -> assertEquals(expected.getTotalEquityHigh(), actual.getTotalEquityHigh()),
                () -> assertEquals(expected.getTotalEquityLow(), actual.getTotalEquityLow()),
                () -> assertEquals(expected.getNetProfit(), actual.getNetProfit()),
                () -> assertEquals(expected.getMaxDrawdown(), actual.getMaxDrawdown()),
                () -> assertEquals(expected.getMaxDrawdownPercent(), actual.getMaxDrawdownPercent()),
                () -> assertEquals(expected.getAverageDrawdown(), actual.getAverageDrawdown()),
                () -> assertEquals(expected.getAverageDrawdownPercent(), actual.getAverageDrawdownPercent()),
                () -> assertEquals(expected.getLongestDrawdownDuration(), actual.getLongestDrawdownDuration()),
                () -> assertEquals(expected.getCurrentDrawdownStartTime(), actual.getCurrentDrawdownStartTime()),
                () -> assertEquals(expected.getTotalEquityHighTime(), actual.getTotalEquityHighTime()),
                () -> assertEquals(expected.getMaxDrawdownTime(), actual.getMaxDrawdownTime()),
                () -> assertEquals(expected.getMaxDrawdownPercentTime(), actual.getMaxDrawdownPercentTime()),
                () -> assertEquals(expected.getDataPoints(), actual.getDataPoints()),
                () -> assertEquals(expected.getStartTime(), actual.getStartTime()),
                () -> assertEquals(expected.getEndTime(), actual.getEndTime()),
                () -> assertEquals(expected.getYearsElapsed(), actual.getYearsElapsed()),
                () -> assertEquals(expected.getLastDayReturn(), actual.getLastDayReturn()),
                () -> assertEquals(expected.getAverageDailyReturn(), actual.getAverageDailyReturn()),
                () -> assertEquals(expected.getCAGR(), actual.getCAGR()),
                () -> assertEquals(expected.getAnnualizedVolatility(), actual.getAnnualizedVolatility()),
                () -> assertEquals(expected.getCalmarRatio(), actual.getCalmarRatio()),
                () -> assertEquals(expected.getAnnualSharpeRatio(), actual.getAnnualSharpeRatio()),
                () -> assertEquals(expected.getAnnualSortinoRatio(), actual.getAnnualSortinoRatio()),
                () -> assertEquals(expected.getTimeEquityCorrelation(), actual.getTimeEquityCorrelation()),
                () -> assertEquals(expected.getTimeLogEquityCorrelation(), actual.getTimeLogEquityCorrelation()),
                () -> assertEquals(expected.toString(), actual.toString()));
    }

    static EquitySummaryStatistics aStatistics(double startingBalance, double... values) {
        var stats = new EquitySummaryStatistics(startingBalance, 0.0);
        int dayNumber = 1;
        for (double value : values)
            stats.add(value, day(dayNumber++));

        return stats;
    }

    static long day(int n) {
        final long NANOS_PER_DAY = 86_400_000_000_000L;
        return n * NANOS_PER_DAY;
    }
}