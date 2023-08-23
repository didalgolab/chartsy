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
        ChartData cd = cf.getChartData();
        ChartProperties cp = cf.getChartProperties();
        boolean isLog = cp.getAxisLogarithmicFlag();
        Rectangle bounds = cf.getMainPanel().getChartPanel().getBounds();
        Insets insets = cf.getMainPanel().getChartPanel().getInsets();
        Range range = cf.getMainPanel().getChartPanel().getRange();
        
        if (!cd.isVisibleNull()) {
            VisibleCandles dataset = cd.getVisible();
            int tickSize = Math.max((int) cp.getBarWidth() / 2, 1);
            for (int i = 0, j = dataset.getLength(); i < j; i++) {
                Candle q0 = dataset.getQuoteAt(i);
                
                int x = (int) (0.5 + cd.getX(i, bounds));
                int yOpen = (int) (0.5 + cd.getY(q0.open(), range, bounds, insets, isLog));
                int yClose = (int) (0.5 + cd.getY(q0.close(), range, bounds, insets, isLog));
                int yHigh = (int) (0.5 + cd.getY(q0.high(), range, bounds, insets, isLog));
                int yLow = (int) (0.5 + cd.getY(q0.low(), range, bounds, insets, isLog));
                
                g.setPaint(cp.getBarColor());
                g.drawLine(x, yLow, x, yHigh);
                g.drawLine(x, yOpen, x - tickSize, yOpen);
                g.drawLine(x, yClose, x + tickSize, yClose);
            }
        }
    }
}
