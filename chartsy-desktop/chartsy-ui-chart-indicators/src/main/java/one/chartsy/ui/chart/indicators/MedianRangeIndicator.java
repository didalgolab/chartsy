/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.indicators;

import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.financial.indicators.MedianRange;
import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.plot.LinePlot;
import org.openide.util.lookup.ServiceProvider;

import java.awt.*;

@ServiceProvider(service = Indicator.class)
public class MedianRangeIndicator extends AbstractIndicator {

    @Parameter(name = "Periods")
    public int periods = 14;

    @Parameter(name = "Line Color")
    public Color lineColor = new Color(0xD33682);

    @Parameter(name = "Line Style")
    public Stroke lineStyle = BasicStrokes.THIN_SOLID;

    public MedianRangeIndicator() {
        super("Median Range");
    }

    @Override
    public String getLabel() {
        return "MedianRange(" + periods + ")";
    }

    @Override
    public void calculate() {
        CandleSeries series = getDataset();
        if (series != null) {
            DoubleSeries result = ValueIndicatorSupport.calculate(series, new MedianRange(periods));
            addPlot("MedianRange", new LinePlot(result, lineColor, lineStyle));
        }
    }
}
