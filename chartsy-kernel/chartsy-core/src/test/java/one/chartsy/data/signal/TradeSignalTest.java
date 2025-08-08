package one.chartsy.data.signal;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class TradeSignalTest {

    @Test
    void goLong_gives_expectedTradeSignal() {
        var now = LocalDateTime.now();
        var ts = TradeSignal.goLong(100.0, 90.0, 110.0, now);
        assertEquals(TradeSignal.Side.LONG, ts.side());
        assertTrue(ts.isLong());
        assertFalse(ts.isShort());
        assertEquals(100.0, ts.entryPrice());
        assertEquals(90.0, ts.stopLoss());
        assertEquals(110.0, ts.takeProfit());
        assertEquals(now, ts.time());
        assertTrue(ts.hasTakeProfit());
    }

    @Test
    void goShort_gives_expectedTradeSignal() {
        var now = LocalDateTime.now();
        var ts = TradeSignal.goShort(100.0, 110.0, 90.0, now);
        assertEquals(TradeSignal.Side.SHORT, ts.side());
        assertTrue(ts.isShort());
        assertFalse(ts.isLong());
        assertEquals(100.0, ts.entryPrice());
        assertEquals(110.0, ts.stopLoss());
        assertEquals(90.0, ts.takeProfit());
        assertEquals(now, ts.time());
        assertTrue(ts.hasTakeProfit());
    }

    @Test
    void goLong_withoutTakeProfit_does_notHaveTakeProfit() {
        var now = LocalDateTime.now();
        var ts = TradeSignal.goLong(100.0, 90.0, now);
        assertFalse(ts.hasTakeProfit());
    }

    @Test
    void goLongOrShort_withInvalidPrices_throwsException() {
        var now = LocalDateTime.now();
        assertThrows(IllegalArgumentException.class,
            () -> TradeSignal.goLong(-1, -2, -3, now));
        assertThrows(IllegalArgumentException.class,
            () -> TradeSignal.goShort(0, 0, 0, now));
    }

    @Test
    void goLong_withNullTime_throwsException() {
        assertThrows(NullPointerException.class,
            () -> TradeSignal.goLong(1.0, 0.5, 1.5, null));
    }
}
