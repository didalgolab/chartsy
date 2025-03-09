/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.overlays;

import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.financial.indicators.SentimentBands;
import one.chartsy.ui.chart.AbstractOverlay;
import one.chartsy.ui.chart.BasicStrokes;
import one.chartsy.ui.chart.Overlay;
import one.chartsy.ui.chart.plot.LinePlot;
import org.openide.util.lookup.ServiceProvider;

import java.awt.*;

@ServiceProvider(service = Overlay.class)
public class SentimentBandsOverlay extends AbstractOverlay {

    @Parameter(name = "Upper Band Color")
    public Color upperBandColor = new Color(0x3B82F6);

    @Parameter(name = "Lower Band Color")
    public Color lowerBandColor = new Color(0xEF4444);

    @Parameter(name = "Band Stroke")
    public Stroke bandStroke = BasicStrokes.THICK_SOLID;

    @Parameter(name = "Fill Bands")
    public boolean fillBands = true;

    @Parameter(name = "Fill Color")
    public Color fillColor = new Color(0x93C5FD, true);

    public SentimentBandsOverlay() {
        super("Sentiment Bands");
    }

    @Override
    public String getLabel() {
        return "Sentiment Bands";
    }

    @Override
    public void calculate() {
        var bars = getDataset();
        if (bars == null)
            return;

        var upperBand = ValueIndicatorSupport.calculate(bars, new SentimentBands(), SentimentBands::getUpperBand);
        var lowerBand = ValueIndicatorSupport.calculate(bars, new SentimentBands(), SentimentBands::getLowerBand);

        addPlot("Upper Band", new LinePlot(upperBand, upperBandColor, bandStroke));
        addPlot("Lower Band", new LinePlot(lowerBand, lowerBandColor, bandStroke));

        // Optional fill between bands
        //if (fillBands) {
        //    addPlot("Fill Band", new FillPlot(upperBand, lowerBand, fillColor));
        //}
    }
}
