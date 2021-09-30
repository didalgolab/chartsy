package one.chartsy.ui.chart.annotation;

import one.chartsy.ui.chart.Annotation;

import java.util.Map;
import java.util.Optional;

public interface AnnotationLookupService {

    Map<String, Annotation> getKeyAnnotations();
}
