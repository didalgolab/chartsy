/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.overlays;

import java.awt.Color;
import java.awt.Stroke;
import java.util.List;

import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.data.packed.PackedCandleSeries;
import one.chartsy.finance.FinancialIndicators;
import one.chartsy.finance.FinancialIndicators.Sfora.Properties;
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
            List<DoubleSeries> result = FinancialIndicators.sfora(PackedCandleSeries.from(quotes), new Properties(framaPeriod, slowdownPeriod, numberOfEnvelops));
            
            for (int i = 0; i < numberOfEnvelops; i++)
                addPlot(String.valueOf(i), new LinePlot(result.get(i), color, stroke));
        }
    }
    
    @Parameter(name = "Color")
    public Color color = new Color(175, 238, 238);
    @Parameter(name = "Stroke")
    public Stroke stroke = BasicStrokes.THIN_SOLID;
    @Parameter(name = "Number of Envelops")
    public int numberOfEnvelops = 8;
    @Parameter(name = "Slowdown Period")
    public int slowdownPeriod = 16;
    @Parameter(name = "FRAMA Period")
    public int framaPeriod = 45;
    
}
