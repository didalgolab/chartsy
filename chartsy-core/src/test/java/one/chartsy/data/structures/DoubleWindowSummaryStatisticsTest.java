/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.structures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;

class DoubleWindowSummaryStatisticsTest {

    @Test
    void constructor_throws_IAE_given_invalid_arguments() {
        assertThrows(IllegalArgumentException.class, () -> new DoubleWindowSummaryStatistics(0));
        assertThrows(IllegalArgumentException.class, () -> new DoubleWindowSummaryStatistics(-1));
    }

    @Test
    void newInstance_isEmpty() {
        var stats = new DoubleWindowSummaryStatistics(5);

        assertTrue(stats.isEmpty(), "New instance should be empty");
        assertFalse(stats.isFull(), "New instance shouldn't be full");
        assertEquals(0, stats.getCount());
        assertEquals(0, stats.getCountTotal());
        assertEquals(0.0, stats.getSum());
        assertEquals(Double.POSITIVE_INFINITY, stats.getMin());
        assertEquals(Double.NEGATIVE_INFINITY, stats.getMax());
        assertEquals(0.0, stats.getAverage());
        assertEquals(0.0, stats.getFirst());
        assertEquals(0.0, stats.getLast());
    }

    @Test
    void add_updates_statistics() {
        var stats = new DoubleWindowSummaryStatistics(3);
        stats.add(1.0);
        stats.add(2.0);
        stats.add(3.0);

        assertFalse(stats.isEmpty());
        assertTrue(stats.isFull());
        assertEquals(3, stats.getCount());
        assertEquals(3, stats.getCountTotal());
        assertEquals(6.0, stats.getSum());
        assertEquals(1.0, stats.getMin());
        assertEquals(3.0, stats.getMax());
        assertEquals(2.0, stats.getAverage());
        assertEquals(1.0, stats.getFirst());
        assertEquals(3.0, stats.getLast());
    }

    @Test
    void add_maintains_correct_statistics_when_added_more_values_than_window_size() {
        var stats = new DoubleWindowSummaryStatistics(3);
        stats.add(1.0);
        stats.add(2.0);
        stats.add(3.0);
        stats.add(4.0);
        stats.add(5.0);

        assertTrue(stats.isFull());
        assertEquals(3, stats.getCount());
        assertEquals(5, stats.getCountTotal());
        assertEquals(12.0, stats.getSum());
        assertEquals(3.0, stats.getMin());
        assertEquals(5.0, stats.getMax());
        assertEquals(4.0, stats.getAverage());
        assertEquals(3.0, stats.getFirst());
        assertEquals(5.0, stats.getLast());
    }

    @Test
    void add_returns_the_correct_removed_value() {
        var stats = new DoubleWindowSummaryStatistics(3);
        assertEquals(0.0, stats.add(1.0));
        assertEquals(0.0, stats.add(2.0));
        assertEquals(0.0, stats.add(3.0));
        assertEquals(1.0, stats.add(4.0)); // Window is full
        assertEquals(2.0, stats.add(5.0));
        assertEquals(3.0, stats.add(6.0));
    }

    @ParameterizedTest
    @MethodSource("provideEdgeCaseValues")
    @DisplayName("Edge case values should be handled correctly")
    void edgeCaseValuesShouldBeHandledCorrectly(double value, double expectedMin, double expectedMax) {
        var stats = new DoubleWindowSummaryStatistics(3);
        stats.add(value);

        assertEquals(value, stats.getMin());
        assertEquals(value, stats.getMax());
        assertEquals(value, stats.getSum());
        assertEquals(value, stats.getAverage());
    }

    private static Stream<Arguments> provideEdgeCaseValues() {
        return Stream.of(
                Arguments.of(Double.NaN, Double.NaN, Double.NaN),
                Arguments.of(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY),
                Arguments.of(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY),
                Arguments.of(0.0, 0.0, 0.0)
                //Arguments.of(-0.0, -0.0, -0.0) // Sign of zeros is not preserved
        );
    }

    @Test
    void add_provides_accurate_sum_using_Kahan_summation() {
        var stats = new DoubleWindowSummaryStatistics(1000);
        double expectedSum = 0.0;
        for (int i = 0; i < 1000; i++) {
            double value = 0.1;
            expectedSum += value;
            stats.add(value);
        }
        assertEquals(expectedSum, stats.getSum(), 1e-10);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 5, 10, 100})
    @DisplayName("Window size should be respected")
    void windowSizeShouldBeRespected(int windowSize) {
        var stats = new DoubleWindowSummaryStatistics(windowSize);
        for (int i = 0; i < windowSize * 2; i++) {
            stats.add(i);
        }
        assertEquals(windowSize, stats.getCount());
        assertEquals(windowSize * 2, stats.getCountTotal());
        assertEquals(windowSize, stats.getLast() - stats.getFirst() + 1, 1e-10);
    }

    @Test
    void toString_returns_string_representation() {
        var stats = new DoubleWindowSummaryStatistics(3);
        stats.add(1.0);
        stats.add(2.0);
        stats.add(3.0);

        assertThat(stats.toString())
                .contains("1,0", "2,0", "3,0"); // Min|Average|Max
    }

    @Test
    void getLastDifference_returns_correct_difference() {
        var stats = new DoubleWindowSummaryStatistics(3);

        // Test with fewer than 2 elements
        assertEquals(0.0, stats.getLastDifference());

        stats.add(1.0);
        assertEquals(1.0, stats.getLastDifference());

        // Test with 2 elements
        stats.add(3.0);
        assertEquals(2.0, stats.getLastDifference());

        // Test with full window
        stats.add(6.0);
        assertEquals(3.0, stats.getLastDifference());

        // Test when wrapping around the circular buffer
        stats.add(10.0);
        assertEquals(4.0, stats.getLastDifference());
    }

    @Test
    void get_retrieves_correct_past_values() {
        var stats = new DoubleWindowSummaryStatistics(3);
        stats.add(1.0);
        stats.add(2.0);
        stats.add(3.0);
        stats.add(4.0);
        stats.add(5.0);

        assertEquals(5.0, stats.get(0));
        assertEquals(4.0, stats.get(1));
        assertEquals(3.0, stats.get(2));
    }

    @Test
    void get_throws_exception_when_given_invalid_index() {
        var stats = new DoubleWindowSummaryStatistics(5);
        stats.add(1.0);
        stats.add(2.0);
        stats.add(3.0);

        assertThrows(IndexOutOfBoundsException.class, () -> stats.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> stats.get(3));
    }

    @Test
    void stressTestWithLargeRandomDataset() {
        int windowSize = 10;
        int dataSize = 10000;
        var random = ThreadLocalRandom.current();

        var stats = new DoubleWindowSummaryStatistics(windowSize);
        double[] data = DoubleStream.generate(random::nextDouble)
                .limit(dataSize)
                .toArray();

        for (int i = 0; i < dataSize; i++) {
            stats.add(data[i]);

            if (i >= windowSize) {
                var expectedStats = DoubleStream.of(data)
                        .skip(i - windowSize + 1)
                        .limit(windowSize)
                        .summaryStatistics();

                assertEquals(expectedStats.getCount(), stats.getCount());
                assertEquals(expectedStats.getSum(), stats.getSum());
                assertEquals(expectedStats.getMin(), stats.getMin());
                assertEquals(expectedStats.getMax(), stats.getMax());
                assertEquals(expectedStats.getAverage(), stats.getAverage());
            }
        }
    }
}