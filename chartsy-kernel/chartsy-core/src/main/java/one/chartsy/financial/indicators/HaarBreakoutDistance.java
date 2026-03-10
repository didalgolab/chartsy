/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.financial.indicators;

import com.google.gson.Gson;
import one.chartsy.Candle;
import one.chartsy.data.RealVector;
import one.chartsy.financial.AbstractCandleIndicator;
import one.chartsy.study.ChartStudy;
import one.chartsy.study.HorizontalLinePlotSpec;
import one.chartsy.study.LinePlotSpec;
import one.chartsy.study.StudyFactory;
import one.chartsy.study.StudyInputKind;
import one.chartsy.study.StudyKind;
import one.chartsy.study.StudyOutput;
import one.chartsy.study.StudyParameter;
import one.chartsy.study.StudyParameterScope;
import one.chartsy.study.StudyParameterType;
import one.chartsy.study.StudyPlacement;
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
@ChartStudy(
        name = "Haar Breakout Distance",
        label = "Haar Breakout Distance",
        category = "Market Structure",
        kind = StudyKind.INDICATOR,
        placement = StudyPlacement.OWN_PANEL
)
@StudyParameter(id = "distanceColor", name = "Distance Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#1565C0", order = 100)
@StudyParameter(id = "distanceStyle", name = "Distance Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "THIN_SOLID", order = 110)
@StudyParameter(id = "showCorrelations", name = "Show Correlations", scope = StudyParameterScope.VISUAL, type = StudyParameterType.BOOLEAN, defaultValue = "false", order = 120)
@StudyParameter(id = "pearsonColor", name = "Pearson Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#2E7D32", order = 130)
@StudyParameter(id = "pearsonStyle", name = "Pearson Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "ULTRATHIN_DOTTED", order = 140)
@StudyParameter(id = "spearmanColor", name = "Spearman Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#EF6C00", order = 150)
@StudyParameter(id = "spearmanStyle", name = "Spearman Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "ULTRATHIN_DOTTED", order = 160)
@StudyParameter(id = "zeroLineColor", name = "Zero Line Color", scope = StudyParameterScope.VISUAL, type = StudyParameterType.COLOR, defaultValue = "#9E9E9E", order = 170)
@StudyParameter(id = "zeroLineStyle", name = "Zero Line Style", scope = StudyParameterScope.VISUAL, type = StudyParameterType.STROKE, defaultValue = "ULTRATHIN_DOTTED", order = 180)
@LinePlotSpec(id = "distancePlot", label = "Distance", output = "distance", colorParameter = "distanceColor", strokeParameter = "distanceStyle", order = 10)
@HorizontalLinePlotSpec(id = "zeroLine", label = "Zero", value = 0.0, colorParameter = "zeroLineColor", strokeParameter = "zeroLineStyle", order = 20)
@LinePlotSpec(id = "pearsonPlot", label = "Pearson", output = "pearson", colorParameter = "pearsonColor", strokeParameter = "pearsonStyle", visibleParameter = "showCorrelations", order = 30)
@LinePlotSpec(id = "spearmanPlot", label = "Spearman", output = "spearman", colorParameter = "spearmanColor", strokeParameter = "spearmanStyle", visibleParameter = "showCorrelations", order = 40)
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

    @StudyFactory(input = StudyInputKind.CANDLES)
    public static HaarBreakoutDistance study() {
        return new HaarBreakoutDistance();
    }

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
    @StudyOutput(id = "distance", name = "Distance", order = 10)
    public double getLast() {
        return lastDistance;
    }

    @StudyOutput(id = "pearson", name = "Pearson", order = 20)
    public double getPearson() {
        return lastPearson;
    }

    @StudyOutput(id = "spearman", name = "Spearman", order = 30)
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
