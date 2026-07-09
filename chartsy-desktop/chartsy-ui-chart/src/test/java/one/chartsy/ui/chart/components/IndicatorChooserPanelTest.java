/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.ui.chart.BasicStrokes;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.DynamicStudyIndicator;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.Overlay;
import one.chartsy.ui.chart.StudyBackedChartPlugin;
import one.chartsy.ui.chart.StudyRegistry;
import one.chartsy.study.StudyAxisDescriptor;
import one.chartsy.study.StudyPresentationPlan;
import org.netbeans.swing.outline.Outline;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.Color;
import java.awt.Stroke;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import static org.assertj.core.api.Assertions.assertThat;

class IndicatorChooserPanelTest {

    @Test
    void plotObjects_groups_visuals_under_expandable_studies() throws Exception {
        OverlayFixture fixture = createOverlayFixture();
        JTable table = fixture.table();
        assertThat(table).isInstanceOf(Outline.class);
        assertThat(table.getColumnName(0)).isEqualTo("Plot Object");
        assertThat(table.getRowCount()).isEqualTo(2);
        assertThat(table.getValueAt(0, 0).toString()).isEqualTo("Bands");
        assertThat(table.getValueAt(0, 1)).isEqualTo("Overlay");
        assertThat(table.getValueAt(0, 2)).isEqualTo("Main chart");
        assertThat(table.getValueAt(1, 0).toString()).isEqualTo("Result");
        assertThat(table.getValueAt(1, 3)).isEqualTo(Boolean.TRUE);
        assertThat(table.getValueAt(1, 6)).isEqualTo(Color.RED);
    }

    @Test
    void visibleCheckbox_updates_legacy_plugin_parameter() throws Exception {
        OverlayFixture fixture = createOverlayFixture();
        JTable table = fixture.table();
        assertThat(table.isCellEditable(0, 3)).isFalse();
        assertThat(table.isCellEditable(1, 3)).isTrue();

        assertThat(callOnEdt(() -> table.editCellAt(1, 3))).isTrue();
        assertThat(table.getEditorComponent()).isInstanceOf(JCheckBox.class);
        runOnEdt(() -> table.getCellEditor().cancelCellEditing());

        runOnEdt(() -> table.setValueAt(Boolean.FALSE, 1, 3));

        assertThat(fixture.overlay().visible).isFalse();
        assertThat(table.getValueAt(1, 3)).isEqualTo(Boolean.FALSE);
    }

    @Test
    void refreshVisualSummary_preserves_collapsed_study_rows() throws Exception {
        OverlayFixture fixture = createOverlayFixture();
        JTable table = fixture.table();

        Outline outline = (Outline) table;
        DefaultMutableTreeNode studyNode = (DefaultMutableTreeNode) table.getValueAt(0, 0);
        runOnEdt(() -> outline.collapsePath(new TreePath(studyNode.getPath())));
        assertThat(table.getRowCount()).isEqualTo(1);

        invokeNoArg(fixture.panel(), "refreshVisualSummary");

        assertThat(table.getRowCount()).isEqualTo(1);
    }

    @Test
    void visibleCheckbox_updates_each_study_plot_independently() throws Exception {
        IndicatorChooserPanel panel = createPanel();
        Indicator fractalDimension = StudyRegistry.getDefault().getIndicator("Fractal Dimension");

        runOnEdt(() -> panel.initForm(
                List.of(fractalDimension), List.of(fractalDimension), List.of(), List.of()));

        JTable table = getField(panel, "plotObjectTable", JTable.class);
        int neutralRow = findRow(table, "Inside Neutral");
        int highRow = findRow(table, "Inside High");
        int fdiRow = findRow(table, "FDI");
        assertThat(table.isCellEditable(neutralRow, 3)).isTrue();
        assertThat(table.isCellEditable(highRow, 3)).isTrue();
        assertThat(table.isCellEditable(fdiRow, 3)).isFalse();

        runOnEdt(() -> table.setValueAt(Boolean.FALSE, neutralRow, 3));

        StudyBackedChartPlugin selectedPlugin = (StudyBackedChartPlugin) panel.getSelectedIndicators().get(0);
        assertThat(selectedPlugin.getStudyParameterValues().get("insideNeutralVisibility")).isEqualTo(Boolean.FALSE);
        assertThat(selectedPlugin.getStudyParameterValues().get("insideHighVisibility")).isEqualTo(Boolean.TRUE);
        assertThat(table.getValueAt(neutralRow, 3)).isEqualTo(Boolean.FALSE);
        assertThat(table.getValueAt(highRow, 3)).isEqualTo(Boolean.TRUE);
    }

    @Test
    void getSelection_reenables_previously_applied_study_plots() throws Exception {
        IndicatorChooserPanel panel = createPanel();
        Indicator fractalDimension = StudyRegistry.getDefault().getIndicator("Fractal Dimension");
        runOnEdt(() -> panel.initForm(
                List.of(fractalDimension), List.of(fractalDimension), List.of(), List.of()));

        JTable table = getField(panel, "plotObjectTable", JTable.class);
        int neutralRow = findRow(table, "Inside Neutral");
        int highRow = findRow(table, "Inside High");
        runOnEdt(() -> {
            table.setValueAt(Boolean.FALSE, neutralRow, 3);
            table.setValueAt(Boolean.FALSE, highRow, 3);
        });
        DynamicStudyIndicator hiddenSnapshot = (DynamicStudyIndicator) panel.getSelectedIndicators().getFirst();

        runOnEdt(() -> {
            table.setValueAt(Boolean.TRUE, neutralRow, 3);
            table.setValueAt(Boolean.TRUE, highRow, 3);
        });
        DynamicStudyIndicator visibleSnapshot = (DynamicStudyIndicator) panel.getSelectedIndicators().getFirst();

        assertThat(visibleSnapshot).isNotSameAs(hiddenSnapshot);
        CandleSeries dataset = sampleDataset(40);
        hiddenSnapshot.setDataset(dataset);
        hiddenSnapshot.calculate();
        visibleSnapshot.setDataset(dataset);
        visibleSnapshot.calculate();
        assertThat(hiddenSnapshot.getPlots()).doesNotContainKeys("Inside Neutral", "Inside High");
        assertThat(visibleSnapshot.getPlots()).containsKeys("Inside Neutral", "Inside High");
    }

    @Test
    void refreshVisualSummary_rebuilds_rows_from_current_plugin_state() throws Exception {
        OverlayFixture fixture = createOverlayFixture();
        JTable table = fixture.table();
        assertThat(table.getValueAt(1, 6)).isEqualTo(Color.RED);
        assertThat(table.getValueAt(1, 3)).isEqualTo(Boolean.TRUE);

        fixture.overlay().color = Color.BLUE;
        fixture.overlay().visible = false;
        invokeNoArg(fixture.panel(), "refreshVisualSummary");

        assertThat(table.getValueAt(1, 6)).isEqualTo(Color.BLUE);
        assertThat(table.getValueAt(1, 3)).isEqualTo(Boolean.FALSE);
    }

    @Test
    void paneSelector_hides_incompatible_panes_until_force_combine_is_enabled() throws Exception {
        IndicatorChooserPanel panel = createPanel();
        DummyIndicator left = new DummyIndicator("Left", new StudyAxisDescriptor(Double.NaN, Double.NaN, false, true, new double[]{20, 40}));
        DummyIndicator right = new DummyIndicator("Right", new StudyAxisDescriptor(Double.NaN, Double.NaN, true, true, new double[]{20, 40}));
        left.setPanelId(1);
        right.setPanelId(2);

        runOnEdt(() -> panel.initForm(List.of(left, right), List.of(left, right), List.of(), List.of()));

        @SuppressWarnings("unchecked")
        JComboBox<Object> paneSelector = getField(panel, "paneSelector", JComboBox.class);
        JCheckBox forceCombine = getField(panel, "forceCombineCheckBox", JCheckBox.class);

        assertThat(paneSelector.getItemCount()).isEqualTo(1);

        runOnEdt(forceCombine::doClick);

        assertThat(paneSelector.getItemCount()).isEqualTo(2);
        assertThat(paneSelector.getItemAt(1).toString()).contains("Force combine");
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String name, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) type.cast(field.get(target));
    }

    private static IndicatorChooserPanel createPanel() throws Exception {
        return callOnEdt(IndicatorChooserPanel::new);
    }

    private static OverlayFixture createOverlayFixture() throws Exception {
        IndicatorChooserPanel panel = createPanel();
        DummyOverlay prototype = new DummyOverlay("Bands");
        runOnEdt(() -> panel.initForm(List.of(), List.of(), List.of(prototype), List.of(prototype)));
        JTable table = getField(panel, "plotObjectTable", JTable.class);
        List<?> selectedPlugins = getField(panel, "selectedPlugins", List.class);
        DummyOverlay overlay = (DummyOverlay) selectedPlugins.getFirst();
        return new OverlayFixture(panel, table, overlay);
    }

    private static int findRow(JTable table, String label) {
        for (int row = 0; row < table.getRowCount(); row++) {
            if (label.equals(table.getValueAt(row, 0).toString()))
                return row;
        }
        throw new AssertionError("Missing plot row: " + label);
    }

    private static CandleSeries sampleDataset(int size) {
        List<Candle> candles = new ArrayList<>(size);
        LocalDate start = LocalDate.of(2026, 1, 1);
        for (int index = 0; index < size; index++) {
            double open = 100 + index * 0.8;
            double close = open + Math.sin(index / 3.0) * 2.0;
            double high = Math.max(open, close) + 1.5;
            double low = Math.min(open, close) - 1.2;
            candles.add(Candle.of(start.plusDays(index).atStartOfDay(), open, high, low, close, 1_000 + index * 25L));
        }
        return CandleSeries.of(
                SymbolResource.of(SymbolIdentity.of("FRACTAL-VISIBILITY"), TimeFrame.Period.DAILY),
                candles);
    }

    private static void invokeNoArg(Object target, String name) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name);
        method.setAccessible(true);
        runOnEdt(() -> {
            try {
                method.invoke(target);
            } catch (ReflectiveOperationException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private static void runOnEdt(Runnable action) throws Exception {
        callOnEdt(() -> {
            action.run();
            return null;
        });
    }

    private static <T> T callOnEdt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private record OverlayFixture(IndicatorChooserPanel panel, JTable table, DummyOverlay overlay) {
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
        public double[] getStepValues(ChartContext cf) {
            return axis.steps();
        }
    }
}
