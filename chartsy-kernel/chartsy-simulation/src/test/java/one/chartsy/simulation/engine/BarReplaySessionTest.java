package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.messaging.data.TradeBar;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static one.chartsy.simulation.engine.CausalBarBacktestTest.*;
import static org.junit.jupiter.api.Assertions.*;

class BarReplaySessionTest {
    private static final SymbolIdentity GBPUSD = SymbolIdentity.of("GBPUSD");

    @Test void replay_symbol_sessions_keep_distinct_availability_for_identical_candle_labels() {
        var candle = bar("2024-04-01", 1.1, 1.2, 1.0, 1.15);
        var candles = Map.of(EURUSD, List.of(candle), GBPUSD, List.of(candle));
        var sessions = Map.<SymbolIdentity, BarSession>of(
                EURUSD, ignored -> new BarSession.Interval(100, 200),
                GBPUSD, ignored -> new BarSession.Interval(150, 250));
        var events = replay(new BarReplayMarketSupplier(candles, sessions), 1024);
        assertEquals(List.of(100L, 150L, 200L, 250L), events.stream().map(MarketEvent::time).toList());
        assertEquals(List.of(EURUSD, GBPUSD, EURUSD, GBPUSD), events.stream().map(MarketEvent::symbol).toList());
        assertInstanceOf(BarOpen.class, events.get(0));
        assertInstanceOf(BarOpen.class, events.get(1));
        assertInstanceOf(TradeBar.class, events.get(2));
        assertInstanceOf(TradeBar.class, events.get(3));
    }

    @Test void replay_symbol_sessions_preserve_completion_then_symbol_order_across_poll_sizes() {
        var first = bar("2024-04-01", 1.1, 1.2, 1.0, 1.15);
        var second = bar("2024-04-02", 1.2, 1.3, 1.1, 1.25);
        var candles = new LinkedHashMap<SymbolIdentity, List<Candle>>();
        candles.put(GBPUSD, List.of(first, second));
        candles.put(EURUSD, List.of(first, second));
        BarSession eur = c -> c.time() == first.time()
                ? new BarSession.Interval(100, 200) : new BarSession.Interval(200, 300);
        BarSession gbp = c -> c.time() == first.time()
                ? new BarSession.Interval(150, 200) : new BarSession.Interval(200, 300);
        var sessions = Map.of(EURUSD, eur, GBPUSD, gbp);
        var expected = replay(new BarReplayMarketSupplier(candles, sessions), 1);
        var forward = new LinkedHashMap<SymbolIdentity, List<Candle>>();
        forward.put(EURUSD, candles.get(EURUSD));
        forward.put(GBPUSD, candles.get(GBPUSD));
        assertEquals(expected, replay(new BarReplayMarketSupplier(forward, sessions), 1024));
        var boundary = expected.stream().filter(e -> e.time() == 200).toList();
        assertEquals(List.of(EURUSD, GBPUSD, EURUSD, GBPUSD), boundary.stream().map(MarketEvent::symbol).toList());
        assertTrue(boundary.subList(0, 2).stream().allMatch(TradeBar.class::isInstance));
        assertTrue(boundary.subList(2, 4).stream().allMatch(BarOpen.class::isInstance));
    }

    @Test void replay_uniform_map_is_equivalent_to_both_existing_constructors() {
        var candles = Map.of(EURUSD, List.of(bar("2024-03-31", 1, 1, 1, 1)),
                GBPUSD, List.of(bar("2024-03-31", 2, 2, 2, 2)));
        var session = BarSession.daily(WARSAW);
        var expected = replay(new BarReplayMarketSupplier(candles, WARSAW), 1);
        assertEquals(expected, replay(new BarReplayMarketSupplier(candles, session), 1024));
        assertEquals(expected, replay(new BarReplayMarketSupplier(candles, Map.of(EURUSD, session, GBPUSD, session)), 2));
    }

    @Test void constructor_copies_session_map_and_rejects_missing_or_extra_symbols() {
        var candles = Map.of(EURUSD, List.of(bar("2024-04-01", 1, 1, 1, 1)));
        BarSession session = ignored -> new BarSession.Interval(100, 200);
        var sessions = new HashMap<SymbolIdentity, BarSession>();
        sessions.put(EURUSD, session);
        var supplier = new BarReplayMarketSupplier(candles, sessions);
        sessions.put(EURUSD, ignored -> new BarSession.Interval(300, 400));
        assertEquals(List.of(100L, 200L), replay(supplier, 1).stream().map(MarketEvent::time).toList());
        assertThrows(IllegalArgumentException.class, () -> new BarReplayMarketSupplier(candles, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new BarReplayMarketSupplier(candles, Map.of(EURUSD, session, GBPUSD, session)));
        sessions.put(EURUSD, null);
        assertThrows(NullPointerException.class, () -> new BarReplayMarketSupplier(candles, sessions));
    }

    private static List<MarketEvent> replay(BarReplayMarketSupplier supplier, int limit) {
        var events = new ArrayList<MarketEvent>();
        try {
            supplier.open();
            while (supplier.poll(events::add, limit) > 0) { }
        } finally { supplier.close(); }
        return events;
    }
}
