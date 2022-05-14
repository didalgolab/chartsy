/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.ui.chart.components.AnnotationPanel;

import java.awt.geom.Point2D;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * Represents a selection of another graphic object.
 * <p>
 * A selection object serves as a visual decoration indicating that another
 * graphic object is selected by a user, as well as provides a methods allowing
 * a user to interact with an object being selected, for example by changing its
 * shape, location, and so on. To indicate the selected state of a graphic, its
 * corresponding selection object is drawn on a top of it. A new selection
 * object is created in the {@link AnnotationPanel} and associated with the
 * graphic object, whenever a user selects an object either manually on the
 * chart or programmatically from the code. Selection objects are created
 * automatically for you by calling the method
 * {@link Annotation#makeSelection()} on the selected graphic; you almost never
 * need to instantiate selection objects manually.
 * 
 * @author Mariusz Bernacki
 *
 */
public abstract class Selection extends Annotation {
    /** The corresponding selected graphic object. */
    private final Annotation graphic;
    
    
    /**
     * Creates a new selection object corresponding the given {@code graphic}
     * object.
     * 
     * @param graphic
     *            the corresponding selected graphic object
     * @throws NullPointerException
     *             if the specified {@code graphic} is {@code null}
     */
    protected Selection(Annotation graphic) {
        super(graphic.getName());
        this.graphic = Objects.requireNonNull(graphic);
    }
    
    @Override
    public abstract Annotation copy();
    
    @Override
    public void applyTransform(UnaryOperator<Point2D> transform, CoordinateSystem coords) {
        getGraphic().applyTransform(transform, coords);
    }
    
    /**
     * Returns the corresponding graphic object over which this selection object
     * is constructed.
     * 
     * @return the selected graphic object
     */
    public final Annotation getGraphic() {
        return graphic;
    }
    
    /**
     * Overriden to return always {@code this} graphic object.
     * 
     * @return always {@code this}
     */
    @Override
    public final Selection makeSelection() {
        return this;
    }
}
