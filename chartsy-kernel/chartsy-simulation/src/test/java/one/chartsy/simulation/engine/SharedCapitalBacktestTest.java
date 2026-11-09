package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.simulation.engine.price.PlaybackMarketPriceService;
import one.chartsy.simulation.time.PlaybackClock;
import one.chartsy.trade.Order;
import one.chartsy.trade.PositionTarget;
import one.chartsy.trade.algorithm.AbstractAlgorithm;
import one.chartsy.trade.algorithm.AlgorithmContext;
import one.chartsy.trade.algorithm.data.InstrumentDataFactory;
import one.chartsy.trade.algorithm.data.SimpleInstrumentPrices;
import one.chartsy.trade.service.AlgorithmEngine;
import one.chartsy.trade.service.connector.TradeConnectorContext;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static one.chartsy.simulation.engine.BacktestExecutionOptions.CapitalMode.*;
import static one.chartsy.simulation.engine.BacktestExecutionOptions.EndOfDataPolicy.*;
import static one.chartsy.simulation.engine.CausalBarBacktestTest.*;
import static one.chartsy.trade.PositionTarget.StopUpdatePolicy.*;
import static org.junit.jupiter.api.Assertions.*;

class SharedCapitalBacktestTest {
    private static final double INITIAL = 10_000;
    private static final double TOLERANCE = 1e-8;

    @Test void sharedCapital_seeds_once_and_keeps_named_strategy_ledgers_unfunded() {
        var clock = new PlaybackClock(ZoneOffset.UTC, 0);
        var context = new TradeConnectorContext("SIMULATOR", clock, new PlaybackMarketPriceService(), new AlgorithmEngine(clock));
        var simulator = new TradingSimulator(context, new BacktestExecutionOptions(INITIAL, 0, KEEP_OPEN, SHARED));
        assertEquals(INITIAL, simulator.getDefaultAccount().getTotalEquity());
        assertEquals(0, simulator.getOrCreateNewAccount("long model").getTotalEquity());
        assertEquals(0, simulator.getOrCreateNewAccount("short model").getTotalEquity());
        assertSame(simulator.getDefaultAccount(), simulator.getOrCreateNewAccount(null));
        assertSame(simulator.getDefaultAccount(), simulator.getOrCreateNewAccount(""));
        assertEquals(INITIAL, simulator.getTotalEquity());
        assertEquals(INITIAL, simulator.getCashBalance());
        assertEquals(0, simulator.getRealizedPnL());
        assertEquals(0, simulator.getUnrealizedPnL());
        assertEquals(INITIAL, simulator.getEquityStatistics().getStartingEquity());
        assertEquals(INITIAL, simulator.getEquityStatistics().getEndingEquity());
        assertEquals(0, simulator.getEquityStatistics().getDataPoints());
    }

    @Test void sharedCapital_cash_getter_adds_realized_PnL_and_equity_adds_open_PnL_across_ledgers() {
        var clock = new PlaybackClock(ZoneOffset.UTC, 0);
        var context = new TradeConnectorContext("SIMULATOR", clock, new PlaybackMarketPriceService(), new AlgorithmEngine(clock));
        var simulator = new TradingSimulator(context, new BacktestExecutionOptions(INITIAL, 0, KEEP_OPEN, SHARED));
        var defaultBalance = simulator.getDefaultAccount().getOrCreateNewBalance(null);
        defaultBalance.enterLong(EURUSD, 1000, 1.1, 1);
        defaultBalance.exitPosition(EURUSD, 1.105, 2);
        var losingModel = simulator.getOrCreateNewAccount("realized model").getOrCreateNewBalance(null);
        losingModel.enterLong(EURUSD, 5000, 1.1, 1);
        losingModel.exitPosition(EURUSD, 1.09, 2);
        var openModel = simulator.getOrCreateNewAccount("open model").getOrCreateNewBalance(null);
        openModel.enterShort(EURUSD, 2000, 1.1, 1);
        openModel.updateMarketData(EURUSD, 1.11, 2);
        assertEquals(-45, simulator.getRealizedPnL(), TOLERANCE);
        assertEquals(-20, simulator.getUnrealizedPnL(), TOLERANCE);
        assertEquals(9955, simulator.getCashBalance(), TOLERANCE);
        assertEquals(9935, simulator.getTotalEquity(), TOLERANCE);
        assertEquals(10005, simulator.getDefaultAccount().getTotalEquity(), TOLERANCE,
                "the default-account accessor still exposes the actual funding ledger");
    }

    @Test void sharedCapital_allows_opposite_positions_with_split_risk_and_independent_ownership() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.106, 1.099, 1.105)),
                new BacktestExecutionOptions(INITIAL, 0, KEEP_OPEN, SHARED), List.of(hedgedSignals()));
        assertEquals(2, result.executions().size());
        assertEquals(2, result.openPositions().size());
        assertEquals(List.of("long model", "short model"), result.openPositions().stream().map(TradingSimulator.OpenPosition::accountId).toList());
        assertEquals(List.of(Order.Side.BUY, Order.Side.SELL_SHORT), result.openPositions().stream().map(TradingSimulator.OpenPosition::side).toList());
        for (var target : result.targetDecisions()) {
            assertEquals(INITIAL, target.equity(), TOLERANCE);
            assertEquals(50, target.riskBudget(), TOLERANCE);
            assertEquals(5000, target.quantity(), TOLERANCE);
        }
        assertEquals(25, result.openPositions().getFirst().unrealizedPnl(), TOLERANCE);
        assertEquals(-25, result.openPositions().getLast().unrealizedPnl(), TOLERANCE);
        assertTotals(result, INITIAL, 0, 0);
    }

    @Test void sharedCapital_next_entry_includes_spread_cost_of_earlier_fill_at_same_open() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.1)),
                new BacktestExecutionOptions(INITIAL, SPREAD, KEEP_OPEN, SHARED), List.of(hedgedSignals()));
        var first = result.targetDecisions().getFirst();
        var second = result.targetDecisions().getLast();
        assertEquals(INITIAL, first.equity(), TOLERANCE);
        assertEquals(9998.5, second.equity(), TOLERANCE);
        assertEquals(49.9925, second.riskBudget(), TOLERANCE);
        assertEquals(4999.25, second.quantity(), TOLERANCE);
        assertEquals(9998.5, result.executions().getFirst().equity(), TOLERANCE);
        assertEquals(9997.000225, result.executions().getLast().equity(), TOLERANCE);
        assertTotals(result, 9997.000225, 0, -2.999775);
    }

    @Test void sharedCapital_stop_of_one_leg_does_not_close_other_leg_and_both_PnLs_reach_report() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.111, 1.099, 1.108)),
                new BacktestExecutionOptions(INITIAL, 0, KEEP_OPEN, SHARED), List.of(hedgedSignals()));
        assertEquals(3, result.executions().size());
        assertEquals(TradingSimulator.Reason.STOP, result.executions().getLast().reason());
        assertEquals(1, result.openPositions().size());
        assertEquals("long model", result.openPositions().getFirst().accountId());
        assertTotals(result, 9990, -50, 40);
        assertEquals(9990, result.executions().getLast().equity(), TOLERANCE,
                "stop report includes the surviving leg marked at the completed bar close");
        assertEquals(-10, result.report().equitySummary().getMaxDrawdown(), TOLERANCE);
        assertEquals(1, result.report().equitySummary().getDataPoints());
    }

    @Test void sharedCapital_liquidation_settles_all_legs_without_double_counting_final_equity_sample() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.111, 1.099, 1.108)),
                new BacktestExecutionOptions(INITIAL, 0, LIQUIDATE, SHARED), List.of(hedgedSignals()));
        assertEquals(4, result.executions().size());
        assertEquals(TradingSimulator.Reason.END_OF_DATA, result.executions().getLast().reason());
        assertTrue(result.openPositions().isEmpty());
        assertTotals(result, 9990, -10, 0);
        assertEquals(1, result.equityCurve().size());
        assertEquals(1, result.report().equitySummary().getDataPoints());
        assertEquals(4, result.equityCurve().getLast().executionCount());
    }

    @Test void sharedCapital_same_side_stop_reset_changes_only_its_leg_and_never_resizes() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.103, 1.099, 1.102),
                        bar("2024-04-02", 1.104, 1.108, 1.102, 1.107)),
                new BacktestExecutionOptions(INITIAL, SPREAD, KEEP_OPEN, SHARED),
                List.of(hedgedSignals(), List.of(new Signal("long model", Order.Side.BUY, .02, .009, RESET_FROM_OPEN))));
        assertEquals(2, result.executions().size());
        var longPosition = result.openPositions().getFirst();
        var shortPosition = result.openPositions().getLast();
        assertEquals(5000, longPosition.quantity(), TOLERANCE);
        assertEquals(4999.25, shortPosition.quantity(), TOLERANCE);
        assertEquals(1.0843, longPosition.stopPrice(), 1e-12);
        assertEquals(1.11, shortPosition.stopPrice(), 1e-12);
        var reset = result.targetDecisions().getLast();
        assertEquals(TradingSimulator.TargetAction.RESET_STOP, reset.action());
        assertEquals(9997.003225, reset.equity(), TOLERANCE);
        assertTrue(Double.isNaN(reset.riskBudget()));
        assertTotals(result, 9997.005475, 0, -2.994525);
    }

    @Test void sharedCapital_reversal_uses_realized_and_other_legs_unrealized_equity() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.103, 1.099, 1.102),
                        bar("2024-04-02", 1.104, 1.108, 1.102, 1.103)),
                new BacktestExecutionOptions(INITIAL, 0, KEEP_OPEN, SHARED),
                List.of(hedgedSignals(), List.of(new Signal("long model", Order.Side.SELL_SHORT, .02, .005, KEEP))));
        assertEquals(4, result.executions().size());
        assertEquals(TradingSimulator.Reason.SIGNAL_REVERSAL, result.executions().get(2).reason());
        var reversal = result.targetDecisions().getLast();
        assertEquals(TradingSimulator.TargetAction.REVERSE, reversal.action());
        assertEquals(INITIAL, reversal.equity(), TOLERANCE, "realized +20 and the other leg's -20 offset");
        assertEquals(2500, reversal.quantity(), TOLERANCE);
        assertEquals(2, result.openPositions().size());
        assertTrue(result.openPositions().stream().allMatch(p -> p.side() == Order.Side.SELL_SHORT));
        assertTotals(result, 10007.5, 20, -12.5);
    }

    @Test void sharedCapital_default_account_positions_contribute_alongside_named_models() {
        var signals = List.of(new Signal("", Order.Side.BUY, .02, .002, KEEP),
                hedgedSignals().getFirst(), hedgedSignals().getLast());
        var result = run(List.of(bar("2024-04-01", 1.1, 1.106, 1.099, 1.105)),
                new BacktestExecutionOptions(INITIAL, 0, KEEP_OPEN, SHARED), List.of(signals));
        assertEquals(3, result.openPositions().size());
        assertEquals(1000, result.openPositions().getFirst().quantity(), TOLERANCE);
        assertTotals(result, 10005, 0, 5);
    }

    @Test void independentAccounts_existing_constructors_and_default_report_semantics_are_preserved() {
        assertEquals(INDEPENDENT_ACCOUNTS, new BacktestExecutionOptions(INITIAL, 0).capitalMode());
        assertEquals(INDEPENDENT_ACCOUNTS, new BacktestExecutionOptions(INITIAL, 0, KEEP_OPEN).capitalMode());
        assertEquals(INDEPENDENT_ACCOUNTS, BacktestExecutionOptions.DEFAULT.capitalMode());
        var bars = List.of(bar("2024-04-01", 1.1, 1.111, 1.099, 1.108));
        var implicit = run(bars, new BacktestExecutionOptions(INITIAL, 0, KEEP_OPEN), List.of(hedgedSignals()));
        var explicit = run(bars, new BacktestExecutionOptions(INITIAL, 0, KEEP_OPEN, INDEPENDENT_ACCOUNTS), List.of(hedgedSignals()));
        assertEquals(implicit.executions(), explicit.executions());
        assertEquals(implicit.equityCurve(), explicit.equityCurve());
        assertEquals(implicit.targetDecisions(), explicit.targetDecisions());
        assertTotals(implicit, INITIAL, 0, 0);
        assertEquals(5000, implicit.targetDecisions().getFirst().quantity(), TOLERANCE);
        assertEquals(5000, implicit.targetDecisions().getLast().quantity(), TOLERANCE);
    }

    @Test void catastrophicGap_records_negative_equity_and_existing_sizing_refuses_later_entries() {
        var bars = List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1),
                bar("2024-04-02", 1.08, 1.08, 1.08, 1.08),
                bar("2024-04-03", 1.09, 1.09, 1.09, 1.09));
        var signal = new PersistentPositionBacktestTest.Signal(Order.Side.BUY, .0001, .01, KEEP);
        for (var capitalMode : List.of(SHARED, INDEPENDENT_ACCOUNTS)) {
            var algorithms = new ArrayList<PersistentPositionBacktestTest.TargetAlgorithm>();
            var result = new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("gap beyond capital",
                    context -> {
                        var algorithm = new PersistentPositionBacktestTest.TargetAlgorithm(context,
                                List.of(signal, signal, signal));
                        algorithms.add(algorithm);
                        return algorithm;
                    }, () -> new BarReplayMarketSupplier(Map.of(EURUSD, bars), WARSAW),
                    new BacktestExecutionOptions(INITIAL, 0, KEEP_OPEN, capitalMode)));
            var algorithm = algorithms.getFirst();
            assertEquals(3, algorithm.openings);
            assertEquals(2, algorithm.rejections.size());
            for (var rejection : algorithm.rejections)
                assertEquals(TradingSimulator.INSUFFICIENT_EQUITY,
                        rejection.rejectionReason());
            assertEquals(List.of(TradingSimulator.Reason.ENTRY, TradingSimulator.Reason.STOP),
                    result.executions().stream().map(TradingSimulator.Execution::reason).toList());
            assertEquals(1_000_000, result.executions().getFirst().fill().tradeQuantity(), TOLERANCE);
            assertEquals(1.08, result.executions().getLast().fill().tradePrice());
            assertEquals(1, result.targetDecisions().size(), "rejected attempts allocate no target or position");
            assertTrue(result.openPositions().isEmpty());
            assertTotals(result, -10_000, -20_000, 0);
            assertEquals(3, result.equityCurve().size());
            assertEquals(-10_000, result.equityCurve().get(1).equity(), TOLERANCE);
            assertEquals(-10_000, result.equityCurve().get(2).equity(), TOLERANCE);
            var stats = result.report().equitySummary();
            assertEquals(-10_000, stats.getTotalEquityLow(), TOLERANCE);
            assertEquals(-20_000, stats.getMaxDrawdown(), TOLERANCE);
            assertEquals(-200, stats.getMaxDrawdownPercent(), TOLERANCE);
            assertEquals(3, stats.getDataPoints());
            assertTrue(Double.isNaN(stats.getCAGR()));
            assertTrue(Double.isNaN(stats.getTimeLogEquityCorrelation()));
        }
    }

    private static List<Signal> hedgedSignals() {
        return List.of(new Signal("long model", Order.Side.BUY, .01, .005, KEEP),
                new Signal("short model", Order.Side.SELL_SHORT, .01, .005, KEEP));
    }

    private static void assertTotals(AlgorithmBacktestResult result, double equity, double realized, double unrealized) {
        assertEquals(equity, result.finalEquity(), TOLERANCE);
        assertEquals(realized, result.realizedPnl(), TOLERANCE);
        assertEquals(unrealized, result.unrealizedPnl(), TOLERANCE);
        assertEquals(INITIAL + realized + unrealized, result.finalEquity(), TOLERANCE);
        var point = result.equityCurve().getLast();
        assertEquals(equity, point.equity(), TOLERANCE);
        assertEquals(realized, point.realizedPnl(), TOLERANCE);
        assertEquals(unrealized, point.unrealizedPnl(), TOLERANCE);
        assertEquals(INITIAL, result.report().equitySummary().getStartingEquity(), TOLERANCE);
        assertEquals(equity, result.report().equitySummary().getEndingEquity(), TOLERANCE);
    }

    private static AlgorithmBacktestResult run(List<Candle> bars, BacktestExecutionOptions options,
                                               List<List<Signal>> signals) {
        return new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("portfolio fixture",
                context -> new PortfolioAlgorithm(context, signals),
                () -> new BarReplayMarketSupplier(Map.of(EURUSD, bars), WARSAW), options));
    }

    private record Signal(String accountId, Order.Side side, double distance, double risk,
                          PositionTarget.StopUpdatePolicy stopPolicy) { }

    private static final class PortfolioAlgorithm extends AbstractAlgorithm<SimpleInstrumentPrices> {
        private final List<List<Signal>> signals;
        private int openings;

        PortfolioAlgorithm(AlgorithmContext context, List<List<Signal>> signals) {
            super(context);
            this.signals = signals;
        }

        @Override protected InstrumentDataFactory<SimpleInstrumentPrices> createInstrumentDataFactory() {
            return SimpleInstrumentPrices::new;
        }

        @Override public void onMarketMessage(MarketEvent event) {
            super.onMarketMessage(event);
            if (event instanceof BarOpen && openings < signals.size()) {
                for (var signal : signals.get(openings++))
                    submitPositionTarget(new PositionTarget(event.time(), generateOrderId(), getId(), "SIMULATOR",
                            signal.accountId, event.symbol(), signal.side, signal.risk, signal.distance, signal.stopPolicy));
            }
        }

        @Override public void onOrderRejected(Order.Rejected rejection) {
            fail("Unexpected rejection: " + rejection);
        }
    }
}
