/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.core.Range;

import java.awt.Color;

public interface Plot {

    Color getPrimaryColor();

    void render(PlotRenderTarget target, PlotRenderContext context);

    default boolean supportsLegend() {
        return true;
    }

    default Range.Builder contributeRange(Range.Builder range, ChartContext cf) {
        return range == null ? new Range.Builder() : range;
    }
}
