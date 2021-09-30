package one.chartsy.ui.chart.internal;

import one.chartsy.commons.Range;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.ChartData;
import one.chartsy.ui.chart.data.VisibleValues;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

public class Graphics2DHelper {

    public static Graphics2D prepareGraphics2D(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_DEFAULT);
        g2.setPaintMode();
        return g2;
    }

    public static void bar(Graphics2D g, ChartContext cf, Range range, Rectangle bounds, VisibleValues dataset, Color color) {
        g.setPaint(color);
        ChartData cd = cf.getChartData();
        boolean isLog = false;
        double zeroY = cd.getY(0.0, bounds, range, isLog);
        double maxY = bounds.getMaxY() + 1.0;

        Rectangle2D shape = new Rectangle2D.Double();
        double width = Math.max(1, cf.getChartProperties().getBarWidth());
        for (int i = 0; i < dataset.getLength(); i++) {
            double value = dataset.getValueAt(i);
            if (!Double.isNaN(value)) {
                double x = cd.getX(i, bounds);
                double y = cd.getY(value, bounds, range, isLog);
                shape.setFrameFromDiagonal(x - width/2, maxY, x + width/2, maxY - (zeroY - y));
                g.fill(shape);
            }
        }
    }

    public static void insideFill(Graphics2D g, ChartContext cf, Range range, Rectangle bounds, VisibleValues upper, VisibleValues lower, Color color) {
        ChartData cd = cf.getChartData();
        g.setPaint(color);
        Point2D.Double point1 = null;
        Point2D.Double point2 = null;

        for (int i = 0; i < upper.getLength(); i++) {
            double value1 = upper.getValueAt(i);
            double value2 = lower.getValueAt(i);
            if (value1 == value1 && value2 == value2) {

                double x = cd.getX(i, bounds);
                double y1 = cd.getY(value1, bounds, range, cf.getChartProperties().getAxisLogarithmicFlag());
                double y2 = cd.getY(value2, bounds, range, cf.getChartProperties().getAxisLogarithmicFlag());

                Point2D.Double p1 = new Point2D.Double(x, y1);
                Point2D.Double p2 = new Point2D.Double(x, y2);

                if (point1 != null && point2 != null) {
                    GeneralPath gp = new GeneralPath();
                    gp.moveTo((float) point1.x, (float) point1.y);
                    gp.lineTo((float) p1.x, (float) p1.y);
                    gp.lineTo((float) p2.x, (float) p2.y);
                    gp.lineTo((float) point2.x, (float) point2.y);
                    gp.closePath();
                    g.fill(gp);
                }

                point1 = p1;
                point2 = p2;
            }
        }
    }
}
