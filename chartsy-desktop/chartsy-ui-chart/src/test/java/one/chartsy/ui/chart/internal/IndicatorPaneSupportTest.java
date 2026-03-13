/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.internal;

import one.chartsy.study.StudyAxisDescriptor;
import one.chartsy.study.StudyPresentationPlan;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.Indicator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IndicatorPaneSupportTest {

    @Test
    void groupByPanePreservesFirstAppearanceOrderAndMembership() {
        DummyIndicator first = new DummyIndicator("First", axis(false, new double[]{20, 40}, Double.NaN, Double.NaN));
        DummyIndicator second = new DummyIndicator("Second", axis(false, new double[]{20, 40}, Double.NaN, Double.NaN));
        DummyIndicator third = new DummyIndicator("Third", axis(false, new double[]{20, 40}, Double.NaN, Double.NaN));

        UUID paneA = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID paneB = UUID.fromString("00000000-0000-0000-0000-000000000022");
        first.setPanelId(paneA);
        second.setPanelId(paneB);
        third.setPanelId(paneA);

        List<IndicatorPaneSupport.PaneGroup> panes = IndicatorPaneSupport.groupByPane(List.of(first, second, third));

        assertThat(panes).hasSize(2);
        assertThat(panes.get(0).id()).isEqualTo(paneA);
        assertThat(panes.get(0).indicators()).containsExactly(first, third);
        assertThat(panes.get(1).id()).isEqualTo(paneB);
        assertThat(panes.get(1).indicators()).containsExactly(second);
    }

    @Test
    void compatibilityRejectsLogarithmicStepAndFixedRangeMismatches() {
        DummyIndicator base = new DummyIndicator("Base", axis(false, new double[]{20, 40}, 0.0, 100.0));
        DummyIndicator matching = new DummyIndicator("Matching", axis(false, new double[]{20, 40}, 0.0, 100.0));
        DummyIndicator logMismatch = new DummyIndicator("Log", axis(true, new double[]{20, 40}, 0.0, 100.0));
        DummyIndicator stepMismatch = new DummyIndicator("Steps", axis(false, new double[]{10, 30}, 0.0, 100.0));
        DummyIndicator rangeMismatch = new DummyIndicator("Range", axis(false, new double[]{20, 40}, 10.0, 100.0));

        assertThat(IndicatorPaneSupport.compatibility(base, List.of(matching)))
                .isEqualTo(IndicatorPaneSupport.Compatibility.COMPATIBLE);
        assertThat(IndicatorPaneSupport.compatibility(base, List.of(logMismatch)))
                .isEqualTo(IndicatorPaneSupport.Compatibility.INCOMPATIBLE);
        assertThat(IndicatorPaneSupport.compatibility(base, List.of(stepMismatch)))
                .isEqualTo(IndicatorPaneSupport.Compatibility.INCOMPATIBLE);
        assertThat(IndicatorPaneSupport.compatibility(base, List.of(rangeMismatch)))
                .isEqualTo(IndicatorPaneSupport.Compatibility.INCOMPATIBLE);
    }

    private static StudyAxisDescriptor axis(boolean logarithmic, double[] steps, double min, double max) {
        return new StudyAxisDescriptor(min, max, logarithmic, true, steps);
    }

    private static final class DummyIndicator extends Indicator {
        private final String label;
        private final StudyAxisDescriptor axis;

        private DummyIndicator(String label, StudyAxisDescriptor axis) {
            super(label);
            this.label = label;
            this.axis = axis;
            setPresentationPlan(StudyPresentationPlan.empty(axis));
        }

        @Override
        public String getLabel() {
            return label;
        }

        @Override
        public DummyIndicator newInstance() {
            DummyIndicator copy = new DummyIndicator(label, axis);
            copy.setPanelId(getPanelId());
            return copy;
        }

        @Override
        public void calculate() {
            setPresentationPlan(StudyPresentationPlan.empty(axis));
        }

        @Override
        public boolean getMarkerVisibility() {
            return false;
        }

        @Override
        public double[] getStepValues(ChartContext cf) {
            return axis.steps();
        }
    }
}
