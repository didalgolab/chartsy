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