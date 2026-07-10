/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.financial.indicators.FramaTrendWhispers;
import one.chartsy.study.StudyDescriptor;
import one.chartsy.study.StudyPlotDescriptor;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

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

    @Test
    void everyRegisteredPlotHasUniqueBooleanVisibilityConfiguration() {
        for (StudyDescriptor descriptor : StudyRegistry.getDefault().getStudyDescriptors()) {
            var visibilityParameters = new HashSet<String>();
            for (StudyPlotDescriptor plot : descriptor.plots()) {
                assertThat(plot.visibilityParameterId()).isNotBlank();
                assertThat(visibilityParameters.add(plot.visibilityParameterId()))
                        .as("unique visibility for %s/%s", descriptor.name(), plot.label())
                        .isTrue();
                assertThat(descriptor.parameter(plot.visibilityParameterId()).effectiveValueType())
                        .as("boolean visibility for %s/%s", descriptor.name(), plot.label())
                        .isEqualTo(Boolean.class);
                assertThat(descriptor.parameter(plot.visibilityParameterId()).defaultValue())
                        .as("visibility default for %s/%s", descriptor.name(), plot.label())
                        .isEqualTo(Boolean.toString(plot.visibleByDefault()));
                assertThat(descriptor.parameter(plot.panelParameterId()).effectiveValueType())
                        .as("integer panel for %s/%s", descriptor.name(), plot.label())
                        .isEqualTo(Integer.class);
                assertThat(descriptor.parameter(plot.panelParameterId()).defaultValue())
                        .as("panel default for %s/%s", descriptor.name(), plot.label())
                        .isEqualTo(Integer.toString(StudyPlotDescriptor.INHERITED_PANEL_ID));
            }
            if (descriptor.hasCustomBuilder() && descriptor.plots().isEmpty()) {
                assertThat(descriptor.parameter(StudyPlotDescriptor.RESULT_VISIBILITY_PARAMETER_ID)
                        .effectiveValueType())
                        .as("aggregate visibility for %s", descriptor.name())
                        .isEqualTo(Boolean.class);
                assertThat(descriptor.parameter(StudyPlotDescriptor.RESULT_PANEL_PARAMETER_ID)
                        .effectiveValueType())
                        .as("aggregate panel for %s", descriptor.name())
                        .isEqualTo(Integer.class);
            }
        }
    }
}
