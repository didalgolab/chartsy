package one.chartsy.simulation.engine;

import one.chartsy.Candle;
import one.chartsy.messaging.MarketEvent;
import one.chartsy.messaging.MarketMessageSource;
import one.chartsy.messaging.data.BarOpen;
import one.chartsy.messaging.data.TradeBar;

/** Expands each source before merging, so no instrument reveals an unfinished candle. */
final class BarReplaySource implements MarketMessageSource {
    private final MarketMessageSource source;
    private final BarSession sessions;
    private TradeBar pendingClose;
    private long previousClose = Long.MIN_VALUE;

    BarReplaySource(MarketMessageSource source, BarSession sessions) {
        this.source = source;
        this.sessions = sessions;
    }

    @Override
    public MarketEvent getMessage() {
        if (pendingClose != null) {
            var close = pendingClose;
            pendingClose = null;
            return close;
        }
        var next = source.getMessage();
        if (next == null)
            return null;
        if (!(next instanceof TradeBar bar))
            throw new IllegalArgumentException("Bar replay requires a candle source");
        Candle candle = bar.get();
        var interval = sessions.interval(candle);
        if (interval.openTime() < previousClose)
            throw new IllegalArgumentException("Overlapping or out-of-order bars for " + bar.symbol());
        previousClose = interval.closeTime();
        pendingClose = new TradeBar.Of(bar.symbol(), Candle.of(interval.closeTime(), candle.open(),
                candle.high(), candle.low(), candle.close(), candle.volume()));
        return new BarOpen(bar.symbol(), interval.openTime(), interval.openTime(), candle.open());
    }

    @Override public boolean isOpen() { return source.isOpen(); }
    @Override public void close() { source.close(); }
}
