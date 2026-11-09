package one.chartsy.simulation.engine.price;

import one.chartsy.data.market.BestBidOfferQuote;

/** Immutable retained BBO state; NaN denotes a side not observed yet. */
public record QuoteSnapshot(double bid, double ask, long time) {
    public static final QuoteSnapshot EMPTY = new QuoteSnapshot(Double.NaN, Double.NaN, Long.MIN_VALUE);

    public boolean hasBid() { return Double.isFinite(bid); }
    public boolean isComplete() { return hasBid() && Double.isFinite(ask); }

    /** Equal timestamps retain input order; unchanged sides keep their last observed price. */
    public QuoteSnapshot update(BestBidOfferQuote update) {
        if (update.time() < time)
            throw new IllegalArgumentException("Quote timestamps must not move backwards");
        if (update.bidChanged() && !Double.isFinite(update.bidPrice())
                || update.askChanged() && !Double.isFinite(update.askPrice()))
            throw new IllegalArgumentException("Changed quote sides must have finite prices");
        double nextBid = update.bidChanged() ? update.bidPrice() : bid;
        double nextAsk = update.askChanged() ? update.askPrice() : ask;
        if (Double.isFinite(nextBid) && Double.isFinite(nextAsk) && nextAsk < nextBid)
            throw new IllegalArgumentException("Crossed bid/ask quote");
        return new QuoteSnapshot(nextBid, nextAsk, update.time());
    }
}
