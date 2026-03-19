/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.ui.chart.components.ChartPluginSelection;
import org.openide.util.Lookup;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChartTemplateCatalog {
    String BUILT_IN_TEMPLATE_NAME = "Built-in Default";
    UUID BUILT_IN_TEMPLATE_KEY = UUID.fromString("8f0fdd06-0a53-4a32-a8d6-d136c4e1c1a6");
    int PAYLOAD_VERSION = 1;

    record LoadedTemplate(
            ChartTemplateSummary summary,
            ChartTemplate chartTemplate,
            StoredChartTemplatePayload payload) {
    }

    List<ChartTemplateSummary> listTemplates();

    LoadedTemplate getTemplate(UUID templateKey);

    LoadedTemplate getDefaultTemplate();

    LoadedTemplate resolveTemplate(UUID templateKey);

    ChartTemplateSummary createTemplate(String name, ChartPluginSelection selection);

    ChartTemplateSummary updateTemplate(UUID templateKey, String name, ChartPluginSelection selection);

    void deleteTemplate(UUID templateKey);

    ChartTemplateSummary setDefaultTemplate(UUID templateKey);

    ChartTemplateSummary restoreBuiltIn();

    static ChartTemplateSummary builtInSummary() {
        return new ChartTemplateSummary(BUILT_IN_TEMPLATE_KEY, BUILT_IN_TEMPLATE_NAME, true, true);
    }

    static LoadedTemplate builtInTemplate() {
        ChartTemplate chartTemplate = ChartTemplateDefaults.basicChartTemplate();
        StoredChartTemplatePayload payload = ChartTemplatePayloadMapper.getDefault().fromChartTemplate(chartTemplate);
        return new LoadedTemplate(builtInSummary(), chartTemplate, payload);
    }

    static ChartTemplateCatalog getDefault() {
        return LazyHolder.INSTANCE;
    }

    final class LazyHolder {
        private LazyHolder() {
        }

        private static final ChartTemplateCatalog INSTANCE = Optional.ofNullable(Lookup.getDefault().lookup(ChartTemplateCatalog.class))
                .orElseGet(PersistentChartTemplateCatalog::new);
    }
}
