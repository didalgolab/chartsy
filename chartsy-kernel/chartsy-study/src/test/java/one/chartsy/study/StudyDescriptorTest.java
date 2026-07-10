/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.study;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudyDescriptorTest {

    @Test
    void addsVisibilityParameter_for_every_plot() {
        LinkedHashMap<String, StudyParameterDescriptor> parameters = new LinkedHashMap<>();
        parameters.put("color", parameter("color", StudyParameterType.COLOR));

        StudyDescriptor descriptor = descriptor(
                parameters,
                List.of(horizontalPlot("baseline", false)),
                StudyPresentationBuilder.class);

        StudyPlotDescriptor plot = descriptor.plots().getFirst();
        String visibilityParameter = StudyPlotDescriptor.visibilityParameterId("baseline");
        assertThat(plot.visibilityParameterId()).isEqualTo(visibilityParameter);
        assertThat(descriptor.parameter(visibilityParameter))
                .extracting(StudyParameterDescriptor::effectiveValueType,
                        StudyParameterDescriptor::defaultValue)
                .containsExactly(Boolean.class, "false");
    }

    @Test
    void addsAggregateResultVisibility_for_custom_builders_without_declared_plots() {
        StudyDescriptor descriptor = descriptor(
                new LinkedHashMap<>(),
                List.of(),
                CustomBuilder.class);

        assertThat(descriptor.parameter(StudyPlotDescriptor.RESULT_VISIBILITY_PARAMETER_ID))
                .extracting(StudyParameterDescriptor::effectiveValueType,
                        StudyParameterDescriptor::defaultValue)
                .containsExactly(Boolean.class, "true");
    }

    @Test
    void rejectsFactoryParameterIdsThatAreNotDeclared() {
        assertThatThrownBy(() -> new StudyDescriptor(
                "study",
                "Study",
                "Study",
                "Category",
                StudyKind.INDICATOR,
                StudyPlacement.OWN_PANEL,
                Object.class,
                Object.class,
                new StudyFactoryDescriptor(StudyInputKind.CANDLES, "", StudyFactoryTarget.CONSTRUCTOR, "study", List.of("periods")),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                new StudyAxisDescriptor(),
                List.of(),
                StudyPresentationBuilder.class
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Factory parameter `periods`");
    }

    @Test
    void rejectsPriceFieldInputParameterThatIsNotDeclared() {
        LinkedHashMap<String, StudyParameterDescriptor> parameters = new LinkedHashMap<>();
        parameters.put("periods", parameter("periods", StudyParameterType.INTEGER));

        assertThatThrownBy(() -> new StudyDescriptor(
                "study",
                "Study",
                "Study",
                "Category",
                StudyKind.INDICATOR,
                StudyPlacement.OWN_PANEL,
                Object.class,
                Object.class,
                new StudyFactoryDescriptor(StudyInputKind.PRICE_FIELD, "priceBase", StudyFactoryTarget.CONSTRUCTOR, "study", List.of("periods")),
                parameters,
                new LinkedHashMap<>(),
                new StudyAxisDescriptor(),
                List.of(),
                StudyPresentationBuilder.class
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Input parameter `priceBase`");
    }

    @Test
    void rejectsPlotsThatReferenceUnknownOutputsOrParameters() {
        LinkedHashMap<String, StudyParameterDescriptor> parameters = new LinkedHashMap<>();
        parameters.put("color", parameter("color", StudyParameterType.COLOR));

        assertThatThrownBy(() -> new StudyDescriptor(
                "study",
                "Study",
                "Study",
                "Category",
                StudyKind.INDICATOR,
                StudyPlacement.OWN_PANEL,
                Object.class,
                Object.class,
                new StudyFactoryDescriptor(StudyInputKind.CANDLES, "", StudyFactoryTarget.CONSTRUCTOR, "study", List.of()),
                parameters,
                new LinkedHashMap<>(),
                new StudyAxisDescriptor(),
                List.of(new StudyPlotDescriptor(
                        "plot",
                        "Plot",
                        10,
                        StudyPlotType.LINE,
                        "missingOutput",
                        "",
                        Double.NaN,
                        Double.NaN,
                        true,
                        "color",
                        "",
                        "",
                        true,
                        StudyMarkerType.NONE
                )),
                StudyPresentationBuilder.class
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown plot output");
    }

    private static StudyParameterDescriptor parameter(String id, StudyParameterType type) {
        return new StudyParameterDescriptor(
                id,
                id,
                "",
                StudyParameterScope.COMPUTATION,
                type,
                switch (type) {
                    case COLOR -> StudyColor.class;
                    case INTEGER -> Integer.class;
                    default -> String.class;
                },
                Void.class,
                "",
                10,
                StudyStereotype.NONE
        );
    }

    private static StudyDescriptor descriptor(
            LinkedHashMap<String, StudyParameterDescriptor> parameters,
            List<StudyPlotDescriptor> plots,
            Class<? extends StudyPresentationBuilder> builderType) {
        return new StudyDescriptor(
                "study",
                "Study",
                "Study",
                "Category",
                StudyKind.INDICATOR,
                StudyPlacement.OWN_PANEL,
                Object.class,
                Object.class,
                new StudyFactoryDescriptor(
                        StudyInputKind.CANDLES, "", StudyFactoryTarget.CONSTRUCTOR, "study", List.of()),
                parameters,
                new LinkedHashMap<>(),
                new StudyAxisDescriptor(),
                plots,
                builderType);
    }

    private static StudyPlotDescriptor horizontalPlot(String id, boolean visibleByDefault) {
        return new StudyPlotDescriptor(
                id,
                id,
                10,
                StudyPlotType.HORIZONTAL_LINE,
                "",
                "",
                0.0,
                Double.NaN,
                true,
                "",
                "",
                "",
                visibleByDefault,
                StudyMarkerType.NONE);
    }

    private static final class CustomBuilder implements StudyPresentationBuilder {
    }
}
