/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import one.chartsy.ui.chart.ChartTemplate;
import one.chartsy.ui.chart.ChartTemplateDefaults;

import java.util.Objects;

final class ChartTemplateSelectionSnapshotFactory {

    private ChartTemplateSelectionSnapshotFactory() {
    }

    static ChartTemplate create(String name, ChartPluginSelection selection) {
        return create(name, ChartTemplateDefaults.baseChartTemplate(Objects.requireNonNull(name, "name")), selection);
    }

    static ChartTemplate create(String name, ChartTemplate baseTemplate, ChartPluginSelection selection) {
        ChartTemplate template = ChartTemplate.copyVisualState(
                Objects.requireNonNull(name, "name"),
                Objects.requireNonNull(baseTemplate, "baseTemplate")
        );
        applySelection(template, Objects.requireNonNull(selection, "selection"));
        return template;
    }

    private static void applySelection(ChartTemplate template, ChartPluginSelection selection) {
        selection.overlays().forEach(template::addOverlay);
        selection.indicators().forEach(template::addIndicator);
    }
}
