package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.trade.Order;
import one.chartsy.trade.PositionMaintenance;
import one.chartsy.trade.PositionStopUpdate;
import one.chartsy.trade.PositionTarget;
import one.chartsy.trade.PositionTarget.StopUpdatePolicy;
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
import static one.chartsy.simulation.engine.TradingSimulator.StopUpdateAction.*;
import static one.chartsy.trade.PositionTarget.StopUpdatePolicy.*;
import static org.junit.jupiter.api.Assertions.*;

class PositionStopUpdateTest {
    @Test void flat_stop_update_is_audited_without_direction_entry_or_fill() {
        var f = new PositionMaintenanceTest.Fixture();
        f.open(1, 1.1);
        update(f, "flat", "", "owner", .01, TIGHTEN_FROM_OPEN);
        var d = f.broker.getStopUpdateDecisions().getFirst();
        assertEquals(NO_POSITION, d.action());
        assertNull(d.heldSide());
        assertNull(d.parentOrderId());
        assertEquals(0, d.quantity());
        assertEquals(10000, d.equity());
        assertEquals(.01, d.stopDistance());
        assertTrue(Double.isNaN(d.candidateStop()));
        assertTrue(Double.isNaN(d.stopBefore()));
        assertTrue(Double.isNaN(d.stopAfter()));
        assertTrue(f.broker.getExecutions().isEmpty());
        assertTrue(f.broker.getTargetDecisions().isEmpty());
        assertTrue(f.rejections.isEmpty());
    }

    @Test void stop_only_tightens_both_sides_but_preserves_unfavorable_equal_and_keep_candidates() {
        for (var side : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
            var f = new PositionMaintenanceTest.Fixture();
            f.open(1, 1.1);
            f.enter("", side, .01);
            var entry = f.broker.getOpenPositions().getFirst();
            f.open(2, side.isBuy() ? 1.104 : 1.096);
            update(f, "unfavorable", "", "owner", .02, TIGHTEN_FROM_OPEN);
            assertEquals(HOLD, last(f).action());
            assertEquals(entry.stopPrice(), last(f).stopAfter());
            assertEquals(side, last(f).heldSide());
            assertNotEquals(last(f).candidateStop(), last(f).stopBefore());

            update(f, "favorable", "", "owner", .005, TIGHTEN_FROM_OPEN);
            assertEquals(RESET_STOP, last(f).action());
            assertEquals(side.isBuy() ? 1.0993 : 1.101, last(f).stopAfter(), 1e-12);
            assertEquals(last(f).candidateStop(), last(f).stopAfter());
            double tightened = last(f).stopAfter();
            update(f, "equal", "", "owner", .005, TIGHTEN_FROM_OPEN);
            assertEquals(HOLD, last(f).action());
            assertEquals(tightened, last(f).stopAfter());
            update(f, "keep", "", "owner", .001, KEEP);
            assertEquals(HOLD, last(f).action());
            assertTrue(Double.isNaN(last(f).candidateStop()));
            assertEquals(tightened, last(f).stopAfter());
            var held = f.broker.getOpenPositions().getFirst();
            assertEquals(entry.quantity(), held.quantity());
            assertEquals(entry.entryPrice(), held.entryPrice());
            assertEquals(entry.entryTime(), held.entryTime());
            assertEquals(entry.parentOrderId(), held.parentOrderId());
            assertEquals(1, f.broker.getExecutions().size());
            assertTrue(f.rejections.isEmpty());
        }
    }

    @Test void real_event_route_exposes_widened_stop_exit_difference_for_long_and_short() {
        for (var side : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
            var bars = List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1),
                    side.isBuy() ? bar("2024-04-02", 1.104, 1.105, 1.087, 1.104)
                            : bar("2024-04-02", 1.096, 1.113, 1.095, 1.096));
            var reset = run(Mode.UPDATE, side, RESET_FROM_OPEN, .02, bars);
            var tight = run(Mode.UPDATE, side, TIGHTEN_FROM_OPEN, .02, bars);
            var unchanged = run(Mode.NO_UPDATE, side, KEEP, .02, bars);
            assertEquals(1, reset.result().executions().size());
            assertEquals(1, reset.result().openPositions().size());
            assertEquals(RESET_STOP, reset.result().stopUpdateDecisions().getFirst().action());
            assertEquals(2, tight.result().executions().size());
            assertTrue(tight.result().openPositions().isEmpty());
            assertEquals(HOLD, tight.result().stopUpdateDecisions().getFirst().action());
            var exit = tight.result().executions().getLast();
            assertEquals(TradingSimulator.Reason.STOP, exit.reason());
            assertEquals(unchanged.result().executions().getLast().fill().tradePrice(), exit.fill().tradePrice());
            assertEquals(unchanged.result().finalEquity(), tight.result().finalEquity());
            assertEquals(1, tight.result().targetDecisions().size());
            assertTrue(reset.algorithm().rejections.isEmpty());
            assertTrue(tight.algorithm().rejections.isEmpty());
        }
    }

    @Test void ordinary_same_side_targets_and_directional_maintenance_share_favorable_only_selection() {
        for (var side : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
            for (var mode : List.of(Mode.TARGET, Mode.MAINTENANCE)) {
                for (double distance : new double[]{.02, .005}) {
                    var run = run(mode, side, TIGHTEN_FROM_OPEN, distance, safeBars(side));
                    var result = run.result();
                    var entry = result.executions().getFirst();
                    var held = result.openPositions().getFirst();
                    assertEquals(1, result.executions().size());
                    assertEquals(entry.fill().tradeQuantity(), held.quantity());
                    assertEquals(entry.fill().tradePrice(), held.entryPrice());
                    assertEquals(entry.parentOrderId(), held.parentOrderId());
                    boolean favorable = distance == .005;
                    double expected = favorable ? (side.isBuy() ? 1.0993 : 1.101)
                            : (side.isBuy() ? 1.0903 : 1.11);
                    assertEquals(expected, held.stopPrice(), 1e-12);
                    if (mode == Mode.TARGET) {
                        assertEquals(favorable ? TradingSimulator.TargetAction.RESET_STOP : TradingSimulator.TargetAction.HOLD,
                                result.targetDecisions().getLast().action());
                        assertTrue(Double.isNaN(result.targetDecisions().getLast().riskBudget()));
                    } else {
                        assertEquals(favorable ? TradingSimulator.MaintenanceAction.RESET_STOP : TradingSimulator.MaintenanceAction.HOLD,
                                result.maintenanceDecisions().getFirst().action());
                    }
                    assertTrue(result.stopUpdateDecisions().isEmpty());
                    assertTrue(run.algorithm().rejections.isEmpty());
                }
            }
        }
    }

    @Test void opening_gap_stop_precedes_update_and_flat_update_cannot_reenter() {
        var run = run(Mode.UPDATE, Order.Side.BUY, RESET_FROM_OPEN, .04,
                List.of(safeBars(Order.Side.BUY).getFirst(), bar("2024-04-02", 1.08, 1.09, 1.07, 1.08)));
        assertEquals(2, run.result().executions().size());
        assertEquals(TradingSimulator.Reason.STOP, run.result().executions().getLast().reason());
        assertEquals(NO_POSITION, run.result().stopUpdateDecisions().getFirst().action());
        assertTrue(run.result().openPositions().isEmpty());
        assertTrue(run.algorithm().rejections.isEmpty());
    }

    @Test void newly_installed_stop_can_trigger_once_at_current_opening_quote() {
        for (var side : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
            var run = run(Mode.UPDATE, side, TIGHTEN_FROM_OPEN, .0001, safeBars(side));
            var result = run.result();
            assertEquals(2, result.executions().size());
            assertEquals(TradingSimulator.Reason.STOP, result.executions().getLast().reason());
            assertEquals(RESET_STOP, result.stopUpdateDecisions().getFirst().action());
            assertEquals(result.stopUpdateDecisions().getFirst().time(), result.executions().getLast().fill().time());
            assertTrue(result.openPositions().isEmpty());
        }
    }

    @Test void native_quotes_define_candidates_without_fixed_spread_substitution() {
        for (var side : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
            var quotes = List.of(QuoteExecutionTest.q(1, 0, 1.1, 1.1004), QuoteExecutionTest.q(2, 0, 1.104, 1.108));
            var result = new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("native-stop-update",
                    c -> new Route(c, Mode.UPDATE, side, RESET_FROM_OPEN, .01),
                    () -> new QuoteReplayMarketSupplier(Map.of(EURUSD, () -> QuoteExecutionTest.source(quotes)), QuoteExecutionTest.DAILY),
                    QuoteExecutionTest.nativeOptions()));
            var d = result.stopUpdateDecisions().getFirst();
            assertEquals(side.isBuy() ? 1.098 : 1.114, d.candidateStop(), 1e-12);
            assertEquals(d.candidateStop(), d.stopAfter());
            assertEquals(quotes.getLast().time(), d.time());
            assertEquals(1, result.executions().size());
        }
    }

    @Test void target_reversal_installs_new_trade_stop_without_comparing_old_opposite_stop() {
        var result = run(Mode.REVERSE, Order.Side.BUY, TIGHTEN_FROM_OPEN, .02, safeBars(Order.Side.BUY)).result();
        assertEquals(3, result.executions().size());
        assertEquals(TradingSimulator.Reason.SIGNAL_REVERSAL, result.executions().get(1).reason());
        assertEquals(TradingSimulator.TargetAction.REVERSE, result.targetDecisions().getLast().action());
        assertEquals(Order.Side.SELL_SHORT, result.openPositions().getFirst().side());
        assertEquals(1.124, result.openPositions().getFirst().stopPrice(), 1e-12);
        assertTrue(result.stopUpdateDecisions().isEmpty());
        assertTrue(result.targetDecisions().getLast().riskBudget() > 0);
    }

    @Test void foreign_unmanaged_and_inconsistent_protection_are_rejected_without_mutation() {
        var f = new PositionMaintenanceTest.Fixture();
        f.open(1, 1.1);
        f.enter("", Order.Side.BUY, .01);
        var before = f.broker.getOpenPositions();
        update(f, "foreign", "", "stranger", .005, RESET_FROM_OPEN);
        assertEquals(before, f.broker.getOpenPositions());
        assertEquals(1, f.rejections.size());
        for (String currency : List.of("", "USD", "EUR")) {
            var g = new PositionMaintenanceTest.Fixture();
            g.open(1, 1.1);
            g.plain(currency);
            var positions = g.broker.getOpenPositions();
            update(g, "unmanaged", "", "owner", .005, RESET_FROM_OPEN);
            assertEquals(positions, g.broker.getOpenPositions());
            assertEquals(1, g.rejections.size());
            assertTrue(g.broker.getStopUpdateDecisions().isEmpty());
        }
        for (double quantity : new double[]{100, -20000}) {
            var g = new PositionMaintenanceTest.Fixture();
            g.open(1, 1.1);
            g.enter("", Order.Side.BUY, .01);
            g.broker.getDefaultAccount().getOrCreateNewBalance(null).onOrderFill(new Order.Filled(g.open.time(),
                    "external", "outside", "owner", "external-fill", EURUSD,
                    quantity > 0 ? Order.Side.BUY : Order.Side.SELL, Math.abs(quantity), 1.1, Math.abs(quantity), 1.1));
            var positions = g.broker.getOpenPositions();
            update(g, "inconsistent", "", "owner", .005, RESET_FROM_OPEN);
            assertEquals(positions, g.broker.getOpenPositions());
            assertEquals(1, g.rejections.size());
            assertTrue(g.broker.getStopUpdateDecisions().isEmpty());
        }
    }

    @Test void account_and_symbol_scope_are_preserved_and_base_conversion_candidate_is_validated() {
        var f = new PositionMaintenanceTest.Fixture(ProfitConversion.BASE_CURRENCY);
        f.open(1, 1.1);
        f.enter("long", Order.Side.BUY, .01);
        f.enter("short", Order.Side.SELL_SHORT, .01);
        var shortBefore = f.broker.getOpenPositions().stream().filter(p -> p.accountId().equals("short")).findFirst().orElseThrow();
        update(f, "long-update", "long", "owner", .005, TIGHTEN_FROM_OPEN);
        assertEquals(shortBefore, f.broker.getOpenPositions().stream().filter(p -> p.accountId().equals("short")).findFirst().orElseThrow());
        double stop = last(f).stopAfter();
        update(f, "invalid-conversion", "long", "owner", 2, TIGHTEN_FROM_OPEN);
        assertEquals(1, f.rejections.size());
        assertEquals(stop, f.broker.getOpenPositions().stream().filter(p -> p.accountId().equals("long")).findFirst().orElseThrow().stopPrice());
        update(f, "unused-keep-candidate", "long", "owner", 2, KEEP);
        assertEquals(HOLD, last(f).action());
        assertEquals(stop, last(f).stopAfter());
        var other = SymbolIdentity.of("GBPUSD");
        f.broker.onPositionStopUpdate(new PositionStopUpdate(f.open.time(), "other-symbol", "owner", "SIMULATOR", "long", other, .01, RESET_FROM_OPEN));
        f.broker.onMarketMessage(new BarOpen(other, f.open.time(), f.open.time(), 1.2));
        assertEquals(NO_POSITION, last(f).action());
        assertEquals(2, f.broker.getOpenPositions().size());
    }

    @Test void malformed_requests_and_duplicate_ids_fail_without_audit_or_mutation() {
        for (double distance : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new PositionStopUpdate(0, "x", "owner", "SIMULATOR", "", EURUSD, distance, KEEP));
        var f = new PositionMaintenanceTest.Fixture();
        f.open(1, 1.1);
        for (var request : List.of(new PositionStopUpdate(0, null, "owner", "SIMULATOR", "", EURUSD, .01, KEEP),
                new PositionStopUpdate(0, "", "owner", "SIMULATOR", "", EURUSD, .01, KEEP),
                new PositionStopUpdate(0, "source", null, "SIMULATOR", "", EURUSD, .01, KEEP),
                new PositionStopUpdate(0, "blank-source", "", "SIMULATOR", "", EURUSD, .01, KEEP),
                new PositionStopUpdate(0, "destination", "owner", null, "", EURUSD, .01, KEEP),
                new PositionStopUpdate(0, "wrong-destination", "owner", "OTHER", "", EURUSD, .01, KEEP),
                new PositionStopUpdate(0, "policy", "owner", "SIMULATOR", "", EURUSD, .01, null),
                new PositionStopUpdate(0, "symbol", "owner", "SIMULATOR", "", null, .01, KEEP)))
            f.broker.onPositionStopUpdate(request);
        f.broker.matchOpeningOrders(f.open, 0);
        assertEquals(8, f.rejections.size());
        assertTrue(f.broker.getStopUpdateDecisions().isEmpty());
        update(f, "same", "", "owner", .01, KEEP);
        update(f, "same", "", "owner", .01, KEEP);
        f.enter("", Order.Side.BUY, .01);
        update(f, "entry-1", "", "owner", .01, KEEP);
        assertEquals(10, f.rejections.size());
        assertEquals(1, f.broker.getStopUpdateDecisions().size());
        var engine = new AlgorithmEngine(f.clock);
        assertThrows(IllegalArgumentException.class, () -> engine.onPositionStopUpdate(new PositionStopUpdate(0, "x", "owner", null, null, EURUSD, .01, KEEP)));
        assertThrows(IllegalArgumentException.class, () -> engine.onPositionStopUpdate(new PositionStopUpdate(0, "x", "owner", "MISSING", null, EURUSD, .01, KEEP)));
        OrderRequestHandler unsupported = order -> { };
        assertThrows(UnsupportedOperationException.class, () -> unsupported.onPositionStopUpdate(new PositionStopUpdate(0, "x", "owner", "venue", null, EURUSD, .01, KEEP)));
    }

    @Test void future_and_quote_callback_requests_wait_until_a_later_opening() {
        var f = new PositionMaintenanceTest.Fixture();
        f.open(1, 1.1);
        f.broker.onPositionStopUpdate(new PositionStopUpdate(f.open.time() + 10, "future", "owner", "SIMULATOR", "", EURUSD, .01, KEEP));
        f.broker.collectQuoteCallbackRequests(f.open.time(), () -> f.broker.onPositionStopUpdate(
                new PositionStopUpdate(f.open.time(), "callback", "owner", "SIMULATOR", "", EURUSD, .01, KEEP)));
        f.broker.matchOpeningOrders(f.open, 0);
        assertTrue(f.broker.getStopUpdateDecisions().isEmpty());
        f.open(2, 1.1);
        assertEquals(2, f.broker.getStopUpdateDecisions().size());
        assertTrue(f.broker.getStopUpdateDecisions().stream().allMatch(d -> d.time() == f.open.time()));
    }

    @Test void fill_callback_update_is_deferred_through_real_runner() {
        var result = run(Mode.ENTRY_CALLBACK, Order.Side.BUY, RESET_FROM_OPEN, .005, safeBars(Order.Side.BUY)).result();
        assertEquals(1, result.executions().size());
        assertEquals(1, result.stopUpdateDecisions().size());
        assertTrue(result.stopUpdateDecisions().getFirst().time() > result.executions().getFirst().fill().time());
        assertEquals(1.0993, result.stopUpdateDecisions().getFirst().stopAfter(), 1e-12);
    }

    @Test void exhausted_and_raw_only_requests_do_not_mutate_protection() {
        var run = run(Mode.COMPLETION, Order.Side.BUY, RESET_FROM_OPEN, .005, List.of(safeBars(Order.Side.BUY).getFirst()));
        assertEquals(1, run.result().openPositions().size());
        assertTrue(run.result().stopUpdateDecisions().isEmpty());
        assertEquals(1, run.algorithm().rejections.size());
        assertEquals("End of market data", run.algorithm().rejections.getFirst().rejectionReason());
        var f = new PositionMaintenanceTest.Fixture();
        f.broker.onPositionStopUpdate(new PositionStopUpdate(0, "raw", "owner", "SIMULATOR", "", EURUSD, .01, KEEP));
        f.clock.setTime(10);
        f.broker.onMarketMessage(new TradeBar.Of(EURUSD, Candle.of(10, 1.1)));
        assertEquals(1, f.rejections.size());
        assertTrue(f.rejections.getFirst().rejectionReason().contains("BarOpen"));
        assertTrue(f.broker.getStopUpdateDecisions().isEmpty());
    }

    @Test void all_old_result_constructors_and_new_list_isolation_are_preserved() {
        assertTrue(new AlgorithmBacktestResult(null, List.of(), List.of(), 0).stopUpdateDecisions().isEmpty());
        assertTrue(new AlgorithmBacktestResult(null, List.of(), List.of(), 0, List.of(), List.of(), 0, 0).stopUpdateDecisions().isEmpty());
        assertTrue(new AlgorithmBacktestResult(null, List.of(), List.of(), 0, List.of(), List.of(), 0, 0, List.of()).stopUpdateDecisions().isEmpty());
        assertTrue(new AlgorithmBacktestResult(null, List.of(), List.of(), 0, List.of(), List.of(), 0, 0, List.of(), List.of()).stopUpdateDecisions().isEmpty());
        var mutable = new ArrayList<TradingSimulator.StopUpdateDecision>();
        var result = new AlgorithmBacktestResult(null, List.of(), List.of(), 0, List.of(), List.of(), 0, 0, List.of(), List.of(), mutable);
        var item = new TradingSimulator.StopUpdateDecision(0, "x", "", EURUSD, null, NO_POSITION, null, 0, 0, .01, Double.NaN, Double.NaN, Double.NaN);
        mutable.add(item);
        assertTrue(result.stopUpdateDecisions().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> result.stopUpdateDecisions().add(item));
    }

    @Test void fromPositionEntry_preserves_supplied_constructor_and_refuses_fabricated_distance() {
        var provided = new PositionStopUpdate(0, "given", "owner", "SIMULATOR", "", EURUSD, .01, KEEP);
        assertEquals(PositionStopUpdate.DistanceSource.PROVIDED, provided.distanceSource());
        assertEquals(.01, provided.stopDistance());
        var entry = PositionStopUpdate.fromPositionEntry(0, "entry", "owner", "SIMULATOR", "", EURUSD, KEEP);
        assertEquals(PositionStopUpdate.DistanceSource.POSITION_ENTRY, entry.distanceSource());
        assertTrue(Double.isNaN(entry.stopDistance()));
        for (double distance : new double[]{0, .01, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new PositionStopUpdate(0, "x", "owner", "SIMULATOR", "",
                    EURUSD, distance, KEEP, PositionStopUpdate.DistanceSource.POSITION_ENTRY));
        assertThrows(IllegalArgumentException.class, () -> new PositionStopUpdate(0, "x", "owner", "SIMULATOR", "",
                EURUSD, .01, KEEP, null));
    }

    @Test void positionEntry_original_distance_survives_same_direction_reset_for_both_sides() {
        for (var side : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
            var f = new PositionMaintenanceTest.Fixture();
            f.open(1, 1.1);
            f.enter("", side, .01);
            var entry = f.broker.getOpenPositions().getFirst();
            f.open(2, side.isBuy() ? 1.104 : 1.096);
            target(f, "reset", side, .02, RESET_FROM_OPEN);
            assertEquals(TradingSimulator.TargetAction.RESET_STOP, f.broker.getTargetDecisions().getLast().action());
            assertEquals(side.isBuy() ? 1.0843 : 1.116, f.broker.getOpenPositions().getFirst().stopPrice(), 1e-12);
            entryUpdate(f, "trail", "", "owner", TIGHTEN_FROM_OPEN);
            var decision = last(f);
            assertEquals(RESET_STOP, decision.action());
            assertEquals(.01, decision.stopDistance());
            assertEquals(side.isBuy() ? 1.0943 : 1.106, decision.stopAfter(), 1e-12);
            assertEquals(side, decision.heldSide());
            var held = f.broker.getOpenPositions().getFirst();
            assertEquals(entry.quantity(), held.quantity());
            assertEquals(entry.entryPrice(), held.entryPrice());
            assertEquals(entry.entryTime(), held.entryTime());
            assertEquals(entry.parentOrderId(), held.parentOrderId());
            assertEquals(1, f.broker.getExecutions().size());
            assertTrue(f.rejections.isEmpty());
        }
    }

    @Test void positionEntry_reversal_resolves_replacement_positions_original_distance() {
        for (var side : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
            var f = new PositionMaintenanceTest.Fixture();
            f.open(1, 1.1);
            f.enter("", side, .01);
            String original = f.broker.getOpenPositions().getFirst().parentOrderId();
            f.open(2, side.isBuy() ? 1.104 : 1.096);
            var reverse = side.isBuy() ? Order.Side.SELL_SHORT : Order.Side.BUY;
            target(f, "reversal", reverse, .02, KEEP);
            var replacement = f.broker.getOpenPositions().getFirst();
            assertNotEquals(original, replacement.parentOrderId());
            assertEquals(TradingSimulator.Reason.SIGNAL_REVERSAL, f.broker.getExecutions().get(1).reason());
            f.open(3, side.isBuy() ? 1.098 : 1.102);
            entryUpdate(f, "trail-replacement", "", "owner", TIGHTEN_FROM_OPEN);
            assertEquals(.02, last(f).stopDistance());
            assertEquals(reverse, last(f).heldSide());
            assertEquals(side.isBuy() ? 1.118 : 1.0823, last(f).stopAfter(), 1e-12);
            assertEquals(replacement.parentOrderId(), last(f).parentOrderId());
            assertEquals(replacement.quantity(), last(f).quantity());
            assertEquals(3, f.broker.getExecutions().size());
            assertTrue(f.rejections.isEmpty());
        }
    }

    @Test void positionEntry_old_gap_executes_before_resolution_and_flat_has_no_distance() {
        for (var side : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
            var gap = side.isBuy() ? bar("2024-04-02", 1.08, 1.09, 1.07, 1.08)
                    : bar("2024-04-02", 1.12, 1.13, 1.11, 1.12);
            var run = run(Mode.ENTRY_DISTANCE, side, TIGHTEN_FROM_OPEN, Double.NaN,
                    List.of(safeBars(side).getFirst(), gap));
            var result = run.result();
            assertEquals(2, result.executions().size());
            assertEquals(TradingSimulator.Reason.STOP, result.executions().getLast().reason());
            var decision = result.stopUpdateDecisions().getFirst();
            assertEquals(NO_POSITION, decision.action());
            assertTrue(Double.isNaN(decision.stopDistance()));
            assertTrue(Double.isNaN(decision.candidateStop()));
            assertNull(decision.heldSide());
            assertNull(decision.parentOrderId());
            assertEquals(0, decision.quantity());
            assertTrue(result.openPositions().isEmpty());
            assertEquals(1, result.targetDecisions().size());
            assertTrue(run.algorithm().rejections.isEmpty());
        }
    }

    @Test void positionEntry_resolves_when_executed_and_respects_account_ownership() {
        var f = new PositionMaintenanceTest.Fixture();
        f.open(1, 1.1);
        var madeWhileFlat = PositionStopUpdate.fromPositionEntry(f.open.time(), "later", "owner", "SIMULATOR", "", EURUSD, KEEP);
        target(f, "entry", Order.Side.BUY, .02, KEEP);
        f.broker.onPositionStopUpdate(madeWhileFlat);
        f.broker.matchOpeningOrders(f.open, 0);
        assertEquals(.02, last(f).stopDistance());
        assertEquals(HOLD, last(f).action());
        var before = f.broker.getOpenPositions();
        entryUpdate(f, "foreign", "", "stranger", TIGHTEN_FROM_OPEN);
        assertEquals(before, f.broker.getOpenPositions());
        assertEquals(1, f.rejections.size());
        entryUpdate(f, "other-account", "flat", "owner", TIGHTEN_FROM_OPEN);
        assertEquals(NO_POSITION, last(f).action());
        assertTrue(Double.isNaN(last(f).stopDistance()));
        assertEquals(before, f.broker.getOpenPositions());
        assertEquals(1, f.broker.getExecutions().size());
        var unmanaged = new PositionMaintenanceTest.Fixture();
        unmanaged.open(1, 1.1);
        unmanaged.plain("");
        var plain = unmanaged.broker.getOpenPositions();
        entryUpdate(unmanaged, "not-managed", "", "owner", TIGHTEN_FROM_OPEN);
        assertEquals(plain, unmanaged.broker.getOpenPositions());
        assertEquals(1, unmanaged.rejections.size());
        assertTrue(unmanaged.broker.getStopUpdateDecisions().isEmpty());
    }

    @Test void positionEntry_and_same_supplied_distance_have_identical_event_results() {
        for (var side : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
            var supplied = run(Mode.UPDATE, side, TIGHTEN_FROM_OPEN, .01, safeBars(side)).result();
            var original = run(Mode.ENTRY_DISTANCE, side, TIGHTEN_FROM_OPEN, Double.NaN, safeBars(side)).result();
            assertEquals(supplied.stopUpdateDecisions(), original.stopUpdateDecisions());
            assertEquals(supplied.executions(), original.executions());
            assertEquals(supplied.openPositions(), original.openPositions());
            assertEquals(supplied.finalEquity(), original.finalEquity());
            assertEquals(supplied.targetDecisions(), original.targetDecisions());
        }
    }

    private static void target(PositionMaintenanceTest.Fixture f, String id, Order.Side side,
                               double distance, StopUpdatePolicy policy) {
        f.broker.onPositionTarget(new PositionTarget(f.open.time(), id, "owner", "SIMULATOR", "", EURUSD,
                side, .01, distance, policy));
        f.broker.matchOpeningOrders(f.open, 0);
    }

    private static void entryUpdate(PositionMaintenanceTest.Fixture f, String id, String account, String source,
                                    StopUpdatePolicy policy) {
        f.broker.onPositionStopUpdate(PositionStopUpdate.fromPositionEntry(f.open.time(), id, source,
                "SIMULATOR", account, EURUSD, policy));
        f.broker.matchOpeningOrders(f.open, 0);
    }

    private static void update(PositionMaintenanceTest.Fixture f, String id, String account, String source,
                               double distance, StopUpdatePolicy policy) {
        f.broker.onPositionStopUpdate(new PositionStopUpdate(f.open.time(), id, source, "SIMULATOR", account, EURUSD, distance, policy));
        f.broker.matchOpeningOrders(f.open, 0);
    }

    private static TradingSimulator.StopUpdateDecision last(PositionMaintenanceTest.Fixture f) {
        return f.broker.getStopUpdateDecisions().getLast();
    }

    private static List<Candle> safeBars(Order.Side side) {
        return List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1),
                side.isBuy() ? bar("2024-04-02", 1.104, 1.105, 1.103, 1.104)
                        : bar("2024-04-02", 1.096, 1.097, 1.095, 1.096));
    }

    private record Run(AlgorithmBacktestResult result, Route algorithm) { }

    private static Run run(Mode mode, Order.Side side, StopUpdatePolicy policy, double distance, List<Candle> bars) {
        var holder = new Route[1];
        var result = new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("stop-update-route",
                c -> holder[0] = new Route(c, mode, side, policy, distance),
                () -> new BarReplayMarketSupplier(Map.of(EURUSD, bars), WARSAW),
                new BacktestExecutionOptions(10000, SPREAD, BacktestExecutionOptions.EndOfDataPolicy.KEEP_OPEN)));
        return new Run(result, holder[0]);
    }

    private enum Mode { UPDATE, ENTRY_DISTANCE, TARGET, MAINTENANCE, REVERSE, NO_UPDATE, ENTRY_CALLBACK, COMPLETION }

    private static final class Route extends AbstractAlgorithm<SimpleInstrumentPrices> {
        private final Mode mode;
        private final Order.Side side;
        private final StopUpdatePolicy policy;
        private final double distance;
        private final List<Order.Rejected> rejections = new ArrayList<>();
        private int opens;

        Route(AlgorithmContext context, Mode mode, Order.Side side, StopUpdatePolicy policy, double distance) {
            super(context);
            this.mode = mode;
            this.side = side;
            this.policy = policy;
            this.distance = distance;
        }

        @Override protected InstrumentDataFactory<SimpleInstrumentPrices> createInstrumentDataFactory() {
            return SimpleInstrumentPrices::new;
        }

        @Override public void onMarketMessage(MarketEvent event) {
            super.onMarketMessage(event);
            if (event instanceof BarOpen) {
                if (++opens == 1) {
                    submitPositionTarget(makePositionTarget(side, EURUSD, .01, .01, KEEP, "SIMULATOR"));
                } else if (mode == Mode.UPDATE || mode == Mode.ENTRY_DISTANCE) {
                    updateStop();
                } else if (mode == Mode.TARGET || mode == Mode.REVERSE) {
                    var direction = mode == Mode.REVERSE ? (side.isBuy() ? Order.Side.SELL_SHORT : Order.Side.BUY) : side;
                    submitPositionTarget(makePositionTarget(direction, EURUSD, .01, distance, policy, "SIMULATOR"));
                } else if (mode == Mode.MAINTENANCE) {
                    submitPositionMaintenance(new PositionMaintenance(event.time(), generateOrderId(), getId(),
                            "SIMULATOR", null, EURUSD, side, distance, policy));
                }
            } else if (event instanceof TradeBar && mode == Mode.COMPLETION) {
                updateStop();
            }
        }

        private void updateStop() {
            submitPositionStopUpdate(mode == Mode.ENTRY_DISTANCE
                    ? makePositionEntryStopUpdate(EURUSD, policy, "SIMULATOR")
                    : makePositionStopUpdate(EURUSD, distance, policy, "SIMULATOR"));
        }

        @Override public void onOrderFilled(Order.Filled fill) {
            if (fill.side().isEntry() && mode == Mode.ENTRY_CALLBACK)
                updateStop();
        }

        @Override public void onOrderRejected(Order.Rejected rejection) {
            rejections.add(rejection);
        }
    }
}
