/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.internal;

import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.Overlay;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChartPluginParameterUtilsTest {

    @Test
    void copiesAnnotatedFieldsAcrossPrivateAndInheritedParameters() {
        DummyIndicator source = new DummyIndicator("Source");
        source.length = 34;
        source.color = Color.BLUE;
        source.setPanelId(UUID.randomUUID());

        DummyIndicator copy = new DummyIndicator("Copy");
        ChartPluginParameterUtils.copyParameterValues(source, copy);

        assertThat(copy.length).isEqualTo(34);
        assertThat(copy.color).isEqualTo(Color.BLUE);
        assertThat(copy.getPanelId()).isEqualTo(source.getPanelId());
    }

    @Test
    void comparesParameterValuesByConcretePluginType() {
        DummyOverlay left = new DummyOverlay("Overlay");
        DummyOverlay right = new DummyOverlay("Overlay");

        assertThat(ChartPluginParameterUtils.haveSameParameterValues(left, right)).isTrue();

        right.visible = false;
        assertThat(ChartPluginParameterUtils.haveSameParameterValues(left, right)).isFalse();
        assertThat(ChartPluginParameterUtils.haveSameParameterValues(left, new DummyIndicator("Indicator"))).isFalse();
    }

    private static final class DummyIndicator extends Indicator {
        @Parameter(name = "Length")
        public int length = 14;
        @Parameter(name = "Line Color")
        public Color color = Color.RED;

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
            // no-op
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

    private static final class DummyOverlay extends Overlay {
        @Parameter(name = "Visible")
        public boolean visible = true;

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
            // no-op
        }

        @Override
        public boolean getMarkerVisibility() {
            return true;
        }
    }
}