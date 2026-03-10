/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.internal;

import one.chartsy.CandleField;
import one.chartsy.ui.chart.DynamicStudyIndicator;
import one.chartsy.ui.chart.StudyRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudyBackedChartPluginParameterUtilsTest {

    @Test
    void copiesAndComparesStudyBackedIndicatorParametersByDescriptorId() {
        DynamicStudyIndicator source = (DynamicStudyIndicator) StudyRegistry.getDefault().getIndicator("Fractal Dimension").newInstance();
        DynamicStudyIndicator target = (DynamicStudyIndicator) StudyRegistry.getDefault().getIndicator("Fractal Dimension").newInstance();

        parameter(source, "priceBase").setValue(CandleField.OPEN);
        parameter(source, "periods").setValue(42);
        parameter(source, "markerVisibility").setValue(Boolean.TRUE);

        assertThat(ChartPluginParameterUtils.haveSameParameterValues(source, target)).isFalse();

        ChartPluginParameterUtils.copyParameterValues(source, target);

        assertThat(ChartPluginParameterUtils.haveSameParameterValues(source, target)).isTrue();
        assertThat(target.getLabel()).isEqualTo("Fractal Dimension (OPEN, 42)");
    }

    @Test
    void rejectsStudyBackedIndicatorsWithDifferentDescriptorIds() {
        DynamicStudyIndicator fractalDimension = (DynamicStudyIndicator) StudyRegistry.getDefault().getIndicator("Fractal Dimension").newInstance();
        DynamicStudyIndicator medianRange = (DynamicStudyIndicator) StudyRegistry.getDefault().getIndicator("Median Range").newInstance();

        assertThat(ChartPluginParameterUtils.haveSameParameterValues(fractalDimension, medianRange)).isFalse();
    }

    private static ChartPluginParameter parameter(DynamicStudyIndicator indicator, String id) {
        return ChartPluginParameterUtils.getParameters(indicator).stream()
                .filter(parameter -> parameter.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
