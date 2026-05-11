/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.plot;

import java.awt.Color;

import one.chartsy.base.DoubleDataset;
import one.chartsy.core.Range;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.PlotRenderContext;
import one.chartsy.ui.chart.PlotRenderTarget;
import one.chartsy.ui.chart.data.VisibleValues;

public class InsideFillPlot extends AbstractPlot {
    /** The upper boundary of the fill. */
    protected final DoubleDataset upper;
    /** The lower boundary of the fill. */
    protected final DoubleDataset lower;


    public InsideFillPlot(DoubleDataset upper, DoubleDataset lower, Color color) {
        super(color);
        this.upper = upper;
        this.lower = lower;
    }

    @Override
    public Range.Builder contributeRange(Range.Builder range, ChartContext cf) {
        Range.Builder builder = range == null ? new Range.Builder() : range;
        VisibleValues upperLine = cf.getChartData().getVisible().getVisibleDataset(upper);
        VisibleValues lowerLine = cf.getChartData().getVisible().getVisibleDataset(lower);
        if (upperLine != null)
            builder = upperLine.getRange(builder);
        if (lowerLine != null)
            builder = lowerLine.getRange(builder);
        return builder;
    }

    @Override
    public void render(PlotRenderTarget target, PlotRenderContext context) {
        target.addInsideFill(upper, lower, primaryColor, context);
    }

    @Override
    public boolean supportsLegend() {
        return false;
    }
}
