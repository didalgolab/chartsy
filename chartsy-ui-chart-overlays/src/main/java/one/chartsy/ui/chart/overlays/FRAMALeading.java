/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.overlays;

import java.awt.Color;
import java.awt.Stroke;

import one.chartsy.data.CandleSeries;
import one.chartsy.data.DoubleSeries;
import one.chartsy.finance.FinancialIndicators;
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
public class FRAMALeading extends AbstractOverlay {

    public FRAMALeading() {
        super("FRAMA, Leading");
    }
    
    @Override
    public String getLabel() {
        return "FRAMA, Leading";
    }
    
    @Override
    public void calculate() {
        CandleSeries quotes = getDataset();
        if (quotes != null) {
            DoubleSeries smudge = FinancialIndicators.leadingFrama(quotes, leadingPeriods);
            addPlot("frama", new LinePlot(smudge, color, stroke));
        }
    }
    
    @Parameter(name = "Color")
    public Color color = new Color(102, 204, 0);
    @Parameter(name = "Stroke")
    public Stroke stroke = BasicStrokes.DEFAULT;
    @Parameter(name = "Leading Periods")
    public int leadingPeriods = 45;
}
