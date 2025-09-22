/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy;

import one.chartsy.core.json.JsonParseException;
import one.chartsy.data.SimpleCandle;
import one.chartsy.time.Chronological;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.UnaryOperator;

import static java.time.LocalDate.parse;
import static one.chartsy.time.Chronological.toEpochNanos;
import static org.junit.jupiter.api.Assertions.*;

class CandleTest {

    @Test void factory_methods_give_SimpleCandle_instances() {
        assertInstanceOf(SimpleCandle.class, Candle.of(0L, 1));
        assertInstanceOf(SimpleCandle.class, Candle.of(0L, 1, 3, 0, 2));
        assertInstanceOf(SimpleCandle.class, Candle.of(0L, 1, 3, 0, 2, 100));
    }

    static final long time = toEpochNanos(parse("2001-01-01").atStartOfDay());

    @Test
    void toString_gives_Json_representation_of_a_Candle() {
        assertEquals("{\"2001-01-01\": {OHLC:[2.2]}}",
                Candle.of(time, 2.2).toString(), "Doji Candle");
        assertEquals("{\"2001-01-01\": {OHLC:[2.2, 3.3]}}",
                Candle.of(time, 2.2, 3.3, 2.2, 3.3).toString(), "Candle without wicks");
        assertEquals("{\"2001-01-01\": {OHLC:[2.2, 3.3, 2.0, 3.0]}}",
                Candle.of(time, 2.2, 3.3, 2.0, 3.0).toString(), "Candle with wicks");
        assertEquals("{\"2001-01-01\": {OHLC:[0.0], V:5.5}}",
                Candle.of(time, 0, 0, 0, 0, 5.5).toString(), "Candle with volume");
    }

    @Test
    void toString_giving_Json_representation_shortens_time_as_much_as_possible() {
        assertEquals("{\"2001-01-01\": {OHLC:[0.0]}}",
                Candle.of(time, 0.).toString(), "Daily Candle");
        assertEquals("{\"2001-01-01 01:00\": {OHLC:[0.0]}}",
                Candle.of(time(t -> t.plusHours(1)), 0.).toString(), "Hourly Candle");
        assertEquals("{\"2001-01-01 00:01\": {OHLC:[0.0]}}",
                Candle.of(time(t -> t.plusMinutes(1)), 0.).toString(), "Minute Candle");
        assertEquals("{\"2001-01-01 00:00:01\": {OHLC:[0.0]}}",
                Candle.of(time(t -> t.plusSeconds(1)), 0.).toString(), "Second Candle");
        assertEquals("{\"2001-01-01 00:00:00.001\": {OHLC:[0.0]}}",
                Candle.of(time(t -> t.plusNanos(1000_000)), 0.).toString(), "Millisecond Candle");
        assertEquals("{\"2001-01-01 00:00:00.000001\": {OHLC:[0.0]}}",
                Candle.of(time(t -> t.plusNanos(1000)), 0.).toString(), "Microsecond Candle");
    }

    private static long time(UnaryOperator<LocalDateTime> op) {
        LocalDateTime dateTime = op.apply(Chronological.toDateTime(time));
        return toEpochNanos(dateTime);
    }

    private static Candle randCandle() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long time = r.nextLong()/1000*1000;
        double o = r.nextDouble(Integer.MIN_VALUE, Integer.MAX_VALUE);
        double h = r.nextDouble(Integer.MIN_VALUE, Integer.MAX_VALUE);
        double l = r.nextDouble(Integer.MIN_VALUE, Integer.MAX_VALUE);
        double c = r.nextDouble(Integer.MIN_VALUE, Integer.MAX_VALUE);
        double v = r.nextDouble(Integer.MIN_VALUE, Integer.MAX_VALUE);
        if (r.nextInt(4) == 0)
            o = h = l = c;
        else if (r.nextInt(4) == 1) {
            o = h;
            c = l;
        }
        return Candle.of(time, o, h, l, c, v);
    }

    @Nested
    class JsonFormatTest {
        @RepeatedTest(10)
        void fromJson_gives_Candle_equal_to_its_Json_representation() {
            Candle originalCandle = randCandle();
            String json = originalCandle.toString();

            SimpleCandle candleFromJson = SimpleCandle.JsonFormat.fromJson(json);
            assertEquals(originalCandle, candleFromJson, "from Json: " + json);
        }

        @ParameterizedTest
        @CsvSource(value = {
                "{\"2001-01-01\": {OHLC:[1.0, 2.0, 3.0]}} //Json with 3-element OHLC array",
                "{\"2001-01-01\": {OHLC:[]}}             //Json with 0-element OHLC array",
                "{\"2001-01-01\": {}}                   //Json without OHLC array",
        }, delimiterString = "//")
        void fromJson_throws_JsonParseException_on_invalid_json(String invalidJson, String comment) {
            assertThrows(JsonParseException.class,
                    () -> SimpleCandle.JsonFormat.fromJson(invalidJson), comment + ": " + invalidJson);
        }
    }
}