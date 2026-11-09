package one.chartsy.simulation.engine;

import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketMessageHandler;
import one.chartsy.messaging.MarketMessageSource;
import one.chartsy.trade.algorithm.MarketSupplier;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Supplier;

/** Reopenable, stable multi-symbol quote replay, optionally exposing only its derived candle phases. */
public final class QuoteReplayMarketSupplier implements MarketSupplier {
    private final Map<SymbolIdentity, ? extends Supplier<? extends MarketMessageSource>> sources;
    private final QuoteSession sessions;
    private final boolean emitQuotes;
    private final MarketReplayMerge merge = new MarketReplayMerge();

    public QuoteReplayMarketSupplier(Map<SymbolIdentity, ? extends Supplier<? extends MarketMessageSource>> sources, QuoteSession sessions) {
        this(sources, sessions, true);
    }

    public static QuoteReplayMarketSupplier candles(Map<SymbolIdentity, ? extends Supplier<? extends MarketMessageSource>> sources, QuoteSession sessions) {
        return new QuoteReplayMarketSupplier(sources, sessions, false);
    }

    private QuoteReplayMarketSupplier(Map<SymbolIdentity, ? extends Supplier<? extends MarketMessageSource>> sources, QuoteSession sessions, boolean emitQuotes) {
        this.sources = Map.copyOf(sources);
        this.sessions = sessions;
        this.emitQuotes = emitQuotes;
    }

    @Override public void open() {
        merge.close();
        var symbols = sources.keySet().stream().sorted(Comparator.comparing(SymbolIdentity::name).thenComparing(Object::toString)).toList();
        try {
            for (int i = 0; i < symbols.size(); i++) {
                var symbol = symbols.get(i);
                var source = sources.get(symbol).get();
                merge.add(emitQuotes ? new QuoteBarReplaySource(symbol, source, sessions) : QuoteBarReplaySource.candles(symbol, source, sessions), i);
            }
        } catch (RuntimeException | Error failure) { merge.close(); throw failure; }
    }

    @Override public int poll(MarketMessageHandler handler, int limit) { return merge.poll(handler, limit); }
    @Override public void close() { merge.close(); }
}
