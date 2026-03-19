/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui;

import one.chartsy.ui.chart.Chart;
import one.chartsy.ui.chart.ChartTemplateCatalog;
import one.chartsy.ui.chart.ChartTemplateSummary;
import one.chartsy.ui.chart.components.ChartPluginSelection;
import one.chartsy.ui.chart.type.CandlestickChart;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChartManagerTest {

    @BeforeAll
    static void ensureChartRegistry() throws Exception {
        Field field = one.chartsy.ui.chart.ChartManager.class.getDeclaredField("standardCharts");
        field.setAccessible(true);

        one.chartsy.ui.chart.ChartManager manager = one.chartsy.ui.chart.ChartManager.getDefault();
        @SuppressWarnings("unchecked")
        Map<String, Chart> currentCharts = (Map<String, Chart>) field.get(manager);
        Map<String, Chart> charts = (currentCharts != null)
                ? new LinkedHashMap<>(currentCharts)
                : new LinkedHashMap<>();
        charts.putIfAbsent("Candle Stick", new CandlestickChart());
        field.set(manager, charts);
    }

    @Test
    void resolveLoadedTemplateFallsBackToBuiltInTemplateWhenCatalogFails() {
        ChartTemplateCatalog catalog = new StubChartTemplateCatalog() {
            @Override
            public LoadedTemplate resolveTemplate(UUID templateKey) {
                throw new IllegalStateException("catalog unavailable");
            }
        };

        ChartManager.TemplateResolution resolution = ChartManager.resolveLoadedTemplate(ChartOpenOptions.DEFAULT, catalog);

        assertThat(resolution.loadedTemplate().summary().builtIn()).isTrue();
        assertThat(resolution.loadedTemplate().summary().templateKey()).isEqualTo(ChartTemplateCatalog.BUILT_IN_TEMPLATE_KEY);
        assertThat(resolution.warningMessage()).contains("built-in template");
    }

    @Test
    void resolveLoadedTemplateUsesCatalogResultWhenAvailable() {
        ChartTemplateCatalog.LoadedTemplate expected = ChartTemplateCatalog.builtInTemplate();
        ChartTemplateCatalog catalog = new StubChartTemplateCatalog() {
            @Override
            public LoadedTemplate resolveTemplate(UUID templateKey) {
                return expected;
            }
        };

        ChartManager.TemplateResolution resolution = ChartManager.resolveLoadedTemplate(ChartOpenOptions.DEFAULT, catalog);

        assertThat(resolution.loadedTemplate()).isSameAs(expected);
        assertThat(resolution.warningMessage()).isNull();
    }

    @Test
    void resolveLoadedTemplateFallsBackToDefaultWithoutWarningWhenRequestedTemplateIsMissing() {
        ChartTemplateCatalog.LoadedTemplate expectedDefault = ChartTemplateCatalog.builtInTemplate();
        ChartTemplateCatalog catalog = new StubChartTemplateCatalog() {
            @Override
            public LoadedTemplate resolveTemplate(UUID templateKey) {
                throw new IllegalArgumentException("missing template");
            }

            @Override
            public LoadedTemplate getDefaultTemplate() {
                return expectedDefault;
            }
        };

        ChartManager.TemplateResolution resolution = ChartManager.resolveLoadedTemplate(
                new ChartOpenOptions(null, UUID.randomUUID()),
                catalog);

        assertThat(resolution.loadedTemplate()).isSameAs(expectedDefault);
        assertThat(resolution.warningMessage()).isNull();
    }

    private abstract static class StubChartTemplateCatalog implements ChartTemplateCatalog {
        @Override
        public List<ChartTemplateSummary> listTemplates() {
            return List.of();
        }

        @Override
        public LoadedTemplate getTemplate(UUID templateKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LoadedTemplate getDefaultTemplate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChartTemplateSummary createTemplate(String name, ChartPluginSelection selection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChartTemplateSummary updateTemplate(UUID templateKey, String name, ChartPluginSelection selection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteTemplate(UUID templateKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChartTemplateSummary setDefaultTemplate(UUID templateKey) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ChartTemplateSummary restoreBuiltIn() {
            throw new UnsupportedOperationException();
        }
    }
}
