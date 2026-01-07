/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.indicators;

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
import org.openide.util.lookup.ServiceProvider;

import java.awt.Color;
import java.awt.Stroke;
import java.util.List;

@ServiceProvider(service = Indicator.class)
public class HaarBreakoutDistanceIndicator extends AbstractIndicator {
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
}
