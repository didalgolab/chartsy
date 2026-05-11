/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.plot;

import java.awt.Color;

import one.chartsy.core.Range;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.Plot;

public abstract class AbstractPlot implements Plot {
    /** The primary color associated with this plot. */
    protected Color primaryColor;
    
    
    protected AbstractPlot(Color primaryColor) {
        this.primaryColor = primaryColor;
    }
    
    @Override
    public Color getPrimaryColor() {
        return primaryColor;
    }

    @Override
    public Range.Builder contributeRange(Range.Builder range, ChartContext cf) {
        return Plot.super.contributeRange(range, cf);
    }
}
