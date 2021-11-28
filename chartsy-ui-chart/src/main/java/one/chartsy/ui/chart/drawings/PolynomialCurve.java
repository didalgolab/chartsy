/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.drawings;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.GeneralPath;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.ChartPlugin.Parameter;
import one.chartsy.ui.chart.internal.ColorServices;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;

/**
 * Represents the polynomial curve {@link Annotation} bound to the framing rectangle.
 * 
 * @author Mariusz Bernacki
 * 
 */
public class PolynomialCurve extends AbstractRectangularShape {
    
    private static final long serialVersionUID = -4937590747803627369L;
    
    @Parameter(name = "Inside Visibility")
    public boolean insideVisibility = false;
    @Parameter(name = "Color")
    public Color color = Color.RED;
    @Parameter(name = "Inside Alpha", stereotype = ChartPlugin.Stereotype.TRANSPARENCY)
    public int insideAlpha = 25;
    @Parameter(name = "Style")
    public Stroke style = StrokeFactory.ULTRATHIN_SOLID;
    
    /** The polynomial coefficients to be drawn. */
    protected double[] polynomial = new double[0];
    
    
    public PolynomialCurve() {
        super("Polynomial Curve", new Rectangle2D.Double());
    }
    
    /**
     * Sets the polynomial to be drawn, replacing the previously set polynomial
     * (if exists).
     * 
     * @param d
     *            the coefficients of the polynomial to be drawn, the zeroth
     *            components corresponds to the monomial of degree 0, etc.
     */
    public void setPolynomial(double[] d) {
        if (d == null)
            throw new IllegalArgumentException("polynomimal coefficients cannot be NULL");
        
        this.polynomial = d;
    }
    
    @Override
    protected Color getFillColor() {
        return ColorServices.getDefault().getTransparentColor(color, insideAlpha);
    }
    
    @Override
    public void paint(Graphics2D g, CoordinateSystem coords, int width, int height) {
        Stroke old = g.getStroke();
        
        GeneralPath gp = new GeneralPath();
        PolynomialFunction function = new PolynomialFunction(polynomial);
        Point2D p1 = coords.transform(startAnchor), p2 = coords.transform(endAnchor);
        double x1 = p1.getX(), x2 = p2.getX();
        double y1 = p1.getY(), y2 = p2.getY();
        int numberOfPoints = 16*polynomial.length;
        for (int i = 0; i <= numberOfPoints; i++) {
            
            double x = x1 + (x2 - x1) * i / numberOfPoints;
            double y = y1 + function.value((double)i / numberOfPoints) * (y2 - y1);
            
            if (i == 0)
                gp.moveTo(x, y);
            else
                gp.lineTo(x, y);
        }
        
        shape.setFrameFromDiagonal(x1, y1, x2, y2);
        if (insideVisibility) {
            g.setPaint(getFillColor());
            g.draw(shape);
        }
        
        g.setPaint(color);
        g.setStroke(style);
        g.draw(gp);
        g.setStroke(old);
        
    }
    
    public void setPoints(long x1, double y1, long x2, double y2) {
        setStartAnchor(new ChartPoint(x1, y1));
        setEndAnchor(new ChartPoint(x2, y2));
    }
}
