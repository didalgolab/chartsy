package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageSource;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.messaging.data.BestBidOfferEvent;
import one.chartsy.messaging.data.TradeBar;

/**
 * Streaming full-BBO replay. The same retained quotes define executable prices and bid candles.
 * At a boundary: previous completion, first new quote, one opening, remaining source-order quotes.
 * Missing/empty intervals produce no invented bar. Input completeness is the source's responsibility.
 */
public final class QuoteBarReplaySource implements MarketMessageSource {
    private final SymbolIdentity symbol;
    private final MarketMessageSource source;
    private final QuoteSession sessions;
    private final boolean emitQuotes;
    private BestBidOfferEvent nextQuote;
    private BarOpen pendingOpen;
    private BarSession.Interval interval;
    private long previousQuoteTime = Long.MIN_VALUE, previousClose = Long.MIN_VALUE;
    private double open, high, low, close;
    private boolean exhausted, closed;

    public QuoteBarReplaySource(SymbolIdentity symbol, MarketMessageSource source, QuoteSession sessions) {
        this(symbol, source, sessions, true);
    }

    public static QuoteBarReplaySource candles(SymbolIdentity symbol, MarketMessageSource source, QuoteSession sessions) {
        return new QuoteBarReplaySource(symbol, source, sessions, false);
    }

    private QuoteBarReplaySource(SymbolIdentity symbol, MarketMessageSource source, QuoteSession sessions, boolean emitQuotes) {
        this.symbol = symbol;
        this.source = source;
        this.sessions = sessions;
        this.emitQuotes = emitQuotes;
    }

    @Override public MarketEvent getMessage() {
        if (closed) return null;
        if (pendingOpen != null) {
            var opening = pendingOpen;
            pendingOpen = null;
            return opening;
        }
        while (true) {
            if (nextQuote == null && !exhausted) readQuote();
            if (interval != null && (nextQuote == null || nextQuote.time() >= interval.closeTime())) {
                long end = interval.closeTime();
                interval = null;
                previousClose = end;
                return new TradeBar.Of(symbol, Candle.of(end, open, high, low, close));
            }
            if (nextQuote == null) return null;
            var quote = nextQuote;
            nextQuote = null;
            var selected = sessions.interval(quote.time());
            if (selected == null) continue;
            if (quote.time() < selected.openTime() || quote.time() >= selected.closeTime())
                throw new IllegalArgumentException("Quote lies outside its declared session");
            if (interval == null) {
                if (selected.openTime() < previousClose)
                    throw new IllegalArgumentException("Overlapping quote sessions");
                interval = selected;
                open = high = low = close = quote.get().bidPrice();
                var opening = new BarOpen(symbol, interval.openTime(), quote.time(), open);
                if (!emitQuotes) return opening;
                pendingOpen = opening;
                return quote;
            }
            if (!selected.equals(interval)) throw new IllegalArgumentException("Session changed before the current interval ended");
            high = Math.max(high, quote.get().bidPrice());
            low = Math.min(low, quote.get().bidPrice());
            close = quote.get().bidPrice();
            if (emitQuotes) return quote;
        }
    }

    private void readQuote() {
        var event = source.getMessage();
        if (event == null) { exhausted = true; return; }
        if (!(event instanceof BestBidOfferEvent quote) || !symbol.equals(event.symbol()))
            throw new IllegalArgumentException("Expected a BBO event for " + symbol);
        var bbo = quote.get();
        if (!bbo.bidChanged() || !bbo.askChanged() || !Double.isFinite(bbo.bidPrice())
                || !Double.isFinite(bbo.askPrice()) || bbo.askPrice() < bbo.bidPrice())
            throw new IllegalArgumentException("Quote-derived bars require full, finite, non-crossed BBO snapshots");
        if (quote.time() < previousQuoteTime) throw new IllegalArgumentException("Quote source moved backwards");
        previousQuoteTime = quote.time();
        nextQuote = quote;
    }

    @Override public boolean isOpen() { return !closed; }
    @Override public void close() { closed = true; source.close(); }
}
