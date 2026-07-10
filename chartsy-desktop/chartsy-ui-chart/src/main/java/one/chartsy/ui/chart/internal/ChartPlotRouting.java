/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.internal;

import one.chartsy.core.Range;
import one.chartsy.study.StudyAxisDescriptor;
import one.chartsy.study.StudyPlacement;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.ChartPlugin;
import one.chartsy.ui.chart.ChartPluginPlotSource;
import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.Overlay;
import one.chartsy.ui.chart.Plot;
import one.chartsy.ui.chart.StudyBackedChartPlugin;
import one.chartsy.ui.chart.data.VisualRange;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Resolves structural plot placement and routes calculated plots to chart panels. */
public final class ChartPlotRouting {
    public static final int MAIN_PANEL_ID = 0;

    private ChartPlotRouting() {
    }

    public record Route(
            ChartPlugin<?> owner,
            String plotId,
            String label,
            Plot plot,
            int panelId) {
    }

    public static List<Route> routes(
            Collection<? extends Overlay> overlays,
            Collection<? extends Indicator> indicators) {
        List<Route> routes = new ArrayList<>();
        overlays.forEach(plugin -> routes.addAll(routes(plugin)));
        indicators.forEach(plugin -> routes.addAll(routes(plugin)));
        return List.copyOf(routes);
    }

    public static List<Route> routes(ChartPlugin<?> plugin) {
        if (!(plugin instanceof ChartPluginPlotSource plotSource))
            return List.of();

        Map<String, Plot> runtimePlots = runtimePlots(plugin);
        if (runtimePlots.isEmpty())
            return List.of();

        List<ChartPluginPlotSource.PlotDescriptor> descriptors = plotSource.getPlotDescriptors();
        if (descriptors.size() == 1 && !runtimePlots.containsKey(descriptors.getFirst().id())) {
            ChartPluginPlotSource.PlotDescriptor descriptor = descriptors.getFirst();
            int panelId = panelId(plugin, descriptor);
            return runtimePlots.entrySet().stream()
                    .map(entry -> new Route(
                            plugin,
                            descriptor.id(),
                            plotSource.getPlotLabel(entry.getKey()),
                            entry.getValue(),
                            panelId))
                    .toList();
        }

        List<Route> routes = new ArrayList<>(descriptors.size());
        for (ChartPluginPlotSource.PlotDescriptor descriptor : descriptors) {
            Plot plot = runtimePlots.get(descriptor.id());
            if (plot != null)
                routes.add(new Route(
                        plugin,
                        descriptor.id(),
                        descriptor.label(),
                        plot,
                        panelId(plugin, descriptor)));
        }
        return List.copyOf(routes);
    }

    public static List<Route> routesForPanel(
            Collection<? extends ChartPlugin<?>> plugins,
            int panelId) {
        return plugins.stream()
                .flatMap(plugin -> routes(plugin).stream())
                .filter(route -> route.panelId() == panelId)
                .toList();
    }

    public static Set<Integer> configuredPanelIds(Collection<? extends ChartPlugin<?>> plugins) {
        Set<Integer> panelIds = new TreeSet<>();
        for (ChartPlugin<?> plugin : plugins)
            panelIds.addAll(configuredPanelIds(plugin));
        return panelIds;
    }

    public static Set<Integer> configuredPanelIds(ChartPlugin<?> plugin) {
        Set<Integer> panelIds = new TreeSet<>();
        if (plugin instanceof ChartPluginPlotSource plotSource) {
            Map<String, ChartPluginParameter> parameters = ChartPluginParameterUtils.getParametersById(plugin);
            for (ChartPluginPlotSource.PlotDescriptor plot : plotSource.getPlotDescriptors()) {
                int panelId = panelId(plugin, plot, parameters);
                if (panelId > MAIN_PANEL_ID)
                    panelIds.add(panelId);
            }
        }
        return panelIds;
    }

    public static int nextPanelId(Collection<? extends ChartPlugin<?>> plugins) {
        return configuredPanelIds(plugins).stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
    }

    public static int panelId(
            ChartPlugin<?> plugin,
            ChartPluginPlotSource.PlotDescriptor plot) {
        return panelId(plugin, plot, ChartPluginParameterUtils.getParametersById(plugin));
    }

    private static int panelId(
            ChartPlugin<?> plugin,
            ChartPluginPlotSource.PlotDescriptor plot,
            Map<String, ChartPluginParameter> parameters) {
        ChartPluginParameter parameter = parameters.get(plot.panelParameterId());
        Object value = parameter == null || !parameter.canRead() ? null : parameter.getValue();
        if (value instanceof Number number && number.intValue() >= MAIN_PANEL_ID)
            return number.intValue();
        return inheritedPanelId(plugin);
    }

    public static void setPanelId(ChartPlugin<?> plugin, String plotId, int panelId) {
        if (!(plugin instanceof ChartPluginPlotSource plotSource))
            return;
        Map<String, ChartPluginParameter> parameters = ChartPluginParameterUtils.getParametersById(plugin);
        for (ChartPluginPlotSource.PlotDescriptor plot : plotSource.getPlotDescriptors()) {
            if (!plot.id().equals(plotId))
                continue;
            setPanelId(parameters, plot, panelId);
            return;
        }
    }

    public static void setAllPanelIds(ChartPlugin<?> plugin, int panelId) {
        if (plugin instanceof ChartPluginPlotSource plotSource) {
            Map<String, ChartPluginParameter> parameters = ChartPluginParameterUtils.getParametersById(plugin);
            for (ChartPluginPlotSource.PlotDescriptor plot : plotSource.getPlotDescriptors())
                setPanelId(parameters, plot, panelId);
        }
    }

    public static void movePanel(ChartPlugin<?> plugin, int sourcePanelId, int targetPanelId) {
        if (plugin instanceof ChartPluginPlotSource plotSource) {
            Map<String, ChartPluginParameter> parameters = ChartPluginParameterUtils.getParametersById(plugin);
            for (ChartPluginPlotSource.PlotDescriptor plot : plotSource.getPlotDescriptors()) {
                if (panelId(plugin, plot, parameters) == sourcePanelId)
                    setPanelId(parameters, plot, targetPanelId);
            }
        }
    }

    private static void setPanelId(
            Map<String, ChartPluginParameter> parameters,
            ChartPluginPlotSource.PlotDescriptor plot,
            int panelId) {
        ChartPluginParameter parameter = parameters.get(plot.panelParameterId());
        if (parameter != null && parameter.canWrite())
            parameter.setValue(panelId);
    }

    public static VisualRange combinedRange(
            Collection<? extends ChartPlugin<?>> plugins,
            int panelId,
            ChartContext context) {
        Range.Builder combined = new Range.Builder();
        boolean logarithmic = true;
        boolean found = false;
        for (ChartPlugin<?> plugin : plugins) {
            List<Route> routes = routes(plugin).stream()
                    .filter(route -> route.panelId() == panelId)
                    .toList();
            if (routes.isEmpty())
                continue;

            VisualRange range = range(plugin, routes, context);
            if (range.range() != null && !range.range().isEmpty())
                combined.add(range.range().min()).add(range.range().max());
            logarithmic &= range.isLogarithmic();
            found = true;
        }

        Range range = combined.toRange();
        if (range.isEmpty())
            range = Range.of(0.0, 1.0);
        return new VisualRange(range, found && logarithmic);
    }

    private static VisualRange range(ChartPlugin<?> plugin, List<Route> routes, ChartContext context) {
        Range.Builder builder = new Range.Builder();
        for (Route route : routes)
            builder = route.plot().contributeRange(builder, context);

        Range range = builder.toRange();
        if (range.isEmpty())
            range = Range.of(0.0, 1.0);
        else {
            double margin = range.length() * 0.01;
            range = new Range.Builder()
                    .add(range.min() - margin)
                    .add(range.max() + margin)
                    .toRange();
        }

        StudyAxisDescriptor axis = axis(plugin);
        return StudyPresentationFactory.applyAxis(new VisualRange(range, axis.logarithmic()), axis);
    }

    private static StudyAxisDescriptor axis(ChartPlugin<?> plugin) {
        if (plugin instanceof StudyBackedChartPlugin studyPlugin) {
            var plan = studyPlugin.getStudyPresentationPlan();
            return plan != null ? plan.axis() : studyPlugin.getStudyDescriptor().axis();
        }
        if (plugin instanceof Indicator indicator)
            return IndicatorPaneSupport.axisDescriptor(indicator);
        return new StudyAxisDescriptor();
    }

    private static int inheritedPanelId(ChartPlugin<?> plugin) {
        if (plugin instanceof Overlay)
            return MAIN_PANEL_ID;
        if (plugin instanceof Indicator indicator) {
            if (plugin instanceof StudyBackedChartPlugin studyPlugin
                    && studyPlugin.getStudyDescriptor().placement() == StudyPlacement.MAIN_PANEL)
                return MAIN_PANEL_ID;
            return Math.max(1, indicator.getPanelId());
        }
        return MAIN_PANEL_ID;
    }

    private static Map<String, Plot> runtimePlots(ChartPlugin<?> plugin) {
        if (plugin instanceof Indicator indicator)
            return indicator.getPlots();
        if (plugin instanceof Overlay overlay)
            return overlay.getPlots();
        return Map.of();
    }
}
