/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.indicators;

import com.google.gson.Gson;
import one.chartsy.CandleField;
import one.chartsy.SystemFiles;
import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.financial.indicators.HaarBreakoutDistance;
import one.chartsy.ui.chart.AbstractIndicator;
import one.chartsy.ui.chart.BasicStrokes;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.data.VisibleValues;
import one.chartsy.ui.chart.data.VisualRange;
import one.chartsy.ui.chart.plot.HorizontalLinePlot;
import one.chartsy.ui.chart.plot.LinePlot;
import one.chartsy.wavelets.HaarWavelet;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.openide.util.lookup.ServiceProvider;

import java.awt.Color;
import java.awt.Stroke;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@ServiceProvider(service = Indicator.class)
public class HaarBreakoutDistanceIndicator extends AbstractIndicator {
    private static final int WINDOW = 256;

    @Parameter(name = "Coefficients File")
    public String coefficientsFile = SystemFiles.PRIVATE_DIR.resolve("Wavelets")
            .resolve("HaarCoefficients.jsonl")
            .toString();

    @Parameter(name = "Distance Line Color")
    public Color distanceColor = new Color(0x1565C0);

    @Parameter(name = "Distance Line Style")
    public Stroke distanceStyle = BasicStrokes.THIN_SOLID;

    @Parameter(name = "Show Correlations")
    public boolean showCorrelations = false;

    @Parameter(name = "Pearson Line Color")
    public Color pearsonColor = new Color(0x2E7D32);

    @Parameter(name = "Pearson Line Style")
    public Stroke pearsonStyle = BasicStrokes.ULTRATHIN_DOTTED;

    @Parameter(name = "Spearman Line Color")
    public Color spearmanColor = new Color(0xEF6C00);

    @Parameter(name = "Spearman Line Style")
    public Stroke spearmanStyle = BasicStrokes.ULTRATHIN_DOTTED;

    @Parameter(name = "Zero Line Color")
    public Color zeroLineColor = new Color(0x9E9E9E);

    @Parameter(name = "Zero Line Style")
    public Stroke zeroLineStyle = BasicStrokes.ULTRATHIN_DOTTED;

    public HaarBreakoutDistanceIndicator() {
        super("Haar Breakout Distance");
    }

    @Override
    public String getLabel() {
        return "Haar Breakout Distance";
    }

    @Override
    public void calculate() {
        CandleSeries candles = getDataset();
        if (candles == null) {
            return;
        }

        DoubleSeries distance;
        DoubleSeries pearsonSeries = null;
        DoubleSeries spearmanSeries = null;
        if (showCorrelations) {
            HaarBreakoutDistance indicator = new HaarBreakoutDistance();
            List<? extends DoubleSeries> outputs = ValueIndicatorSupport.calculate(
                    candles,
                    indicator,
                    HaarBreakoutDistance::getLast,
                    HaarBreakoutDistance::getPearson,
                    HaarBreakoutDistance::getSpearman
            );
            distance = outputs.get(0);
            pearsonSeries = outputs.get(1);
            spearmanSeries = outputs.get(2);
        } else {
            HaarBreakoutDistance indicator = new HaarBreakoutDistance();
            distance = ValueIndicatorSupport.calculate(candles, indicator, HaarBreakoutDistance::getLast);
        }
        addPlot("Distance", new LinePlot(distance, distanceColor, distanceStyle));
        addPlot("Zero", new HorizontalLinePlot(0.0, zeroLineColor, zeroLineStyle));

        if (showCorrelations) {
            DoubleSeries pearsonSeries = DoubleSeries.of(pearsonValues, candles.getTimeline());
            DoubleSeries spearmanSeries = DoubleSeries.of(spearmanValues, candles.getTimeline());
            addPlot("Pearson", new LinePlot(pearsonSeries, pearsonColor, pearsonStyle));
            addPlot("Spearman", new LinePlot(spearmanSeries, spearmanColor, spearmanStyle));
        }
    }

    @Override
    public VisualRange getRange(ChartContext cf) {
        if (plots.isEmpty()) {
            return new VisualRange(Range.empty());
        }

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (String key : plots.keySet()) {
            VisibleValues visible = visibleDataset(cf, key);
            if (visible == null) {
                continue;
            }
            double vMin = visible.getMinimum();
            double vMax = visible.getMaximum();
            if (vMin == Double.MAX_VALUE || vMax == Double.NEGATIVE_INFINITY) {
                continue;
            }
            min = Math.min(min, vMin);
            max = Math.max(max, vMax);
        }

        if (min == Double.POSITIVE_INFINITY || max == Double.NEGATIVE_INFINITY) {
            return new VisualRange(Range.of(0.0, 1.0));
        }

        min = Math.min(min, 0.0);
        max = Math.max(max, 0.0);

        double margin = (max - min) * 0.01;
        if (margin == 0.0) {
            margin = 1e-9;
        }
        return new VisualRange(Range.of(min - margin, max + margin));
    }

    @Override
    public double[] getStepValues(ChartContext cf) {
        return super.getStepValues(cf);
    }

    private List<double[]> loadReferenceCoefficients() {
        if (coefficientsFile == null || coefficientsFile.isBlank()) {
            return List.of();
        }

        Path path = resolveCoefficientsPath();
        if (path == null) {
            return List.of();
        }

        Gson gson = new Gson();
        List<double[]> coefficients = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                HaarCoefficientsRecord record = gson.fromJson(line, HaarCoefficientsRecord.class);
                if (record != null && record.coefficients != null
                        && record.coefficients.length == WINDOW) {
                    coefficients.add(record.coefficients);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read Haar coefficients: " + e.getMessage());
            return List.of();
        }
        return coefficients;
    }

    private Path resolveCoefficientsPath() {
        if (coefficientsFile != null && !coefficientsFile.isBlank()) {
            Path configured = Path.of(coefficientsFile);
            if (Files.exists(configured)) {
                return configured;
            }
        }

        Path found = findUpwards(Path.of("").toAbsolutePath(), Path.of("private", "Wavelets", "HaarCoefficients.jsonl"));
        if (found != null) {
            return found;
        }
        return findUpwards(Path.of("").toAbsolutePath(), Path.of("chartsy-pro", "private", "Wavelets", "HaarCoefficients.jsonl"));
    }

    private Path findUpwards(Path start, Path relativeTarget) {
        Path current = start;
        for (int i = 0; i < 6 && current != null; i++) {
            Path candidate = current.resolve(relativeTarget).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private static final class HaarCoefficientsRecord {
        private double[] coefficients;
    }

    private static Match findBestMatch(double[] current, List<double[]> referenceCoefficients,
                                       PearsonsCorrelation pearson, SpearmansCorrelation spearman) {
        int bestIndex = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        double bestPearson = Double.NaN;

        for (int i = 0; i < referenceCoefficients.size(); i++) {
            double[] reference = referenceCoefficients.get(i);
            double corr = pearson.correlation(current, reference);
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

        if (bestIndex < 0) {
            return new Match(Double.NaN, Double.NaN, Double.NaN);
        }

        double[] best = referenceCoefficients.get(bestIndex);
        double spearmanCorr = spearman.correlation(current, best);
        return new Match(bestDistance, bestPearson, spearmanCorr);
    }

    private record Match(double distance, double pearson, double spearman) {}
}
