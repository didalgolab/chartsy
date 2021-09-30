/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.drawings;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.util.function.UnaryOperator;

import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.ChartPlugin.Parameter;
import one.chartsy.ui.chart.internal.ColorServices;

/**
 * The common base type for a number of pure drawing {@link Annotation} objects
 * whose geometry is defined by a rectangular frame (e.g. rectangles, ellipses).
 * 
 * @author Mariusz Bernacki
 * 
 */
public abstract class AbstractRectangularShape extends Annotation implements RectangularShapeAware {
    /** The serial version UID */
    private static final long serialVersionUID = Annotation.serialVersionUID;
    
    @Parameter(name = "Inside Visibility")
    public boolean insideVisibility = true;
    @Parameter(name = "Color")
    public Color color = Color.RED;
    @Parameter(name = "Inside Alpha", stereotype = ChartPlugin.Stereotype.TRANSPARENCY)
    public int insideAlpha = 25;
    @Parameter(name = "Style")
    public transient Stroke style = StrokeFactory.ULTRATHIN_SOLID;
    
    private final Color fillColor = ColorServices.getDefault().getTransparentColor(color, insideAlpha);
    
    /** The shape of this annotation's geometry. */
    protected final RectangularShape shape;
    /** The starting point of the rectangle region. */
    protected @Parameter(name = "startAnchor") ChartPoint startAnchor = new ChartPoint();
    /** The ending point of the rectangle region. */
    protected @Parameter(name = "endAnchor") ChartPoint endAnchor = new ChartPoint();
    
    
    protected AbstractRectangularShape(String name, RectangularShape shape) {
        super(name);
        this.shape = shape;
    }
    
    protected Color getFillColor() {
        return fillColor;
    }
    
    @Override
    public OrganizedViewInteractor getDrawingInteractor(OrganizedViewInteractorContext context) {
        return new DiagonalDragInteractor(event -> {
            getGraphicBag().applyManipulator(this, event, (g, e) -> {
                setBounds(e.getStartPoint(), e.getEndPoint(), context.getCoordinateSystem());
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
    public void applyTransform(UnaryOperator<Point2D> transform, CoordinateSystem coords) {
        Point2D p1 = coords.transform(getStartAnchor()), p2 = coords.transform(getEndAnchor());
        setStartAnchor(coords.inverseTransform(transform.apply(p1)));
        setEndAnchor(coords.inverseTransform(transform.apply(p2)));
    }
    
    public void setBounds(Point2D p1, Point2D p2, CoordinateSystem coords) {
        setStartAnchor(coords.inverseTransform(p1));
        setEndAnchor(coords.inverseTransform(p2));
    }
    
    @Override
    public void paint(Graphics2D g, CoordinateSystem coords, int width, int height) {
        Stroke old = g.getStroke();
        
        shape.setFrameFromDiagonal(coords.transform(getStartAnchor()), coords.transform(getEndAnchor()));
        if (!shape.getBounds().intersects(coords.getBounds()))
            return;
        
        /*		Rectangle2D bounds = coords.getBounds();
		Point2D p1 = coords.forward(getStartAnchor());
		Point2D p2 = coords.forward(getEndAnchor());
		p1.setLocation(Math.max(0, Math.min(bounds.getWidth(), p1.getX())), Math.max(0, Math.min(bounds.getHeight(), p1.getY())));
		p2.setLocation(Math.max(0, Math.min(bounds.getWidth(), p2.getX())), Math.max(0, Math.min(bounds.getHeight(), p2.getY())));
		shape.setFrameFromDiagonal(p1, p2);
         */
        
        //		shape.
        if (insideVisibility) {
            g.setPaint(getFillColor());
            g.fill(shape);
        }
        
        g.setPaint(color);
        g.setStroke(style);
        g.draw(shape);
        g.setStroke(old);
    }
    
    public ChartPoint getStartAnchor() {
        return startAnchor;
    }
    
    public void setStartAnchor(ChartPoint startAnchor) {
        this.startAnchor = startAnchor;
    }
    
    public ChartPoint getEndAnchor() {
        return endAnchor;
    }
    
    public void setEndAnchor(ChartPoint endAnchor) {
        this.endAnchor = endAnchor;
    }
    
    @Override
    public Rectangle2D getDefinitionFrame(CoordinateSystem coords) {
        Rectangle2D rect = new Rectangle2D.Double();
        rect.setFrameFromDiagonal(coords.transform(startAnchor), coords.transform(endAnchor));
        return rect;
    }
    
    @Override
    public void setDefinitionFrame(Rectangle2D rect, CoordinateSystem coords) {
        startAnchor = coords.inverseTransform(rect.getX(), rect.getY());
        endAnchor = coords.inverseTransform(rect.getMaxX(), rect.getMaxY());
    }
    
    @Override
    public void setDefinitionFrame(ChartPoint p1, ChartPoint p2) {
        startAnchor = p1;
        endAnchor = p2;
    }
}
