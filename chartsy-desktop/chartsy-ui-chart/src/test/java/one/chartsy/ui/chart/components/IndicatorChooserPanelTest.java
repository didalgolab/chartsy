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
import one.chartsy.ui.chart.ChartPlugin;
import one.chartsy.ui.chart.ChartPluginPlotSource;
import one.chartsy.ui.chart.DynamicStudyIndicator;
import one.chartsy.ui.chart.DynamicStudyOverlay;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.Overlay;
import one.chartsy.ui.chart.StudyBackedChartPlugin;
import one.chartsy.ui.chart.StudyRegistry;
import one.chartsy.study.StudyPlotDescriptor;
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
    void initForm_preselects_second_overlay_instance_with_same_name() throws Exception {
        IndicatorChooserPanel panel = createPanel();
        Overlay first = StudyRegistry.getDefault().getOverlay("Sfora");
        Overlay second = StudyRegistry.getDefault().getOverlay("Sfora");

        runOnEdt(() -> panel.initForm(List.of(), List.of(), List.of(first), List.of(first, second), second));

        JComboBox<?> selector = getField(panel, "pluginSelector", JComboBox.class);
        PlotObjectTreeTable table = getField(panel, "plotObjectTable", PlotObjectTreeTable.class);
        runOnEdt(() -> {
            assertThat(first).isNotSameAs(second);
            assertThat(first.getLabel()).isEqualTo(second.getLabel());
            assertThat(selector.getSelectedIndex()).isEqualTo(1);
            assertThat(selector.getSelectedItem()).isNotSameAs(second);
            assertThat(table.getPluginAt(table.getSelectedRow())).isSameAs(selector.getSelectedItem());
        });
    }

    @Test
    void initForm_preselects_requested_indicator_among_other_studies() throws Exception {
        IndicatorChooserPanel panel = createPanel();
        Indicator first = StudyRegistry.getDefault().getIndicator("Chande Momentum Oscillator");
        Indicator second = StudyRegistry.getDefault().getIndicator("Fractal Dimension");
        Overlay overlay = StudyRegistry.getDefault().getOverlay("Sfora");

        runOnEdt(() -> panel.initForm(
                List.of(first, second), List.of(first, second), List.of(overlay), List.of(overlay), second));

        JComboBox<?> selector = getField(panel, "pluginSelector", JComboBox.class);
        PlotObjectTreeTable table = getField(panel, "plotObjectTable", PlotObjectTreeTable.class);
        runOnEdt(() -> {
            assertThat(selector.getSelectedIndex()).isEqualTo(1);
            assertThat(((ChartPlugin<?>) selector.getSelectedItem()).getName()).isEqualTo(second.getName());
            assertThat(selector.getSelectedItem()).isNotSameAs(second);
            assertThat(table.getPluginAt(table.getSelectedRow())).isSameAs(selector.getSelectedItem());
        });
    }

    @Test
    void initForm_without_initial_selection_selects_first_study() throws Exception {
        IndicatorChooserPanel panel = createPanel();
        Indicator indicator = StudyRegistry.getDefault().getIndicator("Fractal Dimension");
        Overlay overlay = StudyRegistry.getDefault().getOverlay("Sfora");

        runOnEdt(() -> panel.initForm(List.of(indicator), List.of(indicator), List.of(overlay), List.of(overlay)));

        JComboBox<?> selector = getField(panel, "pluginSelector", JComboBox.class);
        runOnEdt(() -> {
            assertThat(selector.getSelectedIndex()).isZero();
            assertThat(((ChartPlugin<?>) selector.getSelectedItem()).getName()).isEqualTo(indicator.getName());
        });
    }

    @Test
    void initForm_unmatched_initial_selection_falls_back_to_first_study() throws Exception {
        IndicatorChooserPanel panel = createPanel();
        Overlay first = StudyRegistry.getDefault().getOverlay("Sfora");
        Overlay second = StudyRegistry.getDefault().getOverlay("Sfora");
        Overlay unmatched = StudyRegistry.getDefault().getOverlay("Sfora");

        runOnEdt(() -> panel.initForm(List.of(), List.of(), List.of(first), List.of(first, second), unmatched));

        JComboBox<?> selector = getField(panel, "pluginSelector", JComboBox.class);
        runOnEdt(() -> {
            assertThat(selector.getSelectedIndex()).isZero();
            assertThat(selector.getItemCount()).isEqualTo(2);
        });
    }

    @Test
    void initForm_without_applied_studies_has_no_preselection() throws Exception {
        IndicatorChooserPanel panel = createPanel();
        Overlay overlay = StudyRegistry.getDefault().getOverlay("Sfora");

        runOnEdt(() -> panel.initForm(List.of(), List.of(), List.of(overlay), List.of(), overlay));

        JComboBox<?> selector = getField(panel, "pluginSelector", JComboBox.class);
        runOnEdt(() -> {
            assertThat(selector.getSelectedItem()).isNull();
            assertThat(selector.getItemCount()).isZero();
        });
    }

    @Test
    void plotObjects_groups_visuals_under_expandable_studies() throws Exception {
        OverlayFixture fixture = createOverlayFixture();
        JTable table = fixture.table();
        assertThat(table).isInstanceOf(Outline.class);
        assertThat(table.getColumnName(0)).isEqualTo("Plot Object");
        assertThat(table.getRowCount()).isEqualTo(2);
        assertThat(table.getValueAt(0, 0).toString()).isEqualTo("Bands");
        assertThat(table.getValueAt(0, 1)).isEqualTo("Overlay");
        assertThat(table.getValueAt(0, 2).toString()).isEqualTo("Main chart");
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
        assertThat(table.isCellEditable(fdiRow, 3)).isTrue();

        runOnEdt(() -> table.setValueAt(Boolean.FALSE, neutralRow, 3));

        StudyBackedChartPlugin selectedPlugin = (StudyBackedChartPlugin) panel.getSelectedIndicators().get(0);
        assertThat(selectedPlugin.getStudyParameterValues())
                .containsEntry(StudyPlotDescriptor.visibilityParameterId("insideNeutral"), Boolean.FALSE)
                .containsEntry(StudyPlotDescriptor.visibilityParameterId("insideHigh"), Boolean.TRUE);
        assertThat(table.getValueAt(neutralRow, 3)).isEqualTo(Boolean.FALSE);
        assertThat(table.getValueAt(highRow, 3)).isEqualTo(Boolean.TRUE);
    }

    @Test
    void everyAvailableStudyPlot_has_editable_visibility_and_panel() throws Exception {
        IndicatorChooserPanel panel = createPanel();
        StudyRegistry registry = StudyRegistry.getDefault();
        List<Indicator> indicators = registry.getIndicatorsList();
        List<Overlay> overlays = registry.getOverlaysList();
        runOnEdt(() -> panel.initForm(indicators, indicators, overlays, overlays));

        JTable table = getField(panel, "plotObjectTable", JTable.class);
        int plotCount = 0;
        for (int row = 0; row < table.getRowCount(); row++) {
            if (!"".equals(table.getValueAt(row, 1)))
                continue;
            plotCount++;
            assertThat(table.getValueAt(row, 2))
                    .as("panel value at row %s (%s)", row, table.getValueAt(row, 0))
                    .isInstanceOf(PlotObjectTreeTable.PanelChoice.class);
            assertThat(table.isCellEditable(row, 2))
                    .as("editable panel at row %s (%s)", row, table.getValueAt(row, 0))
                    .isTrue();
            assertThat(table.getValueAt(row, 3))
                    .as("visibility value at row %s (%s)", row, table.getValueAt(row, 0))
                    .isInstanceOf(Boolean.class);
            assertThat(table.isCellEditable(row, 3))
                    .as("editable visibility at row %s (%s)", row, table.getValueAt(row, 0))
                    .isTrue();
        }
        int expectedPlotCount = registry.getStudyDescriptors().stream()
                .mapToInt(descriptor -> Math.max(1, descriptor.plots().size()))
                .sum();
        assertThat(plotCount).isEqualTo(expectedPlotCount);
    }

    @Test
    void implicitVisibility_hides_declared_study_plot() throws Exception {
        IndicatorChooserPanel panel = createPanel();
        Indicator cmo = StudyRegistry.getDefault().getIndicator("Chande Momentum Oscillator");
        runOnEdt(() -> panel.initForm(List.of(cmo), List.of(cmo), List.of(), List.of()));

        JTable table = getField(panel, "plotObjectTable", JTable.class);
        int cmoRow = findRow(table, "CMO");
        runOnEdt(() -> table.setValueAt(Boolean.FALSE, cmoRow, 3));

        DynamicStudyIndicator hidden = (DynamicStudyIndicator) panel.getSelectedIndicators().getFirst();
        assertThat(hidden.getStudyParameterValues())
                .containsEntry(StudyPlotDescriptor.visibilityParameterId("cmo"), Boolean.FALSE);
        hidden.setDataset(sampleDataset(40));
        hidden.calculate();
        assertThat(hidden.getPlots()).doesNotContainKey("cmo");
    }

    @Test
    void aggregateVisibility_hides_custom_builder_result() throws Exception {
        IndicatorChooserPanel panel = createPanel();
        Overlay sfora = StudyRegistry.getDefault().getOverlay("Sfora");
        runOnEdt(() -> panel.initForm(List.of(), List.of(), List.of(sfora), List.of(sfora)));

        JTable table = getField(panel, "plotObjectTable", JTable.class);
        int resultRow = findRow(table, "Result");
        runOnEdt(() -> table.setValueAt(Boolean.FALSE, resultRow, 3));

        DynamicStudyOverlay hidden = (DynamicStudyOverlay) panel.getSelectedOverlays().getFirst();
        assertThat(hidden.getStudyParameterValues())
                .containsEntry(StudyPlotDescriptor.RESULT_VISIBILITY_PARAMETER_ID, Boolean.FALSE);
        hidden.setDataset(sampleDataset(80));
        hidden.calculate();
        assertThat(hidden.getPlots()).isEmpty();
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
        assertThat(hiddenSnapshot.getPlots()).doesNotContainKeys("insideNeutral", "insideHigh");
        assertThat(visibleSnapshot.getPlots()).containsKeys("insideNeutral", "insideHigh");
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
    void panelEditor_moves_study_plots_independently_to_a_new_pane() throws Exception {
        IndicatorChooserPanel panel = createPanel();
        Indicator fractalDimension = StudyRegistry.getDefault().getIndicator("Fractal Dimension");
        runOnEdt(() -> panel.initForm(
                List.of(fractalDimension), List.of(fractalDimension), List.of(), List.of()));

        JTable table = getField(panel, "plotObjectTable", JTable.class);
        int studyRow = findRow(table, "Fractal Dimension (CLOSE, 30)");
        int neutralRow = findRow(table, "Inside Neutral");
        int highRow = findRow(table, "Inside High");
        assertThat(table.isCellEditable(studyRow, 2)).isTrue();
        assertThat(table.isCellEditable(neutralRow, 2)).isTrue();
        assertThat(table.isCellEditable(highRow, 2)).isTrue();
        assertThat(callOnEdt(() -> table.editCellAt(neutralRow, 2))).isTrue();
        assertThat(table.getEditorComponent()).isInstanceOf(JComboBox.class);
        runOnEdt(() -> table.getCellEditor().cancelCellEditing());

        runOnEdt(() -> table.setValueAt(PlotObjectTreeTable.PanelChoice.newPane(2), neutralRow, 2));

        StudyBackedChartPlugin selected = (StudyBackedChartPlugin) panel.getSelectedIndicators().getFirst();
        assertThat(selected.getStudyParameterValues())
                .containsEntry(StudyPlotDescriptor.panelParameterId("insideNeutral"), 2)
                .containsEntry(
                        StudyPlotDescriptor.panelParameterId("insideHigh"),
                        StudyPlotDescriptor.INHERITED_PANEL_ID);
        assertThat(table.getValueAt(studyRow, 2).toString()).isEqualTo("Mixed");
        assertThat(table.getValueAt(neutralRow, 2).toString()).isEqualTo("Pane 2");
        assertThat(table.getValueAt(highRow, 2).toString()).isEqualTo("Pane 1");
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

    private static final class DummyOverlay extends Overlay implements ChartPluginPlotSource {
        private static final List<PlotDescriptor> PLOTS = List.of(
                new PlotDescriptor("result", "Result", "color", "stroke", "visible", "panel"));

        @Parameter(name = "Color")
        public Color color = Color.RED;
        @Parameter(name = "Unused Color")
        public Color unusedColor = Color.BLUE;
        @Parameter(name = "Stroke")
        public Stroke stroke = BasicStrokes.SOLID;
        @Parameter(name = "Visible")
        public boolean visible = true;
        @Parameter(name = "Panel")
        public int panel;

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
        public List<PlotDescriptor> getPlotDescriptors() {
            return PLOTS;
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
