/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.annotation;

import one.chartsy.ui.chart.Annotation;
import org.openide.util.Lookup;

import java.util.Optional;

public interface AnnotationLookup {

    private static AnnotationLookupService service() {
        return Lookup.getDefault().lookup(AnnotationLookupService.class);
    }

    static Optional<Annotation> getAnnotation(Annotation.Key key) {
        return Optional.ofNullable(service().getKeyAnnotations().get(key.value()));
    }
}
