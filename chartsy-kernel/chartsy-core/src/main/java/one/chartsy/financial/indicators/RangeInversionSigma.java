/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import one.chartsy.Candle;
import one.chartsy.data.structures.RingBuffer;
import one.chartsy.financial.AbstractCandleIndicator;
import one.chartsy.study.ChartStudy;
import one.chartsy.study.LinePlotSpec;
import one.chartsy.study.StudyAxis;
import one.chartsy.study.StudyFactory;
import one.chartsy.study.StudyInputKind;
import one.chartsy.study.StudyKind;
import one.chartsy.study.StudyOutput;
import one.chartsy.study.StudyParameter;
import one.chartsy.study.StudyParameterScope;
import one.chartsy.study.StudyParameterType;
import one.chartsy.study.StudyPlacement;

/**
 * For each incoming candle this indicator looks back over every window
 * length w = 7..periods (inclusive), computes the number of inversions in
 * the sequence of ranges  (range_i = high_i - low_i), converts that count
 * to a z-score  z = (observed - mu_w) / sigma_w,  and keeps the maximum
 * |z| among all windows.  The last window length that attains the maximum
 * is also stored.
 * <p>
 * Expected value {@code mu_w = w(w-1)/4} <br/>
 * Standard deviation {@code sigma_w = sqrt(w(w-1)(2w+5)/72)}
 * <p>
 * Note: for clarity the inversion count inside each window is obtained by
 * a mergesort-based O(w log w) algorithm.  If periods is large and speed
 * is critical, swap it for a Fenwick tree or reuse overlapping counts.
 *
 * @author  Mariusz Bernacki
 */
@ChartStudy(
        name = "Range Inversion Sigma",
        label = "RangeInvSigma ({periods})",
        category = "Volatility",
        kind = StudyKind.INDICATOR,
        placement = StudyPlacement.OWN_PANEL
)
@StudyAxis(steps = {-10, -8, -6, -4, -2, 0, 2, 4, 6, 8, 10})
@StudyParameter(id = "color", name = "Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#D32F2F", order = 100)
@StudyParameter(id = "style", name = "Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "THIN_SOLID", order = 110)
@LinePlotSpec(id = "sigma", label = "Sigma", output = "value", colorParameter = "color", strokeParameter = "style", order = 10)
public class RangeInversionSigma extends AbstractCandleIndicator {

    private static final int MIN_WINDOW = 7;
    public static final int DEFAULT_PERIODS = 100;
    /** The maximal look-back length. */
    private final int periods;
    /** The newest element is at index 0. */
    private final RingBuffer<Double> ranges;
    /** The |z|max for the current bar. */
    private double lastSigma = Double.NaN;
    /** The window length that gave it. */
    private int lastWindowLength = 0;

    @StudyFactory(input = StudyInputKind.CANDLES)
    public static RangeInversionSigma study(
            @StudyParameter(id = "periods", name = "Periods", scope = StudyParameterScope.COMPUTATION, defaultValue = "100", order = 10) int periods
    ) {
        return new RangeInversionSigma(periods);
    }

    public RangeInversionSigma() {
        this(DEFAULT_PERIODS);
    }

    public RangeInversionSigma(int periods) {
        if (periods < MIN_WINDOW)
            throw new IllegalArgumentException("`periods` must be at least " + MIN_WINDOW);

        this.periods = periods;
        this.ranges  = new RingBuffer<>(periods);
    }

    @Override
    public void accept(Candle candle) {
        // Append the newest range and discard the oldest if buffer is full.
        double range = candle.high()/candle.low() - 1.0;
        ranges.add(range);

        int available = ranges.length();
        if (available < MIN_WINDOW) {
            lastSigma = Double.NaN;
            lastWindowLength = 0;
            return;
        }

        double maxZ = Double.NEGATIVE_INFINITY;
        int bestW = 0;

        // Examine every window length w = MIN_WINDOW .. available (<= periods).
        for (int w = MIN_WINDOW; w <= available; w++) {
            long inversions = countInversions(w);
            double mu = w * (w - 1.0) / 4.0;
            double var = w * (w - 1.0) * (2.0 * w + 5.0) / 72.0;
            double z = (inversions - mu) / Math.sqrt(var);

            if (z > maxZ) {
                maxZ = z;
                bestW = w;
            }
        }
        lastSigma        = maxZ;
        lastWindowLength = bestW;
    }

    /**
     * Counts inversions in the newest w ranges (chronological order).
     * Complexity: O(w log w) via mergesort.
     */
    private long countInversions(int w) {
        // Copy the newest w items into arr[0..w-1] in chronological order.
        double[] arr = new double[w];
        for (int i = 0; i < w; i++)
            arr[w - 1 - i] = ranges.get(i);

        return mergeCount(arr, new double[w], 0, w - 1);
    }

    /* --- standard mergesort-with-counting --- */
    private static long mergeCount(double[] a, double[] tmp, int left, int right) {
        if (left >= right)
            return 0L;

        int mid = (left + right) >>> 1;
        long count = mergeCount(a, tmp, left, mid) + mergeCount(a, tmp, mid + 1, right);
        int i = left, j = mid + 1, k = left;
        while (i <= mid && j <= right) {
            if (a[i] <= a[j]) {
                tmp[k++] = a[i++];
            } else {
                tmp[k++] = a[j++];
                count += (mid - i + 1);
            }
        }
        while (i <= mid)
            tmp[k++] = a[i++];
        while (j <= right)
            tmp[k++] = a[j++];

        System.arraycopy(tmp, left, a, left, right - left + 1);
        return count;
    }

    /**
     * Gives the maximum absolute sigma multiplier for the latest bar.
     */
    @Override
    @StudyOutput(id = "value", name = "Sigma", order = 10)
    public double getLast() {
        return lastSigma;
    }

    /**
     * Gives the window length that produced the current sigma maximum.
     */
    @StudyOutput(id = "windowLength", name = "Window Length", order = 20)
    public int getLastWindowLength() {
        return lastWindowLength;
    }

    /**
     * Determines if the indicator is ready once at least MIN_WINDOW candles are available.
     */
    @Override
    public boolean isReady() {
        return ranges.length() >= MIN_WINDOW;
    }
}
