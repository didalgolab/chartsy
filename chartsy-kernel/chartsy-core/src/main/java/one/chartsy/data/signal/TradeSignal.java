/*
 * Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.signal;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable value object that represents a single trading signal
 * (either <em>long</em> or <em>short</em>).
 *
 * <p>A signal bundles together all information required to evaluate
 * or execute a trade:
 * <ul>
 *   <li>{@code side} &ndash; specifies whether the intent is to open a
 *       long or a short position;</li>
 *   <li>{@code entryPrice} &ndash; the price at which a market order
 *       becomes active (for a stop or limit entry);</li>
 *   <li>{@code stopLoss} &ndash; the price level that invalidates the
 *       signal and protects capital;</li>
 *   <li>{@code takeProfit} &ndash; the price level that locks in the
 *       desired reward;</li>
 *   <li>{@code time} &ndash; the moment when the signal was generated,
 *       usually taken from the latest price bar processed.</li>
 * </ul>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>All prices must be strictly positive.</li>
 *   <li>{@code stopLoss} must be below {@code entryPrice} for a long
 *       signal and above {@code entryPrice} for a short signal.</li>
 *   <li>{@code takeProfit} must be above {@code entryPrice} for a long
 *       signal and below {@code entryPrice} for a short signal.</li>
 * </ul>
 *
 * <p>The class exposes two static factory methods,
 * {@link #goLong(double, double, double, LocalDateTime)} and
 * {@link #goShort(double, double, double, LocalDateTime)}, which
 * validate these invariants at creation time.</p>
 *
 * <p>Because the object is immutable and fully self-contained, it can
 * be safely shared between threads and stored in collections without
 * additional synchronization.</p>
 */
public final class TradeSignal {

    /** The direction of the intended position. */
    public enum Side {
        /** Expecting price appreciation. */
        LONG,
        /** Expecting price depreciation. */
        SHORT
    }

    private final Side side;
    private final double entryPrice;
    private final double stopLoss;
    private final double takeProfit;
    private final LocalDateTime time;

    /**
     * Creates a {@code LONG} signal.
     *
     * @param entryPrice the trigger price
     * @param stopLoss   protective stop below {@code entryPrice}
     * @param takeProfit profit target above {@code entryPrice}
     * @param time       generation timestamp
     * @return a validated {@code TradeSignal}
     * @throws IllegalArgumentException if any invariant is violated
     * @throws NullPointerException if {@code time} is {@code null}
     */
    public static TradeSignal goLong(double entryPrice,
                                     double stopLoss,
                                     double takeProfit,
                                     LocalDateTime time) {
        return new TradeSignal(Side.LONG, entryPrice, stopLoss, takeProfit, time);
    }

    public static TradeSignal goLong(double entryPrice,
                                     double stopLoss,
                                     LocalDateTime time) {
        return goLong(entryPrice, stopLoss, Double.NaN, time);
    }

    /**
     * Creates a {@code SHORT} signal.
     *
     * @param entryPrice the trigger price
     * @param stopLoss   protective stop above {@code entryPrice}
     * @param takeProfit profit target below {@code entryPrice}
     * @param time       generation timestamp
     * @return a validated {@code TradeSignal}
     * @throws IllegalArgumentException if any invariant is violated
     * @throws NullPointerException     if {@code time} is {@code null}
     */
    public static TradeSignal goShort(double entryPrice,
                                      double stopLoss,
                                      double takeProfit,
                                      LocalDateTime time) {
        return new TradeSignal(Side.SHORT, entryPrice, stopLoss, takeProfit, time);
    }

    public static TradeSignal goShort(double entryPrice,
                                      double stopLoss,
                                      LocalDateTime time) {
        return goShort(entryPrice, stopLoss, Double.NaN, time);
    }

    private TradeSignal(Side side, double entryPrice, double stopLoss, double takeProfit, LocalDateTime time) {
        Objects.requireNonNull(time, "time");
        validatePrices(side, entryPrice, stopLoss, takeProfit);

        this.side = side;
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.takeProfit = takeProfit;
        this.time = time;
    }

    private static void validatePrices(Side side,
                                       double entry,
                                       double stop,
                                       double profit) {
        if (entry <= 0 || stop <= 0 || profit <= 0)
            throw new IllegalArgumentException("Prices must be positive");

        switch (side) {
            case LONG -> {
                if (stop >= entry || profit <= entry)
                    throw new IllegalArgumentException(
                            "For LONG: stopLoss < entryPrice < takeProfit");
            }
            case SHORT -> {
                if (stop <= entry || profit >= entry)
                    throw new IllegalArgumentException(
                            "For SHORT: takeProfit < entryPrice < stopLoss");
            }
            default -> throw new AssertionError("Unexpected side: " + side);
        }
    }

    /* ------------------------------------------------------------------
     *  Accessors
     * ------------------------------------------------------------------ */

    public Side side()             { return side; }
    public double entryPrice()     { return entryPrice; }
    public double stopLoss()       { return stopLoss; }
    public double takeProfit()     { return takeProfit; }
    public LocalDateTime time()    { return time; }

    public boolean isLong()  { return side == Side.LONG; }
    public boolean isShort() { return side == Side.SHORT; }

    public boolean hasTakeProfit() {
        return !Double.isNaN(takeProfit);
    }

    /* ------------------------------------------------------------------
     *  Standard overrides
     * ------------------------------------------------------------------ */

    @Override
    public String toString() {
        return "TradeSignal[" +
               "side=" + side +
               ", entry=" + entryPrice +
               ", stop=" + stopLoss +
               ", target=" + takeProfit +
               ", time=" + time +
               ']';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TradeSignal other))
            return false;

        return Double.compare(other.entryPrice, entryPrice) == 0
                && Double.compare(other.stopLoss, stopLoss) == 0
                && Double.compare(other.takeProfit, takeProfit) == 0
                && side == other.side
                && time.equals(other.time);
    }

    @Override
    public int hashCode() {
        return Objects.hash(side, entryPrice, stopLoss, takeProfit, time);
    }
}
