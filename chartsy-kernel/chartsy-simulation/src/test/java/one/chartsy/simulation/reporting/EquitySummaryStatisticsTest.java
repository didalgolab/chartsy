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
                () -> assertEquals(3.0, stats.getMaxDrawdown()),
                () -> assertEquals(0.75, stats.getMaxDrawdownPercent()),
                () -> assertEquals(1.0, stats.getAvgDrawdown()),
                () -> assertEquals(0.2380952380952381, stats.getAvgDrawdownPercent()),
                () -> assertEquals(day(6)-day(3), stats.getLongestDrawdownDuration()),
                () -> assertEquals(day(6), stats.getCurrentDrawdownStartTime()),
                () -> assertEquals(day(6), stats.getTotalEquityHighTime()),
                () -> assertEquals(day(4), stats.getMaxDrawdownTime()),
                () -> assertEquals(day(4), stats.getMaxDrawdownPercentTime()),
                () -> assertEquals(7, stats.getDataPoints()),
                () -> assertFalse(stats.toString().isBlank())
        );
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