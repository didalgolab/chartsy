package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.trade.Order;
import one.chartsy.trade.PositionClose;
import one.chartsy.trade.PositionTarget;
import one.chartsy.trade.account.ProfitConversion;
import one.chartsy.trade.algorithm.AbstractAlgorithm;
import one.chartsy.trade.algorithm.AlgorithmContext;
import one.chartsy.trade.algorithm.data.InstrumentDataFactory;
import one.chartsy.trade.algorithm.data.SimpleInstrumentPrices;
import one.chartsy.trade.service.AlgorithmEngine;
import one.chartsy.trade.service.OrderRequestHandler;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static one.chartsy.simulation.engine.CausalBarBacktestTest.*;
import static one.chartsy.simulation.engine.TradingSimulator.CloseAction.*;
import static one.chartsy.trade.PositionTarget.StopUpdatePolicy.KEEP;
import static org.junit.jupiter.api.Assertions.*;

class PositionCloseTest {
    @Test void flat_close_is_audited_without_a_fill_or_target() {
        var f = new PositionMaintenanceTest.Fixture(); f.open(1, 1.1);
        close(f, "flat", "", "owner");
        var decision = f.broker.getCloseDecisions().getFirst();
        assertEquals(NO_POSITION, decision.action()); assertNull(decision.parentOrderId());
        assertEquals(0, decision.quantity()); assertEquals(10000, decision.equity());
        assertTrue(Double.isNaN(decision.stopBefore())); assertTrue(f.rejections.isEmpty());
        assertTrue(f.broker.getExecutions().isEmpty()); assertTrue(f.broker.getTargetDecisions().isEmpty());
    }

    @Test void close_routes_through_engine_proxy_and_result_for_both_position_sides() {
        for (var side : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
            var run = run(Mode.OPENING, side, List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1),
                    bar("2024-04-02", 1.104, 1.2, 1.0, 1.104)));
            var result = run.result(); var close = result.closeDecisions().getFirst();
            assertEquals(2, result.executions().size()); assertEquals(1, result.closeDecisions().size());
            assertEquals(CLOSE, close.action()); assertEquals(1, result.targetDecisions().size());
            var exit = result.executions().getLast();
            assertEquals(TradingSimulator.Reason.SIGNAL_CLOSE, exit.reason());
            assertEquals(side.isBuy() ? Order.Side.SELL : Order.Side.BUY_TO_COVER, exit.fill().side());
            assertEquals(side.isBuy() ? 1.104 : 1.1043, exit.fill().tradePrice(), 1e-12);
            assertEquals(close.parentOrderId(), exit.parentOrderId()); assertEquals(close.quantity(), exit.fill().tradeQuantity());
            assertEquals(side.isBuy() ? 10037 : 9957, result.finalEquity(), 1e-8);
            assertEquals(10000 + result.realizedPnl(), result.finalEquity(), 1e-8);
            assertEquals(0, result.unrealizedPnl(), 1e-8); assertTrue(result.openPositions().isEmpty());
            assertTrue(run.algorithm().rejections.isEmpty());
        }
    }

    @Test void native_quote_close_uses_current_bid_or_ask_without_candle_or_fixed_spread_substitution() {
        for (var side : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
            var quotes = List.of(QuoteExecutionTest.q(1, 0, 1.1, 1.1004), QuoteExecutionTest.q(2, 0, 1.104, 1.108));
            var result = new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("native-close", c -> new Route(c, side, Mode.OPENING),
                    () -> new QuoteReplayMarketSupplier(Map.of(EURUSD, () -> QuoteExecutionTest.source(quotes)), QuoteExecutionTest.DAILY),
                    QuoteExecutionTest.nativeOptions()));
            assertEquals(2, result.executions().size());
            assertEquals(side.isBuy() ? 1.104 : 1.108, result.executions().getLast().fill().tradePrice());
            assertEquals(quotes.getLast().time(), result.executions().getLast().fill().time());
            assertEquals(side.isBuy() ? 10036 : 9920, result.finalEquity(), 1e-8);
        }
    }

    @Test void opening_gap_stop_precedes_close_and_produces_only_one_exit() {
        var result = run(Mode.OPENING, Order.Side.BUY, List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1),
                bar("2024-04-02", 1.08, 1.09, 1.07, 1.08))).result();
        assertEquals(2, result.executions().size()); assertEquals(TradingSimulator.Reason.STOP, result.executions().getLast().reason());
        assertEquals(NO_POSITION, result.closeDecisions().getFirst().action()); assertTrue(result.openPositions().isEmpty());
        assertEquals(9797, result.finalEquity(), 1e-8);
    }

    @Test void foreign_source_unmanaged_currency_and_inconsistent_protection_are_rejected() {
        var f = new PositionMaintenanceTest.Fixture(); f.open(1, 1.1); f.enter("", Order.Side.BUY, .01);
        var before = f.broker.getOpenPositions(); close(f, "foreign", "", "stranger");
        assertEquals(before, f.broker.getOpenPositions()); assertEquals(1, f.rejections.size());
        for (String currency : List.of("", "USD", "EUR")) {
            var g = new PositionMaintenanceTest.Fixture(); g.open(1, 1.1); g.plain(currency);
            close(g, "unmanaged", "", "owner");
            assertEquals(1, g.rejections.size()); assertEquals(1, g.broker.getExecutions().size());
            assertTrue(g.broker.getCloseDecisions().isEmpty());
        }
        for (double quantity : new double[]{100, -10000, -20000}) {
            var g = new PositionMaintenanceTest.Fixture(); g.open(1, 1.1); g.enter("", Order.Side.BUY, .01);
            g.broker.getDefaultAccount().getOrCreateNewBalance(null).onOrderFill(new Order.Filled(g.open.time(), "external", "outside", "owner", "external-fill",
                    EURUSD, quantity > 0 ? Order.Side.BUY : Order.Side.SELL, Math.abs(quantity), 1.1, Math.abs(quantity), 1.1));
            var positions = g.broker.getOpenPositions(); close(g, "inconsistent", "", "owner");
            assertEquals(1, g.rejections.size()); assertEquals(positions, g.broker.getOpenPositions());
            assertEquals(1, g.broker.getExecutions().size()); assertTrue(g.broker.getCloseDecisions().isEmpty());
        }
    }

    @Test void close_is_scoped_to_requested_account_and_symbol_and_reuses_currency_conversion() {
        var f = new PositionMaintenanceTest.Fixture(ProfitConversion.BASE_CURRENCY); f.open(1, 1.1);
        f.enter("long", Order.Side.BUY, .01); f.enter("short", Order.Side.SELL_SHORT, .01); f.open(2, 1.104);
        var other = f.broker.getOpenPositions().stream().filter(p -> p.accountId().equals("short")).findFirst().orElseThrow();
        double quantity = f.broker.getOpenPositions().stream().filter(p -> p.accountId().equals("long")).findFirst().orElseThrow().quantity();
        close(f, "missing-account", "absent", "owner");
        assertEquals(NO_POSITION, f.broker.getCloseDecisions().getLast().action());
        close(f, "long-close", "long", "owner");
        assertEquals(List.of(other), f.broker.getOpenPositions());
        assertEquals(quantity * (1.104 - 1.1003) / 1.104, f.broker.getRealizedPnL(), 1e-8);
        var otherSymbol = SymbolIdentity.of("GBPUSD");
        f.broker.onPositionClose(new PositionClose(f.open.time(), "other-symbol", "owner", "SIMULATOR", "short", otherSymbol));
        f.broker.onMarketMessage(new BarOpen(otherSymbol, f.open.time(), f.open.time(), 1.2));
        assertEquals(NO_POSITION, f.broker.getCloseDecisions().getLast().action());
        assertEquals(List.of(other), f.broker.getOpenPositions());
    }

    @Test void close_ID_is_unique_across_flat_closes_and_target_entries() {
        var f = new PositionMaintenanceTest.Fixture(); f.open(1, 1.1);
        close(f, "same", "", "owner"); close(f, "same", "", "owner");
        f.enter("", Order.Side.BUY, .01); close(f, "entry-1", "", "owner");
        assertEquals(2, f.rejections.size()); assertEquals(1, f.broker.getCloseDecisions().size());
        assertEquals(1, f.broker.getExecutions().size()); assertEquals(1, f.broker.getOpenPositions().size());
    }

    @Test void invalid_identity_and_broker_destination_are_rejected_without_execution() {
        var f = new PositionMaintenanceTest.Fixture(); f.open(1, 1.1);
        for (var request : List.of(new PositionClose(0, null, "owner", "SIMULATOR", "", EURUSD),
                new PositionClose(0, "", "owner", "SIMULATOR", "", EURUSD),
                new PositionClose(0, "null-source", null, "SIMULATOR", "", EURUSD),
                new PositionClose(0, "blank-source", "", "SIMULATOR", "", EURUSD),
                new PositionClose(0, "null-destination", "owner", null, "", EURUSD),
                new PositionClose(0, "wrong-destination", "owner", "OTHER", "", EURUSD),
                new PositionClose(0, "null-symbol", "owner", "SIMULATOR", "", null))) f.broker.onPositionClose(request);
        f.broker.matchOpeningOrders(f.open, 0);
        assertEquals(7, f.rejections.size()); assertTrue(f.broker.getExecutions().isEmpty()); assertTrue(f.broker.getCloseDecisions().isEmpty());
        var engine = new AlgorithmEngine(f.clock);
        assertThrows(IllegalArgumentException.class, () -> engine.onPositionClose(new PositionClose(0, "x", "owner", null, null, EURUSD)));
        assertThrows(IllegalArgumentException.class, () -> engine.onPositionClose(new PositionClose(0, "x", "owner", "MISSING", null, EURUSD)));
        OrderRequestHandler unsupported = order -> { };
        assertThrows(UnsupportedOperationException.class, () -> unsupported.onPositionClose(new PositionClose(0, "x", "owner", "venue", null, EURUSD)));
    }

    @Test void future_and_quote_callback_close_wait_for_a_later_eligible_opening() {
        var f = new PositionMaintenanceTest.Fixture(); f.open(1, 1.1);
        f.broker.onPositionClose(new PositionClose(f.open.time() + 10, "future", "owner", "SIMULATOR", "", EURUSD));
        f.broker.collectQuoteCallbackRequests(f.open.time(), () -> f.broker.onPositionClose(new PositionClose(f.open.time(), "callback", "owner", "SIMULATOR", "", EURUSD)));
        f.broker.matchOpeningOrders(f.open, 0); assertTrue(f.broker.getCloseDecisions().isEmpty());
        f.open(2, 1.1); assertEquals(2, f.broker.getCloseDecisions().size());
        assertTrue(f.broker.getCloseDecisions().stream().allMatch(d -> d.time() == f.open.time() && d.action() == NO_POSITION));
    }

    @Test void entry_fill_callback_close_is_deferred_through_real_event_runner() {
        var result = run(Mode.ENTRY_CALLBACK, Order.Side.BUY, twoBars()).result();
        assertEquals(2, result.executions().size()); assertEquals(1, result.closeDecisions().size());
        assertTrue(result.executions().getLast().fill().time() > result.executions().getFirst().fill().time());
        assertEquals(10037, result.finalEquity(), 1e-8);
    }

    @Test void close_retires_protection_before_callback_and_callback_entry_waits_until_next_opening() {
        var bars = new ArrayList<>(twoBars()); bars.add(bar("2024-04-03", 1.105, 1.106, 1.104, 1.105));
        var result = run(Mode.CLOSE_CALLBACK_ENTRY, Order.Side.BUY, bars).result();
        assertEquals(3, result.executions().size()); assertEquals(1, result.closeDecisions().size());
        assertEquals(TradingSimulator.Reason.ENTRY, result.executions().getLast().reason());
        assertTrue(result.executions().getLast().fill().time() > result.executions().get(1).fill().time());
        assertEquals(1, result.openPositions().size());
    }

    @Test void final_completed_bar_close_is_rejected_as_pending_and_KEEP_OPEN_is_preserved() {
        var run = run(Mode.COMPLETION, Order.Side.BUY, List.of(twoBars().getFirst()));
        assertEquals(1, run.result().executions().size()); assertEquals(1, run.result().openPositions().size());
        assertTrue(run.result().closeDecisions().isEmpty()); assertEquals(1, run.algorithm().rejections.size());
        assertEquals("End of market data", run.algorithm().rejections.getFirst().rejectionReason());
    }

    @Test void raw_bar_only_close_is_explicitly_rejected_without_audit_action() {
        var f = new PositionMaintenanceTest.Fixture();
        f.broker.onPositionClose(new PositionClose(0, "raw", "owner", "SIMULATOR", "", EURUSD));
        f.clock.setTime(10); f.broker.onMarketMessage(new TradeBar.Of(EURUSD, Candle.of(10, 1.1)));
        assertEquals(1, f.rejections.size()); assertTrue(f.rejections.getFirst().rejectionReason().contains("BarOpen"));
        assertTrue(f.broker.getCloseDecisions().isEmpty()); assertEquals(0, f.broker.finish());
    }

    @Test void result_preserves_legacy_constructors_and_defensively_copies_close_decisions() {
        assertTrue(new AlgorithmBacktestResult(null, List.of(), List.of(), 0).closeDecisions().isEmpty());
        assertTrue(new AlgorithmBacktestResult(null, List.of(), List.of(), 0, List.of(), List.of(), 0, 0).closeDecisions().isEmpty());
        assertTrue(new AlgorithmBacktestResult(null, List.of(), List.of(), 0, List.of(), List.of(), 0, 0, List.of()).closeDecisions().isEmpty());
        var values = new ArrayList<TradingSimulator.CloseDecision>();
        var result = new AlgorithmBacktestResult(null, List.of(), List.of(), 0, List.of(), List.of(), 0, 0, List.of(), values);
        values.add(new TradingSimulator.CloseDecision(0, "x", "", EURUSD, NO_POSITION, null, 0, 0, Double.NaN));
        assertTrue(result.closeDecisions().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.closeDecisions().add(values.getFirst()));
    }

    static void close(PositionMaintenanceTest.Fixture f, String id, String account, String owner) {
        f.broker.onPositionClose(new PositionClose(f.open.time(), id, owner, "SIMULATOR", account, EURUSD));
        f.broker.matchOpeningOrders(f.open, 0);
    }
    static List<Candle> twoBars() {
        return List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1), bar("2024-04-02", 1.104, 1.105, 1.103, 1.104));
    }
    record Run(AlgorithmBacktestResult result, Route algorithm) { }
    static Run run(Mode mode, Order.Side side, List<Candle> bars) {
        var holder = new Route[1];
        var result = new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("explicit-close", c -> holder[0] = new Route(c, side, mode),
                () -> new BarReplayMarketSupplier(Map.of(EURUSD, bars), WARSAW),
                new BacktestExecutionOptions(10000, SPREAD, BacktestExecutionOptions.EndOfDataPolicy.KEEP_OPEN)));
        return new Run(result, holder[0]);
    }
    enum Mode { OPENING, ENTRY_CALLBACK, CLOSE_CALLBACK_ENTRY, COMPLETION }
    static final class Route extends AbstractAlgorithm<SimpleInstrumentPrices> {
        final Order.Side side; final Mode mode; int opens;
        final List<Order.Rejected> rejections = new ArrayList<>();
        Route(AlgorithmContext context, Order.Side side, Mode mode) { super(context); this.side = side; this.mode = mode; }
        @Override protected InstrumentDataFactory<SimpleInstrumentPrices> createInstrumentDataFactory() { return SimpleInstrumentPrices::new; }
        @Override public void onMarketMessage(MarketEvent event) {
            super.onMarketMessage(event);
            if (event instanceof BarOpen) {
                if (++opens == 1) submitPositionTarget(makePositionTarget(side, EURUSD, .01, .01, KEEP, "SIMULATOR"));
                else if (opens == 2 && (mode == Mode.OPENING || mode == Mode.CLOSE_CALLBACK_ENTRY)) submitPositionClose(makePositionClose(EURUSD, "SIMULATOR"));
            } else if (event instanceof TradeBar && mode == Mode.COMPLETION) submitPositionClose(makePositionClose(EURUSD, "SIMULATOR"));
        }
        @Override public void onOrderFilled(Order.Filled fill) {
            if (fill.side().isEntry() && mode == Mode.ENTRY_CALLBACK) submitPositionClose(makePositionClose(EURUSD, "SIMULATOR"));
            else if (!fill.side().isEntry() && mode == Mode.CLOSE_CALLBACK_ENTRY) submitPositionTarget(makePositionTarget(side, EURUSD, .01, .01, KEEP, "SIMULATOR"));
        }
        @Override public void onOrderRejected(Order.Rejected rejection) { rejections.add(rejection); }
    }
}
