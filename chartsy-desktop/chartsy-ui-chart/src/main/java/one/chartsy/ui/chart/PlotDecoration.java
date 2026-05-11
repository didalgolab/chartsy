package one.chartsy.ui.chart;

import java.awt.Graphics2D;

@FunctionalInterface
public interface PlotDecoration {

    void paint(Graphics2D graphics, PlotRenderContext context, PlotCoordinateSystem coordinateSystem);
}
