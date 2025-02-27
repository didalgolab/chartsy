/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.annotation;

import one.chartsy.ui.chart.Annotation;
import one.chartsy.ui.chart.components.AnnotationPanel;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = ChartAnnotator.class)
public class ChartAnnotator {

    private volatile Annotation currentDrawing;

    public static ChartAnnotator getGlobal() {
        return Lookup.getDefault().lookup(ChartAnnotator.class);
    }

    public void beginDrawing(Annotation annotation) {
        this.currentDrawing = annotation;
    }

    public boolean hasDrawing() {
        return currentDrawing != null;
    }

    public Annotation getCurrentDrawing(AnnotationPanel panel) {
        Annotation newDrawing = currentDrawing.copy();
        currentDrawing = null;
        return newDrawing;
    }
}
