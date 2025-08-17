package one.chartsy.ui.chart.indicators;

import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.financial.indicators.ContinuationIndex;
import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.data.VisualRange;
import one.chartsy.ui.chart.plot.HorizontalLinePlot;
import one.chartsy.ui.chart.plot.LinePlot;
import org.openide.util.lookup.ServiceProvider;

import java.awt.Color;
import java.awt.Stroke;

/**
 * Visual representation of the {@link ContinuationIndex} indicator.
 */
@ServiceProvider(service = Indicator.class)
public class ContinuationIndexIndicator extends AbstractIndicator {

    @Parameter(name = "Gamma")
    public double gamma = 0.8;

    @Parameter(name = "Order")
    public int order = 8;

    @Parameter(name = "Length")
    public int length = 40;

    @Parameter(name = "Line Color")
    public Color lineColor = new Color(0x00695C);

    @Parameter(name = "Line Style")
    public Stroke lineStyle = BasicStrokes.THIN_SOLID;

    @Parameter(name = "Zero Line Color")
    public Color zeroLineColor = Color.GRAY;

    @Parameter(name = "Zero Line Style")
    public Stroke zeroLineStyle = BasicStrokes.ULTRATHIN_DOTTED;

    public ContinuationIndexIndicator() {
        super("Continuation Index");
    }

    @Override
    public String getLabel() {
        return "CI(" + gamma + ", " + order + ", " + length + ")";
    }

    @Override
    public VisualRange getRange(ChartContext cf) {
        return new VisualRange(Range.of(-1.0, 1.0), false);
    }

    @Override
    public void calculate() {
        CandleSeries candles = getDataset();
        if (candles != null) {
            DoubleSeries closes = candles.closes();
            DoubleSeries result = ValueIndicatorSupport.calculate(closes, new ContinuationIndex(gamma, order, length));
            addPlot("CI", new LinePlot(result, lineColor, lineStyle));
            addPlot("Zero", new HorizontalLinePlot(0.0, zeroLineColor, zeroLineStyle));
        }
    }

    @Override
    public double[] getStepValues(ChartContext cf) {
        return new double[] {-1.0, -0.5, 0.0, 0.5, 1.0};
    }
}

