/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.overlays;

import java.awt.Color;
import java.awt.Stroke;
import java.util.function.ToDoubleFunction;

import one.chartsy.data.CandleSeries;
import one.chartsy.financial.ValueIndicatorSupport;
import one.chartsy.financial.indicators.FramaTrendWhispers;
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
            var options = new FramaTrendWhispers.Options(numberOfEnvelops, framaPeriod, slowdownPeriod);

            @SuppressWarnings("unchecked")
            var indicatorPaths = (ToDoubleFunction<FramaTrendWhispers>[]) new ToDoubleFunction[numberOfEnvelops];
            for (int index = 0; index < numberOfEnvelops; index++) {
                final var pathNumber = index;
                indicatorPaths[index] = indicator -> indicator.getPath(pathNumber).getLast();
            }

            var paths = ValueIndicatorSupport.calculate(quotes, new FramaTrendWhispers(options), indicatorPaths);
            for (int index = 0; index < numberOfEnvelops; index++) {
                addPlot(String.valueOf(index + 1), new LinePlot(paths.get(index), color, stroke));
            }
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
