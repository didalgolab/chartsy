/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.study.StudyPlotDescriptor;
import one.chartsy.ui.chart.internal.ChartPluginParameterUtils;
import one.chartsy.ui.chart.internal.ChartPlotRouting;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChartTemplatePayloadMapperTest {

    @Test
    void roundTrip_preserves_implicit_plot_visibility() {
        Indicator cmo = StudyRegistry.getDefault().getIndicator("Chande Momentum Oscillator");
        String visibilityParameter = StudyPlotDescriptor.visibilityParameterId("cmo");
        String panelParameter = StudyPlotDescriptor.panelParameterId("cmo");
        ChartPluginParameterUtils.getParameters(cmo).stream()
                .filter(parameter -> parameter.id().equals(visibilityParameter))
                .findFirst()
                .orElseThrow()
                .setValue(Boolean.FALSE);
        ChartPlotRouting.setPanelId(cmo, "cmo", 3);
        ChartTemplate source = new ChartTemplate("Source");
        source.addIndicator(cmo);

        StoredChartTemplatePayload payload = ChartTemplatePayloadMapper.getDefault().fromChartTemplate(source);
        ChartTemplate restored = ChartTemplatePayloadMapper.getDefault().toChartTemplate("Restored", payload);

        StudyBackedChartPlugin restoredCmo = (StudyBackedChartPlugin) restored.getIndicators().getFirst();
        assertThat(restoredCmo.getStudyParameterValues())
                .containsEntry(visibilityParameter, Boolean.FALSE)
                .containsEntry(panelParameter, 3);
    }
}
