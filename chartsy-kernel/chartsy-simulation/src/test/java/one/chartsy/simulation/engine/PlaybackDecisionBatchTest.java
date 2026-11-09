package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.data.stream.Message;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.simulation.engine.price.PlaybackMarketPriceService;
import one.chartsy.simulation.time.PlaybackClock;
import one.chartsy.trade.Order;
import one.chartsy.trade.PositionMaintenance;
import one.chartsy.trade.PositionTarget;
import one.chartsy.trade.algorithm.AbstractAlgorithm;
import one.chartsy.trade.algorithm.AlgorithmContext;
import one.chartsy.trade.algorithm.data.InstrumentDataFactory;
import one.chartsy.trade.algorithm.data.SimpleInstrumentPrices;
import one.chartsy.trade.service.OrderReportHandler;
import one.chartsy.trade.service.connector.TradeConnectorContext;
import one.chartsy.trade.service.connector.TradeConnectorProxy;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.BiConsumer;

import static one.chartsy.simulation.engine.BacktestExecutionOptions.CapitalMode.SHARED;
import static one.chartsy.simulation.engine.BacktestExecutionOptions.EndOfDataPolicy.KEEP_OPEN;
import static one.chartsy.simulation.engine.CausalBarBacktestTest.*;
import static one.chartsy.trade.PositionTarget.StopUpdatePolicy.KEEP;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlaybackDecisionBatchTest {
    private static final double EPS = 1e-8;

    @Test void openingDecision_all_44_requests_execute_before_fill_callback_can_reenter() {
        var bars = new ArrayList<Candle>();
        for (int day = 0; day < 45; day++) {
            double price = day < 43 ? 1.1 : day == 43 ? 1.081 : 1.082;
            bars.add(bar(LocalDate.of(2024, 1, 1).plusDays(day).toString(), price, price, price, price));
        }
        var maintenanceIds = new ArrayList<String>();
        var run = run(bars, (a, event) -> {
            if (!(event instanceof BarOpen)) return;
            if (a.openings <= 43) {
                a.target("held-" + a.openings, Order.Side.BUY, .0001, .1);
            } else if (a.openings == 44) {
                for (int owner = 1; owner <= 43; owner++)
                    maintenanceIds.add(a.maintain("held-" + owner, Order.Side.SELL_SHORT));
                a.target("new-short", Order.Side.SELL_SHORT, .01, .1);
            }
        }, (a, fill) -> {
            if (fill.side() == Order.Side.SELL && !a.callbackSent) {
                a.callbackSent = true;
                a.target("fill-callback", Order.Side.BUY, .01, .1);
            }
        });
        var result = run.result();
        long decisionTime = run.algorithm().openingTimes.get(43);
        long nextOpening = run.algorithm().openingTimes.get(44);
        assertTrue(run.algorithm().rejections.isEmpty());
        assertEquals(43, result.maintenanceDecisions().size());
        assertEquals(maintenanceIds, result.maintenanceDecisions().stream()
                .map(TradingSimulator.MaintenanceDecision::orderId).toList());
        for (var maintenance : result.maintenanceDecisions()) {
            assertEquals(decisionTime, maintenance.time());
            assertEquals(TradingSimulator.MaintenanceAction.CLOSE_REVERSAL, maintenance.action());
        }
        var reversed = result.executions().stream()
                .filter(e -> e.reason() == TradingSimulator.Reason.SIGNAL_REVERSAL).toList();
        assertEquals(43, reversed.size());
        assertEquals(result.maintenanceDecisions().stream().map(TradingSimulator.MaintenanceDecision::parentOrderId).toList(),
                reversed.stream().map(TradingSimulator.Execution::parentOrderId).toList());
        for (var execution : reversed) {
            assertEquals(decisionTime, execution.fill().time());
            assertEquals(1.081, execution.fill().tradePrice());
        }
        var newEntry = result.targetDecisions().stream().filter(d -> d.accountId().equals("new-short"))
                .findFirst().orElseThrow();
        assertEquals(decisionTime, newEntry.time(), "the 44th request belongs to this decision opening");
        assertEquals(9991.83, newEntry.equity(), EPS, "all 43 realized losses precede sizing");
        assertEquals(99.9183, newEntry.riskBudget(), EPS);
        assertEquals(999.183, newEntry.quantity(), EPS);
        var callback = result.targetDecisions().stream().filter(d -> d.accountId().equals("fill-callback"))
                .findFirst().orElseThrow();
        assertEquals(nextOpening, callback.time(), "fill-generated entry waits for the next opening");
        assertEquals(88, result.executions().size());
        assertEquals(45, result.targetDecisions().size());
        assertEquals(-8.17, result.realizedPnl(), EPS);
        assertEquals(2, result.openPositions().size());
    }

    @Test void openingStop_all_44_callback_requests_stay_before_the_decision_boundary() {
        var bars = List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1),
                bar("2024-04-02", 1.08, 1.08, 1.08, 1.08),
                bar("2024-04-03", 1.081, 1.081, 1.081, 1.081));
        var run = run(bars, (a, event) -> {
            if (!(event instanceof BarOpen)) return;
            if (a.openings == 1) a.target("stopped", Order.Side.BUY, .01, .01);
            if (a.openings == 2) a.target("decision", Order.Side.BUY, .01, .1);
        }, (a, fill) -> {
            if (fill.side() == Order.Side.SELL && !a.callbackSent) {
                a.callbackSent = true;
                for (int owner = 0; owner < 44; owner++)
                    a.target("callback-" + owner, Order.Side.BUY, .0001, .1);
            }
        });
        assertTrue(run.algorithm().rejections.isEmpty());
        var result = run.result();
        long stopOpening = run.algorithm().openingTimes.get(1);
        long nextOpening = run.algorithm().openingTimes.get(2);
        var callbacks = result.targetDecisions().stream().filter(d -> d.accountId().startsWith("callback-")).toList();
        assertEquals(44, callbacks.size());
        for (int owner = 0; owner < callbacks.size(); owner++) {
            assertEquals("callback-" + owner, callbacks.get(owner).accountId());
            assertEquals(nextOpening, callbacks.get(owner).time());
        }
        assertEquals(stopOpening, result.targetDecisions().stream().filter(d -> d.accountId().equals("decision"))
                .findFirst().orElseThrow().time());
        assertEquals(47, result.executions().size());
        assertEquals(45, result.openPositions().size());
        assertEquals(2, result.executions().stream().filter(e -> e.fill().time() == stopOpening).count(),
                "only the stop and the ordinary decision entry execute at the second opening");
    }

    @Test void dispatchPendingMessages_caps_delivery_at_the_captured_queue_size() {
        var clock = new PlaybackClock(ZoneOffset.UTC, 0);
        var reports = mock(OrderReportHandler.class);
        var broker = new TradingSimulator(new TradeConnectorContext("SIMULATOR", clock,
                new PlaybackMarketPriceService(), reports));
        var queue = new ArrayBlockingQueue<Message>(16);
        var proxy = new TradeConnectorProxy(broker, queue);
        var invalid = new PositionTarget(0, "invalid", "fixture", "SIMULATOR", "", null,
                Order.Side.BUY, .01, .01, KEEP);
        var rejections = new ArrayList<Order.Rejected>();
        doAnswer(invocation -> {
            rejections.add(invocation.getArgument(0));
            // A finite fixture also fails promptly if a regression incorrectly drains until empty.
            if (rejections.size() < 5) proxy.onPositionTarget(invalid);
            return null;
        }).when(reports).onOrderRejected(any());
        proxy.onPositionTarget(invalid);
        assertEquals(1, proxy.dispatchPendingMessages());
        assertEquals(1, rejections.size());
        assertEquals(1, queue.size(), "the callback-generated request is beyond this batch");
        assertEquals("Missing target symbol", rejections.getFirst().rejectionReason());
        assertEquals(1, proxy.dispatchPendingMessages());
        assertEquals(2, rejections.size());
        assertEquals(1, queue.size());
        assertTrue(broker.getExecutions().isEmpty());
    }

    @Test void openingDecision_preserves_more_than_1024_targets_at_the_same_opening() {
        var run = run(List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1)), (a, event) -> {
            if (event instanceof BarOpen)
                for (int owner = 0; owner < 1025; owner++)
                    a.target("batch-" + owner, Order.Side.BUY, .000001, .1);
        }, (a, fill) -> { });
        var result = run.result();
        assertTrue(run.algorithm().rejections.isEmpty());
        assertEquals(1025, result.targetDecisions().size());
        assertEquals(1025, result.executions().size());
        assertEquals(1025, result.openPositions().size());
        assertEquals(1025, result.executions().stream().map(TradingSimulator.Execution::parentOrderId).distinct().count());
        long opening = run.algorithm().openingTimes.getFirst();
        for (int owner = 0; owner < 1025; owner++) {
            var target = result.targetDecisions().get(owner);
            assertEquals("batch-" + owner, target.accountId());
            assertEquals(opening, target.time());
            assertEquals(opening, result.executions().get(owner).fill().time());
            assertEquals(.01, target.riskBudget(), EPS);
            assertEquals(.1, target.quantity(), EPS);
        }
        assertEquals(10_000, result.finalEquity(), EPS);
    }

    @Test void endOfData_settles_one_large_finite_batch_without_exhausting_callback_passes() {
        // A quota of32 would require1025 nonempty passes and falsely trip the callback guard.
        int requests = 32 * 1024 + 1;
        var run = run(List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1)), (a, event) -> {
            if (event instanceof TradeBar)
                for (int owner = 0; owner < requests; owner++)
                    a.target("after-last-open-" + owner, Order.Side.BUY, .000001, .1);
        }, (a, fill) -> { });
        assertEquals(requests, run.algorithm().rejections.size());
        assertEquals(requests, run.algorithm().rejections.stream().map(Order.Rejected::orderId).distinct().count());
        for (var rejection : run.algorithm().rejections)
            assertEquals("End of market data", rejection.rejectionReason());
        assertTrue(run.result().executions().isEmpty());
        assertTrue(run.result().targetDecisions().isEmpty());
        assertTrue(run.result().openPositions().isEmpty());
        assertEquals(10_000, run.result().finalEquity(), EPS);
    }

    private static Run run(List<Candle> bars, BiConsumer<BatchAlgorithm, MarketEvent> onMarket,
                           BiConsumer<BatchAlgorithm, Order.Filled> onFill) {
        var algorithms = new ArrayList<BatchAlgorithm>();
        var result = new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("finite decision batch",
                context -> {
                    var algorithm = new BatchAlgorithm(context, onMarket, onFill);
                    algorithms.add(algorithm);
                    return algorithm;
                }, () -> new BarReplayMarketSupplier(Map.of(EURUSD, bars), WARSAW),
                new BacktestExecutionOptions(10_000, 0, KEEP_OPEN, SHARED)));
        return new Run(result, algorithms.getFirst());
    }

    private record Run(AlgorithmBacktestResult result, BatchAlgorithm algorithm) { }

    private static final class BatchAlgorithm extends AbstractAlgorithm<SimpleInstrumentPrices> {
        final BiConsumer<BatchAlgorithm, MarketEvent> onMarket;
        final BiConsumer<BatchAlgorithm, Order.Filled> onFill;
        final List<Long> openingTimes = new ArrayList<>();
        final List<Order.Rejected> rejections = new ArrayList<>();
        int openings;
        boolean callbackSent;

        BatchAlgorithm(AlgorithmContext context, BiConsumer<BatchAlgorithm, MarketEvent> onMarket,
                       BiConsumer<BatchAlgorithm, Order.Filled> onFill) {
            super(context);
            this.onMarket = onMarket;
            this.onFill = onFill;
        }
        @Override protected InstrumentDataFactory<SimpleInstrumentPrices> createInstrumentDataFactory() {
            return SimpleInstrumentPrices::new;
        }
        @Override public void onMarketMessage(MarketEvent event) {
            super.onMarketMessage(event);
            assertEquals(event.time(), getClock().time());
            if (event instanceof BarOpen) { openings++; openingTimes.add(event.time()); }
            onMarket.accept(this, event);
        }
        void target(String account, Order.Side side, double risk, double stop) {
            submitPositionTarget(new PositionTarget(getClock().time(), generateOrderId(), getId(), "SIMULATOR",
                    account, EURUSD, side, risk, stop, KEEP));
        }
        String maintain(String account, Order.Side side) {
            String id = generateOrderId();
            submitPositionMaintenance(new PositionMaintenance(getClock().time(), id, getId(), "SIMULATOR",
                    account, EURUSD, side, .1, KEEP));
            return id;
        }
        @Override public void onOrderFilled(Order.Filled fill) {
            assertEquals(fill.time(), getClock().time());
            onFill.accept(this, fill);
        }
        @Override public void onOrderRejected(Order.Rejected rejection) { rejections.add(rejection); }
    }
}
