package one.chartsy.ui.chart.indicators;

import one.chartsy.CandleField;
import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.financial.indicators.ChandeMomentumOscillator;
import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.data.VisualRange;
import one.chartsy.ui.chart.plot.LinePlot;
import org.openide.util.lookup.ServiceProvider;

import java.awt.Color;
import java.awt.Stroke;

@ServiceProvider(service = Indicator.class)
public class ChandeMomentumOscillatorUI extends AbstractIndicator {

    @Parameter(name = "Price Field")
    public CandleField priceField = CandleField.CLOSE;

    @Parameter(name = "Periods")
    public int periods = 14;

    @Parameter(name = "Line Color")
    public Color color = new Color(0x2962FF);

    @Parameter(name = "Line Style")
    public Stroke style = BasicStrokes.THIN_SOLID;

    public ChandeMomentumOscillatorUI() {
        super("Chande Momentum Oscillator");
    }

    @Override
    public String getLabel() {
        return "CMO(" + priceField + ", " + periods + ")";
    }

    @Override
    public VisualRange getRange(ChartContext cf) {
        return new VisualRange(Range.of(-100, 100), false);
    }

    @Override
    public void calculate() {
        CandleSeries candles = getDataset();
        if (candles != null) {
            DoubleSeries priceSeries = candles.mapToDouble(priceField);
            DoubleSeries cmoValues = ValueIndicatorSupport.calculate(priceSeries, new ChandeMomentumOscillator(periods));
            addPlot("CMO", new LinePlot(cmoValues, color, style));
        }
    }

    @Override
    public double[] getStepValues(ChartContext cf) {
        return new double[] {-100, -50, 0, 50, 100};
    }
}
