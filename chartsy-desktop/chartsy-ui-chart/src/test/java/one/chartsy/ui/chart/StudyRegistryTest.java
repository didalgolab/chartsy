/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.financial.indicators.FramaTrendWhispers;
import one.chartsy.study.StudyDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudyRegistryTest {

    @Test
    void loadsGeneratedDescriptorsAndExposesDynamicPluginsByStableStudyName() {
        StudyRegistry registry = StudyRegistry.getDefault();

        assertThat(registry.getStudyDescriptors())
                .extracting(StudyDescriptor::name)
                .contains(
                        "Fractal Dimension",
                        "Sfora, Width",
                        "Sfora",
                        "Sentiment Bands",
                        "FRAMA, Leading",
                        "FRAMA, Trailing"
                );
        assertThat(registry.getIndicators()).doesNotHaveDuplicates();
        assertThat(registry.getOverlays()).doesNotHaveDuplicates();
        assertThat(registry.getIndicator("Fractal Dimension")).isInstanceOf(DynamicStudyIndicator.class);
        assertThat(registry.getIndicator("Sfora, Width")).isInstanceOf(DynamicStudyIndicator.class);
        assertThat(registry.getOverlay("Sfora")).isInstanceOf(DynamicStudyOverlay.class);
        assertThat(registry.getOverlay("Sentiment Bands")).isInstanceOf(DynamicStudyOverlay.class);
    }

    @Test
    void exposesCustomBuilderDescriptorsForFramaTrendWhispersStudies() {
        StudyRegistry registry = StudyRegistry.getDefault();

        StudyDescriptor sfora = registry.getStudyDescriptors().stream()
                .filter(descriptor -> descriptor.name().equals("Sfora"))
                .findFirst()
                .orElseThrow();
        StudyDescriptor sforaWidth = registry.getStudyDescriptors().stream()
                .filter(descriptor -> descriptor.name().equals("Sfora, Width"))
                .findFirst()
                .orElseThrow();

        assertThat(sfora.hasCustomBuilder()).isTrue();
        assertThat(sforaWidth.hasCustomBuilder()).isTrue();
        assertThat(sfora.implementationType()).isEqualTo(FramaTrendWhispers.class);
        assertThat(sforaWidth.implementationType()).isEqualTo(FramaTrendWhispers.class);
    }

    @Test
    void resolvesFreshPluginInstancesForRepeatedLookups() {
        StudyRegistry registry = StudyRegistry.getDefault();

        Indicator firstIndicator = registry.getIndicator("Fractal Dimension");
        Indicator secondIndicator = registry.getIndicator("Fractal Dimension");
        Overlay firstOverlay = registry.getOverlay("Sfora");
        Overlay secondOverlay = registry.getOverlay("Sfora");

        assertThat(firstIndicator).isNotSameAs(secondIndicator).isInstanceOf(DynamicStudyIndicator.class);
        assertThat(firstOverlay).isNotSameAs(secondOverlay).isInstanceOf(DynamicStudyOverlay.class);
    }
}
