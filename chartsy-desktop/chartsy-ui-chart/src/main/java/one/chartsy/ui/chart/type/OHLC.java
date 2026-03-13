/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart.type;

import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;

import one.chartsy.Candle;
import one.chartsy.core.Range;
import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.data.VisibleCandles;
import org.openide.util.lookup.ServiceProvider;

/**
 * The Open High Low Close Chart (OHLC).
 * This chart is more accurate than the line chart, because it shows the price movement during the day.
 * 
 * @author Mariusz Bernacki
 */
@ServiceProvider(service = Chart.class)
public class OHLC implements Chart {
    
    @Override
    public String getName() {
        return "OHLC";
    }
    
    @Override
    public void paint(Graphics2D g, ChartContext cf, int width, int height) {
        // Price rendering is engine-native; this legacy chart remains only for style selection compatibility.
    }
}
