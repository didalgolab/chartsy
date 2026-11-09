package one.chartsy.simulation.engine;

import one.chartsy.SymbolIdentity;
import one.chartsy.data.market.BestBidOfferQuote;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageSource;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.messaging.data.BestBidOfferEvent;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.time.Chronological;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static one.chartsy.simulation.engine.QuoteExecutionTest.*;
import static org.junit.jupiter.api.Assertions.*;

class QuoteReplayTest {
    @Test void quote_and_candle_replays_derive_identical_phases_and_keep_equal_time_order() {
        var quotes = List.of(q(1, 1, 1.1, 1.1004), q(1, 1, 1.11, 1.1104), q(1, 2, 1.09, 1.0904), q(2, 0, 1.12, 1.1204));
        var ticks = drain(new QuoteBarReplaySource(SYMBOL, source(quotes), DAILY));
        var candles = drain(QuoteBarReplaySource.candles(SYMBOL, source(quotes), DAILY));
        assertEquals(candles, ticks.stream().filter(e -> !(e instanceof BestBidOfferEvent)).toList());
        assertEquals(List.of(BestBidOfferEvent.Of.class, BarOpen.class, BestBidOfferEvent.Of.class,
                BestBidOfferEvent.Of.class, TradeBar.Of.class, BestBidOfferEvent.Of.class, BarOpen.class, TradeBar.Of.class),
                ticks.stream().map(Object::getClass).toList());
        var open = (BarOpen) candles.getFirst();
        assertEquals(q(1, 0, 1, 1).time(), open.barId());
        assertEquals(quotes.getFirst().time(), open.time());
        var bar = ((TradeBar) candles.get(1)).get();
        assertEquals(1.1, bar.open()); assertEquals(1.11, bar.high());
        assertEquals(1.09, bar.low()); assertEquals(1.09, bar.close());
        assertEquals(quotes.getLast().time(), bar.time());
    }

    @Test void streaming_open_exposes_only_the_first_quote_without_reading_later_prices() {
        var reads = new int[1];
        var quotes = List.of(q(1, 0, 1.1, 1.1004), q(1, 1, 3.0, 3.1));
        var raw = new MarketMessageSource() {
            public MarketEvent getMessage() { return reads[0] == quotes.size() ? null : quotes.get(reads[0]++); }
            public boolean isOpen() { return true; }
            public void close() { }
        };
        try (var replay = new QuoteBarReplaySource(SYMBOL, raw, DAILY)) {
            assertSame(quotes.getFirst(), replay.getMessage());
            assertEquals(1, reads[0]);
            assertEquals(1.1, ((BarOpen) replay.getMessage()).open());
            assertEquals(1, reads[0]);
            assertSame(quotes.getLast(), replay.getMessage());
            assertEquals(2, reads[0]);
        }
    }

    @Test void missing_days_are_not_fabricated_and_session_exclusions_apply_to_quotes_and_bars() {
        var warsaw = ZoneId.of("Europe/Warsaw");
        var session = QuoteSession.weekdays(warsaw, LocalTime.of(4, 0), LocalTime.of(19, 0));
        var events = List.of(at("2024-04-01T01:59:59Z"), at("2024-04-01T02:00:00Z"),
                at("2024-04-05T16:59:59Z"), at("2024-04-05T17:00:00Z"), at("2024-04-06T12:00:00Z"));
        var replay = drain(new QuoteBarReplaySource(SYMBOL, source(events), session));
        assertEquals(6, replay.size());
        assertEquals(2, replay.stream().filter(BestBidOfferEvent.class::isInstance).count());
        assertEquals(2, replay.stream().filter(TradeBar.class::isInstance).count());
        assertEquals(Chronological.toEpochNanos(Instant.parse("2024-04-05T17:00:00Z")), replay.getLast().time());
        assertTrue(drain(new QuoteBarReplaySource(SYMBOL, source(List.of()), session)).isEmpty());
    }

    @Test void Warsaw_daily_boundaries_cover_23_and_25_hour_DST_days() {
        var session = QuoteSession.daily(ZoneId.of("Europe/Warsaw"));
        var spring = session.interval(at("2024-03-31T12:00:00Z").time());
        var autumn = session.interval(at("2024-10-27T12:00:00Z").time());
        assertEquals(Duration.ofHours(23).toNanos(), spring.closeTime() - spring.openTime());
        assertEquals(Duration.ofHours(25).toNanos(), autumn.closeTime() - autumn.openTime());
    }

    @Test void merge_is_reopenable_and_deterministic_across_poll_sizes_and_input_map_order() {
        var quotes = List.of(q(1, 0, 1.1, 1.1004), q(1, 0, 1.101, 1.1014), q(2, 0, 1.12, 1.1204));
        var z = SymbolIdentity.of("ZZZ");
        var second = quotes.stream().map(e -> new BestBidOfferEvent.Of(z, e.get())).toList();
        var first = new LinkedHashMap<SymbolIdentity, Supplier<MarketMessageSource>>();
        first.put(SYMBOL, () -> source(quotes)); first.put(z, () -> source(second));
        var reverse = new LinkedHashMap<SymbolIdentity, Supplier<MarketMessageSource>>();
        reverse.put(z, () -> source(second)); reverse.put(SYMBOL, () -> source(quotes));
        var replay = new QuoteReplayMarketSupplier(first, DAILY);
        var expected = replay(replay, 1);
        assertEquals(expected, replay(replay, 1024));
        assertEquals(expected, replay(new QuoteReplayMarketSupplier(reverse, DAILY), 2));
        long boundary = quotes.getLast().time();
        var atBoundary = expected.stream().filter(e -> e.time() == boundary).toList();
        assertInstanceOf(TradeBar.class, atBoundary.get(0));
        assertInstanceOf(TradeBar.class, atBoundary.get(1));
        assertInstanceOf(BestBidOfferEvent.class, atBoundary.get(2));
        assertInstanceOf(BestBidOfferEvent.class, atBoundary.get(3));
        assertInstanceOf(BarOpen.class, atBoundary.get(4));
        assertInstanceOf(BarOpen.class, atBoundary.get(5));
    }

    @Test void invalid_symbol_partial_quote_crossed_quote_and_backwards_source_fail_explicitly() {
        var good = q(1, 0, 1.1, 1.1004);
        var partial = new BestBidOfferEvent.Of(SYMBOL, BestBidOfferQuote.bidOnly(good.time(), 1.1, 1.0));
        for (var events : List.of(List.of(new BestBidOfferEvent.Of(SymbolIdentity.of("WRONG"), good.get())),
                List.of(partial), List.of(q(1, 0, 1.2, 1.1)), List.of(good, q(0, 0, 1.1, 1.1004)))) {
            assertThrows(IllegalArgumentException.class, () -> drain(new QuoteBarReplaySource(SYMBOL, source(events), DAILY)));
        }
    }

    @Test void supplier_open_failure_closes_sources_already_acquired() {
        var closed = new boolean[1];
        var raw = new MarketMessageSource() {
            public MarketEvent getMessage() { throw new IllegalArgumentException("invalid retained data"); }
            public boolean isOpen() { return !closed[0]; }
            public void close() { closed[0] = true; }
        };
        var replay = new QuoteReplayMarketSupplier(Map.of(SYMBOL, () -> raw), DAILY);
        assertThrows(IllegalArgumentException.class, replay::open);
        assertTrue(closed[0]);
        replay.close();
    }

    static BestBidOfferEvent at(String instant) {
        return new BestBidOfferEvent.Of(SYMBOL, BestBidOfferQuote.of(Chronological.toEpochNanos(Instant.parse(instant)), 1.1, 1.0, 1.1004, 2.0));
    }
    static List<MarketEvent> drain(MarketMessageSource source) {
        var result = new ArrayList<MarketEvent>();
        try (source) { MarketEvent event; while ((event = source.getMessage()) != null) result.add(event); }
        return result;
    }
    static List<MarketEvent> replay(QuoteReplayMarketSupplier source, int limit) {
        var result = new ArrayList<MarketEvent>();
        try { source.open(); while (source.poll(result::add, limit) > 0) { } }
        finally { source.close(); }
        return result;
    }
}
