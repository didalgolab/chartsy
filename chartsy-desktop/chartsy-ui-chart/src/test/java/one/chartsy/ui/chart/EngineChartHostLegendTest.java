/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.charting.ChartRendererLegendItem;
import one.chartsy.charting.Legend;
import one.chartsy.charting.Scale;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.ui.chart.internal.ChartPlotRouting;
import one.chartsy.ui.chart.internal.engine.EngineChartHost;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class EngineChartHostLegendTest {

    @Test
    void legendDoubleClick_label_and_swatch_select_the_exact_same_named_overlay() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var first = StudyRegistry.getDefault().getOverlay("FRAMA, Leading");
            var second = StudyRegistry.getDefault().getOverlay("FRAMA, Leading");
            var context = createContext(List.of(first, second), List.of());
            var selected = new ArrayList<ChartPlugin<?>>();
            try (var host = new EngineChartHost(new Scale())) {
                host.onLegendDoubleClick(selected::add);
                host.configurePriceChart(context, List.of(first, second), List.of(), false);
                var firstEntry = entry(host.legend(), 1);
                var secondEntry = entry(host.legend(), 2);
                assertThat(secondEntry.getLabel()).isEqualTo(firstEntry.getLabel());

                doubleClickSwatch(host.legend(), secondEntry);
                doubleClickLabel(host.legend(), secondEntry);
                doubleClickLabel(host.legend(), firstEntry);

                assertThat(selected).hasSize(3);
                assertThat(selected.get(0)).isSameAs(second);
                assertThat(selected.get(1)).isSameAs(second);
                assertThat(selected.get(2)).isSameAs(first);
            }
        });
    }

    @Test
    void legendDoubleClick_shared_indicator_pane_selects_the_clicked_owner() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var first = StudyRegistry.getDefault().getIndicator("Fractal Dimension");
            var second = StudyRegistry.getDefault().getIndicator("Fractal Dimension");
            first.setPanelId(1);
            second.setPanelId(1);
            var context = createContext(List.of(), List.of(first, second));
            var selected = new ArrayList<ChartPlugin<?>>();
            try (var host = new EngineChartHost(new Scale())) {
                host.onLegendDoubleClick(selected::add);
                host.configureIndicatorChart(context, 1, List.of(first, second),
                        ChartPlotRouting.combinedRange(List.of(first, second), 1, context), false, null);
                assertThat(host.legend().getComponentCount()).isEqualTo(2);

                var secondEntry = entry(host.legend(), 1);
                doubleClickLabel(host.legend(), secondEntry);

                assertThat(selected).singleElement().isSameAs(second);
            }
        });
    }

    @Test
    void legendDoubleClick_moved_overlay_and_indicator_plots_keep_their_study_owner() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var overlay = StudyRegistry.getDefault().getOverlay("FRAMA, Trailing");
            var indicator = StudyRegistry.getDefault().getIndicator("Fractal Dimension");
            indicator.setPanelId(1);
            ChartPlotRouting.setAllPanelIds(overlay, 2);
            ChartPlotRouting.setAllPanelIds(indicator, 0);
            var context = createContext(List.of(overlay), List.of(indicator));
            var selected = new ArrayList<ChartPlugin<?>>();
            try (var host = new EngineChartHost(new Scale())) {
                host.onLegendDoubleClick(selected::add);
                host.configurePriceChart(context, List.of(overlay), List.of(indicator), false);
                assertThat(host.legend().getComponentCount()).isEqualTo(2);
                doubleClickSwatch(host.legend(), entry(host.legend(), 1));

                host.configureIndicatorChart(context, 2, List.of(overlay),
                        ChartPlotRouting.combinedRange(List.of(overlay), 2, context), false, null);
                assertThat(host.legend().getComponentCount()).isEqualTo(1);
                doubleClickSwatch(host.legend(), entry(host.legend(), 0));

                assertThat(selected).hasSize(2);
                assertThat(selected.get(0)).isSameAs(indicator);
                assertThat(selected.get(1)).isSameAs(overlay);
            }
        });
    }

    @Test
    void legendDoubleClick_price_title_and_legend_background_open_without_a_study() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var context = createContext(List.of(), List.of());
            var selected = new ArrayList<ChartPlugin<?>>();
            try (var host = new EngineChartHost(new Scale())) {
                host.onLegendDoubleClick(selected::add);
                host.configurePriceChart(context, List.of(), List.of(), false);
                var priceEntry = entry(host.legend(), 0);
                doubleClickLabel(host.legend(), priceEntry);
                assertThat(SwingUtilities.getDeepestComponentAt(host.legend(), 1, 1))
                        .isSameAs(host.legend());
                click(host.legend(), 1, 1, MouseEvent.BUTTON1, 2, false);

                assertThat(selected).hasSize(2).containsOnlyNulls();
            }
        });
    }

    @Test
    void legendDoubleClick_ignores_single_right_consumed_and_chart_area_clicks() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var overlay = StudyRegistry.getDefault().getOverlay("FRAMA, Leading");
            var context = createContext(List.of(overlay), List.of());
            var selected = new ArrayList<ChartPlugin<?>>();
            try (var host = new EngineChartHost(new Scale())) {
                host.onLegendDoubleClick(selected::add);
                host.configurePriceChart(context, List.of(overlay), List.of(), false);
                var overlayEntry = entry(host.legend(), 1);
                click(overlayEntry, 2, 2, MouseEvent.BUTTON1, 1, false);
                click(overlayEntry, 2, 2, MouseEvent.BUTTON3, 2, false);
                click(overlayEntry, 2, 2, MouseEvent.BUTTON1, 2, true);
                click(host.chart().getChartArea(), 100, 100, MouseEvent.BUTTON1, 2, false);

                assertThat(selected).isEmpty();
            }
        });
    }

    @Test
    void legendDoubleClick_rebuilt_entries_and_reconfiguration_use_current_owners_once() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var original = StudyRegistry.getDefault().getOverlay("FRAMA, Leading");
            var replacement = StudyRegistry.getDefault().getOverlay("FRAMA, Leading");
            var context = createContext(List.of(original, replacement), List.of());
            var selected = new ArrayList<ChartPlugin<?>>();
            try (var host = new EngineChartHost(new Scale())) {
                host.onLegendDoubleClick(selected::add);
                host.configurePriceChart(context, List.of(original), List.of(), false);
                var detachedEntry = entry(host.legend(), 1);
                var renderer = detachedEntry.getRenderer();
                renderer.setLegended(false);
                renderer.setLegended(true);
                var rebuiltEntry = entry(host.legend(), 1);
                assertThat(rebuiltEntry).isNotSameAs(detachedEntry);
                doubleClickSwatch(host.legend(), rebuiltEntry);
                click(detachedEntry, 2, 2, MouseEvent.BUTTON1, 2, false);

                host.configurePriceChart(context, List.of(replacement), List.of(), false);
                host.configurePriceChart(context, List.of(replacement), List.of(), false);
                doubleClickSwatch(host.legend(), entry(host.legend(), 1));
                click(rebuiltEntry, 2, 2, MouseEvent.BUTTON1, 2, false);

                assertThat(selected).hasSize(2);
                assertThat(selected.get(0)).isSameAs(original);
                assertThat(selected.get(1)).isSameAs(replacement);
            }
        });
    }

    @Test
    void onLegendDoubleClick_supports_late_registration_and_replaces_the_previous_handler() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var overlay = StudyRegistry.getDefault().getOverlay("FRAMA, Leading");
            var context = createContext(List.of(overlay), List.of());
            var firstSelection = new ArrayList<ChartPlugin<?>>();
            var secondSelection = new ArrayList<ChartPlugin<?>>();
            try (var host = new EngineChartHost(new Scale())) {
                host.configurePriceChart(context, List.of(overlay), List.of(), false);
                var overlayEntry = entry(host.legend(), 1);

                // A host without a handler still consumes accepted legend double-clicks.
                assertThat(click(overlayEntry, 2, 2, MouseEvent.BUTTON1, 2, false).isConsumed()).isTrue();
                host.onLegendDoubleClick(firstSelection::add);
                doubleClickSwatch(host.legend(), overlayEntry);
                host.onLegendDoubleClick(secondSelection::add);
                doubleClickLabel(host.legend(), overlayEntry);

                assertThat(firstSelection).singleElement().isSameAs(overlay);
                assertThat(secondSelection).singleElement().isSameAs(overlay);
            }
        });
    }

    @Test
    void close_detaches_legend_callbacks() throws Exception {
        verifyClosedHost(EngineChartHost::close);
    }

    @Test
    void close_detaches_legend_callbacks_after_the_chart_was_disposed_directly() throws Exception {
        verifyClosedHost(host -> {
            host.chart().dispose();
            host.close();
        });
    }

    private static void verifyClosedHost(Consumer<EngineChartHost> dispose) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var overlay = StudyRegistry.getDefault().getOverlay("FRAMA, Leading");
            var context = createContext(List.of(overlay), List.of());
            var selected = new ArrayList<ChartPlugin<?>>();
            try (var host = new EngineChartHost(new Scale())) {
                host.onLegendDoubleClick(selected::add);
                host.configurePriceChart(context, List.of(overlay), List.of(), false);
                var overlayEntry = entry(host.legend(), 1);

                dispose.accept(host);
                host.close();

                assertThat(click(overlayEntry, 2, 2, MouseEvent.BUTTON1, 2, false).isConsumed()).isFalse();
                assertThat(click(host.legend(), 1, 1, MouseEvent.BUTTON1, 2, false).isConsumed()).isFalse();
                assertThat(selected).isEmpty();
            }
        });
    }

    private static ChartRendererLegendItem entry(Legend legend, int index) {
        legend.setSize(legend.getPreferredSize());
        legend.doLayout();
        assertThat(legend.getComponentCount()).isGreaterThan(index);
        var entry = (ChartRendererLegendItem) legend.getComponent(index);
        assertThat(entry.getWidth()).isGreaterThan(4);
        assertThat(entry.getHeight()).isGreaterThan(0);
        return entry;
    }

    private static void doubleClickSwatch(Legend legend, Component entry) {
        doubleClickAt(legend, entry, 2);
    }

    private static void doubleClickLabel(Legend legend, Component entry) {
        doubleClickAt(legend, entry, entry.getWidth() - 2);
    }

    private static void doubleClickAt(Legend legend, Component entry, int x) {
        Point point = SwingUtilities.convertPoint(entry, x, entry.getHeight() / 2, legend);
        Component target = SwingUtilities.getDeepestComponentAt(legend, point.x, point.y);
        assertThat(target).isSameAs(entry);
        Point local = SwingUtilities.convertPoint(legend, point, target);
        click(target, local.x, local.y, MouseEvent.BUTTON1, 2, false);
    }

    private static MouseEvent click(Component target, int x, int y, int button, int count, boolean consumed) {
        var event = new MouseEvent(target, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                0, x, y, count, false, button);
        if (consumed)
            event.consume();
        target.dispatchEvent(event);
        return event;
    }

    private static ChartFrame createContext(List<Overlay> overlays, List<Indicator> indicators) {
        var template = ChartTemplateDefaults.baseChartTemplate("Legend Fixture");
        overlays.forEach(template::addOverlay);
        indicators.forEach(template::addIndicator);
        var resource = SymbolResource.<Candle>of(SymbolIdentity.of("LEGEND"), TimeFrame.Period.DAILY);
        var candles = new ArrayList<Candle>();
        LocalDate start = LocalDate.of(2024, 1, 2);
        for (int i = 0; i < 220; i++) {
            double close = 100.0 + i / 10.0 + Math.sin(i / 7.0);
            candles.add(Candle.of(start.plusDays(i).atStartOfDay(),
                    close - 0.2, close + 1.0, close - 1.0, close, 1_000_000.0));
        }
        return ChartExporter.createChartFrame(DataProvider.EMPTY, CandleSeries.of(resource, candles), template,
                new Dimension(1280, 720), true);
    }
}
