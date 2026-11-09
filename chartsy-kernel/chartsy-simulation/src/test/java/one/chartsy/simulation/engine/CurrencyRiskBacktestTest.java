package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.trade.Order;
import one.chartsy.trade.PositionTarget;
import one.chartsy.trade.account.ProfitConversion;
import one.chartsy.trade.algorithm.AbstractAlgorithm;
import one.chartsy.trade.algorithm.AlgorithmContext;
import one.chartsy.trade.algorithm.data.InstrumentDataFactory;
import one.chartsy.trade.algorithm.data.SimpleInstrumentPrices;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import static one.chartsy.simulation.engine.CausalBarBacktestTest.*;
import static one.chartsy.simulation.engine.BacktestExecutionOptions.CapitalMode.*;
import static one.chartsy.simulation.engine.BacktestExecutionOptions.EndOfDataPolicy.*;
import static one.chartsy.trade.PositionTarget.StopUpdatePolicy.*;
import static org.junit.jupiter.api.Assertions.*;

class CurrencyRiskBacktestTest {
    private static final SymbolIdentity USDCHF = SymbolIdentity.of("USDCHF");
    private record Signal(String account, Order.Side side, double risk, double distance) { }

    @Test void long_and_short_no_gap_stops_lose_exactly_one_percent_in_account_currency() {
        for (var side : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
            var result = run(List.of(bar("2024-04-01", 1, 1.02, .98, 1.002)),
                    List.of(List.of(new Signal(null, side, .01, .01))), INDEPENDENT_ACCOUNTS);
            var target = result.targetDecisions().getFirst();
            double stop = side == Order.Side.BUY ? 1.0003 - .01 : 1.01;
            assertEquals(100 * stop / .01, target.quantity(), 1e-9);
            assertEquals(100, target.riskBudget(), 1e-9);
            assertEquals(-100, result.realizedPnl(), 1e-9);
            assertEquals(9900, result.finalEquity(), 1e-9);
            assertTrue(result.openPositions().isEmpty());
        }
    }

    @Test void gap_exposure_is_converted_at_actual_fill_and_can_exceed_initial_risk() {
        var result = run(List.of(bar("2024-04-01", 1, 1.002, .999, 1.001),
                        bar("2024-04-02", .97, .975, .96, .974)),
                List.of(List.of(new Signal(null, Order.Side.BUY, .01, .01)), List.of()), INDEPENDENT_ACCOUNTS);
        double quantity = 100 * .9903 / .01;
        double expected = (.97 - 1.0003) * quantity / .97;
        assertEquals(expected, result.realizedPnl(), 1e-9);
        assertTrue(result.realizedPnl() < -100);
        assertEquals(10000 + expected, result.finalEquity(), 1e-9);
    }

    @Test void reversing_settles_converted_cash_before_sizing_the_new_position() {
        var result = run(List.of(bar("2024-04-01", 1, 1.007, .999, 1.006),
                        bar("2024-04-02", 1.005, 1.007, 1.003, 1.004)),
                List.of(List.of(new Signal(null, Order.Side.BUY, .01, .01)),
                        List.of(new Signal(null, Order.Side.SELL_SHORT, .01, .01))), INDEPENDENT_ACCOUNTS);
        double firstQuantity = 100 * .9903 / .01;
        double realized = (1.005 - 1.0003) * firstQuantity / 1.005;
        var second = result.targetDecisions().getLast();
        assertEquals(10000 + realized, second.equity(), 1e-9);
        assertEquals(second.equity() * .01 * 1.015 / .01, second.quantity(), 1e-9);
        double openPnl = (1.005 - 1.0043) * second.quantity() / 1.0043;
        assertEquals(realized, result.realizedPnl(), 1e-9);
        assertEquals(openPnl, result.unrealizedPnl(), 1e-9);
        assertEquals(10000 + realized + openPnl, result.finalEquity(), 1e-9);
        assertEquals(result.finalEquity(), result.report().equitySummary().getEndingEquity(), 1e-9);
    }

    @Test void shared_positions_use_converted_aggregate_marks_and_keep_independent_stops() {
        var result = run(List.of(bar("2024-04-01", 1, 1.002, .999, 1.001)),
                List.of(List.of(new Signal("long", Order.Side.BUY, .005, .01), new Signal("short", Order.Side.SELL_SHORT, .005, .01))), SHARED);
        var first = result.targetDecisions().getFirst(); var second = result.targetDecisions().getLast();
        assertEquals(10000 - .0003 * first.quantity(), second.equity(), 1e-9);
        double pnl = (1.001 - 1.0003) * first.quantity() / 1.001 + (1 - 1.0013) * second.quantity() / 1.0013;
        assertEquals(pnl, result.unrealizedPnl(), 1e-9);
        assertEquals(10000 + pnl, result.finalEquity(), 1e-9);
        assertEquals(2, result.openPositions().size());
    }

    @Test void invalid_base_currency_stop_is_rejected_before_opening_or_changing_an_owned_position() {
        var rejected = new ArrayList<Order.Rejected>();
        var empty = run(List.of(bar("2024-04-01", 1, 1.002, .999, 1.001)),
                List.of(List.of(new Signal(null, Order.Side.BUY, .01, 2))), INDEPENDENT_ACCOUNTS, rejected);
        assertEquals(1, rejected.size()); assertTrue(empty.executions().isEmpty());
        assertEquals(10000, empty.finalEquity());
        for (var firstSide : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
            rejected.clear();
            var held = run(List.of(bar("2024-04-01", 1, 1.002, .999, 1.001),
                            bar("2024-04-02", 1.005, 1.02, .98, 1.001)),
                    List.of(List.of(new Signal(null, firstSide, .01, .01)),
                            List.of(new Signal(null, Order.Side.BUY, .01, 2))), INDEPENDENT_ACCOUNTS, rejected);
            assertEquals(1, rejected.size()); assertEquals(1, held.targetDecisions().size());
            assertEquals(TradingSimulator.Reason.STOP, held.executions().getLast().reason());
            assertEquals(-100, held.realizedPnl(), 1e-9);
            assertTrue(held.openPositions().isEmpty());
        }
    }

    private static AlgorithmBacktestResult run(List<Candle> bars, List<List<Signal>> signals, BacktestExecutionOptions.CapitalMode mode) {
        var rejections = new ArrayList<Order.Rejected>();
        var result = run(bars, signals, mode, rejections);
        assertTrue(rejections.isEmpty(), rejections.toString());
        return result;
    }

    private static AlgorithmBacktestResult run(List<Candle> bars, List<List<Signal>> signals, BacktestExecutionOptions.CapitalMode mode, List<Order.Rejected> rejections) {
        var config = AlgorithmBacktestContext.builder().algorithmName("currency-risk")
                .algorithmFactory(context -> new Signals(context, signals, rejections))
                .marketSupplierFactory(() -> new BarReplayMarketSupplier(Map.of(USDCHF, bars), BarSession.daily(WARSAW)))
                .executionOptions(new BacktestExecutionOptions(10000, .0003, KEEP_OPEN, mode, ProfitConversion.BASE_CURRENCY)).build();
        return new AlgorithmBacktestRunner().runDetailed(config);
    }

    private static final class Signals extends AbstractAlgorithm<SimpleInstrumentPrices> {
        private final List<List<Signal>> signals;
        private int index;
        private final List<Order.Rejected> rejections;
        Signals(AlgorithmContext context, List<List<Signal>> signals, List<Order.Rejected> rejections) { super(context); this.signals = signals; this.rejections = rejections; }
        @Override protected InstrumentDataFactory<SimpleInstrumentPrices> createInstrumentDataFactory() { return SimpleInstrumentPrices::new; }
        @Override public void onMarketMessage(MarketEvent event) {
            super.onMarketMessage(event);
            if (event instanceof BarOpen open && index < signals.size()) {
                for (var signal : signals.get(index++)) submitPositionTarget(new PositionTarget(open.time(), generateOrderId(), getId(), "SIMULATOR",
                        signal.account(), open.symbol(), signal.side(), signal.risk(), signal.distance(), RESET_FROM_OPEN));
            }
        }
        @Override public void onOrderRejected(Order.Rejected rejection) { rejections.add(rejection); }
    }
}
