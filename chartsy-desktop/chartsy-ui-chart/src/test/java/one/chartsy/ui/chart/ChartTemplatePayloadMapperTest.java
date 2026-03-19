/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.Symbol;
import one.chartsy.TimeFrame;
import one.chartsy.ui.chart.internal.ChartPluginParameterUtils;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChartTemplatePayloadMapperTest {

    @Test
    void toPluginSpecNormalizesSupportedParameterTypesAndSkipsPanelId() throws Exception {
        ChartTemplatePayloadMapper mapper = ChartTemplatePayloadMapper.getDefault();
        DummyOverlay overlay = new DummyOverlay("Dummy Overlay");
        overlay.visible = false;
        overlay.period = 34;
        overlay.threshold = 1.25;
        overlay.mode = DummyMode.SLOW;
        overlay.source = "hl2";
        overlay.color = Color.BLUE;
        overlay.stroke = BasicStrokes.DASHED;

        DummyIndicator indicator = new DummyIndicator("Dummy Indicator");
        indicator.visible = false;
        indicator.period = 55;
        indicator.threshold = 2.5;
        indicator.mode = DummyMode.FAST;
        indicator.source = "ohlc4";
        indicator.color = Color.GREEN;
        indicator.stroke = BasicStrokes.DOTTED;
        indicator.setPanelId(UUID.fromString("01234567-89ab-cdef-0123-456789abcdef"));

        StoredPluginSpec overlaySpec = invokeToPluginSpec(mapper, overlay);
        StoredPluginSpec indicatorSpec = invokeToPluginSpec(mapper, indicator);

        assertThat(overlaySpec.parametersView())
                .containsEntry("color", new StoredParameterValue("COLOR", "#0000FF"))
                .containsEntry("stroke", new StoredParameterValue("STROKE", "DASHED"))
                .containsEntry("visible", new StoredParameterValue("BOOLEAN", "false"))
                .containsEntry("period", new StoredParameterValue("INTEGER", "34"))
                .containsEntry("threshold", new StoredParameterValue("DOUBLE", "1.25"))
                .containsEntry("mode", new StoredParameterValue("ENUM", "SLOW"))
                .containsEntry("source", new StoredParameterValue("STRING", "hl2"));
        assertThat(indicatorSpec.parametersView()).containsEntry("color", new StoredParameterValue("COLOR", "#00FF00"));
        assertThat(indicatorSpec.parametersView()).containsEntry("stroke", new StoredParameterValue("STROKE", "DOTTED"));
        assertThat(indicatorSpec.parametersView()).doesNotContainKey("panelId");
    }

    @Test
    void roundTripBuiltInTemplatePreservesParametersAndRegeneratesIndicatorPanels() {
        ChartTemplatePayloadMapper mapper = ChartTemplatePayloadMapper.getDefault();
        ChartTemplate template = ChartTemplateDefaults.basicChartTemplate();

        StoredChartTemplatePayload payload = mapper.fromChartTemplate(template);
        ChartTemplate restored = mapper.toChartTemplate("Restored", payload);

        assertThat(restored.getOverlays()).hasSameSizeAs(template.getOverlays());
        assertThat(restored.getIndicators()).hasSameSizeAs(template.getIndicators());
        assertThat(payload.indicators()).allSatisfy(spec -> assertThat(spec.parametersView()).doesNotContainKey("panelId"));
        for (int i = 0; i < template.getOverlays().size(); i++) {
            assertThat(ChartPluginParameterUtils.haveSameParameterValues(
                    template.getOverlays().get(i),
                    restored.getOverlays().get(i))).isTrue();
        }
        for (int i = 0; i < template.getIndicators().size(); i++) {
            Indicator original = template.getIndicators().get(i);
            Indicator recreated = restored.getIndicators().get(i);
            assertThat(ChartPluginParameterUtils.haveSameParameterValues(original, recreated)).isTrue();
            assertThat(recreated.getPanelId()).isNotEqualTo(original.getPanelId());
        }
    }

    @Test
    void captureCurrentStudiesFallsBackToChartTemplateBeforeUiInitialization() {
        ChartFrame chartFrame = new ChartFrame();
        ChartData chartData = new ChartData();
        chartData.setSymbol(new Symbol("TEST", null));
        chartData.setTimeFrame(TimeFrame.Period.DAILY);
        chartFrame.setChartData(chartData);
        chartFrame.setChartTemplate(ChartTemplateDefaults.basicChartTemplate());

        StoredChartTemplatePayload payload = ChartTemplatePayloadMapper.getDefault().captureCurrentStudies(chartFrame);

        assertThat(payload.overlays()).isNotEmpty();
        assertThat(payload.indicators()).isNotEmpty();
    }

    private static StoredPluginSpec invokeToPluginSpec(ChartTemplatePayloadMapper mapper, ChartPlugin<?> plugin) throws Exception {
        Method method = ChartTemplatePayloadMapper.class.getDeclaredMethod("toPluginSpec", ChartPlugin.class);
        method.setAccessible(true);
        return (StoredPluginSpec) method.invoke(mapper, plugin);
    }

    private enum DummyMode {
        FAST,
        SLOW
    }

    private static final class DummyOverlay extends Overlay {
        @Parameter(name = "Color")
        public Color color = Color.RED;
        @Parameter(name = "Stroke")
        public java.awt.Stroke stroke = BasicStrokes.SOLID;
        @Parameter(name = "Visible")
        public boolean visible = true;
        @Parameter(name = "Period")
        public int period = 20;
        @Parameter(name = "Threshold")
        public double threshold = 1.0;
        @Parameter(name = "Mode")
        public DummyMode mode = DummyMode.FAST;
        @Parameter(name = "Source")
        public String source = "close";

        private DummyOverlay(String name) {
            super(name);
        }

        @Override
        public String getLabel() {
            return getName();
        }

        @Override
        public DummyOverlay newInstance() {
            return new DummyOverlay(getName());
        }

        @Override
        public void calculate() {
        }

        @Override
        public boolean getMarkerVisibility() {
            return visible;
        }
    }

    private static final class DummyIndicator extends Indicator {
        @Parameter(name = "Color")
        public Color color = Color.RED;
        @Parameter(name = "Stroke")
        public java.awt.Stroke stroke = BasicStrokes.SOLID;
        @Parameter(name = "Visible")
        public boolean visible = true;
        @Parameter(name = "Period")
        public int period = 20;
        @Parameter(name = "Threshold")
        public double threshold = 1.0;
        @Parameter(name = "Mode")
        public DummyMode mode = DummyMode.FAST;
        @Parameter(name = "Source")
        public String source = "close";

        private DummyIndicator(String name) {
            super(name);
        }

        @Override
        public String getLabel() {
            return getName();
        }

        @Override
        public DummyIndicator newInstance() {
            return new DummyIndicator(getName());
        }

        @Override
        public void calculate() {
        }

        @Override
        public boolean getMarkerVisibility() {
            return visible;
        }

        @Override
        public double[] getStepValues(ChartContext cf) {
            return new double[0];
        }
    }
}
