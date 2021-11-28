/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.plot;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.function.IntFunction;

import one.chartsy.core.Range;
import one.chartsy.data.DoubleDataset;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.data.VisibleValues;

public class ShapePlot extends AbstractPlot {
    /** The marker function providing marker objects to be plotted for data points on the chart. */
    private final IntFunction<Marker> markerProvider;
    
    private final DoubleDataset yCoordinates;
    
    public ShapePlot(IntFunction<Marker> markerProvider, DoubleDataset yCoords, Color color) {
        super(color);
        this.markerProvider = markerProvider;
        this.yCoordinates = yCoords;
    }
    
    private static final int DEFAULT_MARKER_SIZE = 15;
    
    @Override
    public void paint(Graphics2D g, ChartContext cf, Range range, Rectangle bounds) {
        boolean isLog = cf.getChartProperties().getAxisLogarithmicFlag();
        Stroke old = g.getStroke();
        g.setPaint(primaryColor);
        VisibleValues view = cf.getChartData().getVisible().getVisibleDataset(yCoordinates);
        
        for (int index = 0; index < view.getLength(); index++) {
            double value = view.getValueAt(index);
            if (!Double.isNaN(value)) {
                double x = cf.getChartData().getX(index, bounds);
                double y = cf.getChartData().getY(value, bounds, range, isLog);
                
                Marker marker = markerProvider.apply(index);
                marker.draw(g, (int)(0.5 + x), (int)(0.5 + y), DEFAULT_MARKER_SIZE, primaryColor);
                
            }
        }
        g.setStroke(old);
    }
}
