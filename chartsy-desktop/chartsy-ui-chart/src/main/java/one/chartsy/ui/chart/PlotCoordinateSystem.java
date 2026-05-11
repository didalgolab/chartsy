package one.chartsy.ui.chart;

import java.awt.Rectangle;
import java.awt.geom.Point2D;

public interface PlotCoordinateSystem {

    Rectangle plotBounds();

    Point2D.Double toDisplay(double xValue, double yValue);
}
