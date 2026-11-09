package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.trade.Order;
import one.chartsy.trade.PositionTarget;
import one.chartsy.trade.algorithm.AbstractAlgorithm;
import one.chartsy.trade.algorithm.AlgorithmContext;
import one.chartsy.trade.algorithm.data.InstrumentDataFactory;
import one.chartsy.trade.algorithm.data.SimpleInstrumentPrices;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static one.chartsy.simulation.engine.CausalBarBacktestTest.*;
import static one.chartsy.trade.PositionTarget.IntrabarExitPolicy.*;
import static one.chartsy.trade.PositionTarget.StopUpdatePolicy.*;
import static org.junit.jupiter.api.Assertions.*;

class PersistentBracketBacktestTest {
    record Signal(Order.Side side, double stop, double takeProfit,
                  PositionTarget.StopUpdatePolicy update, PositionTarget.IntrabarExitPolicy intrabar) {
        Signal(Order.Side side, double stop, double takeProfit) {
            this(side, stop, takeProfit, RESET_FROM_OPEN, STOP_FIRST);
        }
    }
    record Run(AlgorithmBacktestResult result, Fixture algorithm) { }

    @Test void legacyConstructor_disables_take_profit_and_defaults_to_stop_first() {
        var request = new PositionTarget(1, "order", "source", "SIMULATOR", null, EURUSD,
                Order.Side.BUY, .01, .01, RESET_FROM_OPEN);
        assertEquals(0, request.takeProfitDistance());
        assertEquals(STOP_FIRST, request.intrabarExitPolicy());
    }

    @Test void explicitDisabledBracket_matches_legacy_stop_only_execution_and_evidence() {
        var bars = List.of(bar("2024-04-01", 1.1, 1.103, 1.099, 1.102),
                bar("2024-04-02", 1.104, 1.108, 1.102, 1.107),
                bar("2024-04-03", 1.107, 1.11, 1.10, 1.105));
        var signals = List.of(new Signal(Order.Side.BUY, .01, 0), new Signal(Order.Side.BUY, .02, 0),
                new Signal(Order.Side.SELL_SHORT, .01, 0));
        var legacy = run(bars, signals, true).result;
        var explicit = run(bars, signals, false).result;
        assertEquals(legacy.executions(), explicit.executions());
        assertEquals(legacy.targetDecisions(), explicit.targetDecisions());
        assertEquals(legacy.openPositions(), explicit.openPositions());
        assertEquals(legacy.equityCurve(), explicit.equityCurve());
        assertEquals(legacy.finalEquity(), explicit.finalEquity());
    }

    @Test void longTakeProfit_is_active_on_entry_bar_and_retires_the_stop() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.121, 1.099, 1.12),
                        bar("2024-04-02", 1.1, 1.2, .5, 1.1)),
                new Signal(Order.Side.BUY, .01, .02)).result;
        assertEquals(2, result.executions().size());
        var exit = result.executions().getLast();
        assertEquals(TradingSimulator.Reason.TAKE_PROFIT, exit.reason());
        assertEquals(1.1203, exit.fill().tradePrice(), 1e-12);
        assertEquals(200, result.realizedPnl(), 1e-8);
        assertEquals(10200, result.finalEquity(), 1e-8);
        assertTrue(result.openPositions().isEmpty());
    }

    @Test void shortTakeProfit_requires_the_ask_low_to_cross_its_level() {
        var first = bar("2024-04-01", 1.1, 1.101, 1.0798, 1.09);
        var signal = new Signal(Order.Side.SELL_SHORT, .01, .02);
        var held = run(List.of(first), signal).result;
        assertEquals(1, held.executions().size(), "bid low alone is insufficient");
        assertEquals(1.08, held.openPositions().getFirst().takeProfitPrice(), 1e-12);
        var closed = run(List.of(first, bar("2024-04-02", 1.09, 1.091, 1.0796, 1.08)), signal).result;
        assertEquals(2, closed.executions().size());
        assertEquals(TradingSimulator.Reason.TAKE_PROFIT, closed.executions().getLast().reason());
        assertEquals(1.08, closed.executions().getLast().fill().tradePrice(), 1e-12);
        assertEquals(200, closed.realizedPnl(), 1e-8);
    }

    @Test void bothTouched_selects_only_the_declared_intrabar_assumption_for_either_side() {
        for (var side : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
            for (var policy : PositionTarget.IntrabarExitPolicy.values()) {
                var result = run(List.of(bar("2024-04-01", 1.1, 1.13, 1.07, 1.1)),
                        new Signal(side, .01, .02, RESET_FROM_OPEN, policy)).result;
                assertEquals(2, result.executions().size());
                assertEquals(policy == STOP_FIRST ? TradingSimulator.Reason.STOP : TradingSimulator.Reason.TAKE_PROFIT,
                        result.executions().getLast().reason());
                assertEquals(policy == STOP_FIRST ? -100 : 200, result.realizedPnl(), 1e-8);
            }
        }
    }

    @Test void openingGap_through_take_profit_fills_better_quote_before_new_signal_and_later_range() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1),
                        bar("2024-04-02", 1.13, 1.132, 1.05, 1.12)),
                new Signal(Order.Side.BUY, .01, .02), new Signal(Order.Side.SELL_SHORT, .10, .20)).result;
        assertEquals(List.of(TradingSimulator.Reason.ENTRY, TradingSimulator.Reason.TAKE_PROFIT, TradingSimulator.Reason.ENTRY),
                result.executions().stream().map(TradingSimulator.Execution::reason).toList());
        var exit = result.executions().get(1);
        assertEquals(1.13, exit.fill().tradePrice(), 1e-12);
        assertEquals(297, exit.realizedPnl(), 1e-8);
        assertEquals(exit.fill().time(), result.executions().get(2).fill().time());
        assertEquals(10297, result.targetDecisions().getLast().equity(), 1e-8);
        assertEquals(1029.7, result.openPositions().getFirst().quantity(), 1e-8);
        assertEquals(TradingSimulator.TargetAction.OPEN, result.targetDecisions().getLast().action());
    }

    @Test void openingGap_through_short_take_profit_uses_the_better_ask() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1),
                        bar("2024-04-02", 1.07, 1.14, 1.06, 1.1)),
                new Signal(Order.Side.SELL_SHORT, .01, .02)).result;
        assertEquals(2, result.executions().size());
        assertEquals(TradingSimulator.Reason.TAKE_PROFIT, result.executions().getLast().reason());
        assertEquals(1.0703, result.executions().getLast().fill().tradePrice(), 1e-12);
        assertEquals(297, result.realizedPnl(), 1e-8);
    }

    @Test void openingStop_precedes_later_target_touch_even_with_take_profit_first_policy() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1),
                        bar("2024-04-02", 1.08, 1.14, 1.075, 1.10)),
                new Signal(Order.Side.BUY, .01, .02, RESET_FROM_OPEN, TAKE_PROFIT_FIRST)).result;
        assertEquals(2, result.executions().size());
        assertEquals(TradingSimulator.Reason.STOP, result.executions().getLast().reason());
        assertEquals(1.08, result.executions().getLast().fill().tradePrice(), 1e-12);
        assertEquals(-203, result.realizedPnl(), 1e-8);
    }

    @Test void sameSideReset_replaces_both_levels_without_resizing_or_changing_parent() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1),
                        bar("2024-04-02", 1.104, 1.105, 1.102, 1.104)),
                new Signal(Order.Side.BUY, .01, .02),
                new Signal(Order.Side.BUY, .02, .03, RESET_FROM_OPEN, TAKE_PROFIT_FIRST)).result;
        assertEquals(1, result.executions().size());
        var held = result.openPositions().getFirst();
        assertEquals(10000, held.quantity(), 1e-8);
        assertEquals(1.1003, held.entryPrice(), 1e-12);
        assertEquals(1.0843, held.stopPrice(), 1e-12);
        assertEquals(1.1343, held.takeProfitPrice(), 1e-12);
        var reset = result.targetDecisions().getLast();
        assertEquals(TradingSimulator.TargetAction.RESET_BRACKET, reset.action());
        assertEquals(result.targetDecisions().getFirst().parentOrderId(), reset.parentOrderId());
        assertEquals(1.1203, reset.takeProfitBefore(), 1e-12);
        assertEquals(1.1343, reset.takeProfitAfter(), 1e-12);
        assertEquals(TAKE_PROFIT_FIRST, reset.intrabarExitPolicy());
        assertTrue(Double.isNaN(reset.riskBudget()));
    }

    @Test void keepAndNeutral_preserve_both_levels_and_the_active_intrabar_policy() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1),
                        bar("2024-04-02", 1.104, 1.105, 1.102, 1.104),
                        bar("2024-04-03", 1.104, 1.105, 1.102, 1.104)),
                new Signal(Order.Side.BUY, .01, .02),
                new Signal(Order.Side.BUY, .02, .03, KEEP, TAKE_PROFIT_FIRST)).result;
        assertEquals(1, result.executions().size());
        assertEquals(2, result.targetDecisions().size(), "neutral opening submits nothing");
        assertEquals(1.0903, result.openPositions().getFirst().stopPrice(), 1e-12);
        assertEquals(1.1203, result.openPositions().getFirst().takeProfitPrice(), 1e-12);
        assertEquals(STOP_FIRST, result.targetDecisions().getLast().intrabarExitPolicy());
        assertEquals(TradingSimulator.TargetAction.HOLD, result.targetDecisions().getLast().action());
    }

    @Test void malformedReset_is_rejected_before_either_existing_level_changes() {
        for (double distance : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY}) {
            var run = run(List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1),
                            bar("2024-04-02", 1.104, 1.105, 1.102, 1.104)),
                    new Signal(Order.Side.BUY, .01, .02), new Signal(Order.Side.BUY, .02, distance));
            assertEquals(1, run.algorithm.rejections.size());
            assertEquals(1, run.result.executions().size());
            assertEquals(1, run.result.targetDecisions().size());
            assertEquals(1.0903, run.result.openPositions().getFirst().stopPrice(), 1e-12);
            assertEquals(1.1203, run.result.openPositions().getFirst().takeProfitPrice(), 1e-12);
        }
    }

    @Test void tighten_changes_only_stop_and_rejects_a_new_take_profit_distance() {
        var bars = List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1),
                bar("2024-04-02", 1.104, 1.105, 1.102, 1.104));
        var initial = new Signal(Order.Side.BUY, .01, .02);
        var result = run(bars, initial, new Signal(Order.Side.BUY, .01, 0, TIGHTEN_FROM_OPEN, STOP_FIRST)).result;
        assertEquals(1.0943, result.openPositions().getFirst().stopPrice(), 1e-12);
        assertEquals(1.1203, result.openPositions().getFirst().takeProfitPrice(), 1e-12);
        assertEquals(TradingSimulator.TargetAction.RESET_STOP, result.targetDecisions().getLast().action());
        var rejected = run(bars, initial, new Signal(Order.Side.BUY, .01, .03, TIGHTEN_FROM_OPEN, STOP_FIRST));
        assertEquals(1, rejected.algorithm.rejections.size());
        assertEquals(1.0903, rejected.result.openPositions().getFirst().stopPrice(), 1e-12);
    }

    @Test void explicitStopOnlyReset_removes_take_profit_without_resizing() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1),
                        bar("2024-04-02", 1.104, 1.13, 1.102, 1.12)),
                new Signal(Order.Side.BUY, .01, .02), new Signal(Order.Side.BUY, .02, 0)).result;
        assertEquals(1, result.executions().size());
        assertTrue(Double.isNaN(result.openPositions().getFirst().takeProfitPrice()));
        assertEquals(10000, result.openPositions().getFirst().quantity(), 1e-8);
        assertEquals(TradingSimulator.TargetAction.RESET_BRACKET, result.targetDecisions().getLast().action());
    }

    @Test void immediatelyMarketableReset_closes_retained_units_once_and_retires_take_profit() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1),
                        bar("2024-04-02", 1.104, 1.11, 1.102, 1.104)),
                new Signal(Order.Side.BUY, .01, .02), new Signal(Order.Side.BUY, .0001, .001)).result;
        assertEquals(2, result.executions().size());
        assertEquals(TradingSimulator.Reason.STOP, result.executions().getLast().reason());
        assertEquals(10000, result.executions().getLast().fill().tradeQuantity(), 1e-8);
        assertEquals(37, result.realizedPnl(), 1e-8);
        assertEquals(result.targetDecisions().getLast().time(), result.executions().getLast().fill().time());
        assertTrue(result.openPositions().isEmpty());
    }

    @Test void reversal_retires_old_bracket_before_sizing_new_risk() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1),
                        bar("2024-04-02", 1.106, 1.125, 1.102, 1.11)),
                new Signal(Order.Side.BUY, .01, .02), new Signal(Order.Side.SELL_SHORT, .03, .04)).result;
        assertEquals(List.of(TradingSimulator.Reason.ENTRY, TradingSimulator.Reason.SIGNAL_REVERSAL, TradingSimulator.Reason.ENTRY),
                result.executions().stream().map(TradingSimulator.Execution::reason).toList());
        assertEquals(57, result.realizedPnl(), 1e-8);
        var held = result.openPositions().getFirst();
        assertEquals(100.57 / .03, held.quantity(), 1e-8);
        assertEquals(1.136, held.stopPrice(), 1e-12);
        assertEquals(1.066, held.takeProfitPrice(), 1e-12);
        assertNotEquals(result.executions().getFirst().parentOrderId(), held.parentOrderId());
    }

    @Test void orderedQuoteTakeProfit_uses_first_crossing_price_and_ignores_bar_policy() {
        for (var policy : PositionTarget.IntrabarExitPolicy.values()) {
            var quotes = List.of(QuoteExecutionTest.q(1, 0, 1.1, 1.1004),
                    QuoteExecutionTest.q(1, 1, 1.123, 1.1237), QuoteExecutionTest.q(1, 2, 1.08, 1.0805));
            var result = new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("bracket quote fixture",
                    context -> new Fixture(context, List.of(new Signal(Order.Side.BUY, .01, .02, RESET_FROM_OPEN, policy)), false),
                    () -> new QuoteReplayMarketSupplier(Map.of(EURUSD, () -> QuoteExecutionTest.source(quotes)), QuoteExecutionTest.DAILY),
                    QuoteExecutionTest.nativeOptions()));
            assertEquals(2, result.executions().size());
            assertEquals(TradingSimulator.Reason.TAKE_PROFIT, result.executions().getLast().reason());
            assertEquals(quotes.get(1).time(), result.executions().getLast().fill().time());
            assertEquals(1.123, result.executions().getLast().fill().tradePrice(), 1e-12);
            assertEquals(226, result.realizedPnl(), 1e-8);
        }
    }

    static Run run(List<Candle> bars, Signal... signals) { return run(bars, List.of(signals), false); }

    static Run run(List<Candle> bars, List<Signal> signals, boolean legacy) {
        var holder = new ArrayList<Fixture>();
        var result = new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("bracket fixture", context -> {
            var fixture = new Fixture(context, signals, legacy);
            holder.add(fixture);
            return fixture;
        }, () -> new BarReplayMarketSupplier(Map.of(EURUSD, bars), WARSAW),
                new BacktestExecutionOptions(10000, SPREAD, BacktestExecutionOptions.EndOfDataPolicy.KEEP_OPEN)));
        return new Run(result, holder.getFirst());
    }

    static class Fixture extends AbstractAlgorithm<SimpleInstrumentPrices> {
        final List<Signal> signals;
        final boolean legacy;
        final List<Order.Rejected> rejections = new ArrayList<>();
        int openings;
        Fixture(AlgorithmContext context, List<Signal> signals, boolean legacy) {
            super(context);
            this.signals = signals;
            this.legacy = legacy;
        }
        @Override protected InstrumentDataFactory<SimpleInstrumentPrices> createInstrumentDataFactory() {
            return SimpleInstrumentPrices::new;
        }
        @Override public void onMarketMessage(MarketEvent event) {
            super.onMarketMessage(event);
            if (event instanceof BarOpen && openings < signals.size()) {
                var signal = signals.get(openings++);
                var target = legacy
                        ? makePositionTarget(signal.side, event.symbol(), .01, signal.stop, signal.update, "SIMULATOR")
                        : makePositionTarget(signal.side, event.symbol(), .01, signal.stop, signal.takeProfit,
                                signal.update, signal.intrabar, "SIMULATOR");
                submitPositionTarget(target);
            }
        }
        @Override public void onOrderRejected(Order.Rejected rejection) { rejections.add(rejection); }
    }
}
