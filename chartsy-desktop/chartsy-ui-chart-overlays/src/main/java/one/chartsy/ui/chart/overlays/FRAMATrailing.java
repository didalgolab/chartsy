/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.overlays;

import java.awt.Color;
import java.awt.Stroke;

import one.chartsy.data.CandleSeries;
import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.financial.indicators.FramaTrendEquilibrium;
import one.chartsy.ui.chart.BasicStrokes;
import org.openide.util.lookup.ServiceProvider;

import one.chartsy.ui.chart.AbstractOverlay;
import one.chartsy.ui.chart.Overlay;
import one.chartsy.ui.chart.plot.LinePlot;

/**
 * The relative performance moving average.
 * 
 * @author Mariusz Bernacki
 */
@ServiceProvider(service = Overlay.class)
public class FRAMATrailing extends AbstractOverlay {
    
    public FRAMATrailing() {
        super("FRAMA, Trailing");
    }
    
    @Override
    public String getLabel() {
        return "FRAMA, Trailing";
    }
    
    @Override
    public void calculate() {
        CandleSeries quotes = getDataset();
        if (quotes != null) {
            var values = ValueIndicatorSupport.calculate(quotes.closes(), new FramaTrendEquilibrium(), FramaTrendEquilibrium::getAverage);
            addPlot("frama", new LinePlot(values, color, stroke));
        }
    }
    
    @Parameter(name = "Color")
    public Color color = new Color(0, 204, 204);
    @Parameter(name = "Stroke")
    public Stroke stroke = BasicStrokes.DEFAULT;
    
}
