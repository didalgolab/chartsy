/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import java.util.EventObject;

/**
 * The event that will be delivered to the listeners of the interactors on an organized view.
 * 
 * @author Mariusz Bernacki
 *
 */
public class InteractorChangeEvent extends EventObject {
    /** The old interactor object. */
    private final OrganizedViewInteractor oldValue;
    /** The new interactor object. */
    private final OrganizedViewInteractor newValue;
    
    
    public InteractorChangeEvent(OrganizedView source, OrganizedViewInteractor oldValue, OrganizedViewInteractor newValue) {
        super(source);
        this.oldValue = oldValue;
        this.newValue = newValue;
    }
    
    public OrganizedView getOrganizedView() {
        return (OrganizedView) getSource();
    }
    
    /**
     * @return the oldValue
     */
    public OrganizedViewInteractor getOldValue() {
        return oldValue;
    }
    
    /**
     * @return the newValue
     */
    public OrganizedViewInteractor getNewValue() {
        return newValue;
    }
}
