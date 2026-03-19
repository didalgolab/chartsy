/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui;

import one.chartsy.TimeFrame;
import one.chartsy.financial.SymbolIdentifier;
import one.chartsy.ui.chart.Chart;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.ChartData;
import one.chartsy.ui.chart.ChartFrame;
import one.chartsy.ui.chart.ChartTemplate;
import one.chartsy.ui.chart.ChartTemplateSummary;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.Overlay;
import one.chartsy.ui.chart.StoredChartTemplatePayload;
import org.junit.jupiter.api.Test;
import org.openide.windows.TopComponent;

import java.awt.Graphics2D;
import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChartTopComponentCloneTest {

    @Test
    void cloneComponentUsesVisibleSnapshotAndOriginalAppliedPayload() throws Exception {
        ChartTemplate appliedTemplate = createTemplate("Applied", 1, 1);
        ChartTemplate visibleTemplate = createTemplate("Visible", 2, 1);
        ChartFrame payloadFrame = new ChartFrame();
        payloadFrame.setChartTemplate(appliedTemplate);
        StoredChartTemplatePayload appliedPayload = payloadFrame.captureCurrentStudyPayload();
        ChartTemplateSummary summary = new ChartTemplateSummary(UUID.randomUUID(), "Applied", true, false);

        TrackingChartFrame source = new TrackingChartFrame(visibleTemplate, appliedPayload);
        ChartData chartData = new ChartData();
        chartData.setSymbol(new SymbolIdentifier("TEST"));
        chartData.setTimeFrame(TimeFrame.Period.DAILY);
        chartData.setChart(appliedTemplate.getChart());
        source.setChartData(chartData);
        source.setChartTemplate(appliedTemplate);
        source.setAppliedChartTemplate(summary, appliedPayload);

        TopComponent cloneComponent = new ChartTopComponent(source).cloneComponent();
        ChartFrame clonedChart = extractChartFrame((ChartTopComponent) cloneComponent);

        assertThat(clonedChart.getChartTemplate()).isSameAs(visibleTemplate);
        assertThat(clonedChart.getAppliedTemplatePayload()).isEqualTo(appliedPayload);
        assertThat(clonedChart.isTemplateDirty()).isTrue();
        assertThat(source.snapshotRequested).isTrue();
    }

    private static ChartFrame extractChartFrame(ChartTopComponent topComponent) throws Exception {
        Field chartField = ChartTopComponent.class.getDeclaredField("chart");
        chartField.setAccessible(true);
        return (ChartFrame) chartField.get(topComponent);
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

    private static final class TrackingChartFrame extends ChartFrame {
        private final ChartTemplate visibleTemplate;
        private final StoredChartTemplatePayload appliedPayload;
        private boolean snapshotRequested;

        private TrackingChartFrame(ChartTemplate visibleTemplate, StoredChartTemplatePayload appliedPayload) {
            this.visibleTemplate = visibleTemplate;
            this.appliedPayload = appliedPayload;
        }

        @Override
        public ChartTemplate snapshotVisibleTemplate(String templateName) {
            snapshotRequested = true;
            return visibleTemplate;
        }

        @Override
        public StoredChartTemplatePayload getAppliedTemplatePayload() {
            return appliedPayload;
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
        public double[] getStepValues(one.chartsy.ui.chart.ChartContext cf) {
            return new double[0];
        }
    }
}
