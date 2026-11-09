package one.chartsy.simulation.engine;

import one.chartsy.SymbolIdentity;
import one.chartsy.data.market.BestBidOfferQuote;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageSource;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.messaging.data.BestBidOfferEvent;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.simulation.engine.price.PlaybackPriceHandle;
import one.chartsy.time.Chronological;
import one.chartsy.trade.ExitRules;
import one.chartsy.trade.Order;
import one.chartsy.trade.PositionTarget;
import one.chartsy.trade.account.ProfitConversion;
import one.chartsy.trade.algorithm.AbstractAlgorithm;
import one.chartsy.trade.algorithm.AlgorithmContext;
import one.chartsy.trade.algorithm.data.InstrumentDataFactory;
import one.chartsy.trade.algorithm.data.SimpleInstrumentPrices;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static one.chartsy.simulation.engine.BacktestExecutionOptions.CapitalMode.*;
import static one.chartsy.simulation.engine.BacktestExecutionOptions.EndOfDataPolicy.*;
import static one.chartsy.simulation.engine.BacktestExecutionOptions.ExecutionMode.*;
import static one.chartsy.trade.PositionTarget.StopUpdatePolicy.*;
import static org.junit.jupiter.api.Assertions.*;

class QuoteExecutionTest {
    static final SymbolIdentity SYMBOL = SymbolIdentity.of("EURUSD");
    static final double EPS = 1e-8;
    static final QuoteSession DAILY = QuoteSession.daily(ZoneOffset.UTC);

    @Test void delayed_first_quote_is_the_actual_opening_and_execution_time() {
        var quotes = List.of(q(1, 1, 1.1, 1.1004), q(1, 2, 1.102, 1.1024));
        var result = run(quotes, nativeOptions(), (a, e) -> {
            if (e instanceof BarOpen opening) {
                assertEquals(q(1, 0, 1, 1).time(), opening.barId());
                a.target(Order.Side.BUY, .01);
            }
        });
        assertEquals(quotes.getFirst().time(), result.executions().getFirst().fill().time());
        assertEquals(10016, result.finalEquity(), EPS);
    }

    @Test void native_long_fills_first_crossing_bid_at_its_time_and_does_not_replay_OHLC_stop() {
        var quotes = List.of(q(1, 0, 1.1, 1.1004), q(1, 1, 1.095, 1.0957),
                q(1, 2, 1.089, 1.09), q(1, 3, 1.105, 1.107));
        var result = run(quotes, nativeOptions(), (a, e) -> {
            if (e instanceof BarOpen) a.target(Order.Side.BUY, .01);
        });
        assertEquals(2, result.executions().size());
        var stop = result.executions().getLast();
        assertEquals(TradingSimulator.Reason.STOP, stop.reason());
        assertEquals(quotes.get(2).time(), stop.fill().time());
        assertEquals(1.089, stop.fill().tradePrice());
        assertEquals(-114, stop.realizedPnl(), EPS);
        assertEquals(9886, result.finalEquity(), EPS);
        assertEquals(1, result.equityCurve().size(), "quote events do not add daily equity samples");
        assertTrue(result.openPositions().isEmpty());
    }

    @Test void native_short_stops_on_ask_even_when_bid_never_crosses_stop() {
        var result = run(List.of(q(1, 0, 1.1, 1.1004), q(1, 1, 1.099, 1.112)), nativeOptions(),
                (a, e) -> { if (e instanceof BarOpen) a.target(Order.Side.SELL_SHORT, .01); });
        assertEquals(1.112, result.executions().getLast().fill().tradePrice());
        assertEquals(-120, result.realizedPnl(), EPS);
        assertEquals(9880, result.finalEquity(), EPS);
    }

    @Test void fixed_quote_mode_uses_declared_spread_instead_of_native_ask() {
        var quotes = List.of(q(1, 0, 1.1, 1.1004), q(1, 1, 1.099, 1.112));
        var result = run(quotes, options(TICK_FIXED_SPREAD, KEEP_OPEN, INDEPENDENT_ACCOUNTS, ProfitConversion.QUOTE_CURRENCY),
                (a, e) -> { if (e instanceof BarOpen) a.target(Order.Side.SELL_SHORT, .01); });
        assertEquals(1, result.executions().size());
        assertEquals(1.0993, result.openPositions().getFirst().marketPrice(), 1e-12);
        assertEquals(10007, result.finalEquity(), EPS);
    }

    @Test void final_valuation_and_liquidation_use_last_native_side_without_an_extra_spread() {
        for (var side : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
            var quotes = List.of(q(1, 0, 1.1, 1.1004), q(1, 1, 1.105, 1.1058));
            double expected = side.isBuy() ? 46 : -58;
            var held = run(quotes, nativeOptions(), (a, e) -> { if (e instanceof BarOpen) a.target(side, .01); });
            var closed = run(quotes, options(TICK_NATIVE_SPREAD, LIQUIDATE, INDEPENDENT_ACCOUNTS, ProfitConversion.QUOTE_CURRENCY),
                    (a, e) -> { if (e instanceof BarOpen) a.target(side, .01); });
            assertEquals(expected, held.unrealizedPnl(), EPS);
            assertEquals(quotes.getLast().time(), held.openPositions().getFirst().marketTime());
            assertEquals(expected, closed.realizedPnl(), EPS);
            assertEquals(held.finalEquity(), closed.finalEquity(), EPS);
            assertEquals(1, closed.equityCurve().size());
            assertEquals(TradingSimulator.Reason.END_OF_DATA, closed.executions().getLast().reason());
        }
    }

    @Test void reverse_settles_at_current_bid_before_sizing_the_new_short() {
        var result = run(List.of(q(1, 0, 1.1, 1.1004), q(1, 1, 1.102, 1.1028),
                        q(2, 0, 1.106, 1.107), q(2, 1, 1.103, 1.104)), nativeOptions(),
                (a, e) -> { if (e instanceof BarOpen) a.target(a.opens == 1 ? Order.Side.BUY : Order.Side.SELL_SHORT, a.opens == 1 ? .01 : .02); });
        assertEquals(3, result.executions().size());
        assertEquals(56, result.realizedPnl(), EPS);
        assertEquals(10056, result.targetDecisions().getLast().equity(), EPS);
        assertEquals(5028, result.openPositions().getFirst().quantity(), EPS);
        assertEquals(10.056, result.unrealizedPnl(), EPS);
        assertEquals(10066.056, result.finalEquity(), EPS);
    }

    @Test void neutral_keeps_the_owned_stop_and_same_side_reset_widens_without_resizing() {
        var result = run(List.of(q(1, 0, 1.1, 1.1004), q(2, 0, 1.104, 1.105),
                        q(3, 0, 1.104, 1.106), q(3, 1, 1.095, 1.0956)), nativeOptions(), (a, e) -> {
            if (e instanceof BarOpen && a.opens != 2) a.target(Order.Side.BUY, a.opens == 1 ? .01 : .02);
        });
        assertEquals(1, result.executions().size());
        assertEquals(2, result.targetDecisions().size());
        var position = result.openPositions().getFirst();
        assertEquals(10000, position.quantity(), EPS);
        assertEquals(1.086, position.stopPrice(), 1e-12);
        assertEquals(1.0904, result.targetDecisions().getLast().stopBefore(), 1e-12);
        assertEquals(TradingSimulator.TargetAction.RESET_STOP, result.targetDecisions().getLast().action());
        assertEquals(9946, result.finalEquity(), EPS);
    }

    @Test void native_immediately_marketable_entry_and_reset_stops_close_once() {
        for (int resetDay : List.of(1, 2)) {
            var result = run(List.of(q(1, 0, 1.1, 1.1004), q(2, 0, 1.104, 1.105)), nativeOptions(),
                    (a, e) -> { if (e instanceof BarOpen && a.opens <= resetDay) a.target(Order.Side.BUY, a.opens == resetDay ? .0001 : .01); });
            assertEquals(2, result.executions().size());
            assertTrue(result.openPositions().isEmpty());
            assertEquals(TradingSimulator.Reason.STOP, result.executions().getLast().reason());
            assertEquals(resetDay == 1 ? -400 : 36, result.realizedPnl(), EPS);
        }
    }

    @Test void stop_callback_at_opening_cannot_reenter_same_timestamp_but_normal_opening_decision_can() {
        var quotes = List.of(q(1, 0, 1.1, 1.1004), q(2, 0, 1.08, 1.0804),
                q(2, 0, 1.081, 1.0814), q(3, 0, 1.082, 1.0824));
        var result = run(quotes, nativeOptions(), (a, e) -> {
            if (e instanceof BarOpen && a.opens == 1) a.target(Order.Side.BUY, .01);
            if (e instanceof BarOpen && a.opens == 2) a.targetIn("daily", Order.Side.BUY, .01, .01);
        }, true);
        var defaultEntries = result.targetDecisions().stream().filter(d -> d.accountId().isEmpty()).toList();
        assertEquals(2, defaultEntries.size());
        assertEquals(quotes.getLast().time(), defaultEntries.getLast().time(), "stop callback waits until the next opening");
        assertEquals(quotes.get(1).time(), result.targetDecisions().stream().filter(d -> d.accountId().equals("daily")).findFirst().orElseThrow().time());
        assertEquals(4, result.executions().size());
    }

    @Test void quote_callback_order_is_deferred_even_before_first_open_and_at_equal_time_second_quote() {
        var quotes = List.of(q(1, 0, 1.1, 1.1004), q(1, 0, 1.101, 1.1014), q(2, 0, 1.102, 1.1024));
        var result = run(quotes, nativeOptions(), (a, e) -> {
            if (e instanceof BestBidOfferEvent && a.quotes == 1) a.target(Order.Side.BUY, .01);
        });
        assertEquals(1, result.executions().size());
        assertEquals(quotes.getLast().time(), result.executions().getFirst().fill().time());
    }

    @Test void equal_time_tick_after_the_open_can_stop_new_exposure_in_retained_record_order() {
        var quotes = List.of(q(1, 0, 1.1, 1.1004), q(1, 0, 1.089, 1.0894), q(1, 0, 1.11, 1.1104));
        var result = run(quotes, nativeOptions(), (a, e) -> { if (e instanceof BarOpen) a.target(Order.Side.BUY, .01); });
        assertEquals(2, result.executions().size());
        assertEquals(quotes.getFirst().time(), result.executions().getLast().fill().time());
        assertEquals(-114, result.realizedPnl(), EPS);
    }

    @Test void shared_opposite_legs_have_independent_stops_and_current_quote_marks() {
        var result = run(List.of(q(1, 0, 1.1, 1.1004), q(1, 1, 1.11, 1.112)),
                options(TICK_NATIVE_SPREAD, KEEP_OPEN, SHARED, ProfitConversion.QUOTE_CURRENCY), (a, e) -> {
            if (e instanceof BarOpen) {
                a.targetIn("long", Order.Side.BUY, .01, .005);
                a.targetIn("short", Order.Side.SELL_SHORT, .01, .005);
            }
        });
        assertEquals(3, result.executions().size());
        assertEquals(4999, result.targetDecisions().getLast().quantity(), EPS);
        assertEquals(9998, result.targetDecisions().getLast().equity(), EPS);
        assertEquals(-59.988, result.realizedPnl(), EPS);
        assertEquals(48, result.unrealizedPnl(), EPS);
        assertEquals(9988.012, result.finalEquity(), EPS);
        assertEquals(result.finalEquity(), result.executions().getLast().equity(), EPS);
        assertEquals("long", result.openPositions().getFirst().accountId());
    }

    @Test void base_currency_risk_and_profit_convert_at_executable_native_prices() {
        var result = run(List.of(q(1, 0, 1.1, 1.1004), q(1, 1, 1.105, 1.106)),
                options(TICK_NATIVE_SPREAD, LIQUIDATE, INDEPENDENT_ACCOUNTS, ProfitConversion.BASE_CURRENCY),
                (a, e) -> { if (e instanceof BarOpen) a.target(Order.Side.BUY, .01); });
        double quantity = 100 * 1.0904 / .01;
        assertEquals(quantity, result.targetDecisions().getFirst().quantity(), EPS);
        assertEquals(100, quantity * .01 / result.targetDecisions().getFirst().stopAfter(), EPS);
        assertEquals(quantity * (1.105 - 1.1004) / 1.105, result.realizedPnl(), EPS);
    }

    @Test void scheduled_exit_uses_last_native_ask_and_one_daily_completion() {
        var result = run(List.of(q(1, 0, 1.1, 1.1004), q(1, 1, 1.103, 1.104)), nativeOptions(), (a, e) -> {
            if (e instanceof BarOpen) a.scheduledShort();
        });
        assertEquals(TradingSimulator.Reason.SCHEDULED_CLOSE, result.executions().getLast().reason());
        assertEquals(1.104, result.executions().getLast().fill().tradePrice());
        assertEquals(-40, result.realizedPnl(), EPS);
        assertEquals(1, result.equityCurve().size());
    }

    @Test void price_handle_keeps_quotes_separate_from_daily_trade_fields() {
        var handle = new PlaybackPriceHandle(SYMBOL);
        var quote = q(1, 0, 1.1, 1.1004);
        handle.onMarketMessage(new BarOpen(SYMBOL, quote.time(), quote.time(), 1.08));
        handle.onMarketMessage(quote);
        assertEquals(1.08, handle.getLastTradePrice());
        assertEquals(1.1, handle.getQuote().bid());
        assertEquals(1.1004, handle.getQuote().ask());
        handle.onMarketMessage(new BestBidOfferEvent.Of(SYMBOL, BestBidOfferQuote.askOnly(quote.time(), 1.101, 2.0)));
        assertEquals(1.1, handle.getQuote().bid());
        assertEquals(1.101, handle.getQuote().ask());
        assertThrows(IllegalArgumentException.class, () -> handle.onMarketMessage(q(1, 0, 1.2, 1.1)));
        assertThrows(IllegalArgumentException.class, () -> handle.onMarketMessage(q(0, 0, 1.1, 1.2)));
    }

    @Test void symbol_spread_selects_fixed_quotes_while_native_quotes_ignore_both_fixed_settings() {
        for (var mode : List.of(TICK_FIXED_SPREAD, TICK_NATIVE_SPREAD)) {
            var selected = new BacktestExecutionOptions(10000, .7, LIQUIDATE, INDEPENDENT_ACCOUNTS,
                    ProfitConversion.QUOTE_CURRENCY, mode, Map.of(SYMBOL, .0005));
            var result = run(List.of(q(1, 0, 1.1, 1.1004), q(1, 1, 1.105, 1.106)), selected, (a, e) -> {
                if (e instanceof BarOpen) a.target(Order.Side.SELL_SHORT, .02);
            });
            assertEquals(2, result.executions().size());
            assertEquals(1.1, result.executions().getFirst().fill().tradePrice(), EPS);
            assertEquals(mode == TICK_FIXED_SPREAD ? 1.1055 : 1.106, result.executions().getLast().fill().tradePrice(), EPS);
            assertEquals(mode == TICK_FIXED_SPREAD ? -27.5 : -30, result.realizedPnl(), EPS);
        }
    }

    static BacktestExecutionOptions nativeOptions() { return options(TICK_NATIVE_SPREAD, KEEP_OPEN, INDEPENDENT_ACCOUNTS, ProfitConversion.QUOTE_CURRENCY); }
    static BacktestExecutionOptions options(BacktestExecutionOptions.ExecutionMode mode, BacktestExecutionOptions.EndOfDataPolicy end,
                                            BacktestExecutionOptions.CapitalMode capital, ProfitConversion conversion) {
        return new BacktestExecutionOptions(10000, .0003, end, capital, conversion, mode);
    }
    static BestBidOfferEvent q(int day, int hour, double bid, double ask) {
        long time = Chronological.toEpochNanos(Instant.parse("2024-04-01T00:00:00Z").plusSeconds((day - 1L) * 86400 + hour * 3600L));
        return new BestBidOfferEvent.Of(SYMBOL, BestBidOfferQuote.of(time, bid, 1.0, ask, 2.0));
    }
    static MarketMessageSource source(List<? extends MarketEvent> events) {
        return new MarketMessageSource() {
            int i; boolean closed;
            public MarketEvent getMessage() { return closed || i == events.size() ? null : events.get(i++); }
            public boolean isOpen() { return !closed; }
            public void close() { closed = true; }
        };
    }
    static AlgorithmBacktestResult run(List<? extends MarketEvent> quotes, BacktestExecutionOptions options, BiConsumer<Fixture, MarketEvent> action) {
        return run(quotes, options, action, false);
    }
    static AlgorithmBacktestResult run(List<? extends MarketEvent> quotes, BacktestExecutionOptions options, BiConsumer<Fixture, MarketEvent> action, boolean reenterAfterStop) {
        return new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("quote fixture",
                context -> new Fixture(context, action, reenterAfterStop),
                () -> new QuoteReplayMarketSupplier(Map.of(SYMBOL, () -> source(quotes)), DAILY), options));
    }
    static class Fixture extends AbstractAlgorithm<SimpleInstrumentPrices> {
        final BiConsumer<Fixture, MarketEvent> action;
        final boolean reenterAfterStop;
        int opens, quotes;
        Fixture(AlgorithmContext context, BiConsumer<Fixture, MarketEvent> action, boolean reenterAfterStop) {
            super(context); this.action = action; this.reenterAfterStop = reenterAfterStop;
        }
        @Override protected InstrumentDataFactory<SimpleInstrumentPrices> createInstrumentDataFactory() { return SimpleInstrumentPrices::new; }
        @Override public void onMarketMessage(MarketEvent event) {
            super.onMarketMessage(event);
            assertEquals(event.time(), getClock().time());
            if (event instanceof BarOpen) opens++;
            if (event instanceof BestBidOfferEvent) quotes++;
            action.accept(this, event);
        }
        void scheduledShort() { submitOrder(makeMarketOrder(Order.Side.SELL_SHORT, 10000, SYMBOL)
                .destinationId("SIMULATOR").exitRules(new ExitRules(.02, 1)).toNewOrder()); }
        void target(Order.Side side, double distance) { targetIn("", side, distance, .01); }
        void targetIn(String account, Order.Side side, double distance, double risk) {
            var target = makePositionTarget(side, SYMBOL, risk, distance, RESET_FROM_OPEN, "SIMULATOR");
            submitPositionTarget(new PositionTarget(target.time(), target.orderId(), target.sourceId(), target.destinationId(),
                    account, target.symbol(), target.side(), target.riskFraction(), target.stopDistance(), target.stopUpdatePolicy()));
        }
        @Override public void onOrderFilled(Order.Filled fill) {
            assertEquals(fill.time(), getClock().time());
            if (reenterAfterStop && fill.side() == Order.Side.SELL) target(Order.Side.BUY, .01);
        }
    }
}
