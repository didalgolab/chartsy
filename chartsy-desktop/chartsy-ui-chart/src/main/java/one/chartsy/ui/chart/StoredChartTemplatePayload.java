/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import java.util.List;
import java.util.Objects;

public record StoredChartTemplatePayload(
        List<StoredPluginSpec> overlays,
        List<StoredPluginSpec> indicators) {

    public static final StoredChartTemplatePayload EMPTY = new StoredChartTemplatePayload(List.of(), List.of());

    public StoredChartTemplatePayload {
        overlays = List.copyOf(Objects.requireNonNull(overlays, "overlays"));
        indicators = List.copyOf(Objects.requireNonNull(indicators, "indicators"));
    }

    public boolean isEmpty() {
        return overlays.isEmpty() && indicators.isEmpty();
    }
}
