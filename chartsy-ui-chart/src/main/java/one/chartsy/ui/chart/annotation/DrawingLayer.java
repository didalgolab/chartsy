/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.annotation;

import one.chartsy.ui.chart.Annotation;
import one.chartsy.ui.chart.ChartPoint;
import one.chartsy.ui.chart.PolyPointsAware;
import one.chartsy.ui.chart.RectangularShapeAware;

public interface DrawingLayer {
    
    void addAnnotation(Annotation graphic);
    
    /**
     * Draws a {@code Line} connecting the two points specified by the
     * coordinate pairs. Each pair of <b>{@code (x, y)}</b> coordinates defines
     * a point on the financial chart. The x-coordinates are defined as
     * epoch-seconds describing the point on a chart time-line. The
     * y-coordinates represents prices on a chart.
     * 
     * @param p1
     *            the (x,y) coordinate of the first point
     * @param p2
     *            the (x,y) coordinate of the second point
     * @return the created {@code Line} object which can be further adjusted by
     *         a caller
     */
    @SuppressWarnings("unchecked")
    default <A extends Annotation & PolyPointsAware> A drawLine(ChartPoint p1, ChartPoint p2) {
        Annotation.Key key = Annotation.Key(Annotation.Key.LINE);

        Annotation obj = AnnotationLookup.getAnnotation(key)
                .orElseThrow(() -> new UnsupportedOperationException("Cannot drawLine because of no compatible Annotation installed"))
                .copy();
        if (!(obj instanceof PolyPointsAware))
            throw new UnsupportedOperationException("Cannot drawLine because " + obj.getClass().getSimpleName() + " should implement PolyPointsAware");

        ((PolyPointsAware) obj).setPoints(p1, p2);
        addAnnotation(obj);
        return (A) obj;
    }
    
    /**
     * Draws the specified {@code Rectangle} on the graphic layer.
     * <p>
     * Each pair of <b>{@code (x, y)}</b> coordinates defines a point on the
     * financial chart. The x-coordinates are defined as epoch-seconds describing
     * the point on a chart time-line. The y-coordinates represents prices on a
     * chart.
     * 
     * @param p1
     *            the (x,y) coordinate of the rectangle's diagonal starting point
     * @param p2
     *            the (x,y) coordinate of the rectangle's diagonal ending point
     * @return the created {@code Rectangle} object which can be further adjusted by
     *         a caller
     */
    @SuppressWarnings("unchecked")
    default <A extends Annotation & RectangularShapeAware> A drawRect(ChartPoint p1, ChartPoint p2) {
        Annotation.Key key = Annotation.Key(Annotation.Key.RECT);

        Annotation obj = AnnotationLookup.getAnnotation(key)
                .orElseThrow(() -> new UnsupportedOperationException("Cannot drawRect because of no compatible Annotation installed"))
                .copy();
        if (!(obj instanceof RectangularShapeAware))
            throw new UnsupportedOperationException("Cannot drawRect because " + obj.getClass().getSimpleName() + " should implement RectangularShapeAware");

        ((RectangularShapeAware) obj).setDefinitionFrame(p1, p2);
        addAnnotation(obj);
        return (A) obj;
    }
    
    /**
     * Draws the specified {@code Ellipse} on the graphic layer.
     * <p>
     * Each pair of <b>{@code (x, y)}</b> coordinates defines a point on the
     * financial chart. The x-coordinates are defined as epoch-seconds describing
     * the point on a chart time-line. The y-coordinates represents prices on a
     * chart.
     * 
     * @param p1
     *            the (x,y) coordinate defining the starting point of ellipse's
     *            bounding box diagonal
     * @param p2
     *            the (x,y) coordinate defining the ending point of ellipse's
     *            bounding box diagonal
     * @return the created {@code Ellipse} object which can be further adjusted by a
     *         caller
     */
    @SuppressWarnings("unchecked")
    default <A extends Annotation & RectangularShapeAware> A drawEllipse(ChartPoint p1, ChartPoint p2) {
        Annotation.Key key = Annotation.Key(Annotation.Key.ELLIPSE);

        Annotation obj = AnnotationLookup.getAnnotation(key)
                .orElseThrow(() -> new UnsupportedOperationException("Cannot drawEllipse because of no compatible Annotation installed"))
                .copy();
        if (!(obj instanceof RectangularShapeAware))
            throw new UnsupportedOperationException("Cannot drawEllipse because " + obj.getClass().getSimpleName() + " should implement RectangularShapeAware");

        ((RectangularShapeAware) obj).setDefinitionFrame(p1, p2);
        addAnnotation(obj);
        return (A) obj;
    }
}
