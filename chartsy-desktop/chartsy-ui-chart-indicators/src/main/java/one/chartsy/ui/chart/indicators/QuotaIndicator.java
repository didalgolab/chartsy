/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.indicators;

import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.financial.indicators.Quota;
import one.chartsy.ui.chart.AbstractIndicator;
import one.chartsy.ui.chart.BasicStrokes;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.plot.LinePlot;
import org.openide.util.lookup.ServiceProvider;

import java.awt.*;

@ServiceProvider(service = Indicator.class)
public class QuotaIndicator extends AbstractIndicator {

    @Parameter(name = "Periods")
    public int periods = Quota.DEFAULT_PERIODS;

    @Parameter(name = "Quota Fraction")
    public double quotaFraction = Quota.DEFAULT_QUOTA_FRACTION;

    @Parameter(name = "Line Color")
    public Color color = Color.BLACK;

    @Parameter(name = "Line Style")
    public Stroke stroke = BasicStrokes.THICK_SOLID;

    public QuotaIndicator() {
        super("Quota");
    }

    @Override
    public String getLabel() {
        return "Quota (" + periods + ", " + quotaFraction + ")";
    }

    @Override
    public void calculate() {
        var series = getDataset();
        if (series != null) {
            var quota = ValueIndicatorSupport.calculate(series, new Quota(periods, quotaFraction), Quota::getLast);
            addPlot("Quota", new LinePlot(quota, color, stroke));
        }
    }

    @Override
    public double[] getStepValues(ChartContext cf) {
        return new double[0];
    }
}