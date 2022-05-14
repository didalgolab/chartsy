/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.kernel.annotation.processors;

import java.util.Collections;
import java.util.Set;

import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

import org.immutables.value.Generated;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

@ServiceProvider(service = Processor.class)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ImmutablesRegistrationProcessor extends LayerGeneratingProcessor {
    /** The folder in a layer where the mapper specification is stored. */
    public static final String NAMESPACE = "Immutables/Generated";
    
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(Generated.class.getCanonicalName());
    }
    
    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) throws LayerGenerationException {
        for (Element e : roundEnv.getElementsAnnotatedWith(Generated.class)) {
            Generated a = e.getAnnotation(Generated.class);
            if (a != null)
                layer().file(NAMESPACE + '/' + e)
                        .stringvalue("from", resolveClassNameRelativeTo(e, a.from()))
                        .write();
        }
        return true;
    }

    protected String resolveClassNameRelativeTo(Element element, String from) {
        PackageElement packageElement = processingEnv.getElementUtils().getPackageOf(element);
        return packageElement + "." + from;
    }
}