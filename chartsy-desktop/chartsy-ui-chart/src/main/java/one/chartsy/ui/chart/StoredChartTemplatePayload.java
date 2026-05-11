/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import java.util.List;
import java.util.Objects;

public record StoredChartTemplatePayload(
        List<StoredPluginSpec> overlays,
        List<StoredPluginSpec> indicators,
        String chartTypeName,
        StoredChartProperties chartProperties) {

    public static final StoredChartTemplatePayload EMPTY = new StoredChartTemplatePayload(List.of(), List.of());

    public StoredChartTemplatePayload(List<StoredPluginSpec> overlays, List<StoredPluginSpec> indicators) {
        this(overlays, indicators, null, null);
    }

    public StoredChartTemplatePayload {
        overlays = List.copyOf(Objects.requireNonNull(overlays, "overlays"));
        indicators = List.copyOf(Objects.requireNonNull(indicators, "indicators"));
        chartTypeName = normalizeChartTypeName(chartTypeName);
    }

    public boolean isEmpty() {
        return overlays.isEmpty() && indicators.isEmpty();
    }

    public boolean hasChartType() {
        return chartTypeName != null;
    }

    public boolean hasChartProperties() {
        return chartProperties != null;
    }

    public String chartTypeNameOrDefault() {
        return hasChartType() ? chartTypeName : ChartTemplateDefaults.defaultChartName();
    }

    public StoredChartProperties chartPropertiesOrDefault() {
        return hasChartProperties() ? chartProperties : StoredChartProperties.defaultValue();
    }

    public static StoredChartTemplatePayload fromChartTemplate(ChartTemplate chartTemplate) {
        return ChartTemplatePayloadMapper.getDefault().fromChartTemplate(chartTemplate);
    }

    private static String normalizeChartTypeName(String chartTypeName) {
        if (chartTypeName == null || chartTypeName.isBlank())
            return null;
        return chartTypeName.strip();
    }
}
