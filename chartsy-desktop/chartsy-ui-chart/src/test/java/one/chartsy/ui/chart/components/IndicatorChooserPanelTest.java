/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import one.chartsy.ui.chart.BasicStrokes;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.Overlay;
import one.chartsy.study.StudyAxisDescriptor;
import one.chartsy.study.StudyPresentationPlan;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Stroke;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IndicatorChooserPanelTest {

    @Test
    void refreshVisualSummaryRebuildsRowsFromCurrentPluginState() throws Exception {
        IndicatorChooserPanel panel = new IndicatorChooserPanel();
        DummyOverlay selectedOverlay = new DummyOverlay("Bands");

        SwingUtilities.invokeAndWait(() -> panel.initForm(List.of(), List.of(), List.of(selectedOverlay), List.of(selectedOverlay)));

        DummyOverlay editableOverlay = (DummyOverlay) panel.getSelectedOverlays().get(0);
        JTable table = getField(panel, "plotObjectTable", JTable.class);
        assertThat(table.getValueAt(0, 7)).isEqualTo(Color.RED);
        assertThat(table.getValueAt(0, 4)).isEqualTo(Boolean.TRUE);

        editableOverlay.color = Color.BLUE;
        editableOverlay.visible = false;
        invokeNoArg(panel, "refreshVisualSummary");

        assertThat(table.getValueAt(0, 7)).isEqualTo(Color.BLUE);
        assertThat(table.getValueAt(0, 4)).isEqualTo(Boolean.FALSE);
    }

    @Test
    void paneSelectorHidesIncompatiblePanesUntilForceCombineIsEnabled() throws Exception {
        IndicatorChooserPanel panel = new IndicatorChooserPanel();
        DummyIndicator left = new DummyIndicator("Left", new StudyAxisDescriptor(Double.NaN, Double.NaN, false, true, new double[]{20, 40}));
        DummyIndicator right = new DummyIndicator("Right", new StudyAxisDescriptor(Double.NaN, Double.NaN, true, true, new double[]{20, 40}));
        left.setPanelId(UUID.fromString("00000000-0000-0000-0000-000000000101"));
        right.setPanelId(UUID.fromString("00000000-0000-0000-0000-000000000202"));

        SwingUtilities.invokeAndWait(() -> panel.initForm(List.of(left, right), List.of(left, right), List.of(), List.of()));

        @SuppressWarnings("unchecked")
        JComboBox<Object> paneSelector = getField(panel, "paneSelector", JComboBox.class);
        JCheckBox forceCombine = getField(panel, "forceCombineCheckBox", JCheckBox.class);

        assertThat(paneSelector.getItemCount()).isEqualTo(1);

        SwingUtilities.invokeAndWait(forceCombine::doClick);

        assertThat(paneSelector.getItemCount()).isEqualTo(2);
        assertThat(paneSelector.getItemAt(1).toString()).contains("Force combine");
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) type.cast(field.get(target));
    }

    private static void invokeNoArg(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try {
                method.invoke(target);
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static final class DummyOverlay extends Overlay {
        @Parameter(name = "Color")
        public Color color = Color.RED;
        @Parameter(name = "Stroke")
        public Stroke stroke = BasicStrokes.SOLID;
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
            return visible;
        }
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
        public double[] getStepValues(one.chartsy.ui.chart.ChartContext cf) {
            return axis.steps();
        }
    }
}
