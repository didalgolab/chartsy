/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import one.chartsy.ui.chart.Indicator;
import one.chartsy.ui.chart.Overlay;

import java.util.List;
import java.util.Objects;

/**
 * The detached plugin selection prepared by the chooser.
 *
 * @param indicators
 *            the selected indicators
 * @param overlays
 *            the selected overlays
 *
 * @author Mariusz Bernacki
 */
public record ChartPluginSelection(List<Indicator> indicators, List<Overlay> overlays) {
    public ChartPluginSelection {
        indicators = List.copyOf(Objects.requireNonNull(indicators, "indicators"));
        overlays = List.copyOf(Objects.requireNonNull(overlays, "overlays"));
    }
}
