/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
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
