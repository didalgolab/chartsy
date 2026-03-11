/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.Symbol;
import one.chartsy.TimeFrame;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChartFrameTemplateStateTest {

    @Test
    void applyLoadedTemplateStartsCleanAndTracksTemplateDivergence() {
        ChartTemplate baseTemplate = ChartTemplateDefaults.basicChartTemplate();
        StoredChartTemplatePayload payload = ChartTemplatePayloadMapper.getDefault().fromChartTemplate(baseTemplate);
        ChartTemplateSummary summary = new ChartTemplateSummary(
                java.util.UUID.fromString("c6a004f1-8d15-44ae-a5df-fbf7f6701ec8"),
                "Workspace Default",
                true,
                false);

        ChartFrame chartFrame = new ChartFrame();
        ChartData chartData = new ChartData();
        chartData.setSymbol(new Symbol("TEST", null));
        chartData.setTimeFrame(TimeFrame.Period.DAILY);
        chartFrame.setChartData(chartData);

        chartFrame.applyLoadedTemplate(new ChartTemplateCatalog.LoadedTemplate(summary, baseTemplate, payload));

        assertThat(chartFrame.getAppliedChartTemplate()).isNotNull();
        assertThat(chartFrame.getAppliedChartTemplate().name()).isEqualTo("Workspace Default");
        assertThat(chartFrame.isTemplateDirty()).isFalse();

        ChartTemplate modifiedTemplate = ChartTemplateDefaults.basicChartTemplate();
        modifiedTemplate.addOverlay(modifiedTemplate.getOverlays().getFirst().newInstance());
        chartFrame.setChartTemplate(modifiedTemplate);

        assertThat(chartFrame.isTemplateDirty()).isTrue();

        chartFrame.revertToAppliedTemplate();

        assertThat(chartFrame.isTemplateDirty()).isFalse();
    }
}
