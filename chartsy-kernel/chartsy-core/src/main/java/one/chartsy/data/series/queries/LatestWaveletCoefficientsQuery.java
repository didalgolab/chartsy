/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.data.series.queries;

import one.chartsy.Candle;
import one.chartsy.CandleField;
import one.chartsy.data.RealVector;
import one.chartsy.data.Series;
import one.chartsy.data.SeriesQuery;
import one.chartsy.time.Chronological;
import one.chartsy.wavelets.DiscreteWaveletTransform;
import one.chartsy.wavelets.HaarWavelet;
import one.chartsy.wavelets.Wavelet;

import java.util.function.ToDoubleFunction;

/**
 * Extracts wavelet coefficients from the latest bars in a {@link Series} of {@link Candle}s.
 */
public final class LatestWaveletCoefficientsQuery implements SeriesQuery<Series<Candle>, LatestWaveletCoefficientsQuery.Result, Candle> {
    private final int window;
    private final DiscreteWaveletTransform dwt;
    private final ToDoubleFunction<Candle> extractor;

    public LatestWaveletCoefficientsQuery(int window) {
        this(window, new HaarWavelet(), CandleField.CLOSE);
    }

    public LatestWaveletCoefficientsQuery(int window, Wavelet wavelet) {
        this(window, wavelet, CandleField.CLOSE);
    }

    public LatestWaveletCoefficientsQuery(int window, Wavelet wavelet, ToDoubleFunction<Candle> extractor) {
        if (window <= 0) {
            throw new IllegalArgumentException("window must be positive");
        }
        if ((window & (window - 1)) != 0) {
            throw new IllegalArgumentException("window must be a power of two");
        }
        if (wavelet == null) {
            throw new IllegalArgumentException("wavelet is required");
        }
        if (extractor == null) {
            throw new IllegalArgumentException("extractor is required");
        }
        this.window = window;
        this.dwt = new DiscreteWaveletTransform(wavelet);
        this.extractor = extractor;
    }

    public int getWindow() {
        return window;
    }

    @Override
    public Result queryFrom(Series<Candle> series) {
        if (series == null) {
            throw new IllegalArgumentException("series is required");
        }
        if (series.length() < window) {
            return null;
        }

        double[] values = new double[window];
        Chronological.ChronoOrder order = series.getTimeline().getOrder();
        if (order.isReversed()) {
            for (int i = 0; i < window; i++) {
                values[window - 1 - i] = extractor.applyAsDouble(series.get(i));
            }
        } else {
            int start = series.length() - window;
            for (int i = 0; i < window; i++) {
                values[i] = extractor.applyAsDouble(series.get(start + i));
            }
        }

        double[] coefficients = dwt.transform(RealVector.from(values)).values();
        return new Result(values, coefficients);
    }

    public record Result(double[] values, double[] coefficients) {}
}
