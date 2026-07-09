/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChartTemplatePayloadMapperTest {

    @Test
    void toChartTemplate_migrates_legacy_fractal_inside_visibility() {
        LinkedHashMap<String, StoredParameterValue> parameters = new LinkedHashMap<>();
        parameters.put("insideVisibility", new StoredParameterValue("BOOLEAN", "false"));
        StoredPluginSpec fractalDimension = new StoredPluginSpec(
                "one.chartsy.financial.indicators.FractalDimension",
                DynamicStudyIndicator.class.getName(),
                "Fractal Dimension",
                parameters);
        StoredChartTemplatePayload payload = new StoredChartTemplatePayload(List.of(), List.of(fractalDimension));

        ChartTemplate template = ChartTemplatePayloadMapper.getDefault().toChartTemplate("Legacy", payload);

        assertThat(template.getIndicators()).hasSize(1);
        StudyBackedChartPlugin indicator = (StudyBackedChartPlugin) template.getIndicators().getFirst();
        assertThat(indicator.getStudyParameterValues())
                .containsEntry("insideNeutralVisibility", Boolean.FALSE)
                .containsEntry("insideHighVisibility", Boolean.FALSE);
    }
}
