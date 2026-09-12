/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.misc;

import one.chartsy.Candle;
import one.chartsy.data.CandleSeries;

import java.time.LocalDate;
import java.util.Locale;

/**
 * A compact, immutable snapshot of recent closing prices for a table preview.
 * Points are chronological and retain the candle's original end timestamp.
 * No reference to the source series or provider is retained.
 *
 * <p>Nonfinite closes are represented as {@code NaN} gaps. Extrema ignore these
 * gaps; percentage change requires valid first and last closes and a nonzero
 * first close. Empty snapshots have null dates and NaN statistics.
 */
public final class PriceSeriesThumbnail implements Comparable<PriceSeriesThumbnail> {
    private final long[] times;
    private final double[] closes;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final double min;
    private final double max;
    private final double changePercent;
    private final String description;

    /**
     * Copies the requested calendar-month window, ending on the latest candle's
     * trading date. The cutoff date is inclusive; exclusive next-midnight candle
     * timestamps are interpreted using {@link Candle#getDate()}.
     *
     * @throws IllegalArgumentException if {@code months} is not positive
     */
    public static PriceSeriesThumbnail of(CandleSeries series, int months) {
        if (months <= 0)
            throw new IllegalArgumentException("months must be positive");
        if (series.isEmpty())
            return new PriceSeriesThumbnail(new long[0], new double[0], null, null,
                    Double.NaN, Double.NaN);

        LocalDate endDate = series.get(0).getDate();
        LocalDate cutoff = endDate.minusMonths(months);
        int count = 0;
        while (count < series.length() && !series.get(count).getDate().isBefore(cutoff))
            count++;

        long[] times = new long[count];
        double[] closes = new double[count];
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < count; i++) {
            Candle candle = series.get(count - i - 1);
            times[i] = candle.time();
            double close = candle.close();
            closes[i] = Double.isFinite(close) ? close : Double.NaN;
            if (Double.isFinite(close)) {
                min = Math.min(min, close);
                max = Math.max(max, close);
            }
        }
        if (!Double.isFinite(min))
            min = max = Double.NaN;

        return new PriceSeriesThumbnail(times, closes, series.get(count - 1).getDate(), endDate, min, max);
    }

    private PriceSeriesThumbnail(long[] times, double[] closes, LocalDate startDate, LocalDate endDate,
                                 double min, double max) {
        this.times = times;
        this.closes = closes;
        this.startDate = startDate;
        this.endDate = endDate;
        this.min = min;
        this.max = max;
        double change = closes.length == 0 || closes[0] == 0.0 ? Double.NaN
                : (closes[closes.length - 1] / closes[0] - 1.0) * 100.0;
        this.changePercent = Double.isFinite(change) ? change : Double.NaN;
        this.description = closes.length == 0 ? "No closing prices"
                : startDate + " to " + endDate + " ("
                + (Double.isFinite(changePercent) ? String.format(Locale.ROOT, "%+.2f%%", changePercent)
                : "change unavailable") + ")";
    }

    public int size() {
        return times.length;
    }

    public long timeAt(int index) {
        return times[index];
    }

    public double closeAt(int index) {
        return closes[index];
    }

    public LocalDate startDate() {
        return startDate;
    }

    public LocalDate endDate() {
        return endDate;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double changePercent() {
        return changePercent;
    }

    @Override
    public int compareTo(PriceSeriesThumbnail other) {
        return Double.compare(changePercent, other.changePercent);
    }

    @Override
    public String toString() {
        return description;
    }
}
