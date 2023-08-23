/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import java.awt.BasicStroke;
import java.io.ObjectStreamException;
import java.io.Serial;
import java.io.Serializable;

/**
 * The serializable replacement for {@code BasicStroke} objects.
 * 
 * @author Mariusz Bernacki
 *
 */
public class SerializableBasicStroke implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 9067946475536696534L;
    
    float width;
    
    int join;
    int cap;
    float miterlimit;
    
    float[] dash;
    float dash_phase;
    
    public SerializableBasicStroke(BasicStroke stroke) {
        this.width = stroke.getLineWidth();
        this.join = stroke.getLineJoin();
        this.cap = stroke.getEndCap();
        this.miterlimit = stroke.getMiterLimit();
        this.dash = stroke.getDashArray();
        this.dash_phase = stroke.getDashPhase();
    }
    
    @Serial
    Object readResolve() throws ObjectStreamException {
        return new BasicStroke(width, cap, join, miterlimit, dash, dash_phase);
    }
}
