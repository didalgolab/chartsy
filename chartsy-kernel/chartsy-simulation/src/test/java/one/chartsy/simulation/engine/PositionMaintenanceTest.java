package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.simulation.engine.price.PlaybackMarketPriceService;
import one.chartsy.simulation.time.PlaybackClock;
import one.chartsy.trade.Order;
import one.chartsy.trade.OrderType;
import one.chartsy.trade.PositionMaintenance;
import one.chartsy.trade.PositionTarget;
import one.chartsy.trade.account.ProfitConversion;
import one.chartsy.trade.algorithm.AbstractAlgorithm;
import one.chartsy.trade.algorithm.AlgorithmContext;
import one.chartsy.trade.algorithm.data.InstrumentDataFactory;
import one.chartsy.trade.algorithm.data.SimpleInstrumentPrices;
import one.chartsy.trade.service.OrderReportHandler;
import one.chartsy.trade.service.connector.TradeConnectorContext;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static one.chartsy.simulation.engine.CausalBarBacktestTest.*;
import static one.chartsy.simulation.engine.TradingSimulator.MaintenanceAction.*;
import static one.chartsy.trade.PositionTarget.StopUpdatePolicy.*;
import static org.junit.jupiter.api.Assertions.*;

class PositionMaintenanceTest {
    @Test void flat_maintenance_is_audited_without_entry_fill_or_rejection() {
        var f = new Fixture(); f.open(1, 1.1); f.maintain("m", "", "owner", Order.Side.BUY, .01, KEEP);
        assertEquals(NO_POSITION, f.last().action()); assertNull(f.last().parentOrderId());
        assertEquals(0, f.last().quantity()); assertEquals(10000, f.last().equity());
        assertTrue(f.broker.getExecutions().isEmpty()); assertTrue(f.rejections.isEmpty());
        assertTrue(f.broker.getTargetDecisions().isEmpty());
    }
    @Test void same_side_reset_can_widen_without_resizing_and_keep_preserves_the_stop() {
        var f = new Fixture(); f.open(1, 1.1); f.enter("", Order.Side.BUY, .01); f.open(2, 1.104);
        f.maintain("reset", "", "owner", Order.Side.BUY, .02, RESET_FROM_OPEN);
        assertEquals(RESET_STOP, f.last().action()); assertEquals(1.0903, f.last().stopBefore(), 1e-12);
        assertEquals(1.0843, f.last().stopAfter(), 1e-12); assertEquals(10000, f.last().quantity(), 1e-8);
        assertEquals(10037, f.last().equity(), 1e-8);
        f.maintain("keep", "", "owner", Order.Side.BUY, .03, KEEP);
        assertEquals(HOLD, f.last().action()); assertEquals(1.0843, f.last().stopAfter(), 1e-12);
        assertEquals(1, f.broker.getExecutions().size());
    }
    @Test void opposite_signal_closes_owned_position_without_replacement_and_reconciles_final_account() {
        var f = new Fixture(); f.open(1, 1.1); f.enter("", Order.Side.BUY, .01); f.open(2, 1.106);
        f.maintain("close", "", "owner", Order.Side.SELL_SHORT, .02, RESET_FROM_OPEN); f.broker.finish();
        assertEquals(CLOSE_REVERSAL, f.last().action()); assertTrue(Double.isNaN(f.last().stopAfter()));
        assertEquals(2, f.broker.getExecutions().size()); assertTrue(f.broker.getOpenPositions().isEmpty());
        var exit = f.broker.getExecutions().getLast();
        assertEquals(TradingSimulator.Reason.SIGNAL_REVERSAL, exit.reason());
        assertEquals(57, exit.realizedPnl(), 1e-8); assertEquals(10057, f.broker.getCashBalance(), 1e-8);
        assertEquals(f.last().parentOrderId(), exit.parentOrderId());
        assertEquals(f.broker.getTotalEquity(), f.broker.getEquityCurve().getLast().equity());
    }
    @Test void immediate_reset_crossing_closes_once_as_stop_without_entry() {
        var f = new Fixture(); f.open(1, 1.1); f.enter("", Order.Side.BUY, .01); f.open(2, 1.104);
        f.maintain("reset", "", "owner", Order.Side.BUY, .0001, RESET_FROM_OPEN);
        assertEquals(RESET_STOP, f.last().action()); assertEquals(2, f.broker.getExecutions().size());
        assertEquals(TradingSimulator.Reason.STOP, f.broker.getExecutions().getLast().reason());
        assertEquals(37, f.broker.getRealizedPnL(), 1e-8); assertTrue(f.broker.getOpenPositions().isEmpty());
    }
    @Test void opening_gap_stop_precedes_maintenance_which_then_stays_flat() {
        var f = new Fixture(); f.open(1, 1.1); f.enter("", Order.Side.BUY, .01); f.open(2, 1.08);
        f.maintain("gap", "", "owner", Order.Side.BUY, .01, RESET_FROM_OPEN);
        assertEquals(NO_POSITION, f.last().action()); assertEquals(2, f.broker.getExecutions().size());
        assertEquals(-203, f.broker.getRealizedPnL(), 1e-8); assertTrue(f.broker.getOpenPositions().isEmpty());
    }
    @Test void foreign_source_and_unmanaged_balance_are_rejected_without_mutation() {
        var f = new Fixture(); f.open(1, 1.1); f.enter("", Order.Side.BUY, .01);
        var before = f.broker.getOpenPositions();
        f.maintain("foreign", "", "stranger", Order.Side.SELL_SHORT, .02, RESET_FROM_OPEN);
        assertEquals(1, f.rejections.size()); assertEquals(before, f.broker.getOpenPositions());
        for (String currency : List.of("", "USD", "EUR")) {
            var g = new Fixture(); g.open(1, 1.1); g.plain(currency);
            g.maintain("unmanaged", "", "owner", Order.Side.SELL_SHORT, .01, RESET_FROM_OPEN);
            assertEquals(1, g.rejections.size(), currency); assertEquals(1, g.broker.getExecutions().size());
            assertEquals(Order.Side.BUY, g.broker.getOpenPositions().getFirst().side());
        }
    }
    @Test void opposite_accounts_preserve_independent_ownership_and_shared_marked_equity() {
        var f = new Fixture(ProfitConversion.QUOTE_CURRENCY); f.open(1, 1.1);
        f.enter("long", Order.Side.BUY, .005); f.enter("short", Order.Side.SELL_SHORT, .005); f.open(2, 1.105);
        var shortBefore = f.broker.getOpenPositions().stream().filter(p -> p.accountId().equals("short")).findFirst().orElseThrow();
        double before = f.broker.getTotalEquity();
        f.maintain("long-close", "long", "owner", Order.Side.SELL_SHORT, .01, KEEP);
        f.broker.finish();
        assertEquals(before, f.broker.getTotalEquity(), 1e-8);
        assertEquals(1, f.broker.getOpenPositions().size()); assertEquals(shortBefore, f.broker.getOpenPositions().getFirst());
        assertEquals(10000 + f.broker.getRealizedPnL() + f.broker.getUnrealizedPnL(), f.broker.getTotalEquity(), 1e-8);
        assertEquals(f.broker.getTotalEquity(), f.broker.getEquityCurve().getLast().equity());
    }
    @Test void base_currency_reversal_uses_executable_exit_conversion_and_no_new_stop() {
        var f = new Fixture(ProfitConversion.BASE_CURRENCY); f.open(1, 1.1); f.enter("leg", Order.Side.BUY, .01); f.open(2, 1.106);
        double quantity = f.broker.getOpenPositions().getFirst().quantity();
        f.maintain("close", "leg", "owner", Order.Side.SELL_SHORT, 100, RESET_FROM_OPEN);
        assertEquals(quantity * (1.106 - 1.1003) / 1.106, f.broker.getRealizedPnL(), 1e-8);
        assertEquals(2, f.broker.getExecutions().size()); assertTrue(f.rejections.isEmpty());
    }
    @Test void base_currency_invalid_reset_preserves_stop_but_keep_ignores_unused_candidate_price() {
        var f = new Fixture(ProfitConversion.BASE_CURRENCY); f.open(1, 1.1); f.enter("leg", Order.Side.BUY, .01);
        double before = f.broker.getOpenPositions().getFirst().stopPrice();
        f.maintain("bad", "leg", "owner", Order.Side.BUY, 2, RESET_FROM_OPEN);
        assertEquals(1, f.rejections.size()); assertEquals(before, f.broker.getOpenPositions().getFirst().stopPrice());
        f.maintain("keep", "leg", "owner", Order.Side.BUY, 2, KEEP);
        assertEquals(HOLD, f.last().action()); assertEquals(before, f.last().stopAfter());
    }
    @Test void duplicate_maintenance_ID_is_rejected_after_one_audited_action() {
        var f = new Fixture(); f.open(1, 1.1);
        f.maintain("same", "", "owner", Order.Side.BUY, .01, KEEP);
        f.maintain("same", "", "owner", Order.Side.BUY, .01, KEEP);
        assertEquals(1, f.rejections.size()); assertEquals(1, f.broker.getMaintenanceDecisions().size());
    }
    @Test void future_request_waits_for_eligible_opening_and_is_not_silently_dropped() {
        var f = new Fixture(); f.open(1, 1.1);
        var future = new PositionMaintenance(f.open.time() + 10, "future", "owner", "SIMULATOR", "", EURUSD, Order.Side.BUY, .01, KEEP);
        f.broker.onPositionMaintenance(future); f.broker.matchOpeningOrders(f.open, 0);
        assertTrue(f.broker.getMaintenanceDecisions().isEmpty()); f.open(2, 1.2);
        assertEquals(NO_POSITION, f.last().action()); assertEquals(f.open.time(), f.last().time());
    }
    @Test void quote_callback_maintenance_is_deferred_even_if_same_time_opening_follows() {
        var f = new Fixture(); f.open(1, 1.1);
        var request = new PositionMaintenance(f.open.time(), "defer", "owner", "SIMULATOR", "", EURUSD, Order.Side.BUY, .01, KEEP);
        f.broker.collectQuoteCallbackRequests(f.open.time(), () -> f.broker.onPositionMaintenance(request));
        f.broker.matchOpeningOrders(f.open, 0); assertTrue(f.broker.getMaintenanceDecisions().isEmpty());
        f.open(2, 1.1); assertEquals(1, f.broker.getMaintenanceDecisions().size());
    }
    @Test void raw_completed_bar_rejects_maintenance_without_causal_opening() {
        var f = new Fixture();
        f.broker.onPositionMaintenance(new PositionMaintenance(0, "raw", "owner", "SIMULATOR", "", EURUSD, Order.Side.BUY, .01, KEEP));
        var bar = new TradeBar.Of(EURUSD, Candle.of(10, 1.1)); f.clock.setTime(10); f.broker.onMarketMessage(bar);
        assertEquals(1, f.rejections.size()); assertTrue(f.rejections.getFirst().rejectionReason().contains("BarOpen"));
        assertTrue(f.broker.getMaintenanceDecisions().isEmpty()); assertEquals(0, f.broker.finish());
    }
    @Test void invalid_distance_is_rejected_at_construction_and_other_fields_by_broker() {
        for (double distance : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThrows(IllegalArgumentException.class, () -> new PositionMaintenance(0, "m", "owner", "SIMULATOR", "", EURUSD, Order.Side.BUY, distance, KEEP));
        var f = new Fixture(); f.open(1, 1.1);
        for (var side : new Order.Side[]{null, Order.Side.SELL, Order.Side.BUY_TO_COVER})
            f.maintain("bad-" + side, "", "owner", side, .01, KEEP);
        f.maintain("bad-policy", "", "owner", Order.Side.BUY, .01, null);
        f.maintain("", "", "owner", Order.Side.BUY, .01, KEEP);
        f.maintain("source", "", "", Order.Side.BUY, .01, KEEP);
        f.broker.onPositionMaintenance(new PositionMaintenance(0, "symbol", "owner", "SIMULATOR", "", null, Order.Side.BUY, .01, KEEP));
        assertEquals(7, f.rejections.size()); assertTrue(f.broker.getExecutions().isEmpty());
    }
    @Test void algorithm_engine_proxy_and_result_route_maintenance_end_to_end() {
        var result = new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("maintenance-route", Route::new,
                () -> new BarReplayMarketSupplier(Map.of(EURUSD, List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1),
                        bar("2024-04-02", 1.104, 1.105, 1.103, 1.104))), WARSAW),
                new BacktestExecutionOptions(10000, SPREAD, BacktestExecutionOptions.EndOfDataPolicy.KEEP_OPEN)));
        assertEquals(2, result.executions().size()); assertEquals(1, result.maintenanceDecisions().size());
        assertEquals(CLOSE_REVERSAL, result.maintenanceDecisions().getFirst().action());
        assertEquals(10037, result.finalEquity(), 1e-8); assertTrue(result.openPositions().isEmpty());
    }
    @Test void native_quote_route_closes_at_current_bid_without_fixed_spread_or_candle_extrema() {
        var quotes = List.of(QuoteExecutionTest.q(1, 0, 1.1, 1.1004), QuoteExecutionTest.q(2, 0, 1.104, 1.108));
        var result = new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("native-maintenance-route", Route::new,
                () -> new QuoteReplayMarketSupplier(Map.of(EURUSD, () -> QuoteExecutionTest.source(quotes)), QuoteExecutionTest.DAILY),
                QuoteExecutionTest.nativeOptions()));
        assertEquals(2, result.executions().size()); assertEquals(1.104, result.executions().getLast().fill().tradePrice());
        assertEquals(quotes.getLast().time(), result.executions().getLast().fill().time());
        assertEquals(10036, result.finalEquity(), 1e-8); assertEquals(CLOSE_REVERSAL, result.maintenanceDecisions().getFirst().action());
    }

    static final class Route extends AbstractAlgorithm<SimpleInstrumentPrices> {
        int opens;
        Route(AlgorithmContext context) { super(context); }
        @Override protected InstrumentDataFactory<SimpleInstrumentPrices> createInstrumentDataFactory() { return SimpleInstrumentPrices::new; }
        @Override public void onMarketMessage(MarketEvent event) {
            super.onMarketMessage(event);
            if (event instanceof BarOpen) {
                if (++opens == 1) submitPositionTarget(makePositionTarget(Order.Side.BUY, EURUSD, .01, .01, KEEP, "SIMULATOR"));
                else submitPositionMaintenance(new PositionMaintenance(event.time(), generateOrderId(), getId(), "SIMULATOR", null, EURUSD, Order.Side.SELL_SHORT, .01, KEEP));
            }
        }
        @Override public void onOrderRejected(Order.Rejected rejection) { fail(rejection.rejectionReason()); }
    }
    static final class Fixture implements OrderReportHandler {
        final PlaybackClock clock = new PlaybackClock(ZoneOffset.UTC, 0);
        final List<Order.Rejected> rejections = new ArrayList<>();
        final TradingSimulator broker; BarOpen open; int ids;
        Fixture() { this(null); }
        Fixture(ProfitConversion sharedConversion) {
            var options = new BacktestExecutionOptions(10000, SPREAD, BacktestExecutionOptions.EndOfDataPolicy.KEEP_OPEN,
                    sharedConversion == null ? BacktestExecutionOptions.CapitalMode.INDEPENDENT_ACCOUNTS : BacktestExecutionOptions.CapitalMode.SHARED,
                    sharedConversion == null ? ProfitConversion.QUOTE_CURRENCY : sharedConversion);
            broker = new TradingSimulator(new TradeConnectorContext("SIMULATOR", clock, new PlaybackMarketPriceService(), this), options);
        }
        void open(int day, double price) {
            open = (BarOpen) replay(Map.of(EURUSD, List.of(bar("2024-04-%02d".formatted(day), price, price, price, price))), 100).getFirst();
            clock.setTime(open.time()); broker.onMarketMessage(open);
        }
        void enter(String account, Order.Side side, double risk) {
            broker.onPositionTarget(new PositionTarget(open.time(), "entry-" + ++ids, "owner", "SIMULATOR", account, EURUSD, side, risk, .01, KEEP));
            broker.matchOpeningOrders(open, 0);
        }
        void plain(String currency) {
            broker.onOrderPlacement(new Order.Builder().time(open.time()).orderId("plain").sourceId("owner").destinationId("SIMULATOR")
                    .symbol(EURUSD).side(Order.Side.BUY).type(OrderType.MARKET).quantity(10000).currency(currency).toNewOrder());
            broker.matchOpeningOrders(open, 0);
        }
        void maintain(String id, String account, String owner, Order.Side side, double distance, PositionTarget.StopUpdatePolicy policy) {
            broker.onPositionMaintenance(new PositionMaintenance(open.time(), id, owner, "SIMULATOR", account, EURUSD, side, distance, policy));
            broker.matchOpeningOrders(open, 0);
        }
        TradingSimulator.MaintenanceDecision last() { return broker.getMaintenanceDecisions().getLast(); }
        public void onOrderRejected(Order.Rejected rejection) { rejections.add(rejection); }
        public void onOrderStatusChanged(Order.StatusChanged event) { }
        public void onOrderFilled(Order.Filled event) { }
        public void onOrderPartiallyFilled(Order.PartiallyFilled event) { }
    }
}
