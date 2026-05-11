/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.plot;

import java.awt.Color;
import java.util.function.IntFunction;

import one.chartsy.base.DoubleDataset;
import one.chartsy.core.Range;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.PlotRenderContext;
import one.chartsy.ui.chart.PlotRenderTarget;
import one.chartsy.ui.chart.data.VisibleValues;

public class ShapePlot extends AbstractPlot {
    /** The marker function providing marker objects to be plotted for data points on the chart. */
    private final IntFunction<Marker> markerProvider;

    private final DoubleDataset yCoordinates;

    public ShapePlot(IntFunction<Marker> markerProvider, DoubleDataset yCoords, Color color) {
        super(color);
        this.markerProvider = markerProvider;
        this.yCoordinates = yCoords;
    }

    private static final int DEFAULT_MARKER_SIZE = 15;

    @Override
    public Range.Builder contributeRange(Range.Builder range, ChartContext cf) {
        VisibleValues view = cf.getChartData().getVisible().getVisibleDataset(yCoordinates);
        return view != null ? view.getRange(range) : ShapePlot.super.contributeRange(range, cf);
    }

    @Override
    public void render(PlotRenderTarget target, PlotRenderContext context) {
        target.addScatter(yCoordinates, markerProvider, primaryColor, DEFAULT_MARKER_SIZE, context);
    }
}
