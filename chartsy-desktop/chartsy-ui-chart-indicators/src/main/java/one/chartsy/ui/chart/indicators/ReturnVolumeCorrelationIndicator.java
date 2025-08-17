/* Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.indicators;

import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.data.VisualRange;
import one.chartsy.ui.chart.plot.LinePlot;
import org.openide.util.lookup.ServiceProvider;

import java.awt.*;

/**
 * Visual representation for {@link one.chartsy.financial.indicators.ReturnVolumeCorrelation}.
 */
@ServiceProvider(service = Indicator.class)
public class ReturnVolumeCorrelationIndicator extends AbstractIndicator {

    @Parameter(name = "Periods")
    public int periods = one.chartsy.financial.indicators.ReturnVolumeCorrelation.DEFAULT_PERIODS;

    @Parameter(name = "Line Color")
    public Color color = new Color(0xDB4437);

    @Parameter(name = "Line Style")
    public Stroke style = BasicStrokes.THIN_SOLID;

    public ReturnVolumeCorrelationIndicator() {
        super("Return/Volume Correlation");
    }

    @Override
    public String getLabel() {
        return "Ret/Vol Corr (" + periods + ")";
    }

    @Override
    public VisualRange getRange(ChartContext cf) {
        return new VisualRange(Range.of(-1, 1), false);
    }

    @Override
    public void calculate() {
        CandleSeries candles = getDataset();
        if (candles != null) {
            DoubleSeries values = ValueIndicatorSupport.calculate(candles,
                    new one.chartsy.financial.indicators.ReturnVolumeCorrelation(periods));
            addPlot("Correlation", new LinePlot(values, color, style));
        }
    }

    @Override
    public double[] getStepValues(ChartContext cf) {
        return new double[] {-1, -0.5, 0, 0.5, 1};
    }
}
