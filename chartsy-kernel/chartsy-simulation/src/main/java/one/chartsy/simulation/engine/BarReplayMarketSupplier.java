package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageHandler;
import one.chartsy.messaging.MarketMessageSource;
import one.chartsy.messaging.data.TradeBar;
import one.chartsy.trade.algorithm.MarketSupplier;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Causal replay of chronological per-symbol candles; sources are merged after phase expansion. */
public final class BarReplayMarketSupplier implements MarketSupplier {
    private final Map<SymbolIdentity, ? extends Iterable<Candle>> candles;
    private final Map<SymbolIdentity, BarSession> sessions;
    private final MarketReplayMerge merge = new MarketReplayMerge();

    public BarReplayMarketSupplier(Map<SymbolIdentity, ? extends Iterable<Candle>> candles, ZoneId zone) {
        this(candles, BarSession.daily(zone));
    }

    public BarReplayMarketSupplier(Map<SymbolIdentity, ? extends Iterable<Candle>> candles, BarSession session) {
        this(candles, uniformSessions(candles, session));
    }

    /** Each symbol's candles use its own interval mapping, before the sources are merged. */
    public BarReplayMarketSupplier(Map<SymbolIdentity, ? extends Iterable<Candle>> candles,
                                   Map<SymbolIdentity, BarSession> sessions) {
        this.candles = Map.copyOf(candles);
        this.sessions = Map.copyOf(sessions);
        if (!this.candles.keySet().equals(this.sessions.keySet()))
            throw new IllegalArgumentException("Sessions must match the candle symbols exactly");
    }

    private static Map<SymbolIdentity, BarSession> uniformSessions(
            Map<SymbolIdentity, ? extends Iterable<Candle>> candles, BarSession session) {
        var sessions = new HashMap<SymbolIdentity, BarSession>();
        for (var symbol : candles.keySet())
            sessions.put(symbol, session);
        return sessions;
    }

    @Override public void open() {
        merge.close();
        var symbols = candles.keySet().stream().sorted(Comparator.comparing(SymbolIdentity::name)
                .thenComparing(Object::toString)).toList();
        for (int i = 0; i < symbols.size(); i++) {
            var symbol = symbols.get(i);
            var iterator = candles.get(symbol).iterator();
            MarketMessageSource source = new MarketMessageSource() {
                private boolean open = true;
                @Override public MarketEvent getMessage() {
                    return open && iterator.hasNext() ? new TradeBar.Of(symbol, iterator.next()) : null;
                }
                @Override public boolean isOpen() { return open; }
                @Override public void close() { open = false; }
            };
            merge.add(new BarReplaySource(source, sessions.get(symbol)), i);
        }
    }

    @Override public int poll(MarketMessageHandler handler, int limit) { return merge.poll(handler, limit); }
    @Override public void close() { merge.close(); }
}
