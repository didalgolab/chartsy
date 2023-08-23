/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.drawings;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.ChartPlugin.Parameter;
import org.openide.util.lookup.ServiceProvider;

/**
 * Creates a line of any angle by clicking and dragging your mouse from the
 * beginning to the end of the line. The line will be drawn in the default
 * color. This color can be changed from the Annotation Settings.
 * 
 * @author Mariusz Bernacki
 */
@Annotation.Key(Annotation.Key.LINE)
@ServiceProvider(service = Annotation.class)
public class Line extends Annotation implements PolyPointsAware {
    /** The serial version UID */
    private static final long serialVersionUID = Annotation.serialVersionUID;
    /** The helper shape object used by the paint related methods. */
    final Line2D line = new Line2D.Double();
    /** The line color. */
    public @Parameter(name = "color") Color color = Color.RED;
    /** The line style. */
    public @Parameter(name = "style") Stroke style = BasicStrokes.ULTRATHIN_SOLID;
    /** The line start anchor. */
    public @Parameter(name = "startAnchor")
    ChartPoint startAnchor = new ChartPoint();
    /** The line end anchor. */
    public @Parameter(name = "endAnchor") ChartPoint endAnchor = new ChartPoint();
    
    
    public Line() {
        this("Line");
    }
    
    protected Line(String name) {
        super(name);
    }
    
    @Override
    public List<ChartPoint> getAnchorPoints() {
        return Arrays.asList(startAnchor, endAnchor);
    }
    
    @Override
    public OrganizedViewInteractor getDrawingInteractor(OrganizedViewInteractorContext context) {
        return new DiagonalDragInteractor(event -> {
            getGraphicBag().applyManipulator(this, event, (g, e) -> {
                setPoints(e.getStartPoint(), e.getEndPoint(), context.getCoordinateSystem());
            });
        });
    }
    
    @Override
    public Rectangle2D getBoundingBox(CoordinateSystem coords) {
        Rectangle r = new Rectangle();
        r.setFrameFromDiagonal(coords.transform(getStartAnchor()), coords.transform(getEndAnchor()));
        r.width++;
        r.height++;
        return r;
    }
    
    @Override
    public boolean contains(double x, double y, CoordinateSystem coords) {
        Point2D p1 = coords.transform(startAnchor);
        Point2D p2 = coords.transform(endAnchor);
        return Line2D.ptSegDist(p1.getX(), p1.getY(), p2.getX(), p2.getY(), x, y) < 4;
    }
    
    @Override
    public void applyTransform(UnaryOperator<Point2D> transform, CoordinateSystem coords) {
        Point2D p1 = coords.transform(getStartAnchor()), p2 = coords.transform(getEndAnchor());
        setStartAnchor(coords.inverseTransform(transform.apply(p1)));
        setEndAnchor(coords.inverseTransform(transform.apply(p2)));
    }
    
    @Override
    public void paint(Graphics2D g, CoordinateSystem coords, int width, int height) {
        Stroke old = g.getStroke();
        g.setPaint(color);
        g.setStroke(style);
        
        line.setLine(coords.transform(startAnchor), coords.transform(endAnchor));
        g.draw(line);
        g.setStroke(old);
    }
    
    @Override
    public void setPoints(ChartPoint... points) {
        if (points.length != 2)
            throw new IllegalArgumentException("2 points expected for Line Annotation, but found " + points.length);
        
        setStartAnchor(points[0]);
        setEndAnchor(points[1]);
    }
    
    public void setPoints(Point2D p1, Point2D p2, CoordinateSystem coords) {
        setPoints(coords.inverseTransform(p1), coords.inverseTransform(p2));
    }
    
    public final ChartPoint getStartAnchor() {
        return startAnchor;
    }
    
    public void setStartAnchor(ChartPoint startAnchor) {
        this.startAnchor = startAnchor;
    }
    
    public final ChartPoint getEndAnchor() {
        return endAnchor;
    }
    
    public void setEndAnchor(ChartPoint endAnchor) {
        this.endAnchor = endAnchor;
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * For the {@code Line} annotation fixed count: {@code 2} is returned.
     */
    @Override
    public int getPointCount() {
        return 2;
    }
    
    @Override
    public Point2D getPoint(int index, CoordinateSystem coords) {
        return coords.transform((index == 0)? getStartAnchor() : getEndAnchor());
    }
    
    @Override
    public void movePoint(int index, Point2D point, CoordinateSystem coords) {
        if (index == 0)
            setStartAnchor(coords.inverseTransform(point));
        else
            setEndAnchor(coords.inverseTransform(point));
    }
    
    @Override
    public Selection makeSelection() {
        return new PolyPointsSelection(this);
    }
}
