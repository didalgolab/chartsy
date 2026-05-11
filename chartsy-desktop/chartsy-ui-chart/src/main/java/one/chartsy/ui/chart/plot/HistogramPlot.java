/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.plot;

import java.awt.Color;

import one.chartsy.base.DoubleDataset;
import one.chartsy.core.Range;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.PlotRenderContext;
import one.chartsy.ui.chart.PlotRenderTarget;

public class HistogramPlot extends AbstractTimeSeriesPlot {
    /** The histogram positive color. */
    protected final Color histogramPositiveColor;
    /** The histogram negative color. */
    protected final Color histogramNegativeColor;

    public HistogramPlot(DoubleDataset dataset, Color barColor) {
        this(dataset, barColor, barColor);
    }

    public HistogramPlot(DoubleDataset dataset, Color histPositiveColor, Color histNegativeColor) {
        super(dataset, histPositiveColor);
        this.histogramPositiveColor = histPositiveColor;
        this.histogramNegativeColor = histNegativeColor;
    }

    @Override
    public void render(PlotRenderTarget target, PlotRenderContext context) {
        target.addHistogram(getTimeSeries(), histogramPositiveColor, histogramNegativeColor, context);
    }

    @Override
    public Range.Builder contributeRange(Range.Builder range, ChartContext cf) {
        Range.Builder builder = super.contributeRange(range, cf);
        return builder.add(0.0);
    }
}
