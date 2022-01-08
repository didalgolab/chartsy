/* Copyright 2022 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.indicators;

import java.awt.Color;
import java.awt.Stroke;

import one.chartsy.CandleField;
import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.finance.FinancialIndicators;
import one.chartsy.ui.chart.BasicStrokes;
import one.chartsy.ui.chart.data.VisualRange;
import org.openide.util.lookup.ServiceProvider;

import one.chartsy.ui.chart.AbstractIndicator;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.plot.FillPlot;
import one.chartsy.ui.chart.plot.HorizontalLinePlot;
import one.chartsy.ui.chart.plot.LinePlot;

/**
 * The fractal dimension indicator.
 * 
 * @author Mariusz Bernacki
 */
@ServiceProvider(service=Indicator.class)
public class FractalDimension extends AbstractIndicator {
    
    @Parameter(name = "Price Field")
    public CandleField priceBase = CandleField.CLOSE;
    
    @Parameter(name = "Periods")
    public int periods = 30;
    
    @Parameter(name = "Line Color")
    public Color color = new Color(0x388E8E);
    
    @Parameter(name = "Line Style")
    public Stroke style = BasicStrokes.THIN_SOLID;
    
    @Parameter(name = "Delimiter Line Color")
    public Color delimiterLineColor = Color.BLACK;
    
    @Parameter(name = "Delimiter Line Style")
    public Stroke delimiterLineStyle = BasicStrokes.ULTRATHIN_DOTTED;
    
    @Parameter(name = "Inside Visibility")
    public boolean insideVisibility = true;
    
    @Parameter(name = "Inside Neutral Color")
    public Color insideNeutralColor = new Color(0xEBF3F3);
    
    @Parameter(name = "Inside High Color")
    public Color insideHighColor = new Color(0x388E8E);
    
    
    /**
     * Constructs a Fractal Dimension indicator using the default settings.
     */
    public FractalDimension() {
        super("Fractal Dimension");
    }
    
    @Override
    public String getLabel() {
        return "Fractal Dimension (" + priceBase + ", " + periods + ")";
    }
    
    @Override
    public VisualRange getRange(ChartContext cf) {
        VisualRange range = super.getRange(cf);
        range = new VisualRange(Range.of(Math.min(1.35d, range.getMin()), Math.max(1.65d, range.getMax())), range.isLogarithmic());
        return range;
    }
    
    @Override
    public void calculate() {
        CandleSeries quotes = getDataset();
        if (quotes != null) {
            DoubleSeries values = quotes.mapToDouble(priceBase);
            DoubleSeries result = FinancialIndicators.fdi(values, periods);
            
            if (insideVisibility) {
                addPlot("fill", new FillPlot(result, 1.4, 1.6, true, insideNeutralColor));
                addPlot("fill2", new FillPlot(result, 1.6, 1.6, true, insideHighColor));
            }
            addPlot("FDI", new LinePlot(result, color, style));
            addPlot("1.4", new HorizontalLinePlot(1.4, delimiterLineColor, delimiterLineStyle));
            addPlot("1.6", new HorizontalLinePlot(1.6, delimiterLineColor, delimiterLineStyle));
        }
    }
    
    @Override
    public double[] getStepValues(ChartContext cf) {
        return new double[] { 0.2, 0.4, 0.6, 0.8, 1, 1.2, 1.4, 1.6, 1.8, 2 };
    }
}
