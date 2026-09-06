/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.charting.Legend;
import one.chartsy.charting.LegendEntry;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.ui.chart.components.IndicatorChooserDialog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Exercises real legend hit testing, action lookup, and modal chooser selection together. */
@Tag("swing")
@Isolated("Uses the shared Swing event thread and application-modal dialogs")
@Timeout(20)
class LegendStudyChooserIntegrationTest {
    private JFrame owner;
    private ChartFrame chart;
    private List<Indicator> indicators;
    private List<Overlay> overlays;

    @BeforeEach
    void create_chart_with_same_named_studies() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "Real chooser dialogs require a graphical display");
        SwingUtilities.invokeAndWait(() -> {
            owner = new JFrame("Legend study chooser integration test");
            StudyRegistry registry = StudyRegistry.getDefault();
            overlays = List.of(registry.getOverlay("FRAMA, Leading"), registry.getOverlay("FRAMA, Leading"));
            indicators = List.of(registry.getIndicator("Fractal Dimension"), registry.getIndicator("Fractal Dimension"));
            indicators.forEach(indicator -> indicator.setPanelId(1));

            ChartTemplate template = ChartTemplateDefaults.baseChartTemplate("Legend chooser test");
            overlays.forEach(template::addOverlay);
            indicators.forEach(template::addIndicator);
            chart = ChartExporter.createChartFrame(DataProvider.EMPTY, dataset(), template, new Dimension(1280, 720), true);
            // A dedicated hidden owner keeps dialog discovery and disposal local to this test.
            owner.setContentPane(chart);
            layout(chart);
        });
    }

    @AfterEach
    void dispose_chart_and_owned_dialogs() throws Exception {
        if (owner == null)
            return;
        SwingUtilities.invokeAndWait(() -> {
            try {
                if (chart != null) {
                    chart.getMainStackPanel().getIndicatorPanels().forEach(panel -> panel.disposeResources());
                    chart.getMainStackPanel().getChartPanel().getEngineChart().dispose();
                    indicators.forEach(Indicator::close);
                    overlays.forEach(Overlay::close);
                }
            } finally {
                if (owner != null)
                    owner.dispose();
            }
        });
    }

    @Test
    void legendDoubleClick_overlay_swatch_selects_second_same_named_study() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Legend legend = find(chart.getMainStackPanel().getChartPanel(), Legend.class, null);
            assertThat(legend.getComponentCount()).isEqualTo(3);
            LegendEntry first = (LegendEntry) legend.getComponent(1);
            LegendEntry second = (LegendEntry) legend.getComponent(2);
            assertThat(second.getLabel()).isEqualTo(first.getLabel());

            // The selector lists both indicators before the two overlays.
            doubleClickSwatch(second, 3, overlays.get(1));
        });
    }

    @Test
    void legendDoubleClick_indicator_label_selects_second_same_named_study() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Legend legend = find(chart.getMainStackPanel().getIndicatorPanels().getFirst(), Legend.class, null);
            assertThat(legend.getComponentCount()).isEqualTo(2);
            LegendEntry first = (LegendEntry) legend.getComponent(0);
            LegendEntry second = (LegendEntry) legend.getComponent(1);
            assertThat(second.getLabel()).isEqualTo(first.getLabel());

            doubleClickLabel(second, 1, indicators.get(1));
        });
    }

    @Test
    void legendDoubleClick_price_title_opens_chooser_with_default_selection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Legend legend = find(chart.getMainStackPanel().getChartPanel(), Legend.class, null);
            doubleClickLabel((LegendEntry) legend.getComponent(0), 0, indicators.get(0));
        });
    }

    private void doubleClickSwatch(LegendEntry entry, int expectedIndex, ChartPlugin<?> expectedSource) {
        layout(chart);
        doubleClick(entry, 4, expectedIndex, expectedSource);
    }

    private void doubleClickLabel(LegendEntry entry, int expectedIndex, ChartPlugin<?> expectedSource) {
        layout(chart);
        doubleClick(entry, entry.getWidth() - 4, expectedIndex, expectedSource);
    }

    private void doubleClick(LegendEntry entry, int x, int expectedIndex, ChartPlugin<?> expectedSource) {
        Point point = SwingUtilities.convertPoint(entry, x, entry.getHeight() / 2, chart);
        Component target = SwingUtilities.getDeepestComponentAt(chart, point.x, point.y);
        assertThat(target).as("Legend entry must receive the click through the chart's layers").isSameAs(entry);
        Point local = SwingUtilities.convertPoint(chart, point, target);
        MouseEvent click = new MouseEvent(target, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                0, local.x, local.y, 2, false, MouseEvent.BUTTON1);

        CompletableFuture<Void> verified = new CompletableFuture<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        Timer observer = new Timer(50, event -> {
            if (System.nanoTime() >= deadline) {
                ((Timer) event.getSource()).stop();
                verified.completeExceptionally(new AssertionError("No owned chooser appeared within five seconds"));
                owner.dispose();
                return;
            }
            for (Window window : owner.getOwnedWindows()) {
                if (!window.isShowing())
                    continue;
                ((Timer) event.getSource()).stop();
                try {
                    assertThat(window).isInstanceOf(IndicatorChooserDialog.class);
                    assertChooserSelection((IndicatorChooserDialog) window, expectedIndex, expectedSource);
                    verified.complete(null);
                } catch (Throwable failure) {
                    verified.completeExceptionally(failure);
                } finally {
                    window.dispose();
                }
                return;
            }
        });
        observer.start();
        try {
            // A modal chooser runs the observer in its nested event loop and returns after disposal.
            target.dispatchEvent(click);
            assertThat(verified.isDone()).as("Double-click must open the modal chooser").isTrue();
            verified.join();
            assertThat(click.isConsumed()).isTrue();
            assertChartStudiesUnchanged();
        } finally {
            observer.stop();
            for (Window window : owner.getOwnedWindows())
                window.dispose();
        }
    }

    private void assertChooserSelection(IndicatorChooserDialog dialog, int expectedIndex,
                                        ChartPlugin<?> expectedSource) throws ReflectiveOperationException {
        assertThat(dialog.getOwner()).isSameAs(owner);
        assertThat(dialog.getModalityType()).isEqualTo(Dialog.ModalityType.APPLICATION_MODAL);
        JComboBox<?> selector = find(dialog, JComboBox.class, "indicatorChooser.selector");
        JTable table = find(dialog, JTable.class, "indicatorChooser.plotTable");
        assertThat(selector).isNotNull();
        assertThat(table).isNotNull();
        assertThat(selector.getItemCount()).isEqualTo(4);
        assertThat(selector.getSelectedIndex()).isEqualTo(expectedIndex);
        ChartPlugin<?> selected = (ChartPlugin<?>) selector.getSelectedItem();
        assertThat(selected).isNotSameAs(expectedSource);
        assertThat(selected.getLabel()).isEqualTo(expectedSource.getLabel());
        assertThat(table.getSelectedRow()).isGreaterThanOrEqualTo(0);

        // The table is package-private; inspect its selected study without adding a production test hook.
        Method getPluginAt = table.getClass().getDeclaredMethod("getPluginAt", int.class);
        getPluginAt.setAccessible(true);
        assertThat(getPluginAt.invoke(table, table.getSelectedRow())).isSameAs(selected);
    }

    private void assertChartStudiesUnchanged() {
        List<Indicator> currentIndicators = chart.getMainStackPanel().getIndicatorsList();
        List<Overlay> currentOverlays = chart.getMainStackPanel().getChartPanel().getOverlays();
        assertThat(currentIndicators).hasSize(indicators.size());
        assertThat(currentOverlays).hasSize(overlays.size());
        for (int index = 0; index < indicators.size(); index++)
            assertThat(currentIndicators.get(index)).isSameAs(indicators.get(index));
        for (int index = 0; index < overlays.size(); index++)
            assertThat(currentOverlays.get(index)).isSameAs(overlays.get(index));
    }

    private static <T extends Component> T find(Component root, Class<T> type, String name) {
        if (type.isInstance(root) && (name == null || name.equals(root.getName())))
            return type.cast(root);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = find(child, type, name);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    private static void layout(Component component) {
        component.doLayout();
        if (component instanceof Container container)
            for (Component child : container.getComponents())
                layout(child);
    }

    private static CandleSeries dataset() {
        var candles = new ArrayList<Candle>();
        LocalDate start = LocalDate.of(2024, 1, 2);
        for (int index = 0; index < 220; index++) {
            double close = 100 + index * 0.1 + Math.sin(index / 7.0);
            candles.add(Candle.of(start.plusDays(index).atStartOfDay(),
                    close - 0.2, close + 1, close - 1, close, 1_000_000));
        }
        return CandleSeries.of(SymbolResource.of(SymbolIdentity.of("LEGEND-CHOOSER"), TimeFrame.Period.DAILY), candles);
    }
}
