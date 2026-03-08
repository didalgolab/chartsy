/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import one.chartsy.ui.chart.BasicStrokes;
import one.chartsy.ui.chart.Overlay;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Stroke;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

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
}