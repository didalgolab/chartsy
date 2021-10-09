package one.chartsy;

import one.chartsy.data.SimpleCandle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CandleTest {

    @Test void factory_methods_give_SimpleCandle_instances() {
        assertInstanceOf(SimpleCandle.class, Candle.of(0L, 1));
        assertInstanceOf(SimpleCandle.class, Candle.of(0L, 1, 3, 0, 2));
        assertInstanceOf(SimpleCandle.class, Candle.of(0L, 1, 3, 0, 2, 100));
        assertInstanceOf(SimpleCandle.class, Candle.of(0L, 1, 3, 0, 2, 100, 10));
    }
}