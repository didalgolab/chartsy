/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.study;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudyDescriptorTest {

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
                        "missingVisibility",
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
}
