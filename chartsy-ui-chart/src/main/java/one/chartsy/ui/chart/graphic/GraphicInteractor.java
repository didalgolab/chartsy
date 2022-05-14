/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.graphic;

import one.chartsy.ui.chart.Annotation;
import one.chartsy.ui.chart.OrganizedViewInteractorContext;

import java.awt.AWTEvent;

/**
 * Used to handle events targeted to an annotation graphic.
 * 
 * @author Mariusz Bernacki
 *
 */
public interface GraphicInteractor {
    
    /**
     * Processes the event. If the interactor determines that it is not
     * interested in a kind of event that was delivered it should return
     * {@code false} from this method.
     * 
     * @param event
     *            the AWT event to process
     * @param graphic
     *            the annotation graphic
     * @param context
     *            the context in which the event occurred
     * @return {@code true} if the event was successfully handled by this
     *         interactor, {@code false} otherwise
     */
    boolean processEvent(AWTEvent event, Annotation graphic, OrganizedViewInteractorContext context);
    
}