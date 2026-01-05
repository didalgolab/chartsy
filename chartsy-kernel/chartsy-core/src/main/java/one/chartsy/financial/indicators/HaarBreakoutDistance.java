/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import com.google.gson.Gson;
import one.chartsy.Candle;
import one.chartsy.data.RealVector;
import one.chartsy.financial.AbstractCandleIndicator;
import one.chartsy.wavelets.DiscreteWaveletTransform;
import one.chartsy.wavelets.HaarWavelet;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes the minimum Haar-wavelet distance of the last 256 closes to a reference
 * set of breakout coefficient vectors using correlation-derived distance (1 - Pearson).
 */
public class HaarBreakoutDistance extends AbstractCandleIndicator {
    public static final int WINDOW = 256;
    private static final String COEFFICIENTS_RESOURCE = "HaarBreakoutDistance.jsonl";

    private final DiscreteWaveletTransform dwt = new DiscreteWaveletTransform(new HaarWavelet());
    private final List<double[]> referenceCoefficients;
    private final double[] window = new double[WINDOW];
    private final double[] orderedWindow = new double[WINDOW];
    private final PearsonsCorrelation pearson = new PearsonsCorrelation();
    private final SpearmansCorrelation spearman = new SpearmansCorrelation();

    private int windowSize;
    private int windowIndex;
    private double lastDistance = Double.NaN;
    private double lastPearson = Double.NaN;
    private double lastSpearman = Double.NaN;

    public HaarBreakoutDistance() {
        this(ReferenceCoefficientsHolder.INSTANCE);
    }

    private HaarBreakoutDistance(List<double[]> referenceCoefficients) {
        if (referenceCoefficients == null || referenceCoefficients.isEmpty()) {
            throw new IllegalArgumentException("Reference coefficient vectors are required");
        }
        for (double[] coeffs : referenceCoefficients) {
            if (coeffs == null || coeffs.length != WINDOW) {
                throw new IllegalArgumentException("Each coefficient vector must have length " + WINDOW);
            }
        }
        this.referenceCoefficients = List.copyOf(referenceCoefficients);
    }

    @Override
    public void accept(Candle bar) {
        double close = bar.close();
        if (windowSize < WINDOW) {
            window[windowSize++] = close;
            if (windowSize < WINDOW) {
                lastDistance = Double.NaN;
                lastPearson = Double.NaN;
                lastSpearman = Double.NaN;
                return;
            }
            windowIndex = 0;
        } else {
            window[windowIndex] = close;
            windowIndex = (windowIndex + 1) % WINDOW;
        }

        for (int i = 0; i < WINDOW; i++) {
            orderedWindow[i] = window[(windowIndex + i) % WINDOW];
        }
        double[] currentCoefficients = dwt.transform(RealVector.from(orderedWindow)).values();

        int bestIndex = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestPearson = Double.NaN;
        for (int i = 0; i < referenceCoefficients.size(); i++) {
            double corr = pearson.correlation(currentCoefficients, referenceCoefficients.get(i));
            if (Double.isNaN(corr) || Double.isInfinite(corr)) {
                continue;
            }
            if (corr > 1.0) {
                corr = 1.0;
            } else if (corr < -1.0) {
                corr = -1.0;
            }
            double distance = 1.0 - corr;
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
                bestPearson = corr;
            }
        }

        if (bestIndex >= 0) {
            lastDistance = bestDistance;
            double[] best = referenceCoefficients.get(bestIndex);
            lastPearson = bestPearson;
            lastSpearman = spearman.correlation(currentCoefficients, best);
        } else {
            lastDistance = Double.NaN;
            lastPearson = Double.NaN;
            lastSpearman = Double.NaN;
        }
    }

    @Override
    public double getLast() {
        return lastDistance;
    }

    public double getPearson() {
        return lastPearson;
    }

    public double getSpearman() {
        return lastSpearman;
    }

    @Override
    public boolean isReady() {
        return windowSize >= WINDOW;
    }

    private static final class ReferenceCoefficientsHolder {
        private static final List<double[]> INSTANCE = loadReferenceCoefficients();
    }

    private static List<double[]> loadReferenceCoefficients() {
        InputStream stream = HaarBreakoutDistance.class.getResourceAsStream(COEFFICIENTS_RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("Missing coefficients resource: " + COEFFICIENTS_RESOURCE);
        }

        Gson gson = new Gson();
        List<double[]> coefficients = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                HaarCoefficientsRecord record = gson.fromJson(line, HaarCoefficientsRecord.class);
                if (record != null && record.coefficients != null && record.coefficients.length == WINDOW) {
                    coefficients.add(record.coefficients);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read coefficients resource: " + COEFFICIENTS_RESOURCE, e);
        }
        if (coefficients.isEmpty()) {
            throw new IllegalStateException("No valid coefficients found in resource: " + COEFFICIENTS_RESOURCE);
        }
        return List.copyOf(coefficients);
    }

    private static final class HaarCoefficientsRecord {
        private double[] coefficients;
    }

}
