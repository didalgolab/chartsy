/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.financial.SymbolIdentifier;
import one.chartsy.TimeFrame;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;

import static org.assertj.core.api.Assertions.assertThat;

class ChartFrameTemplateStateTest {

    @Test
    void applyLoadedTemplateStartsCleanAndTracksStudyDivergence() {
        ChartTemplate baseTemplate = createTemplate("Workspace Default", 1, 1);
        StoredChartTemplatePayload payload = ChartTemplatePayloadMapper.getDefault().fromChartTemplate(baseTemplate);
        ChartTemplateSummary summary = new ChartTemplateSummary(
                java.util.UUID.fromString("c6a004f1-8d15-44ae-a5df-fbf7f6701ec8"),
                "Workspace Default",
                true,
                false);

        ChartFrame chartFrame = new ChartFrame();
        ChartData chartData = new ChartData();
        chartData.setSymbol(new SymbolIdentifier("TEST"));
        chartData.setTimeFrame(TimeFrame.Period.DAILY);
        chartData.setChart(baseTemplate.getChart());
        chartFrame.setChartData(chartData);

        chartFrame.applyLoadedTemplate(new ChartTemplateCatalog.LoadedTemplate(summary, baseTemplate, payload));

        assertThat(chartFrame.getAppliedChartTemplate()).isNotNull();
        assertThat(chartFrame.getAppliedTemplatePayload()).isEqualTo(payload);
        assertThat(chartFrame.isTemplateDirty()).isFalse();

        ChartTemplate modifiedTemplate = createTemplate("Workspace Default", 2, 1);
        chartFrame.setChartTemplate(modifiedTemplate);

        assertThat(chartFrame.captureCurrentStudyPayload()).isNotEqualTo(payload);
        assertThat(chartFrame.isTemplateDirty()).isTrue();
    }

    private static ChartTemplate createTemplate(String name, int overlayCount, int indicatorCount) {
        ChartTemplate template = new ChartTemplate(name);
        template.setChart(new TestChart("Test Chart"));
        for (int i = 0; i < overlayCount; i++)
            template.addOverlay(new DummyOverlay("Overlay " + (i + 1)));
        for (int i = 0; i < indicatorCount; i++)
            template.addIndicator(new DummyIndicator("Indicator " + (i + 1)));
        return template;
    }

    private record TestChart(String name) implements Chart {
        @Override
        public String getName() {
            return name;
        }

        @Override
        public void paint(Graphics2D g, ChartContext cf, int width, int height) {
        }
    }

    private static final class DummyOverlay extends Overlay {
        private DummyOverlay(String name) {
            super(name);
        }

        @Override
        public String getLabel() {
            return getName();
        }

        @Override
        public Overlay newInstance() {
            return new DummyOverlay(getName());
        }

        @Override
        public void calculate() {
        }

        @Override
        public boolean getMarkerVisibility() {
            return true;
        }
    }

    private static final class DummyIndicator extends Indicator {
        private DummyIndicator(String name) {
            super(name);
        }

        @Override
        public String getLabel() {
            return getName();
        }

        @Override
        public Indicator newInstance() {
            return new DummyIndicator(getName());
        }

        @Override
        public void calculate() {
        }

        @Override
        public boolean getMarkerVisibility() {
            return true;
        }

        @Override
        public double[] getStepValues(ChartContext cf) {
            return new double[0];
        }
    }
}
