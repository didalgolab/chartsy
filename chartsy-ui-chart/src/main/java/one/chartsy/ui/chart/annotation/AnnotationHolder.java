package one.chartsy.ui.chart.annotation;

import one.chartsy.ui.chart.Annotation;
import one.chartsy.ui.chart.components.AnnotationPanel;

public class AnnotationHolder {
    private static AnnotationHolder current = new AnnotationHolder();
    private Annotation instance;

    private AnnotationHolder() { }

    public static AnnotationHolder current() {
        return current;
    }

    public void setNewAnnotation(Annotation instance) {
        this.instance = instance;
    }

    public boolean hasNew() {
        return instance != null;
    }

    public Annotation getNewAnnotation(AnnotationPanel panel) {
        Annotation result = instance.copy();
        instance = null;
        return result;
    }

    public void clearNewAnnotation() {
        instance = null;
    }

}
