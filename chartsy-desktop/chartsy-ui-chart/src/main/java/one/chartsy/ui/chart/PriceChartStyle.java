/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.ui.chart.type.OHLC;

import java.util.Locale;

/**
 * The native engine-backed price chart representations supported by the desktop UI.
 */
public enum PriceChartStyle {
    CANDLE,
    OHLC;

    public static PriceChartStyle fromChart(Chart chart) {
        if (chart == null)
            return CANDLE;
        if (chart instanceof OHLC)
            return OHLC;

        String name = chart.getName();
        if (name == null)
            return CANDLE;
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.contains("ohlc") ? OHLC : CANDLE;
    }
}
