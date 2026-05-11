/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.plot;

import one.chartsy.data.DoubleSeries;
import one.chartsy.ui.chart.PlotRenderContext;
import one.chartsy.ui.chart.PlotRenderTarget;

import java.awt.Color;
import java.awt.Stroke;

public class LinePlot extends AbstractTimeSeriesPlot {
    /** The stroke used by this plot. */
    protected final Stroke stroke;


    public LinePlot(DoubleSeries timeSeries, Color color, Stroke stroke) {
        super(timeSeries.values(), color);
        this.stroke = stroke;
    }

    @Override
    public void render(PlotRenderTarget target, PlotRenderContext context) {
        target.addLine(getTimeSeries(), primaryColor, stroke, context);
    }
}
