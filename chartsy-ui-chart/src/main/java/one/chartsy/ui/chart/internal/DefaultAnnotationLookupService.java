/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.internal;

import one.chartsy.ui.chart.Annotation;
import one.chartsy.ui.chart.annotation.AnnotationLookupService;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@ServiceProvider(service = AnnotationLookupService.class)
public class DefaultAnnotationLookupService implements AnnotationLookupService {

    private Map<String, Annotation> keyAnnotations;

    /**
     * Returns the key annotations currently installed.
     * <p>
     * The key annotation technically is an {@link Annotation} marked with a
     * {@code Annotation.Key}. The annotation key marker indicates that the
     * annotation corresponds to a specified key "shape" and will be
     * automatically used by the framework whenever an implementation of a
     * particular "shape" is needed.
     *
     * @return the readonly map of the key annotations currently installed
     */
    @Override
    public Map<String, Annotation> getKeyAnnotations() {
        Map<String, Annotation> result = keyAnnotations;
        if (result == null) {
            // Load all key annotations
            result = new HashMap<>();
            for (Annotation obj : Lookup.getDefault().lookupAll(Annotation.class)) {
                Annotation.Key key = obj.getClass().getAnnotation(Annotation.Key.class);
                if (key != null && key.value() != null)
                    result.put(key.value(), obj);
            }
            keyAnnotations = Collections.unmodifiableMap(result);
        }
        return result;
    }
}
