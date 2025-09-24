/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.market.aggregation;

import one.chartsy.Candle;
import one.chartsy.data.ReversalAmount;
import one.chartsy.data.SimpleCandle;
import one.chartsy.data.SimpleCandleBuilder;
import one.chartsy.data.market.SimpleTick;
import one.chartsy.data.market.Tick;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static one.chartsy.data.market.aggregation.Kagi.Trend.DOWN;
import static one.chartsy.data.market.aggregation.Kagi.Trend.UP;
import static org.junit.jupiter.api.Assertions.*;

class KagiIndicatorTest {

    KagiIndicator initial = new KagiIndicator(ReversalAmount.of(10)), kagi = initial;
    static long candleTime;

    @Test
    void getReversalAmount() {
        var reversalAmount = ReversalAmount.ofPercentage(10);
        var kagi = new KagiIndicator(reversalAmount);

        assertSame(reversalAmount, kagi.getReversalAmount());
    }

    @Test
    void is_initially_empty() {
        assertFalse(initial.isPresent());
        assertEquals((0), initial.length());
        assertEquals(Kagi.Trend.UNSPECIFIED, initial.getKagiDirection());
        assertThrows(IndexOutOfBoundsException.class, () -> initial.get(0));
        assertNull(initial.get());
    }

    @Test
    void remains_at_UNSPECIFIED_trend_after_first_few_candles() {
        Candle c1,c2;
        kagi.onCandle(c1 = c(1));
        kagi.onCandle(c2 = c(2));

        assertTrue(kagi.isPresent());
        assertEquals((0), kagi.length());
        assertEquals(Kagi.Trend.UNSPECIFIED, kagi.getKagiDirection());
        var kagiCandle = kagi.get();
        assertEquals(Candle.of(c2.time(), c1.open(), c2.high(), c1.low(), c2.close()), kagiCandle.baseCandle());
    }

    @Test
    void creates_Kagi_candle_after_sustained_price_move_1TO21() {
        Candle first,last;
        // first (and only) kagi candle
        kagi.onCandle( first=c(1) );
        kagi.onCandle( c(2) );
        kagi.onCandle( c(5) );
        kagi.onCandle( c(10) );
        kagi.onCandle( c(20) );
        kagi.onCandle( c(19) );
        kagi.onCandle( last=c(21) );

        assertTrue(kagi.isPresent());
        assertEquals((1), kagi.length());
        assertEquals(UP, kagi.getKagiDirection());
        var kagiCandle = kagi.get(0);
        assertTrue(kagiCandle.isBullish());
        assertEquals(Candle.of(last.time(), first.open(), last.high(), first.low(), last.close()), kagiCandle.baseCandle());
        assertEquals(last.time(), kagiCandle.time());
        assertEquals(first.open(), kagiCandle.open());
        assertEquals(last.close(), kagiCandle.close());
        assertEquals(last.high(), kagiCandle.high());
        assertEquals(first.low(), kagiCandle.low());
        assertEquals(UP, kagiCandle.trend());
        assertEquals((7), kagiCandle.formedElementsCount());
    }

    @Test
    void reverses_Kagi_candle_after_sustained_price_move() {
        Candle c12,c11;

        // first Kagi candle
        creates_Kagi_candle_after_sustained_price_move_1TO21();
        kagi.onCandle( c(21) );
        // second Kagi handle
        kagi.onCandle( c(12) );
        assertEquals(1, kagi.length(), "just before Kagi reversal trigger");
        kagi.onCandle( c(11) );
        assertEquals(2, kagi.length(), "just after Kagi reversal trigger");

        // verify
        var kagi1 = kagi.get(1);
        assertTrue(kagi1.isBullish());
        assertEquals(UP, kagi1.trend());
        assertEquals(21, kagi1.close());
        var kagi0 = kagi.get(0);
        assertTrue(kagi0.isBearish()); // is kagi rising?
        assertEquals(UP, kagi0.trend()); // is kagi yang line?
        assertEquals(11, kagi0.close());
        //assertEquals(21, kagi0.yangLevel());
        assertEquals(1, kagi0.yinLevel());
        assertEquals((1 + 1), kagi0.formedElementsCount(), "two candles from highest high");

        assertTrue(kagi.isPresent());
        assertEquals(Kagi.Trend.DOWN, kagi.getKagiDirection());
    }

    @Test
    void creates_multiple_Kagi_Candles_after_each_reversal() {
        Candle first,last;
        // first kagi candle
        kagi.onCandle( first=c(1) );
        kagi.onCandle( c(31) );
        // second kagi handle
        kagi.onCandle( c(0) );
        // third kagi handle
        kagi.onCandle( c(9) );
        kagi.onCandle( c(11) );
        kagi.onCandle( last=c(30) );

        assertEquals(3, kagi.length());
        assertEquals(UP, kagi.getKagiDirection());
        var kagi0 = kagi.get(0);
        assertTrue(kagi0.isBullish());
        assertEquals(last.close(), kagi0.close());
        assertEquals(0, kagi0.open());
        assertEquals(1, kagi0.yinLevel());
        assertEquals(Kagi.Trend.DOWN, kagi0.trend()); // yin still
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reverses_Kagi_on_High_to_Low_swing_between_candles(boolean inverted) {
        Candle c1,c2,c3,c4;
        // given
        kagi.onCandle( c1=c(inverted, """
                {"2022-04-01": {OHLC:[11,19,10,19], V:1}}""") );
        kagi.onCandle( c2=c(inverted, """
                {"2022-04-02": {OHLC:[21,31,20,30], V:2}}""") );
        kagi.onCandle( c3=c(inverted, """
                {"2022-04-03": {OHLC:[30,39,21,39], V:3}}""") );
        kagi.onCandle( c4=c(inverted, """
                {"2022-04-04": {OHLC:[40,49,40,49], V:4}}""") );

        // verify
        assertEquals(3, kagi.length());

        var kagi2 = kagi.get(2);
        assertEquals(c(c1, c2), kagi2.baseCandle());
        assertEquals(c(c1, c2).low(), kagi2.low());
        assertEquals(c(c1, c2).high(), kagi2.high());
        assertEquals(!inverted, kagi2.isBullish(), "kagi[2].bullish");

        var kagi1 = kagi.get(1);
        assertEquals(c3, kagi1.baseCandle());
        if (!inverted) {
            assertEquals(c2.high(), kagi1.high());
            assertEquals(c3.low(), kagi1.low());
        } else {
            assertEquals(c2.low(), kagi1.low());
            assertEquals(c3.high(), kagi1.high());
        }
        assertEquals( !inverted, kagi1.isBearish(), "kagi[1].bearish");

        var kagi0 = kagi.get(0);
        assertEquals(c4, kagi0.baseCandle());
        assertEquals(c(c3, c4).low(), kagi0.low());
        assertEquals(c(c3, c4).high(), kagi0.high());
        assertEquals( !inverted, kagi0.isBullish(), "kagi[0].bullish");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reverses_Kagi_on_High_to_Low_swing_with_highest_high_reversal_candle(boolean inverted) {
        Candle c1,c2,c3,c4;
        // given
        kagi.onCandle( c1=c(inverted, """
                {"2022-04-01": {OHLC:[11,19,10,19], V:1}}""") );
        kagi.onCandle( c2=c(inverted, """
                {"2022-04-02": {OHLC:[21,31,20,30], V:2}}""") );
        kagi.onCandle( c3=c(inverted, """
                {"2022-04-03": {OHLC:[30,39,21,29], V:3}}""") );
        kagi.onCandle( c4=c(inverted, """
                {"2022-04-04": {OHLC:[40,49,40,49], V:4}}""") );

        // verify
        assertEquals(3, kagi.length());

        var kagi2 = kagi.get(2);
        assertEquals(c(c1, c2, c3), kagi2.baseCandle());
        assertEquals(c(c1, c2, c3).low(), kagi2.low());
        assertEquals(c(c1, c2, c3).high(), kagi2.high());
        assertEquals(!inverted, kagi2.isBullish(), "kagi[2].bullish");

        var kagi1 = kagi.get(1);
        assertEquals(c3, kagi1.baseCandle());
        if (!inverted) {
            assertEquals(c(c2, c3).high(), kagi1.high());
            assertEquals(c3.low(), kagi1.low());
        } else {
            assertEquals(c(c2, c3).low(), kagi1.low());
            assertEquals(c3.high(), kagi1.high());
        }
        assertEquals( !inverted, kagi1.isBearish(), "kagi[1].bearish");

        var kagi0 = kagi.get(0);
        assertEquals(c4, kagi0.baseCandle());
        assertEquals(c(c3, c4).low(), kagi0.low());
        assertEquals(c(c3, c4).high(), kagi0.high());
        assertEquals( !inverted, kagi0.isBullish(), "kagi[0].bullish");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reverses_Kagi_on_High_to_Close_same_candle_swing(boolean inverted) {
        Candle c1,c2,c3;
        // given
        kagi.onCandle( c1=c(inverted, """
                {"2022-04-01": {OHLC:[11,19,10,19], V:1}}""") );
        kagi.onCandle( c2=c(inverted, """
                {"2022-04-02": {OHLC:[21,29,20,28], V:2}}""") );
        kagi.onCandle( c3=c(inverted, """
                {"2022-04-03": {OHLC:[32,39,29,29], V:3}}""") );

        // verify
        assertEquals(2, kagi.length());

        var kagi1 = kagi.get(1);
        assertEquals(c(c1, c2, c3), kagi1.baseCandle());
        assertEquals(c(c1, c2, c3).low(), kagi1.low());
        assertEquals(c(c1, c2, c3).high(), kagi1.high());
        assertEquals( !inverted, kagi1.isBullish(), "kagi[1].bullish");

        var kagi0 = kagi.get(0);
        assertEquals(c3, kagi0.baseCandle());
        assertEquals(c3.low(), kagi0.low());
        assertEquals(c3.high(), kagi0.high());
        assertEquals( !inverted, kagi0.isBearish(), "kagi[0].bullish");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void supports_aggregation_from_Ticks_11_TO30_THEN19(boolean inverted) {
        Tick t1,t2,t3,t4,t5,t6;
        // given
        kagi.onTick( t1=t(inverted, """
                {"2022-04-01T00:00": {P:11, V:1}}""") );
        kagi.onTick( t2=t(inverted, """
                {"2022-04-02T00:00": {P:10, V:2}}""") );
        kagi.onTick( t3=t(inverted, """
                {"2022-04-03T00:00": {P:30, V:3}}""") );
        kagi.onTick( t4=t(inverted, """
                {"2022-04-04T00:00": {P:20, V:4}}""") );
        kagi.onTick( t5=t(inverted, """
                {"2022-04-05T00:00": {P:21, V:5}}""") );
        kagi.onTick( t6=t(inverted, """
                {"2022-04-06T00:00": {P:19, V:6}}""") );

        // verify
        assertEquals(2, kagi.length());

        var kagi1 = kagi.get(1);
        assertEquals(t(t1, t2, t3), kagi1.baseCandle());
        assertEquals(t(t1, t2, t3).low(), kagi1.low());
        assertEquals(t(t1, t2, t3).high(), kagi1.high());
        assertEquals((3), kagi1.formedElementsCount());
        assertEquals( !inverted, kagi1.isBullish(), "kagi[1].bullish");
        assertEquals( !inverted? UP : DOWN, kagi1.trend(), "kagi[1].trend");

        var kagi0 = kagi.get(0);
        assertEquals(t(t4, t5, t6), kagi0.baseCandle());
        assertEquals(t(t3, t4, t5, t6).low(), kagi0.low());
        assertEquals(t(t3, t4, t5, t6).high(), kagi0.high());
        assertEquals((3), kagi0.formedElementsCount());
        assertEquals(t2.price(), !inverted? kagi0.yinLevel() : kagi0.yangLevel());
        assertEquals( !inverted, kagi0.isBearish(), "kagi[0].bullish");
        assertEquals( !inverted? UP : DOWN, kagi0.trend(), "kagi[0].trend");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reverses_Kagi_trend_on_downward_swing_aggregated_from_Ticks(boolean inverted) {
        // given
        Tick t7;
        supports_aggregation_from_Ticks_11_TO30_THEN19(inverted);
        kagi.onTick( t7=t(inverted, """
                {"2022-04-07T00:00": {P:0.01, V:7}}""") );

        // verify
        var kagi0 = kagi.get(0);
        assertEquals( !inverted? DOWN : UP, kagi0.trend(), "kagi[0].trend");
        assertEquals(t7.price(), kagi0.close());
    }

    static Candle c(double price) {
        return Candle.of(++candleTime, price);
    }

    static Candle c(String ohlc) {
        return SimpleCandle.JsonFormat.fromJson(ohlc);
    }

    static Candle c(boolean inverted, String ohlc) {
        Candle c = SimpleCandle.JsonFormat.fromJson(ohlc);
        if (inverted)
            c = Candle.of(c.time(), -c.open(), -c.low(), -c.high(), -c.close(), c.volume());
        return c;
    }

    static Candle c(Candle... series) {
        SimpleCandleBuilder cb = new SimpleCandleBuilder();
        for (Candle c : series)
            cb.addCandle(c);
        return cb.get();
    }

    static Candle t(Tick... series) {
        SimpleCandleBuilder cb = new SimpleCandleBuilder();
        for (Tick t : series)
            cb.addTick(t);
        return cb.get();
    }

    static Tick t(boolean inverted, String json) {
        Tick t = SimpleTick.JsonFormat.fromJson(json, SimpleTick.class);
        if (inverted)
            t = Tick.of(t.time(), -t.price(), t.size());
        return t;
    }
}