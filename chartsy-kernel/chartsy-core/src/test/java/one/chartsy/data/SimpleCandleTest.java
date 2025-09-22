/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimpleCandleTest {
    final SimpleCandle bullish = SimpleCandle.of(0L, 1, 3, 0, 2, 100);
    final SimpleCandle bearish = SimpleCandle.of(0L, 2, 3, 0, 1, 100);
    final SimpleCandle neutral = SimpleCandle.of(0L, 3, 3, 0, 3, 100);
    final List<SimpleCandle> trendingCandles = List.of(bullish, bearish, neutral);

    @Test void provides_valid_trending_information() {
        for (SimpleCandle c : trendingCandles) {
            assertEquals((c.close() > c.open()), c.isBullish(), c + ".isBullish");
            assertEquals((c.open() > c.close()), c.isBearish(), c + ".isBearish");
            assertEquals((c.open() == c.close()), c.isDoji(), c + ".isDoji");
        }
    }
}