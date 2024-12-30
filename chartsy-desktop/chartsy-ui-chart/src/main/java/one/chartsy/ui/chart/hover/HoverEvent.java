/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
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
