/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.drawings;

import one.chartsy.ui.chart.Annotation;
import org.openide.util.lookup.ServiceProvider;

/**
 * The rectangular shape annotation.
 * 
 * @author Mariusz Bernacki
 */
@Annotation.Key(Annotation.Key.RECT)
@ServiceProvider(service = Annotation.class)
public class Rectangle extends AbstractRectangularShape {

    public Rectangle() {
        super("Rectangle", new java.awt.Rectangle());
    }
}
