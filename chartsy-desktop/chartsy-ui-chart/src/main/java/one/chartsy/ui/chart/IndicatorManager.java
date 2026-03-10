/*
 * Copyright 2024 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import java.util.List;

/**
 * Provides access to registered chart indicators.
 *
 * @author Mariusz Bernacki
 */
public class IndicatorManager {

    public static IndicatorManager getDefault() {
        return Holder.INSTANCE;
    }

    private IndicatorManager() {
    }

    public Indicator getIndicator(String key) {
        return StudyRegistry.getDefault().getIndicator(key);
    }

    public List<Indicator> getIndicatorsList() {
        return StudyRegistry.getDefault().getIndicatorsList();
    }

    public List<String> getIndicators() {
        return StudyRegistry.getDefault().getIndicators();
    }

    private static final class Holder {
        private static final IndicatorManager INSTANCE = new IndicatorManager();
    }
}
