/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
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
