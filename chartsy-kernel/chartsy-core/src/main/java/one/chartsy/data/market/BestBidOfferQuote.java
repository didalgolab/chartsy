/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.market;

import one.chartsy.time.Chronological;

/**
 * Canonical top-of-book delta. Presence flags show which side changed.
 * Prices and sizes are ordinary double values, not encoded Decimal64 values.
 */
public record BestBidOfferQuote(
        long time,
        boolean bidChanged,
        double bidPrice,
        double bidSize,
        boolean askChanged,
        double askPrice,
        double askSize
) implements Chronological {

    public static BestBidOfferQuote bidOnly(long time, long price, long size) {
        return new BestBidOfferQuote(time, true, price, size, false, Double.POSITIVE_INFINITY, 0L);
    }

    public static BestBidOfferQuote askOnly(long time, long price, long size) {
        return new BestBidOfferQuote(time, false, Double.NEGATIVE_INFINITY, 0L, true, price, size);
    }

    public static BestBidOfferQuote of(long time, long bidPrice, long bidSize, long askPrice, long askSize) {
        return new BestBidOfferQuote(time, true, bidPrice, bidSize, true, askPrice, askSize);
    }

    public static BestBidOfferQuote bidOnly(long time, double price, double size) {
        return new BestBidOfferQuote(time, true, price, size, false, Double.NaN, 0);
    }

    public static BestBidOfferQuote askOnly(long time, double price, double size) {
        return new BestBidOfferQuote(time, false, Double.NaN, 0, true, price, size);
    }

    public static BestBidOfferQuote of(long time, double bidPrice, double bidSize, double askPrice, double askSize) {
        return new BestBidOfferQuote(time, true, bidPrice, bidSize, true, askPrice, askSize);
    }
}
