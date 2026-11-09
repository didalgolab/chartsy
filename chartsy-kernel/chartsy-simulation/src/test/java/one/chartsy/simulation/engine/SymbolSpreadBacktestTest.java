package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageHandler;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.trade.ExitRules;
import one.chartsy.trade.Order;
import one.chartsy.trade.PositionTarget;
import one.chartsy.trade.account.ProfitConversion;
import one.chartsy.trade.algorithm.AbstractAlgorithm;
import one.chartsy.trade.algorithm.AlgorithmContext;
import one.chartsy.trade.algorithm.MarketSupplier;
import one.chartsy.trade.algorithm.data.InstrumentDataFactory;
import one.chartsy.trade.algorithm.data.SimpleInstrumentPrices;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static one.chartsy.simulation.engine.BacktestExecutionOptions.CapitalMode.*;
import static one.chartsy.simulation.engine.BacktestExecutionOptions.EndOfDataPolicy.*;
import static one.chartsy.simulation.engine.BacktestExecutionOptions.ExecutionMode.*;
import static one.chartsy.simulation.engine.CausalBarBacktestTest.*;
import static org.junit.jupiter.api.Assertions.*;

class SymbolSpreadBacktestTest {
    private static final SymbolIdentity GBPUSD = SymbolIdentity.of("GBPUSD");
    private static final double EPS = 1e-8;

    @Test void candle_quotes_use_each_symbols_spread_for_entries_marks_and_unprotected_liquidation() {
        var flat = List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1));
        for (var capital : BacktestExecutionOptions.CapitalMode.values())
            for (var end : BacktestExecutionOptions.EndOfDataPolicy.values())
                for (var side : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
                    var result = run(Map.of(EURUSD, flat, GBPUSD, flat), options(capital, end), (a, e) -> {
                        if (e instanceof BarOpen) a.order(e.symbol(), side, null);
                    });
                    assertEquals(9992, result.finalEquity(), EPS);
                    assertEquals(end == LIQUIDATE ? -8 : 0, result.realizedPnl(), EPS);
                    assertEquals(end == KEEP_OPEN ? -8 : 0, result.unrealizedPnl(), EPS);
                    assertEquals(end == LIQUIDATE ? 4 : 2, result.executions().size());
                    assertEquals(end == KEEP_OPEN ? 2 : 0, result.openPositions().size());
                    var entries = result.executions().stream().filter(x -> x.reason() == TradingSimulator.Reason.ENTRY).toList();
                    assertEquals(List.of(EURUSD, GBPUSD), entries.stream().map(x -> x.fill().symbol()).toList());
                    assertEquals(side == Order.Side.BUY ? 1.1003 : 1.1, entries.getFirst().fill().tradePrice(), EPS);
                    assertEquals(side == Order.Side.BUY ? 1.1005 : 1.1, entries.getLast().fill().tradePrice(), EPS);
                }
    }

    @Test void completed_bar_stop_uses_the_shorts_own_ask_high_without_stopping_the_other_symbol() {
        var bars = List.of(bar("2024-04-01", 1.1, 1.1096, 1.099, 1.1));
        var result = run(Map.of(EURUSD, bars, GBPUSD, bars), options(SHARED, KEEP_OPEN), (a, e) -> {
            if (e instanceof BarOpen) a.order(e.symbol(), Order.Side.SELL_SHORT, new ExitRules(.01, 99));
        });
        assertEquals(3, result.executions().size());
        var stop = result.executions().getLast();
        assertEquals(TradingSimulator.Reason.STOP, stop.reason());
        assertEquals(GBPUSD, stop.fill().symbol());
        assertEquals(1.11, stop.fill().tradePrice(), EPS);
        assertEquals(-100, result.realizedPnl(), EPS);
        assertEquals(EURUSD, result.openPositions().getFirst().symbol());
        assertEquals(-3, result.unrealizedPnl(), EPS);
        assertEquals(9897, result.finalEquity(), EPS);
    }

    @Test void opening_gap_stop_fills_at_the_symbols_current_ask() {
        var bars = List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1),
                bar("2024-04-02", 1.12, 1.12, 1.12, 1.12));
        var result = run(Map.of(GBPUSD, bars), options(SHARED, KEEP_OPEN), (a, e) -> {
            if (e instanceof BarOpen && a.openings.get(e.symbol()) == 1)
                a.order(e.symbol(), Order.Side.SELL_SHORT, new ExitRules(.01, 99));
        });
        assertEquals(2, result.executions().size());
        assertEquals(TradingSimulator.Reason.STOP, result.executions().getLast().reason());
        assertEquals(1.1205, result.executions().getLast().fill().tradePrice(), EPS);
        assertEquals(-205, result.realizedPnl(), EPS);
    }

    @Test void scheduled_and_end_of_data_exits_use_the_symbols_last_ask() {
        var bars = List.of(bar("2024-04-01", 1.1, 1.105, 1.1, 1.105));
        for (int holdingBars : List.of(1, 3)) {
            var result = run(Map.of(GBPUSD, bars), options(SHARED, LIQUIDATE), (a, e) -> {
                if (e instanceof BarOpen) a.order(e.symbol(), Order.Side.SELL_SHORT, new ExitRules(.02, holdingBars));
            });
            assertEquals(2, result.executions().size());
            assertEquals(holdingBars == 1 ? TradingSimulator.Reason.SCHEDULED_CLOSE : TradingSimulator.Reason.END_OF_DATA,
                    result.executions().getLast().reason());
            assertEquals(1.1055, result.executions().getLast().fill().tradePrice(), EPS);
            assertEquals(-55, result.realizedPnl(), EPS);
        }
    }

    @Test void immediate_opening_stop_uses_the_symbols_spread_once() {
        var result = run(Map.of(GBPUSD, List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1))),
                options(SHARED, KEEP_OPEN), (a, e) -> {
                    if (e instanceof BarOpen) a.order(e.symbol(), Order.Side.BUY, new ExitRules(.0002, 99));
                });
        assertEquals(2, result.executions().size());
        assertEquals(TradingSimulator.Reason.STOP, result.executions().getLast().reason());
        assertEquals(1.1005, result.executions().getFirst().fill().tradePrice(), EPS);
        assertEquals(1.1, result.executions().getLast().fill().tradePrice(), EPS);
        assertEquals(-5, result.realizedPnl(), EPS);
    }

    @Test void reversal_settles_at_the_symbols_ask_before_sizing_the_new_position() {
        var bars = List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1),
                bar("2024-04-02", 1.104, 1.105, 1.104, 1.105));
        var result = run(Map.of(GBPUSD, bars), options(SHARED, KEEP_OPEN), (a, e) -> {
            if (e instanceof BarOpen)
                a.target(e.symbol(), a.openings.get(e.symbol()) == 1 ? Order.Side.SELL_SHORT : Order.Side.BUY);
        });
        assertEquals(3, result.executions().size());
        assertEquals(TradingSimulator.Reason.SIGNAL_REVERSAL, result.executions().get(1).reason());
        assertEquals(1.1045, result.executions().get(1).fill().tradePrice(), EPS);
        assertEquals(1.1045, result.executions().getLast().fill().tradePrice(), EPS);
        assertEquals(-45, result.realizedPnl(), EPS);
        assertEquals(9955, result.targetDecisions().getLast().equity(), EPS);
        assertEquals(9955, result.targetDecisions().getLast().quantity(), EPS);
        assertEquals(9959.9775, result.finalEquity(), EPS);
    }

    @Test void shared_risk_uses_prior_symbols_spread_cost_in_deterministic_opening_order() {
        var candles = new LinkedHashMap<SymbolIdentity, List<Candle>>();
        var flat = List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1));
        candles.put(GBPUSD, flat);
        candles.put(EURUSD, flat);
        var result = run(candles, options(SHARED, KEEP_OPEN), (a, e) -> {
            if (e instanceof BarOpen) a.target(e.symbol(), Order.Side.BUY);
        });
        var first = result.targetDecisions().getFirst();
        var second = result.targetDecisions().getLast();
        assertEquals(EURUSD, first.symbol());
        assertEquals(GBPUSD, second.symbol());
        assertEquals(10000, first.equity(), EPS);
        assertEquals(10000, first.quantity(), EPS);
        assertEquals(9997, second.equity(), EPS);
        assertEquals(9997, second.quantity(), EPS);
        assertEquals(9992.0015, result.finalEquity(), EPS);
    }

    @Test void raw_bar_compatibility_uses_symbol_spread_at_the_next_bar_open_and_final_liquidation() {
        List<MarketEvent> events = List.of(new TradeBar.Of(GBPUSD, bar("2024-04-01", 1.1, 1.1, 1.1, 1.1)),
                new TradeBar.Of(GBPUSD, bar("2024-04-02", 1.1, 1.102, 1.1, 1.102)));
        var result = run(() -> rawSupplier(events), options(INDEPENDENT_ACCOUNTS, LIQUIDATE), (a, e) -> {
            if (e instanceof TradeBar && a.completedBars == 1) a.order(e.symbol(), Order.Side.BUY, null);
        });
        assertEquals(2, result.executions().size());
        assertEquals(events.getLast().time(), result.executions().getFirst().fill().time());
        assertEquals(1.1005, result.executions().getFirst().fill().tradePrice(), EPS);
        assertEquals(1.102, result.executions().getLast().fill().tradePrice(), EPS);
        assertEquals(15, result.realizedPnl(), EPS);
    }

    private static BacktestExecutionOptions options(BacktestExecutionOptions.CapitalMode capital,
                                                     BacktestExecutionOptions.EndOfDataPolicy end) {
        return new BacktestExecutionOptions(10000, .0001, end, capital, ProfitConversion.QUOTE_CURRENCY,
                CANDLE_FIXED_SPREAD, Map.of(EURUSD, .0003, GBPUSD, .0005));
    }

    private static AlgorithmBacktestResult run(Map<SymbolIdentity, List<Candle>> candles,
                                               BacktestExecutionOptions options, BiConsumer<Fixture, MarketEvent> action) {
        return run(() -> new BarReplayMarketSupplier(candles, WARSAW), options, action);
    }

    private static AlgorithmBacktestResult run(MarketSupplierFactory supplier,
                                               BacktestExecutionOptions options, BiConsumer<Fixture, MarketEvent> action) {
        return new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("symbol spread fixture",
                context -> new Fixture(context, action), supplier, options));
    }

    private static MarketSupplier rawSupplier(List<MarketEvent> events) {
        return new MarketSupplier() {
            private Iterator<MarketEvent> iterator;
            @Override public void open() { iterator = events.iterator(); }
            @Override public int poll(MarketMessageHandler handler, int limit) {
                if (limit <= 0 || !iterator.hasNext()) return 0;
                handler.onMarketMessage(iterator.next());
                return 1;
            }
            @Override public void close() { iterator = List.<MarketEvent>of().iterator(); }
        };
    }

    private static final class Fixture extends AbstractAlgorithm<SimpleInstrumentPrices> {
        private final BiConsumer<Fixture, MarketEvent> action;
        final Map<SymbolIdentity, Integer> openings = new HashMap<>();
        int completedBars;

        Fixture(AlgorithmContext context, BiConsumer<Fixture, MarketEvent> action) {
            super(context);
            this.action = action;
        }

        @Override protected InstrumentDataFactory<SimpleInstrumentPrices> createInstrumentDataFactory() {
            return SimpleInstrumentPrices::new;
        }

        @Override public void onMarketMessage(MarketEvent event) {
            super.onMarketMessage(event);
            assertEquals(event.time(), getClock().time());
            if (event instanceof BarOpen) openings.merge(event.symbol(), 1, Integer::sum);
            if (event instanceof TradeBar) completedBars++;
            action.accept(this, event);
        }

        void order(SymbolIdentity symbol, Order.Side side, ExitRules rules) {
            submitOrder(makeMarketOrder(side, 10000, symbol).destinationId("SIMULATOR").exitRules(rules).toNewOrder());
        }

        void target(SymbolIdentity symbol, Order.Side side) {
            submitPositionTarget(makePositionTarget(side, symbol, .01, .01,
                    PositionTarget.StopUpdatePolicy.KEEP, "SIMULATOR"));
        }

        @Override public void onOrderRejected(Order.Rejected rejection) {
            fail("Unexpected rejection: " + rejection);
        }
    }
}
