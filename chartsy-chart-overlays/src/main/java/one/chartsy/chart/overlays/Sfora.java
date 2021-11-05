/* Copyright 2021 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.chart.overlays;

import java.awt.Color;
import java.awt.Stroke;
import java.util.List;

import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.data.packed.PackedCandleSeries;
import one.chartsy.finance.FinancialIndicators;
import one.chartsy.ui.chart.StrokeFactory;
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
public class Sfora extends AbstractOverlay {
    
    public Sfora() {
        super("Sfora");
    }
    
    @Override
    public String getLabel() {
        return "Sfora";
    }
    
    @Override
    public void calculate() {
        CandleSeries quotes = getDataset();
        if (quotes != null) {
            List<DoubleSeries> result = FinancialIndicators.sfora(PackedCandleSeries.from(quotes), framaPeriod, numberOfEnvelops, slowdownPeriod);
            
            for (int i = 0; i < numberOfEnvelops; i++)
                addPlot(String.valueOf(i), new LinePlot(result.get(i).values(), color, stroke));
        }
    }
    
    @Parameter(name = "Color")
    public Color color = new Color(175, 238, 238);
    @Parameter(name = "Stroke")
    public Stroke stroke = StrokeFactory.THIN_SOLID;
    @Parameter(name = "Number of Envelops")
    public int numberOfEnvelops = 8;
    @Parameter(name = "Slowdown Period")
    public int slowdownPeriod = 16;
    @Parameter(name = "FRAMA Period")
    public int framaPeriod = 45;
    
}
