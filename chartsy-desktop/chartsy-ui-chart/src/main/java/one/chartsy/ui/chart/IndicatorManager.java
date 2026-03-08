/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import java.util.List;

/**
 *
 * @author Mariusz Bernacki
 */
public class IndicatorManager {

    private final ChartPluginRegistry<Indicator> indicators = new ChartPluginRegistry<>(Indicator.class);

    public static IndicatorManager getDefault() {
        return Holder.INSTANCE;
    }

    private IndicatorManager() {
    }

    public Indicator getIndicator(String key) {
        return indicators.get(key);
    }

    public List<Indicator> getIndicatorsList() {
        return indicators.getPlugins();
    }

    public List<String> getIndicators() {
        return indicators.getNames();
    }

    private static final class Holder {
        private static final IndicatorManager INSTANCE = new IndicatorManager();
    }
}
