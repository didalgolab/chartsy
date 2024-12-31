/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data;

import one.chartsy.Candle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class SimpleCandleBuilderTest {

    @Test void isEmpty_when_newly_created() {
        SimpleCandleBuilder newlyCreated = SimpleCandleBuilder.create();

        assertFalse(newlyCreated.isPresent(), "isPresent");
        assertNull(newlyCreated.get(), "get");
    }

    @ParameterizedTest
    @MethodSource("builders")
    void get_gives_aggregated_Candle(SimpleCandleBuilder builder) {
        builder.addCandle(Candle.of(1L, 1));
        builder.addCandle(Candle.of(2L, 2));
        builder.addCandle(Candle.of(3L, 3));

        assertTrue(builder.isPresent(), "formed candle isPresent");
        assertEquals(Candle.of(3L, 1, 3, 1, 3), builder.get(), "aggregated Candle");
    }

    @ParameterizedTest
    @MethodSource("builders")
    void put_discards_previously_aggregated_Candles(SimpleCandleBuilder builder) {
        get_gives_aggregated_Candle(builder); //merge some random Candles before actual put
        builder.putCandle(Candle.of(10L, 10));

        assertTrue(builder.isPresent(), "formed candle isPresent");
        assertEquals(Candle.of(10L, 10), builder.get(), "Candle after put()");
    }

    private static Stream<SimpleCandleBuilder> builders() {
        return Stream.of(SimpleCandleBuilder.create());
    }
}