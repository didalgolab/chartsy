/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.annotation;

import java.util.EventListener;

import one.chartsy.ui.chart.Annotation;

/**
 * The listener interested in changes occurring in a {@code GraphicModel}.
 * 
 * @author Mariusz Bernacki
 *
 */
public interface GraphicModelListener extends EventListener {
    
    void graphicCreated(Annotation graphic);
    
    void graphicRemoved(Annotation graphic);
    
    void graphicWillUpdate(Annotation graphic);
    
    void graphicUpdated(Annotation graphic, Annotation oldGraphic);
    
    void graphicContentChanged();
}
