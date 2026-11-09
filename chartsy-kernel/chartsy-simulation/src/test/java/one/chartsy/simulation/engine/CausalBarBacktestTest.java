package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.context.ExecutionContext;
import one.chartsy.data.DataQuery;
import one.chartsy.core.ResourceHandle;
import one.chartsy.data.DataSubscription;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.messaging.common.ShutdownResponse;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.time.Chronological;
import one.chartsy.trade.ExitRules;
import one.chartsy.trade.Order;
import one.chartsy.trade.OrderType;
import one.chartsy.trade.TimeInForce;
import one.chartsy.trade.algorithm.AbstractAlgorithm;
import one.chartsy.trade.algorithm.AlgorithmContext;
import one.chartsy.trade.algorithm.data.InstrumentDataFactory;
import one.chartsy.trade.algorithm.data.SimpleInstrumentPrices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.*;

class CausalBarBacktestTest {
    static final SymbolIdentity EURUSD = SymbolIdentity.of("EURUSD");
    static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    static final double SPREAD = .0003;

    @TempDir Path directory;

    @Test void defaultFlatFileSupplier_preserves_intraday_bars_and_raw_timestamps() throws IOException {
        Files.writeString(directory.resolve("EURUSD.txt"), "symbol,date,time,open,high,low,close,volume\n"
                + "EURUSD,20240401,120000,1.1,1.2,1.0,1.15,1\n"
                + "EURUSD,20240401,120100,1.15,1.2,1.1,1.18,1\n");
        try (var provider = new FlatFileDataProvider(FlatFileFormat.FOREXTESTER,
                ResourceHandle.of(FileSystems.getDefault()), "minute fixture", List.of(directory))) {
            var supplier = new FlatFileDataMarketSupplier(provider, DataSubscription.of(List.of("EURUSD")));
            var events = new ArrayList<MarketEvent>();
            try {
                supplier.open();
                while (supplier.poll(events::add, 1024) > 0) { }
            } finally { supplier.close(); }
            assertEquals(2, events.size());
            assertTrue(events.stream().allMatch(TradeBar.class::isInstance));
            // Preserve the provider's existing timestamp convention, including its requested timeframe.
            var raw = provider.iterator(DataQuery.<Candle>builder()
                    .resource(SymbolResource.of("EURUSD", TimeFrame.Period.DAILY)).build(), new ExecutionContext());
            try {
                assertEquals(raw.getMessage(), events.getFirst());
                assertEquals(raw.getMessage(), events.getLast());
            } finally { raw.close(); }
            assertEquals(Duration.ofMinutes(1).toNanos(), events.getLast().time() - events.getFirst().time());
            var result = new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("raw fixture",
                    context -> new FixtureAlgorithm(context, (a, e) -> { if (a.fills.isEmpty()) a.plainOrder(null); }),
                    () -> new FlatFileDataMarketSupplier(provider, DataSubscription.of(List.of("EURUSD")))));
            assertEquals(2, result.executions().size());
            assertEquals(1.15, result.executions().getFirst().fill().tradePrice(), 1e-12);
            assertEquals(10300, result.finalEquity(), 1e-8);
        }
    }

    @Test void dailySession_uses_actual_Warsaw_midnights_across_DST() {
        var sessions = BarSession.daily(WARSAW);
        var spring = sessions.interval(bar("2024-03-31", 1, 1, 1, 1));
        var autumn = sessions.interval(bar("2024-10-27", 1, 1, 1, 1));
        assertEquals(Duration.ofHours(23).toNanos(), spring.closeTime() - spring.openTime());
        assertEquals(Duration.ofHours(25).toNanos(), autumn.closeTime() - autumn.openTime());
        assertEquals("2024-03-30T23:00:00Z", Chronological.toInstant(spring.openTime()).toString());
    }

    @Test void replayMerge_is_stable_and_closes_all_symbols_before_any_next_open() {
        var bars = List.of(bar("2024-03-30", 1, 2, .5, 1.2), bar("2024-03-31", 1.2, 2, .5, 1.3));
        var first = new LinkedHashMap<SymbolIdentity, List<Candle>>();
        first.put(SymbolIdentity.of("ZZZ"), bars);
        first.put(EURUSD, bars);
        var reverse = new LinkedHashMap<SymbolIdentity, List<Candle>>();
        reverse.put(EURUSD, bars);
        reverse.put(SymbolIdentity.of("ZZZ"), bars);
        var trace = replay(first, 1);
        assertEquals(trace, replay(reverse, 1024));
        assertEquals(8, trace.size());
        assertInstanceOf(TradeBar.class, trace.get(2));
        assertInstanceOf(TradeBar.class, trace.get(3));
        assertInstanceOf(BarOpen.class, trace.get(4));
        assertInstanceOf(BarOpen.class, trace.get(5));
        assertEquals(trace.get(3).time(), trace.get(4).time());
        assertEquals(4, BarOpen.class.getRecordComponents().length);
    }

    @Test void replay_rejects_overlapping_source_bars() {
        var one = bar("2024-04-01", 1, 1, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> replay(Map.of(EURUSD, List.of(one, one)), 1024));
    }

    @Test void unchangedBidBar_charges_one_spread_for_both_directions() {
        for (var side : List.of(Order.Side.BUY, Order.Side.SELL_SHORT)) {
            var result = run(List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1)),
                    (a, e) -> { if (e instanceof BarOpen) a.enter(side, .01, 1); }, SPREAD);
            assertEquals(9997, result.finalEquity(), 1e-8);
            assertEquals(2, result.executions().size());
            assertEquals(-3, result.executions().getLast().realizedPnl(), 1e-8);
            assertEquals(TradingSimulator.Reason.SCHEDULED_CLOSE, result.executions().getLast().reason());
            assertEquals(result.finalEquity(), result.equityCurve().getLast().equity(), 1e-8);
        }
    }

    @Test void entryStop_is_active_on_entry_bar_and_retires_scheduled_close() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.12, 1.09, 1.11)),
                (a, e) -> { if (e instanceof BarOpen) a.enter(Order.Side.BUY, .001, 1); }, SPREAD);
        assertEquals(2, result.executions().size());
        assertEquals(TradingSimulator.Reason.STOP, result.executions().getLast().reason());
        assertEquals(1.0993, result.executions().getLast().fill().tradePrice(), 1e-12);
        assertEquals(9990, result.finalEquity(), 1e-8);
    }

    @Test void shortStop_uses_ask_high_even_when_bid_high_does_not_reach_stop() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.1008, 1.0998, 1.1)),
                (a, e) -> { if (e instanceof BarOpen) a.enter(Order.Side.SELL_SHORT, .001, 1); }, SPREAD);
        assertEquals(TradingSimulator.Reason.STOP, result.executions().getLast().reason());
        assertEquals(1.101, result.executions().getLast().fill().tradePrice(), 1e-12);
    }

    @Test void openingGap_fills_at_worse_executable_quote_and_counts_instrument_bars() {
        var bars = List.of(bar("2024-04-05", 1.1, 1.102, 1.099, 1.101),
                bar("2024-04-08", 1.08, 1.082, 1.079, 1.081));
        var result = run(bars, (a, e) -> { if (e instanceof BarOpen && a.opens == 1) a.enter(Order.Side.BUY, .01, 2); }, SPREAD);
        var exit = result.executions().getLast();
        assertEquals(TradingSimulator.Reason.STOP, exit.reason());
        assertEquals(1.08, exit.fill().tradePrice(), 1e-12);
        assertEquals(BarSession.daily(WARSAW).interval(bars.getLast()).openTime(), exit.fill().time());
        assertEquals(9797, result.finalEquity(), 1e-8);
    }

    @Test void holdingPeriod_counts_actual_bars_across_weekend() {
        var bars = List.of(bar("2024-04-05", 1.1, 1.102, 1.099, 1.101),
                bar("2024-04-08", 1.101, 1.102, 1.10, 1.102));
        var result = run(bars, (a, e) -> { if (e instanceof BarOpen && a.opens == 1) a.enter(Order.Side.BUY, .01, 2); }, SPREAD);
        assertEquals(TradingSimulator.Reason.SCHEDULED_CLOSE, result.executions().getLast().reason());
        assertEquals(BarSession.daily(WARSAW).interval(bars.getLast()).closeTime(), result.executions().getLast().fill().time());
    }

    @Test void immediateProtection_fills_at_quote_once_when_spread_exceeds_stop_distance() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1)),
                (a, e) -> { if (e instanceof BarOpen) a.enter(Order.Side.BUY, .0001, 1); }, SPREAD);
        assertEquals(2, result.executions().size());
        assertEquals(result.executions().getFirst().fill().time(), result.executions().getLast().fill().time());
        assertEquals(-3, result.executions().getLast().realizedPnl(), 1e-8);
    }

    @Test void consecutive_same_direction_decisions_create_distinct_round_trips() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1), bar("2024-04-02", 1.1, 1.1, 1.1, 1.1)),
                (a, e) -> { if (e instanceof BarOpen) a.enter(Order.Side.BUY, .01, 1); }, SPREAD);
        assertEquals(4, result.executions().size());
        assertNotEquals(result.executions().get(0).parentOrderId(), result.executions().get(2).parentOrderId());
        assertEquals(9994, result.finalEquity(), 1e-8);
    }

    @Test void completedBar_decision_waits_for_next_open_and_final_reports_are_drained() {
        var algorithm = new ArrayList<FixtureAlgorithm>();
        var bars = List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1), bar("2024-04-02", 1.2, 1.2, 1.2, 1.2));
        var result = run(bars, (a, e) -> {
            if (algorithm.isEmpty()) algorithm.add(a);
            if (e instanceof TradeBar) a.enter(Order.Side.BUY, .01, 1);
        }, SPREAD);
        assertEquals(2, result.executions().size());
        assertEquals(1.2003, result.executions().getFirst().fill().tradePrice(), 1e-12);
        assertEquals(1, algorithm.getFirst().rejections.size());
        assertEquals("End of market data", algorithm.getFirst().rejections.getFirst().rejectionReason());
        assertEquals(2, algorithm.getFirst().fillsAtClose);
    }

    @Test void unsupportedTimeInForce_is_rejected_once_and_removed() {
        var seen = new ArrayList<FixtureAlgorithm>();
        var bars = List.of(bar("2024-04-01", 1, 1, 1, 1), bar("2024-04-02", 1, 1, 1, 1));
        var result = run(bars, (a, e) -> {
            if (seen.isEmpty()) seen.add(a);
            if (e instanceof BarOpen && a.opens == 1)
                a.plainOrder(TimeInForce.CLOSE);
        }, SPREAD);
        assertTrue(result.executions().isEmpty());
        assertEquals(1, seen.getFirst().rejections.size());
    }

    @Test void unfinished_future_range_does_not_change_entry_price_or_opening_information() {
        var first = run(List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1)),
                (a, e) -> { if (e instanceof BarOpen) a.enter(Order.Side.BUY, .01, 1); }, SPREAD);
        var changed = run(List.of(bar("2024-04-01", 1.1, 1.9, .9, 1.8)),
                (a, e) -> { if (e instanceof BarOpen) a.enter(Order.Side.BUY, .01, 1); }, SPREAD);
        assertEquals(first.executions().getFirst().fill().tradePrice(), changed.executions().getFirst().fill().tradePrice());
        assertEquals(first.executions().getFirst().fill().time(), changed.executions().getFirst().fill().time());
    }

    @Test void plainMarketPosition_is_liquidated_at_EOF_with_zero_spread_raw_difference() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.2, 1.1, 1.2)), (a, e) -> {
            if (e instanceof BarOpen)
                a.plainOrder(null);
        }, 0);
        assertEquals(11000, result.finalEquity(), 1e-8);
        assertEquals(TradingSimulator.Reason.END_OF_DATA, result.executions().getLast().reason());
        assertEquals(1000, result.executions().getLast().realizedPnl(), 1e-8);
    }

    @Test void fillCallback_order_waits_for_next_open_even_when_parent_was_queued_before_open() {
        var bars = List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1),
                bar("2024-04-02", 1.2, 1.2, 1.2, 1.2), bar("2024-04-03", 1.3, 1.3, 1.3, 1.3));
        var result = run(bars, (a, e) -> {
            a.submitOnFirstFill = true;
            if (e instanceof TradeBar && a.opens == 1) a.enter(Order.Side.BUY, .01, 1);
        }, SPREAD);
        var entries = result.executions().stream().filter(e -> e.reason() == TradingSimulator.Reason.ENTRY).toList();
        assertEquals(2, entries.size());
        assertEquals(1.2003, entries.getFirst().fill().tradePrice(), 1e-12);
        assertEquals(1.3003, entries.getLast().fill().tradePrice(), 1e-12);
    }

    @Test void protectedPosition_with_unfinished_holding_period_is_closed_at_EOF() {
        var result = run(List.of(bar("2024-04-01", 1.1, 1.101, 1.099, 1.101)),
                (a, e) -> { if (e instanceof BarOpen) a.enter(Order.Side.SELL_SHORT, .01, 3); }, SPREAD);
        assertEquals(2, result.executions().size());
        assertEquals(TradingSimulator.Reason.END_OF_DATA, result.executions().getLast().reason());
        assertEquals(9987, result.finalEquity(), 1e-8);
    }

    @Test void shutdown_at_open_uses_latest_opening_quote_without_reading_unfinished_close() {
        var bars = List.of(bar("2024-04-01", 1.1, 1.1, 1.1, 1.1),
                bar("2024-04-02", 1.2, 1.8, 1.0, 1.7));
        for (int entryDay : List.of(1, 2)) {
            var result = run(bars, (a, e) -> {
                if (e instanceof BarOpen && a.opens == entryDay) {
                    a.enter(Order.Side.BUY, .01, 1);
                    a.shutdown();
                }
            }, SPREAD);
            assertEquals(2, result.executions().size());
            assertEquals(9997, result.finalEquity(), 1e-8);
            assertEquals(result.executions().getFirst().fill().time(), result.executions().getLast().fill().time());
            assertEquals(result.executions().getLast().fill().time(), result.equityCurve().getLast().time());
        }
    }

    static List<MarketEvent> replay(Map<SymbolIdentity, List<Candle>> candles, int limit) {
        var result = new ArrayList<MarketEvent>();
        var supplier = new BarReplayMarketSupplier(candles, WARSAW);
        try {
            supplier.open();
            while (supplier.poll(result::add, limit) > 0) { }
        } finally { supplier.close(); }
        return result;
    }

    static AlgorithmBacktestResult run(List<Candle> bars, BiConsumer<FixtureAlgorithm, MarketEvent> action, double spread) {
        return new AlgorithmBacktestRunner().runDetailed(new AlgorithmBacktestContext("fixture",
                context -> new FixtureAlgorithm(context, action),
                () -> new BarReplayMarketSupplier(Map.of(EURUSD, bars), WARSAW),
                new BacktestExecutionOptions(10000, spread)));
    }

    static Candle bar(String date, double open, double high, double low, double close) {
        return Candle.of(Chronological.toEpochNanos(LocalDate.parse(date).atStartOfDay()), open, high, low, close);
    }

    static class FixtureAlgorithm extends AbstractAlgorithm<SimpleInstrumentPrices> {
        final BiConsumer<FixtureAlgorithm, MarketEvent> action;
        final List<Order.Filled> fills = new ArrayList<>();
        final List<Order.Rejected> rejections = new ArrayList<>();
        int opens;
        int fillsAtClose;
        boolean submitOnFirstFill;
        FixtureAlgorithm(AlgorithmContext context, BiConsumer<FixtureAlgorithm, MarketEvent> action) {
            super(context);
            this.action = action;
        }
        @Override protected InstrumentDataFactory<SimpleInstrumentPrices> createInstrumentDataFactory() {
            return SimpleInstrumentPrices::new;
        }
        @Override public void onMarketMessage(MarketEvent event) {
            assertEquals(event.time(), getClock().time(), "Playback clock precedes every market callback");
            super.onMarketMessage(event);
            if (event instanceof BarOpen) opens++;
            action.accept(this, event);
        }
        void shutdown() {
            context.getShutdownResponseHandler().onShutdownResponse(new ShutdownResponse(getId(), getClock().time()));
        }
        void plainOrder(TimeInForce timeInForce) {
            submitOrder(makeMarketOrder(Order.Side.BUY, 10000, EURUSD).destinationId("SIMULATOR")
                    .timeInForce(timeInForce).toNewOrder());
        }
        void enter(Order.Side side, double distance, int bars) {
            submitOrder(makeMarketOrder(side, 10000, EURUSD).destinationId("SIMULATOR")
                    .exitRules(new ExitRules(distance, bars)).toNewOrder());
        }
        @Override public void onOrderFilled(Order.Filled fill) {
            assertEquals(fill.time(), getClock().time(), "Playback clock precedes every fill callback");
            fills.add(fill);
            if (submitOnFirstFill && fills.size() == 1) plainOrder(null);
        }
        @Override public void onOrderRejected(Order.Rejected rejected) {
            assertEquals(rejected.time(), getClock().time());
            rejections.add(rejected);
        }
        @Override public void close() { fillsAtClose = fills.size(); }
    }
}
