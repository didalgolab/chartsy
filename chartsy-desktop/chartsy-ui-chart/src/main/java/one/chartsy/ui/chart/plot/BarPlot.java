/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.plot;

import java.awt.Color;

import one.chartsy.base.DoubleDataset;
import one.chartsy.core.Range;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.PlotRenderContext;
import one.chartsy.ui.chart.PlotRenderTarget;

public class BarPlot extends AbstractTimeSeriesPlot {

    public BarPlot(DoubleDataset timeSeries, Color color) {
        super(timeSeries, color);
    }

    @Override
    public void render(PlotRenderTarget target, PlotRenderContext context) {
        target.addBar(getTimeSeries(), primaryColor, context);
    }

    @Override
    public Range.Builder contributeRange(Range.Builder range, ChartContext cf) {
        Range.Builder builder = super.contributeRange(range, cf);
        return builder.add(0.0);
    }
}
