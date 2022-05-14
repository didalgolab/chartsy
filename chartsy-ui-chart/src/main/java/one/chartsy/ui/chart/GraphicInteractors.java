/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.ui.chart.graphic.GraphicInteractor;

import java.lang.ref.SoftReference;
import java.util.WeakHashMap;

/**
 * The soft cache of graphic interactors.
 * 
 * @author Mariusz Bernacki
 *
 */
final class GraphicInteractors {
    
    private static final WeakHashMap<Class<? extends GraphicInteractor>, SoftReference<GraphicInteractor>> map = new WeakHashMap<>();
    
    public static GraphicInteractor get(Class<? extends GraphicInteractor> type) throws InstantiationException, IllegalAccessException {
        if (type == null)
            return null;
        
        GraphicInteractor interactor;
        SoftReference<GraphicInteractor> ref = map.get(type);
        if (ref == null || (interactor = ref.get()) == null) {
            interactor = type.newInstance();
            map.put(type, new SoftReference<>(interactor));
        }
        return interactor;
    }
}