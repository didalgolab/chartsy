/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.hover;

/**
 * The interface for receiving events when a hoverable component is hovered by the mouse.
 * 
 * @author Mariusz Bernacki
 * 
 */
public interface HoverListener {
    
    /**
     * Called when the mouse enters the hoverable component.
     * 
     * @param event
     *            the hover event
     */
    void mouseEntered(HoverEvent event);
    
    /**
     * Called when the mouse exits the hoverable component.
     * 
     * @param event
     *            the hover event
     */
    void mouseExited(HoverEvent event);
    
}
