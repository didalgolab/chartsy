/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.trade.account;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.trade.Direction;
import one.chartsy.trade.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.function.Consumer;

import static one.chartsy.time.Chronological.now;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccountBalanceEntryTest {
    static final double EPSILON = 1E-13;
    static final double INITIAL_BALANCE = 1000.0;
    static final SymbolIdentity SYMBOL = SymbolIdentity.of("USD");

    AccountBalanceEntry holding = new AccountBalanceEntry(INITIAL_BALANCE);

    @Test
    void once_constructed_contains_initial_balance() {
        final double INITIAL_BALANCE = 1000.0;
        var newlyCreated = new AccountBalanceEntry(INITIAL_BALANCE);

        assertEquals(INITIAL_BALANCE, newlyCreated.getStartingBalance());
        assertEquals(0, newlyCreated.getRealizedPnL());
        assertNull(newlyCreated.getPosition(SYMBOL), "Position is NULL");
    }

    @Test
    void onOrderFill_enters_long_when_bought_at_market() {
        holding.onOrderFill(aFill());

        assertNotNull(holding.getPosition(SYMBOL), "Position is NOT NULL");
        assertEquals(Direction.LONG, holding.getPosition(SYMBOL).getDirection());
        assertEquals(INITIAL_BALANCE, holding.getStartingBalance());
        assertEquals(0, holding.getRealizedPnL());
    }

    @Test
    void onOrderFill_enters_long_and_profits_when_bought_at_bullish_market() {
        holding.onOrderFill(aFill(p -> {
            p.tradePrice(100.0);
            p.tradeQuantity(2.0);
        }));
        holding.onBarEvent(aBar(102.0)); // +4 unrealized profit

        assertEquals(INITIAL_BALANCE, holding.getStartingBalance());
        assertEquals(0, holding.getRealizedPnL());
        assertEquals(4.0, holding.getUnrealizedPnL());
        var position = holding.getPosition(SYMBOL);
        assertEquals(100.0, position.getEntryPrice());
        assertEquals(100.0, position.getAveragePrice());
    }

    @Test
    void onOrderFill_realizes_profits_when_bought_at_bullish_market_then_sold() {
        holding.onOrderFill(aFill(p -> {
            p.tradePrice(100.0);
            p.tradeQuantity(2.0);
        }));
        holding.onBarEvent(aBar(102.0)); // +4 unrealized profit
        holding.onOrderFill(aFill(p -> {
            p.tradePrice(104.0);
            p.tradeQuantity(-2.0);
        })); // +8 realized profit

        assertEquals(INITIAL_BALANCE, holding.getStartingBalance());
        assertEquals(8.0, holding.getRealizedPnL());
        assertEquals(0.0, holding.getUnrealizedPnL());
        assertNull(holding.getPosition(SYMBOL), "Position is NULL");
    }

    @Test
    void onOrderFill_enters_long_and_losses_when_bought_at_bearish_market() {
        holding.onOrderFill(aFill(p -> {
            p.tradePrice(100.0);
            p.tradeQuantity(2.0);
        }));
        holding.onBarEvent(aBar(98.0)); // -4 unrealized loss

        assertEquals(-4.0, holding.getUnrealizedPnL());
    }

    @Test
    void onOrderFill_enters_long_and_scales_in_profits_when_bought_twice() {
        holding.onOrderFill(aFill(p -> {
            p.tradePrice(100.0);
            p.tradeQuantity(1.0);
        }));
        holding.onBarEvent(aBar(101.0)); // +1 unrealized profit
        holding.onOrderFill(aFill(p -> {
            p.tradePrice(102.0);
            p.tradeQuantity(2.0);
        })); // +2 unrealized profit and scale-in
        holding.onBarEvent(aBar(110.0)); //  +10 + 16 unrealized profit

        assertEquals(INITIAL_BALANCE, holding.getStartingBalance());
        assertEquals(0, holding.getRealizedPnL());
        assertEquals(26.0, holding.getUnrealizedPnL(), EPSILON);
    }

    @Test
    void onOrderFill_enters_long_and_scales_out_profits_when_bought_and_reduced() {
        holding.onOrderFill(aFill(p -> {
            p.tradePrice(100.0);
            p.tradeQuantity(2.0);
        }));
        holding.onBarEvent(aBar(101.0)); // +2 unrealized profit
        holding.onOrderFill(aFill(p -> {
            p.side(Order.Side.SELL);
            p.tradePrice(102.0);
            p.tradeQuantity(1.0);
        })); // +2 realized profit + 2 unrealized profit and scaled-out
        holding.onBarEvent(aBar(110.0)); //  +2 realized profit + 10 unrealized profit

        assertEquals(INITIAL_BALANCE, holding.getStartingBalance());
        assertEquals(2.0, holding.getRealizedPnL());
        assertEquals(10.0, holding.getUnrealizedPnL(), EPSILON);
    }

    @Test
    void onOrderFill_enters_long_and_reverses_when_bought_and_sold_more() {
        holding.onOrderFill(aFill(p -> {
            p.tradePrice(100.0);
            p.tradeQuantity(1.0);
        }));
        holding.onBarEvent(aBar(101.0)); // +1 unrealized profit
        holding.onOrderFill(aFill(p -> {
            p.side(Order.Side.SELL);
            p.tradePrice(102.0);
            p.tradeQuantity(3.0);
        })); // +2 realized profit and reverse with doubling the position
        holding.onBarEvent(aBar(112.0)); //  +2 realized profit - 20 loss

        assertEquals(2.0, holding.getRealizedPnL(), EPSILON);
        assertEquals(-20.0, holding.getUnrealizedPnL(), EPSILON);
    }

    @Test
    void onOrderFill_realizes_partial_profits_when_position_partially_closed() {
        holding.onOrderFill(aFill(p -> {
            p.tradePrice(100.0);
            p.tradeQuantity(10.0);
        }));
        holding.onBarEvent(aBar(110.0)); // +100 unrealized profit
        holding.onOrderFill(aFill(p -> {
            p.side(Order.Side.SELL);
            p.tradePrice(110.0);
            p.tradeQuantity(4.0);
        })); // +40 realized profit + 60 unrealized profit

        assertEquals(40.0, holding.getRealizedPnL(), EPSILON);
        assertEquals(60.0, holding.getUnrealizedPnL(), EPSILON);
        assertNotNull(holding.getPosition(SYMBOL), "Position still open with reduced quantity");
        assertEquals(6.0, holding.getPosition(SYMBOL).getQuantity(), EPSILON);
        assertEquals(100.0, holding.getPosition(SYMBOL).getAveragePrice(), EPSILON);
    }

    @Test
    void onOrderFill_enters_short_when_sold_short_at_market() {
        holding.onOrderFill(aFill(p -> p.side(Order.Side.SELL_SHORT)));

        assertNotNull(holding.getPosition(SYMBOL), "Position is NOT NULL");
        assertEquals(Direction.SHORT, holding.getPosition(SYMBOL).getDirection());
        assertEquals(INITIAL_BALANCE, holding.getStartingBalance());
        assertEquals(0, holding.getRealizedPnL());
    }

    @Test
    void onOrderFill_enters_short_and_profits_when_sold_short_at_bearish_market() {
        holding.onOrderFill(aFill(p -> {
            p.side(Order.Side.SELL_SHORT);
            p.tradePrice(100.0);
            p.tradeQuantity(2.0);
        }));
        holding.onBarEvent(aBar(98.0));

        assertEquals(INITIAL_BALANCE, holding.getStartingBalance());
        assertEquals(0, holding.getRealizedPnL());
        assertEquals(4.0, holding.getUnrealizedPnL());
        var position = holding.getPosition(SYMBOL);
        assertEquals(100.0, position.getEntryPrice());
        assertEquals(100.0, position.getAveragePrice());
    }

    @Test
    void onOrderFill_enters_short_and_scales_out_losses_when_sold_short_and_reduced() {
        holding.onOrderFill(aFill(p -> {
            p.side(Order.Side.SELL);
            p.tradePrice(100.0);
            p.tradeQuantity(2.0);
        }));
        holding.onBarEvent(aBar(101.0)); // -2 unrealized loss
        holding.onOrderFill(aFill(p -> {
            p.side(Order.Side.BUY);
            p.tradePrice(102.0);
            p.tradeQuantity(1.0);
        })); // -2 realized loss - 2 unrealized loss and scaled-out
        holding.onBarEvent(aBar(110.0)); // -2 realized loss - 10 unrealized loss

        assertEquals(INITIAL_BALANCE, holding.getStartingBalance());
        assertEquals(-2.0, holding.getRealizedPnL());
        assertEquals(-10.0, holding.getUnrealizedPnL(), EPSILON);
    }

    @Test
    void onOrderFill_realizes_profits_when_sold_short_at_bearish_market_then_bought_to_cover() {
        holding.onOrderFill(aFill(p -> {
            p.side(Order.Side.SELL_SHORT);
            p.tradePrice(100.0);
            p.tradeQuantity(2.0);
        }));
        holding.onBarEvent(aBar(98.0));
        holding.onOrderFill(aFill(p -> {
            p.side(Order.Side.BUY_TO_COVER);
            p.tradePrice(96.0);
            p.tradeQuantity(2.0);
        }));

        assertEquals(INITIAL_BALANCE, holding.getStartingBalance());
        assertEquals(8.0, holding.getRealizedPnL());
        assertEquals(0.0, holding.getUnrealizedPnL());
        assertNull(holding.getPosition(SYMBOL), "Position is NULL");
    }

    @Test
    void onOrderFill_enters_short_and_reverses_when_sold_short_then_bought_more_on_bullish_market() {
        holding.onOrderFill(aFill(p -> {
            p.side(Order.Side.SELL);
            p.tradePrice(100.0);
            p.tradeQuantity(1.0);
        }));
        holding.onBarEvent(aBar(101.0)); // -1 unrealized loss
        holding.onOrderFill(aFill(p -> {
            p.side(Order.Side.BUY);
            p.tradePrice(102.0);
            p.tradeQuantity(3.0);
        })); // -2 realized loss
        holding.onBarEvent(aBar(112.0)); // -2 realized loss + 20 unrealized profit

        assertEquals(-2.0, holding.getRealizedPnL(), EPSILON);
        assertEquals(20.0, holding.getUnrealizedPnL(), EPSILON);
    }

    @Test
    void onOrderFill_enters_short_and_reverses_when_sold_then_bought_more_on_bearish_market() {
        holding.onOrderFill(aFill(p -> {
            p.side(Order.Side.SELL);
            p.tradePrice(100.0);
            p.tradeQuantity(1.0);
        }));
        holding.onBarEvent(aBar(99.0)); // +1 unrealized profit
        holding.onOrderFill(aFill(p -> {
            p.side(Order.Side.BUY);
            p.tradePrice(98.0);
            p.tradeQuantity(3.0);
        })); // +2 realized profit
        holding.onBarEvent(aBar(88.0)); // +2 realized profit -20 unrealized loss

        assertEquals(2.0, holding.getRealizedPnL(), EPSILON);
        assertEquals(-20.0, holding.getUnrealizedPnL(), EPSILON);
    }

    @ParameterizedTest(name = "{index} - Entering LONG from {0}")
    @CsvSource({
            "FLAT,     0.0,   100.0, 5.0, 100.0, 0.0, 1000.0",
            "LONG,     2.0,   100.0, 5.0, 100.0, 0.0, 1000.0",
            "SHORT,   -3.0,    98.0, 5.0, 100.0,  -6.0, 994.0"
    })
    void enterLong_fromVariousPositions(String currentPosition, double currentQuantity, double currentPrice,
                                        double newQuantity, double newPrice,
                                        double expectedRealizedProfit, double expectedBalanceAfter) {
        if (!"FLAT".equals(currentPosition)) {
            Direction direction = currentQuantity > 0 ? Direction.LONG : Direction.SHORT;
            holding.onOrderFill(aFill(p -> {
                p.side(direction.isLong() ? Order.Side.BUY : Order.Side.SELL_SHORT);
                p.tradePrice(currentPrice);
                p.tradeQuantity(Math.abs(currentQuantity));
            }));
        }

        holding.enterLong(SYMBOL, newQuantity, newPrice, now() + 1);

        var position = holding.getPosition(SYMBOL);
        assertNotNull(position, "Position should exist");
        assertEquals(Direction.LONG, position.getDirection());
        assertEquals(newQuantity, position.getQuantity(), EPSILON);
        assertEquals(newPrice, position.getAveragePrice(), EPSILON);
        assertEquals(expectedBalanceAfter, holding.getEquity(), EPSILON);
        assertEquals(0.0, holding.getUnrealizedPnL(), EPSILON);
        assertEquals(expectedRealizedProfit, holding.getRealizedPnL(), EPSILON);
    }

    @ParameterizedTest(name = "{index} - Entering SHORT from {0}")
    @CsvSource({
            "FLAT,     0.0,   100.0, 5.0, 100.0, 0.0, 1000.0",
            "SHORT,   -2.0,   100.0, 5.0, 100.0, 0.0, 1000.0",
            "LONG,     3.0,    98.0, 5.0, 100.0,  6.0, 1006.0"
    })
    void enterShort_fromVariousPositions(String currentPosition, double currentQuantity, double currentPrice,
                                         double newQuantity, double newPrice,
                                         double expectedRealizedProfit, double expectedBalanceAfter) {
        if (!"FLAT".equals(currentPosition)) {
            Direction direction = currentQuantity > 0 ? Direction.LONG : Direction.SHORT;
            holding.onOrderFill(aFill(p -> {
                p.side(direction.isLong() ? Order.Side.BUY : Order.Side.SELL_SHORT);
                p.tradePrice(currentPrice);
                p.tradeQuantity(Math.abs(currentQuantity));
            }));
        }

        holding.enterShort(SYMBOL, newQuantity, newPrice, now() + 1);

        var position = holding.getPosition(SYMBOL);
        assertNotNull(position, "Position should exist");
        assertEquals(Direction.SHORT, position.getDirection());
        assertEquals(newQuantity, position.getQuantity(), EPSILON);
        assertEquals(newPrice, position.getAveragePrice(), EPSILON);
        assertEquals(expectedBalanceAfter, holding.getEquity(), EPSILON);
        assertEquals(0.0, holding.getUnrealizedPnL(), EPSILON);
        assertEquals(expectedRealizedProfit, holding.getRealizedPnL(), EPSILON);
    }

    @ParameterizedTest(name = "{index} - Exiting {0} position results in realized profit: {4}")
    @CsvSource({
            "LONG,  5.0,  100.0,  110.0,  50.0, 1050.0",
            "LONG,  5.0,  100.0,   95.0, -25.0,  975.0",
            "SHORT, 5.0,  100.0,   90.0,  50.0, 1050.0",
            "SHORT, 5.0,  100.0,  105.0, -25.0,  975.0"
    })
    void exitPosition_realizesProfitCorrectly(String direction, double quantity, double entryPrice,
                                              double exitPrice, double expectedRealizedProfit,
                                              double expectedFinalBalance) {
        holding.onOrderFill(aFill(p -> {
            p.side(direction.equals("LONG") ? Order.Side.BUY : Order.Side.SELL_SHORT);
            p.tradePrice(entryPrice);
            p.tradeQuantity(quantity);
        }));

        holding.exitPosition(SYMBOL, exitPrice, now() + 1);

        assertNull(holding.getPosition(SYMBOL), "Position should be fully exited");
        assertEquals(expectedFinalBalance, holding.getEquity(), EPSILON);
        assertEquals(0.0, holding.getUnrealizedPnL(), EPSILON);
        assertEquals(expectedRealizedProfit, holding.getRealizedPnL(), EPSILON);
    }
    
    static TradeExecutionEvent aFill() {
        return aFill(__ -> {});
    }

    static TradeExecutionEvent aFill(Consumer<ImmutableTradeExecutionEvent.Builder> customizer) {
        var builder = TradeExecutionEvent.builder()
                .time(now())
                .symbol(SYMBOL)
                .side(Order.Side.BUY)
                .tradePrice(1.0)
                .tradeQuantity(1.0);
        customizer.accept(builder);
        return builder.build();
    }

    static TradeBar aBar(double price) {
        return new TradeBar.Of(
                SYMBOL,
                Candle.of(now(), price)
        );
    }
}