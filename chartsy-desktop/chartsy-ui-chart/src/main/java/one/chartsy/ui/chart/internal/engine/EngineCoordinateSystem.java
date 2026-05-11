package one.chartsy.ui.chart.internal.engine;

import one.chartsy.charting.ChartProjector;
import one.chartsy.charting.DataWindow;
import one.chartsy.charting.DoublePoints;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Point2D;

final class EngineCoordinateSystem {
    private final one.chartsy.charting.Chart chart;
    private final Rectangle plotRect;
    private final one.chartsy.charting.CoordinateSystem coordinateSystem;
    private final ChartProjector projector;

    EngineCoordinateSystem(one.chartsy.charting.Chart chart) {
        this.chart = chart;
        var chartArea = chart.getChartArea();
        if (chartArea == null) {
            this.plotRect = new Rectangle();
            this.coordinateSystem = null;
            this.projector = null;
            return;
        }
        this.plotRect = chartArea.getPlotRect();
        this.coordinateSystem = chart.getCoordinateSystem(0);
        this.projector = (plotRect == null || plotRect.isEmpty())
                ? null
                : chart.getLocalProjector2D(plotRect, coordinateSystem);
    }

    Point2D.Double toDisplay(double x, double y) {
        if (projector == null || coordinateSystem == null || plotRect == null || plotRect.isEmpty())
            return new Point2D.Double();
        DoublePoints points = new DoublePoints(1);
        try {
            points.add(x, y);
            projector.toDisplay(points, plotRect, coordinateSystem);
            return new Point2D.Double(points.getX(0), points.getY(0));
        } finally {
            points.dispose();
        }
    }

    Shape toShape(DataWindow window) {
        if (projector == null || coordinateSystem == null || plotRect == null || plotRect.isEmpty())
            return new Rectangle2D.Double();
        return projector.getShape(window, plotRect, coordinateSystem);
    }

    Rectangle plotRect() {
        return plotRect;
    }

    one.chartsy.charting.Chart chart() {
        return chart;
    }
}
