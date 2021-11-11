/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.chart.indicators;

import one.chartsy.core.collections.DoubleMinMaxList;
import one.chartsy.data.DoubleSeries;
import one.chartsy.finance.FinancialIndicators;
import one.chartsy.ui.chart.AbstractIndicator;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.StrokeFactory;
import one.chartsy.ui.chart.data.VisualRange;
import one.chartsy.ui.chart.plot.LinePlot;

import org.openide.util.lookup.ServiceProvider;

import java.awt.*;

/**
 * The relative performance squeeze indicator.
 * 
 * @author Mariusz Bernacki
 */
@ServiceProvider(service = Indicator.class)
public class SforaWidth extends AbstractIndicator {

    @Parameter(name = "RPMA %1 Line Color")
    public Color rpma1Color = Color.black;
    
    @Parameter(name = "RPMA %2 Line Color")
    public Color rpma2Color = Color.green.darker();
    
    @Parameter(name = "ATR Periods")
    public int atrPeriods = 60;
    
    @Parameter(name = "ATR Line Color")
    public Color atrColor = Color.red;
    
    @Parameter(name = "Line Style")
    public Stroke style = StrokeFactory.DEFAULT;
    
    @Parameter(name = "Number of Envelops")
    public int numberOfEnvelops = 8;
    @Parameter(name = "Slowdown Period")
    public int slowdownPeriod = 16;
    @Parameter(name = "FRAMA Period")
    public int framaPeriod = 45;
    
    
    public SforaWidth() {
        super("Sfora, Width");
    }
    
    @Override
    public VisualRange getRange(ChartContext cf) {
        return super.getRange(cf).asLogarithmic();
    }
    
    @Override
    public void paint(Graphics2D g, ChartContext view, Rectangle bounds) {
        boolean isLog = view.getChartProperties().getAxisLogarithmicFlag();
        view.getChartProperties().setAxisLogarithmicFlag(true);
        try {
            super.paint(g, view, bounds);
        } finally {
            view.getChartProperties().setAxisLogarithmicFlag(isLog);
        }
    }
    
    @Override
    public void calculate() {
        var quotes = getDataset();
        if (quotes != null) {
            DoubleSeries atr1 = quotes.atr(atrPeriods).sma(atrPeriods);
            DoubleSeries atr2 = atr1.mul(1.1);
            addPlot("ATR%60 L", new LinePlot(atr1, atrColor, style));
            addPlot("ATR%60 H", new LinePlot(atr2, atrColor, style));
            addPlot("ATR%60 /2", new LinePlot(atr1.mul(0.5), atrColor, StrokeFactory.DOTTED));
            addPlot("ATR%60 *2", new LinePlot(atr1.mul(2.0), atrColor, StrokeFactory.DOTTED));
            addPlot("ATR%60 2/2", new LinePlot(quotes.closes().wilders(atrPeriods).sma(atrPeriods).mul(0.5), Color.BLACK, StrokeFactory.DOTTED));

            DoubleMinMaxList bands = FinancialIndicators.Sfora.bands(quotes, new FinancialIndicators.Sfora.Properties(framaPeriod, slowdownPeriod, numberOfEnvelops));
            DoubleSeries width = bands.getMaximum().sub(bands.getMinimum());
            
            addPlot("RPMA %1", new LinePlot(width, rpma1Color, style));
        }
    }
}
