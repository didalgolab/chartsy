package one.chartsy.ui.chart;

import one.chartsy.base.DoubleDataset;
import one.chartsy.ui.chart.plot.Marker;

import java.awt.Color;
import java.awt.Paint;
import java.awt.Stroke;
import java.util.function.IntFunction;

public interface PlotRenderTarget {

    void addLine(DoubleDataset values, Color color, Stroke stroke, PlotRenderContext context);

    void addHistogram(DoubleDataset values, Color positiveColor, Color negativeColor, PlotRenderContext context);

    void addBar(DoubleDataset values, Color color, PlotRenderContext context);

    void addHorizontalLine(double value, Color color, Stroke stroke, PlotRenderContext context);

    void addFill(DoubleDataset values, double floor, double ceiling, boolean upper, Paint paint, PlotRenderContext context);

    void addInsideFill(DoubleDataset upper, DoubleDataset lower, Paint paint, PlotRenderContext context);

    void addScatter(DoubleDataset values, IntFunction<Marker> markerProvider, Color color, int markerSize, PlotRenderContext context);

    void addDecoration(PlotDecoration decoration, PlotRenderContext context);

    void addLegendEntry(String label, LegendMarkerSpec markerSpec);

    default void addLegendEntry(String label, Color color) {
        addLegendEntry(label, LegendMarkerSpec.line(color, new java.awt.BasicStroke(2f)));
    }
}
