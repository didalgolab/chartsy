/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.hover;

import java.util.EventObject;

/**
 * The event containing information about an object hovered by the mouse.
 * 
 * @author Mariusz Bernacki
 *
 */
public class HoverEvent extends EventObject {

    /** The hovered component value. */
    protected final Object value;
    
    
    /**
     * Constructs a new {@code HoverEvent} from the given object being hovered
     * 
     * @param source
     *            the source component that generated this event
     * @param value the hovered component value
     */
    public HoverEvent(Object source, Object value) {
        super(source);
        this.value = value;
    }
    
    /**
     * @return the value
     */
    public Object getValue() {
        return value;
    }
}
