/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
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
