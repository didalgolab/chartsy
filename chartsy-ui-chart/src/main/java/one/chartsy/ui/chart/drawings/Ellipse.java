/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.drawings;

import java.awt.geom.Ellipse2D;

import one.chartsy.ui.chart.Annotation;
import org.openide.util.lookup.ServiceProvider;

/**
 * The ellipse shape annotation.
 * 
 * @author Mariusz Bernacki
 * 
 */
@Annotation.Key(Annotation.Key.RECT)
@ServiceProvider(service = Annotation.class)
public class Ellipse extends AbstractRectangularShape {
    /** The serial version UID */
    private static final long serialVersionUID = Annotation.serialVersionUID;
    
    
    public Ellipse() {
        super("Ellipse", new Ellipse2D.Double());
    }
}
