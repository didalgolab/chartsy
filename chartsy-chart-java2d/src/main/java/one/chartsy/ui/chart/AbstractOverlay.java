/* Copyright 2021 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart;

import java.lang.reflect.InvocationTargetException;

public abstract class AbstractOverlay extends Overlay {
    @Parameter(name = "Marker Visibility")
    public boolean markerVisibility;
    
    
    protected AbstractOverlay(String name) {
        super(name);
    }
    
    @Override
    public Overlay newInstance() {
        try {
            return getClass().getDeclaredConstructor().newInstance();
        } catch (IllegalAccessException | InstantiationException | NoSuchMethodException | SecurityException | InvocationTargetException e) {
            throw new RuntimeException("Cannot instantiate " + getClass().getSimpleName(), e);
        }
    }
    
    @Override
    public boolean getMarkerVisibility() {
        return markerVisibility;
    }
    
}
