/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.plot;

import java.awt.Color;

import one.chartsy.data.DoubleSeries;
import one.chartsy.ui.chart.PlotRenderContext;
import one.chartsy.ui.chart.PlotRenderTarget;

public class FillPlot extends AbstractTimeSeriesPlot {

    private final double f, t;
    private final boolean upper;


    public FillPlot(DoubleSeries values, double f, double t, boolean upper, Color color) {
        super(values.values(), color);
        this.f = f;
        this.t = t;
        this.upper = upper;
    }

    @Override
    public void render(PlotRenderTarget target, PlotRenderContext context) {
        target.addFill(getTimeSeries(), f, t, upper, primaryColor, context);
    }

    @Override
    public boolean supportsLegend() {
        return false;
    }
}
