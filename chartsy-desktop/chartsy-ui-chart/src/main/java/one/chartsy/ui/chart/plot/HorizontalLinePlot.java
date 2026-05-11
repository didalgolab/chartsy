/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.plot;

import java.awt.Color;
import java.awt.Stroke;

import one.chartsy.core.Range;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.PlotRenderContext;
import one.chartsy.ui.chart.PlotRenderTarget;

public class HorizontalLinePlot extends AbstractPlot {
    /** The stroke used by this plot. */
    protected final Stroke stroke;
    /** The horizontal line value. */
    protected final double value;


    public HorizontalLinePlot(double value, Color color, Stroke stroke) {
        super(color);
        this.stroke = stroke;
        this.value = value;
    }

    @Override
    public Range.Builder contributeRange(Range.Builder range, ChartContext cf) {
        return (range == null ? new Range.Builder() : range).add(value);
    }

    @Override
    public void render(PlotRenderTarget target, PlotRenderContext context) {
        target.addHorizontalLine(value, primaryColor, stroke, context);
    }
}
