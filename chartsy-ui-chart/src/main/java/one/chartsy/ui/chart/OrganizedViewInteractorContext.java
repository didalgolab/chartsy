/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.ui.chart.components.AnnotationPanel;

import java.awt.Component;
import java.awt.Container;
import java.awt.geom.Rectangle2D;

/**
 * The class representing the context passed to the organized view interactors.
 * This interface is implemented by the {@link AnnotationPanel}.
 * 
 * @author Mariusz Bernacki
 *
 */
public interface OrganizedViewInteractorContext {
    
    /**
     * Repaints a specified graphic object. An area occupied by the bounding
     * rectangle of the {@code graphic} object is cleared and repainted by the
     * GUI system.
     * 
     * @param graphic
     *            the graphic object to repaint
     */
    void repaint(Annotation graphic);
    
    void repaint(Rectangle2D dirtyRegion);
    
    /**
     * Returns the coordinate system currently used by the view.
     * 
     * @return the coordinate system used to display graphic objects
     */
    CoordinateSystem getCoordinateSystem();
    
    /**
     * Adds the specified component to the view's container with the specified
     * constraints at the specified index. Also notifies the layout manager to
     * add the component to the container's layout using the specified
     * constraints object.
     * <p>
     * If the component is not an ancestor of this container and has a non-null
     * parent, it is removed from its current parent before it is added to this
     * container.
     * <p>
     * This method changes layout-related information, and therefore,
     * invalidates the component hierarchy. If the container has already been
     * displayed, the hierarchy must be validated thereafter in order to display
     * the added component.
     *
     * @param comp
     *            the component to be added
     * @param constraints
     *            an object expressing layout constraints for this
     * @param index
     *            the position in the container's list at which to insert the
     *            component; {@code -1} means insert at the end component
     * @throws NullPointerException
     *             if {@code comp} is {@code null}
     * @throws IllegalArgumentException
     *             if {@code index} is not invalid
     * @see Container#add(Component, Object, int)
     */
    void add(Component comp, Object constraints, int index);
    
    void paintImmediately(Rectangle2D createUnion);
    
}
