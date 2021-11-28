/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.plot;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

import one.chartsy.core.Range;
import one.chartsy.data.DoubleSeries;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.ChartData;
import one.chartsy.ui.chart.data.VisibleValues;
import one.chartsy.ui.chart.plot.LinePlot.Simplify.SimplifiedGeneralPath;

public class LinePlot extends AbstractTimeSeriesPlot {
    /** The stroke used by this plot. */
    protected final Stroke stroke;
    
    
    public LinePlot(DoubleSeries timeSeries, Color color, Stroke stroke) {
        super(timeSeries.values(), color);
        this.stroke = stroke;
    }
    
    @Override
    public void paint(Graphics2D g, ChartContext cf, Range range, Rectangle bounds) {
        Object oldStrokeControl = g.getRenderingHint(RenderingHints.KEY_STROKE_CONTROL);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        VisibleValues values = getVisibleData(cf);
        if (values != null) {
            int candleWidth = (int) Math.round(cf.getChartProperties().getBarWidth());
            //    		if (candleWidth <= 1)
            //    			drawSubpixelOptimizedLine(g, cf, range, bounds, values);
            //    		else
            drawLine(g, cf, range, bounds, values);
        }
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, oldStrokeControl);
    }
    
    protected void drawLine(Graphics2D g, ChartContext cf, Range range, Rectangle bounds, VisibleValues values) {
        boolean isLog = cf.getChartProperties().getAxisLogarithmicFlag();
        Stroke old = g.getStroke();
        g.setPaint(primaryColor);
        if (stroke != null)
            g.setStroke(stroke);
        
        SimplifiedGeneralPath path = null;
        for (int i = 0; i < values.getLength(); i++) {
            double value = values.getValueAt(i);
            if (value == value) {
                double x = cf.getChartData().getX(i, bounds);
                double y = cf.getChartData().getY(value, bounds, range, isLog);
                
                if (path != null) {
                    path.lineTo(x, y);
                } else {
                    path = new SimplifiedGeneralPath();
                    path.moveTo(x, y);
                }
            }
        }
        if (path != null)
            g.draw(path.getPath());
        g.setStroke(old);
    }
    
    protected void drawSubpixelOptimizedLine(Graphics2D g, ChartContext cf, Range range, Rectangle bounds, VisibleValues values) {
        boolean isLog = cf.getChartProperties().getAxisLogarithmicFlag();
        ChartData cd = cf.getChartData();
        Stroke old = g.getStroke();
        g.setPaint(primaryColor);
        if (stroke != null)
            g.setStroke(stroke);
        
        int xPrev = -1;
        double qMax = Double.NEGATIVE_INFINITY, qMin = Double.MAX_VALUE;
        GeneralPath path = new GeneralPath(GeneralPath.WIND_NON_ZERO, bounds.width);
        for (int i = 0, j = values.getLength(); i < j; i++) {
            double value = values.getValueAt(i);
            if (value == value) {
                int x = (int)(0.5 + cd.getX(i, bounds));
                if (x != xPrev) {
                    // project y-coordinates and draw a vertical tick
                    if (xPrev >= 0) {
                        double yMax = cd.getY(qMax, bounds, range, isLog);
                        double yMin = cd.getY(qMin, bounds, range, isLog);
                        path.lineTo(xPrev, yMax);
                        path.lineTo(xPrev, yMin);
                    } else {
                        double y = cd.getY(value, bounds, range, isLog);
                        path.moveTo(x, y);
                    }
                    
                    qMax = qMin = value;
                    xPrev = x;
                } else {
                    // coerce current bar data with the previous data
                    if (value > qMax)
                        qMax = value;
                    if (value < qMin)
                        qMin = value;
                }
            } else {
                if (xPrev >= 0) {
                    double yMax = cd.getY(qMax, bounds, range, isLog);
                    double yMin = cd.getY(qMin, bounds, range, isLog);
                    path.lineTo(xPrev, yMax);
                    path.lineTo(xPrev, yMin);
                }
                g.draw(path);
                
                xPrev = -1;
                qMax = Double.NEGATIVE_INFINITY;
                qMin = Double.MAX_VALUE;
                path.reset();
            }
        }
        
        // draw the remaining aggregated bar
        if (xPrev >= 0) {
            double yMax = cd.getY(qMax, bounds, range, isLog);
            double yMin = cd.getY(qMin, bounds, range, isLog);
            path.lineTo(xPrev, yMax);
            path.lineTo(xPrev, yMin);
        }
        g.draw(path);
        g.setStroke(old);
    }
    
    public static class Simplify {
        public static float[][] simplify(float[][] points, float tolerance) {
            float sqTolerance = tolerance * tolerance;
            
            return simplifyDouglasPeucker(points, sqTolerance);
        }
        
        public static float[][] simplify(float[][] points, float tolerance,
                boolean highestQuality) {
            float sqTolerance = tolerance * tolerance;
            
            if (!highestQuality)
                points = simplifyRadialDistance(points, sqTolerance);
            
            points = simplifyDouglasPeucker(points, sqTolerance);
            
            return points;
        }
        
        // distance-based simplification
        public static float[][] simplifyRadialDistance(float[][] points,
                float sqTolerance) {
            int len = points.length;
            
            float[] point = new float[2];
            float[] prevPoint = points[0];
            
            ArrayList<float[]> newPoints = new ArrayList<>();
            newPoints.add(prevPoint);
            
            for (int i = 1; i < len; i++) {
                point = points[i];
                
                if (getSquareDistance(point, prevPoint) > sqTolerance) {
                    newPoints.add(point);
                    prevPoint = point;
                }
            }
            
            if (!prevPoint.equals(point))
                newPoints.add(point);
            
            return newPoints.toArray(new float[newPoints.size()][2]);
        }
        
        public static class SimplifiedGeneralPath {
            private final Point2D.Double currPoint = new Point2D.Double();
            private boolean hasCurrPoint;
            private double currDistance;
            private final Point2D.Double p1 = new Point2D.Double(), p2 = new Point2D.Double();
            private final GeneralPath path = new GeneralPath();
            
            public void moveTo(double x, double y) {
                if (hasCurrPoint) {
                    path.lineTo(currPoint.x, currPoint.y);
                    hasCurrPoint = false;
                }
                
                path.moveTo(x, y);
                p2.setLocation(x, y);
            }
            
            public void lineTo(double x, double y) {
                if (!hasCurrPoint) {
                    p1.setLocation(p2);
                    p2.setLocation(x, y);
                } else if (Math.abs(x - p2.x) > 0.5 && Line2D.ptLineDistSq(p1.x, p1.y, p2.x, p2.y, x, y) > 0.25) {
                    path.lineTo(currPoint.x, currPoint.y);
                    currDistance = -1.0;
                    p1.setLocation(p2);
                    p2.setLocation(x, y);
                }
                setCurrPoint(x, y);
            }
            
            private void setCurrPoint(double x, double y) {
                double dist = Point2D.distanceSq(p1.x, p1.y, x, y);
                if (!hasCurrPoint || dist > currDistance) {
                    currPoint.setLocation(x, y);
                    hasCurrPoint = true;
                    currDistance = dist;
                }
            }
            
            public GeneralPath getPath() {
                if (hasCurrPoint) {
                    path.lineTo(currPoint.x, currPoint.y);
                    hasCurrPoint = false;
                }
                return path;
            }
        }
        
        // simplification using optimized Douglas-Peucker algorithm with recursion
        // elimination
        public static float[][] simplifyDouglasPeucker(float[][] points,
                float sqTolerance) {
            int len = points.length;
            Integer[] markers = new Integer[len];
            
            Integer first = 0;
            Integer last = len - 1;
            
            float maxSqDist;
            float sqDist;
            int index = 0;
            
            ArrayList<Integer> firstStack = new ArrayList<>();
            ArrayList<Integer> lastStack = new ArrayList<>();
            
            ArrayList<float[]> newPoints = new ArrayList<>();
            
            markers[first] = markers[last] = 1;
            
            while (last != null) {
                maxSqDist = 0;
                
                for (int i = first + 1; i < last; i++) {
                    sqDist = getSquareSegmentDistance(points[i], points[first],
                            points[last]);
                    
                    if (sqDist > maxSqDist) {
                        index = i;
                        maxSqDist = sqDist;
                    }
                }
                
                if (maxSqDist > sqTolerance) {
                    markers[index] = 1;
                    
                    firstStack.add(first);
                    lastStack.add(index);
                    
                    firstStack.add(index);
                    lastStack.add(last);
                }
                
                if (firstStack.size() > 1)
                    first = firstStack.remove(firstStack.size() - 1);
                else
                    first = null;
                
                if (lastStack.size() > 1)
                    last = lastStack.remove(lastStack.size() - 1);
                else
                    last = null;
            }
            
            for (int i = 0; i < len; i++)
                if (markers[i] != null)
                    newPoints.add(points[i]);
            
            return newPoints.toArray(new float[newPoints.size()][2]);
        }
        
        // simplification using optimized Douglas-Peucker algorithm with recursion
        // elimination
        public static Float[] simplifyDouglasPeucker(float[] points,
                float sqTolerance) {
            int len = points.length / 2;
            
            Integer[] markers = new Integer[len];
            
            Integer first = 0;
            Integer last = len - 1;
            
            float maxSqDist;
            float sqDist;
            int index = 0;
            
            ArrayList<Integer> firstStack = new ArrayList<>();
            ArrayList<Integer> lastStack = new ArrayList<>();
            
            ArrayList<Float> newPoints = new ArrayList<>();
            
            markers[first] = markers[last] = 1;
            
            while (last != null) {
                maxSqDist = 0;
                
                for (int i = first + 1; i < last; i++) {
                    sqDist = getSquareSegmentDistance(points, i << 1, first << 1, last << 1);
                    
                    if (sqDist > maxSqDist) {
                        index = i;
                        maxSqDist = sqDist;
                    }
                }
                
                if (maxSqDist > sqTolerance) {
                    markers[index] = 1;
                    
                    firstStack.add(first);
                    lastStack.add(index);
                    
                    firstStack.add(index);
                    lastStack.add(last);
                }
                
                if (firstStack.size() > 1)
                    first = firstStack.remove(firstStack.size() - 1);
                else
                    first = null;
                
                if (lastStack.size() > 1)
                    last = lastStack.remove(lastStack.size() - 1);
                else
                    last = null;
            }
            
            for (int i = 0; i < len; i++) {
                if (markers[i] != null) {
                    newPoints.add(points[i << 1]);
                    newPoints.add(points[(i << 1) + 1]);
                }
            }
            
            return newPoints.toArray(new Float[newPoints.size()]);
        }
        
        public static float getSquareDistance(float[] p1, float[] p2) {
            float dx = p1[0] - p2[0], dy = p1[1] - p2[1], dz = p1[2] - p2[2];
            return dx * dx + dy * dy + dz * dz;
        }
        
        public static double getSquareDistance(double x, double y, Point2D p2) {
            double dx = x - p2.getX(), dy = y - p2.getY();
            return dx * dx + dy * dy;
        }
        
        // square distance from a point to a segment
        public static float getSquareSegmentDistance(float[] p, float[] p1,
                float[] p2) {
            float x = p1[0], y = p1[1], z = p1[2];
            
            float dx = p2[0] - x, dy = p2[1] - y, dz = p2[2] - z;
            
            float t;
            
            if (dx != 0 || dy != 0 || dz != 0) {
                t = ((p[0] - x) * dx + (p[1] - y) * dy) + (p[2] - z) * dz
                        / (dx * dx + dy * dy + dz * dz);
                
                if (t > 1) {
                    x = p2[0];
                    y = p2[1];
                    z = p2[2];
                    
                } else if (t > 0) {
                    x += dx * t;
                    y += dy * t;
                    z += dz * t;
                }
            }
            
            dx = p[0] - x;
            dy = p[1] - y;
            dz = p[2] - z;
            
            return dx * dx + dy * dy + dz * dz;
        }
        public static double getSquareSegmentDistance(double x0, double y0, Point2D p1, Point2D p2) {
            double x = p1.getX(), y = p1.getY();
            double dx = p2.getX() - x, dy = p2.getY() - y;
            double t;
            
            if (dx != 0 || dy != 0) {
                t = ((x0 - x) * dx + (y0 - y) * dy)
                        / (dx * dx + dy * dy);
                
                if (t > 1) {
                    x = p2.getX();
                    y = p2.getY();
                    
                } else if (t > 0) {
                    x += dx * t;
                    y += dy * t;
                }
            }
            
            dx = x0 - x;
            dy = y0 - y;
            
            return dx * dx + dy * dy;
        }
        // square distance from a point to a segment
        public static float getSquareSegmentDistance(float[] a, int p, int p1,
                int p2) {
            float x = a[p1], y = a[p1 + 1];
            
            float dx = a[p2] - x, dy = a[p2 + 1] - y;
            
            float t;
            
            if (dx != 0 || dy != 0) {
                t = ((a[p] - x) * dx + (a[p + 1] - y) * dy)
                        / (dx * dx + dy * dy);
                
                if (t > 1) {
                    x = a[p2];
                    y = a[p2 + 1];
                    
                } else if (t > 0) {
                    x += dx * t;
                    y += dy * t;
                }
            }
            
            dx = a[p] - x;
            dy = a[p + 1] - y;
            
            return dx * dx + dy * dy;
        }
    } 
    
    protected void drawLine0(Graphics2D g, Component c) {
        //        boolean isLog = cf.getChartProperties().getAxisLogarithmicFlag();
        Stroke old = g.getStroke();
        g.setPaint(primaryColor);
        if (stroke != null)
            g.setStroke(stroke);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        final double N = 200;
        GeneralPath path = null;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double value = c.getHeight()/2;
        for (int i = 0; i < c.getWidth()*N; i++) {
            value += r.nextDouble(-1.0, 1.0);
            if (value == value) {
                double x = i/N;
                double y = value;
                
                if (path != null) {
                    path.lineTo(x, y);
                } else {
                    path = new GeneralPath();
                    path.moveTo(x, y);
                }
            }
        }
        if (path != null)
            g.draw(path);
        g.setStroke(old);
    }
    
    protected void drawLine2(Graphics2D g, Component c) {
        //      boolean isLog = cf.getChartProperties().getAxisLogarithmicFlag();
        Stroke old = g.getStroke();
        g.setPaint(primaryColor);
        if (stroke != null)
            g.setStroke(stroke);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        final double N = 2000, dx = 1.0/N;
        SimplifiedGeneralPath path = null;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double value = c.getHeight()/2;
        for (double i = 0, j = c.getWidth(); i < j; i+=dx) {
            value += r.nextDouble(-0.1, 0.1);
            if (value == value) {
                double x = i;
                double y = value;
                
                if (path != null) {
                    path.lineTo(x, y);
                } else {
                    path = new SimplifiedGeneralPath();
                    path.moveTo(x, y);
                }
            }
        }
        if (path != null)
            g.draw(path.getPath());
        g.setStroke(old);
    }
    
    protected void drawSubpixelOptimizedLine0(Graphics2D g, Component c) {
        //        boolean isLog = cf.getChartProperties().getAxisLogarithmicFlag();
        //        ChartData cd = cf.getChartData();
        Stroke old = g.getStroke();
        g.setPaint(primaryColor);
        if (stroke != null)
            g.setStroke(stroke);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        final double N = 100, dx = 1.0/N;
        double xPrev = -1;
        double qMax = Double.NEGATIVE_INFINITY, qMin = Double.MAX_VALUE;
        GeneralPath path = new GeneralPath(GeneralPath.WIND_NON_ZERO, c.getWidth());
        ThreadLocalRandom r = ThreadLocalRandom.current();
        double value = c.getHeight()/2;
        for (double i = 0, j = c.getWidth(); i < j; i+=dx) {
            value += r.nextDouble(-.3, .3);
            if (value == value) {
                double x = i;
                if (x > xPrev + 0.2) {
                    // project y-coordinates and draw a vertical tick
                    if (xPrev >= 0) {
                        double yMax = qMax;
                        double yMin = qMin;
                        path.lineTo(xPrev, yMax);
                        path.lineTo(xPrev, yMin);
                    } else {
                        double y = value;
                        path.moveTo(x, y);
                    }
                    
                    qMax = qMin = value;
                    xPrev = x;
                } else {
                    // coerce current bar data with the previous data
                    if (value > qMax)
                        qMax = value;
                    if (value < qMin)
                        qMin = value;
                }
            } else {
                if (xPrev >= 0) {
                    double yMax = qMax;
                    double yMin = qMin;
                    path.lineTo(xPrev, yMax);
                    path.lineTo(xPrev, yMin);
                }
                g.draw(path);
                
                xPrev = -1;
                qMax = Double.NEGATIVE_INFINITY;
                qMin = Double.MAX_VALUE;
                path.reset();
            }
        }
        
        // draw the remaining aggregated bar
        if (xPrev >= 0) {
            double yMax = qMax;
            double yMin = qMin;
            path.lineTo(xPrev, yMax);
            path.lineTo(xPrev, yMin);
        }
        g.draw(path);
        g.setStroke(old);
    }
}
