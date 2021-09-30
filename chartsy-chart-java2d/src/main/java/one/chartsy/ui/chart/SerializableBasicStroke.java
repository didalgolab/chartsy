/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
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
