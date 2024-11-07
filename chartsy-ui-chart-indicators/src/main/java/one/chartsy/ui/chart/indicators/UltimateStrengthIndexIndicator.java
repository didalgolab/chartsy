/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.indicators;

import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.financial.indicators.UltimateStrengthIndex;
import one.chartsy.ui.chart.AbstractIndicator;
import one.chartsy.ui.chart.BasicStrokes;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.data.VisualRange;
import one.chartsy.ui.chart.plot.FillPlot;
import one.chartsy.ui.chart.plot.HorizontalLinePlot;
import one.chartsy.ui.chart.plot.LinePlot;
import org.openide.util.lookup.ServiceProvider;

import java.awt.Color;
import java.awt.Stroke;

/**
 * The Ultimate Strength Index (USI) indicator visualization.
 * 
 * @author Mariusz Bernacki
 */
@ServiceProvider(service=Indicator.class)
public class UltimateStrengthIndexIndicator extends AbstractIndicator {
    
    @Parameter(name = "Length")
    public int length = 28;
    
    @Parameter(name = "Line Color")
    public Color lineColor = new Color(0x0066FF);
    
    @Parameter(name = "Line Style")
    public Stroke lineStyle = BasicStrokes.THIN_SOLID;
    
    @Parameter(name = "Zero Line Color")
    public Color zeroLineColor = Color.BLACK;
    
    @Parameter(name = "Zero Line Style")
    public Stroke zeroLineStyle = BasicStrokes.ULTRATHIN_DOTTED;
    
    @Parameter(name = "Fill Visibility")
    public boolean fillVisibility = true;
    
    @Parameter(name = "Bullish Zone Color")
    public Color bullishColor = new Color(0xCCFFCC, true); // Light green with transparency
    
    @Parameter(name = "Bearish Zone Color")
    public Color bearishColor = new Color(0xFFCCCC, true); // Light red with transparency
    
    public UltimateStrengthIndexIndicator() {
        super("Ultimate Strength Index");
    }
    
    @Override
    public String getLabel() {
        return "USI (" + length + ")";
    }
    
    @Override
    public VisualRange getRange(ChartContext cf) {
        return new VisualRange(Range.of(-1.0, 1.0));
    }
    
    @Override
    public void calculate() {
        CandleSeries quotes = getDataset();
        if (quotes != null) {
            DoubleSeries result = ValueIndicatorSupport.calculate(quotes.closes(), new UltimateStrengthIndex(length));

            if (fillVisibility) {
                addPlot("fillBullish", new FillPlot(result, 0, 1.0, true, bullishColor));
                addPlot("fillBearish", new FillPlot(result, -1.0, 0, true, bearishColor));
            }
            
            addPlot("USI", new LinePlot(result, lineColor, lineStyle));
            addPlot("Zero", new HorizontalLinePlot(0.0, zeroLineColor, zeroLineStyle));
        }
    }
    
    @Override
    public double[] getStepValues(ChartContext cf) {
        return new double[] { -1.0, -0.75, -0.5, -0.25, 0.0, 0.25, 0.5, 0.75, 1.0 };
    }
}