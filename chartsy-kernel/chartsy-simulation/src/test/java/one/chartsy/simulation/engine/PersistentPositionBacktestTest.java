package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
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
import static one.chartsy.trade.PositionTarget.StopUpdatePolicy.*;
import static org.junit.jupiter.api.Assertions.*;

class PersistentPositionBacktestTest {
    record Signal(Order.Side side, double distance, double risk, PositionTarget.StopUpdatePolicy policy) {
        Signal(Order.Side side, double distance, PositionTarget.StopUpdatePolicy policy) { this(side, distance, .01, policy); }
    }
    record Run(AlgorithmBacktestResult result, TargetAlgorithm algorithm) { }

    @Test void initialEntry_risks_one_percent_of_ledger_equity_and_remains_open_at_EOF() {
        var run = run(List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1)),
                new Signal(Order.Side.BUY, .01, RESET_FROM_OPEN));
        var result = run.result;
        assertEquals(1, result.executions().size());
        assertEquals(1, result.openPositions().size());
        var position = result.openPositions().getFirst();
        assertEquals(10000, position.quantity(), 1e-8);
        assertEquals(1.1003, position.entryPrice(), 1e-12);
        assertEquals(1.0903, position.stopPrice(), 1e-12);
        assertEquals(-3, result.unrealizedPnl(), 1e-8);
        assertEquals(0, result.realizedPnl(), 1e-8);
        assertEquals(9997, result.finalEquity(), 1e-8);
        var decision = result.targetDecisions().getFirst();
        assertEquals(10000, decision.equity(), 1e-8);
        assertEquals(100, decision.riskBudget(), 1e-8);
        assertEquals(decision.riskBudget(), position.quantity() * (position.entryPrice() - position.stopPrice()), 1e-8);
        assertEquals(1, result.equityCurve().getLast().executionCount());
    }

    @Test void sameDirection_reset_can_widen_stop_but_never_resizes_or_reopens() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.103, 1.099, 1.102),
                        bar("2024-04-02", 1.104, 1.108, 1.102, 1.107)),
                new Signal(Order.Side.BUY, .01, RESET_FROM_OPEN),
                new Signal(Order.Side.BUY, .02, RESET_FROM_OPEN)).result;
        assertEquals(1, result.executions().size());
        var held = result.openPositions().getFirst();
        assertEquals(10000, held.quantity(), 1e-8);
        assertEquals(1.1003, held.entryPrice(), 1e-12);
        assertEquals(1.0843, held.stopPrice(), 1e-12);
        assertEquals(10067, result.finalEquity(), 1e-8);
        var reset = result.targetDecisions().getLast();
        assertEquals(TradingSimulator.TargetAction.RESET_STOP, reset.action());
        assertEquals(10037, reset.equity(), 1e-8, "risk decision sees today's executable quote, not yesterday's close");
        assertEquals(1.0903, reset.stopBefore(), 1e-12);
        assertTrue(Double.isNaN(reset.riskBudget()), "held positions have no new entry risk allocation");
    }

    @Test void sameDirection_keep_preserves_absolute_stop_and_quantity() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.103, 1.099, 1.102),
                        bar("2024-04-02", 1.104, 1.108, 1.102, 1.107)),
                new Signal(Order.Side.BUY, .01, RESET_FROM_OPEN),
                new Signal(Order.Side.BUY, .02, KEEP)).result;
        assertEquals(1, result.executions().size());
        assertEquals(1.0903, result.openPositions().getFirst().stopPrice(), 1e-12);
        assertEquals(10000, result.openPositions().getFirst().quantity(), 1e-8);
        assertEquals(TradingSimulator.TargetAction.HOLD, result.targetDecisions().getLast().action());
    }

    @Test void reversal_settles_old_position_before_sizing_new_risk_from_realized_equity() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.106, 1.099, 1.105),
                        bar("2024-04-02", 1.106, 1.108, 1.102, 1.103)),
                new Signal(Order.Side.BUY, .01, RESET_FROM_OPEN),
                new Signal(Order.Side.SELL_SHORT, .02, RESET_FROM_OPEN)).result;
        assertEquals(List.of(TradingSimulator.Reason.ENTRY, TradingSimulator.Reason.SIGNAL_REVERSAL, TradingSimulator.Reason.ENTRY),
                result.executions().stream().map(TradingSimulator.Execution::reason).toList());
        var reversal = result.executions().get(1);
        var replacement = result.executions().get(2);
        assertEquals(57, reversal.realizedPnl(), 1e-8);
        assertEquals(reversal.fill().time(), replacement.fill().time());
        assertEquals(5028.5, replacement.fill().tradeQuantity(), 1e-8);
        assertEquals(10057, result.targetDecisions().getLast().equity(), 1e-8);
        assertEquals(100.57, result.targetDecisions().getLast().riskBudget(), 1e-8);
        assertEquals(57, result.realizedPnl(), 1e-8);
        assertEquals(13.57695, result.unrealizedPnl(), 1e-8);
        assertEquals(10070.57695, result.finalEquity(), 1e-8);
        assertEquals(Order.Side.SELL_SHORT, result.openPositions().getFirst().side());
        assertEquals(3, result.equityCurve().getLast().executionCount());
    }

    @Test void gapStop_precedes_signal_and_new_entry_sizes_from_post_gap_account_equity() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.105, 1.095, 1.1),
                        bar("2024-04-02", 1.08, 1.085, 1.075, 1.08)),
                new Signal(Order.Side.BUY, .01, RESET_FROM_OPEN),
                new Signal(Order.Side.BUY, .01, RESET_FROM_OPEN)).result;
        assertEquals(3, result.executions().size());
        var stop = result.executions().get(1);
        var entry = result.executions().get(2);
        assertEquals(TradingSimulator.Reason.STOP, stop.reason());
        assertEquals(1.08, stop.fill().tradePrice(), 1e-12);
        assertEquals(-203, stop.realizedPnl(), 1e-8);
        assertEquals(stop.fill().time(), entry.fill().time());
        assertEquals(9797, entry.fill().tradeQuantity(), 1e-8);
        assertEquals(9797, result.targetDecisions().getLast().equity(), 1e-8);
        assertEquals(97.97, result.targetDecisions().getLast().riskBudget(), 1e-8);
        assertEquals(9794.0609, result.finalEquity(), 1e-8);
    }

    @Test void shortStop_uses_ask_high_and_loses_the_initial_one_percent_risk_without_gap() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.1008, 1.0998, 1.1)),
                new Signal(Order.Side.SELL_SHORT, .001, RESET_FROM_OPEN)).result;
        assertEquals(2, result.executions().size());
        assertEquals(TradingSimulator.Reason.STOP, result.executions().getLast().reason());
        assertEquals(-100, result.realizedPnl(), 1e-8);
        assertEquals(9900, result.finalEquity(), 1e-8);
        assertTrue(result.openPositions().isEmpty());
    }

    @Test void immediately_marketable_initial_stop_can_exceed_nominal_risk_due_to_spread() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1)),
                new Signal(Order.Side.BUY, .0001, RESET_FROM_OPEN)).result;
        assertEquals(2, result.executions().size());
        assertEquals(result.executions().getFirst().fill().time(), result.executions().getLast().fill().time());
        assertEquals(100, result.targetDecisions().getFirst().riskBudget(), 1e-8);
        assertEquals(-300, result.realizedPnl(), 1e-8);
        assertTrue(result.openPositions().isEmpty());
    }

    @Test void immediately_marketable_reset_stop_closes_existing_quantity_once_without_reentry() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.103, 1.099, 1.102),
                        bar("2024-04-02", 1.104, 1.108, 1.102, 1.107)),
                new Signal(Order.Side.BUY, .01, RESET_FROM_OPEN),
                new Signal(Order.Side.BUY, .0001, RESET_FROM_OPEN)).result;
        assertEquals(2, result.executions().size());
        assertEquals(TradingSimulator.Reason.STOP, result.executions().getLast().reason());
        assertEquals(10000, result.executions().getLast().fill().tradeQuantity(), 1e-8);
        assertEquals(37, result.realizedPnl(), 1e-8);
        assertTrue(result.openPositions().isEmpty());
    }

    @Test void opening_reversal_spends_exactly_one_spread_per_execution_round_trip() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1),
                        bar("2024-04-02", 1.1, 1.1, 1.1, 1.1)),
                new Signal(Order.Side.BUY, .01, KEEP), new Signal(Order.Side.SELL_SHORT, .01, KEEP)).result;
        assertEquals(-3, result.realizedPnl(), 1e-8);
        assertEquals(9997, result.openPositions().getFirst().quantity(), 1e-8);
        assertEquals(-2.9991, result.unrealizedPnl(), 1e-8);
        assertEquals(9994.0009, result.finalEquity(), 1e-8);
    }

    @Test void invalid_risk_side_or_stop_is_rejected_once_without_position_mutation() {
        for (var signal : List.of(new Signal(Order.Side.BUY, .01, 0, KEEP),
                new Signal(Order.Side.BUY, .01, 1.01, KEEP), new Signal(Order.Side.BUY, .01, Double.NaN, KEEP),
                new Signal(Order.Side.BUY, 0, KEEP), new Signal(Order.Side.BUY, Double.POSITIVE_INFINITY, KEEP),
                new Signal(Order.Side.SELL, .01, KEEP))) {
            var run = run(List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1)), signal);
            assertEquals(1, run.algorithm.rejections.size());
            assertTrue(run.result.executions().isEmpty());
            assertTrue(run.result.openPositions().isEmpty());
            assertEquals(10000, run.result.finalEquity(), 1e-8);
        }
    }

    @Test void carried_position_has_no_scheduled_close_after_many_same_direction_bars() {
        var bars = new ArrayList<Candle>();
        var signals = new ArrayList<Signal>();
        for (int i = 1; i <= 10; i++) {
            bars.add(bar("2024-04-%02d".formatted(i), 1.1, 1.101, 1.099, 1.1));
            signals.add(new Signal(Order.Side.BUY, .01, KEEP));
        }
        var result = run(bars, signals.toArray(Signal[]::new)).result;
        assertEquals(1, result.executions().size());
        assertEquals(10, result.targetDecisions().size());
        assertEquals(9997, result.finalEquity(), 1e-8);
        assertEquals(1, result.openPositions().size());
        for (var point : result.equityCurve()) assertEquals(1, point.executionCount());
    }

    @Test void existing_explicit_USD_balance_position_is_rejected_instead_of_hedged_in_main_balance() {
        var bars = List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1), bar("2024-04-02", 1.1, 1.1, 1.1, 1.1));
        var rejected = new ArrayList<Order.Rejected>();
        var result = new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("mixed balance fixture",
                context -> new TargetAlgorithm(context, List.of()) {
                    int days;
                    @Override public void onMarketMessage(MarketEvent event) {
                        super.onMarketMessage(event);
                        if (event instanceof BarOpen) {
                            if (++days == 1)
                                submitOrder(makeMarketOrder(Order.Side.BUY, 10000, event.symbol())
                                        .destinationId("SIMULATOR").currency("USD").toNewOrder());
                            else
                                submitPositionTarget(makePositionTarget(Order.Side.SELL_SHORT, event.symbol(), .01, .01,
                                        KEEP, "SIMULATOR"));
                        }
                    }
                    @Override public void onOrderRejected(Order.Rejected rejection) { rejected.add(rejection); }
                }, () -> new BarReplayMarketSupplier(Map.of(EURUSD, bars), WARSAW),
                new BacktestExecutionOptions(10000, SPREAD, BacktestExecutionOptions.EndOfDataPolicy.KEEP_OPEN)));
        assertEquals(1, rejected.size());
        assertEquals(1, result.executions().size());
        assertEquals(1, result.openPositions().size());
        assertEquals(Order.Side.BUY, result.openPositions().getFirst().side());
        assertEquals(9997, result.finalEquity(), 1e-8);
    }

    static Run run(List<Candle> bars, Signal... signals) {
        var holder = new ArrayList<TargetAlgorithm>();
        var result = new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("risk fixture", context -> {
            var algorithm = new TargetAlgorithm(context, List.of(signals));
            holder.add(algorithm);
            return algorithm;
        }, () -> new BarReplayMarketSupplier(Map.of(EURUSD, bars), WARSAW),
                new BacktestExecutionOptions(10000, SPREAD, BacktestExecutionOptions.EndOfDataPolicy.KEEP_OPEN)));
        return new Run(result, holder.getFirst());
    }

    static class TargetAlgorithm extends AbstractAlgorithm<SimpleInstrumentPrices> {
        final List<Signal> signals;
        final List<Order.Rejected> rejections = new ArrayList<>();
        int openings;
        TargetAlgorithm(AlgorithmContext context, List<Signal> signals) {
            super(context);
            this.signals = signals;
        }
        @Override protected InstrumentDataFactory<SimpleInstrumentPrices> createInstrumentDataFactory() {
            return SimpleInstrumentPrices::new;
        }
        @Override public void onMarketMessage(MarketEvent event) {
            assertEquals(event.time(), getClock().time());
            super.onMarketMessage(event);
            if (event instanceof BarOpen && openings < signals.size()) {
                var signal = signals.get(openings++);
                submitPositionTarget(makePositionTarget(signal.side, event.symbol(), signal.risk, signal.distance,
                        signal.policy, "SIMULATOR"));
            }
        }
        @Override public void onOrderFilled(Order.Filled fill) { assertEquals(fill.time(), getClock().time()); }
        @Override public void onOrderRejected(Order.Rejected rejection) { rejections.add(rejection); }
    }
}
