/*
 * Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.type;

import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

import one.chartsy.Candle;
import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.components.ChartPanel;
import one.chartsy.ui.chart.data.VisibleCandles;
import one.chartsy.ui.chart.internal.CoordCalc;
import org.openide.util.lookup.ServiceProvider;

/**
 * Paints a chart with candles representing the open-high-low-close datapoints.
 * <p>
 * The {@code CandlestickChart} expects datapoints specified as a {@link CandleSeries}
 * object.<br>
 * Each candle is a visual figure, whose top and bottom of the vertical lines,
 * known as "wicks", represent {@link Candle#high() high} and {@link Candle#low() low}
 * data prices respectively, while the top and bottom of the closed box, known
 * as "body", represent {@link Candle#open() open} and {@link Candle#close() close}
 * data prices. The candle body is filled differently depending on whether the
 * particular data bar {@link Candle#isBullish() is bullish} or
 * {@link Candle#isBearish() is bearish}.
 * 
 * 
 * @author Mariusz Bernacki
 * 
 */
@ServiceProvider(service = Chart.class)
public class CandlestickChart implements Chart {
    
    @Override
    public String getName() {
        return "Candle Stick";
    }
    
    @Override
    public void paint(Graphics2D g, ChartContext cf, int width, int height) {
        // switch antialias off
        Object oldAntialiasValue = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        
        if (cf.getChartData().getDataRenderingHint() != null) {
            paintWithHint(g, cf, cf.getChartData().getDataRenderingHint());
            return;
        }
        
        ChartData cd = cf.getChartData();
        ChartProperties cp = cf.getChartProperties();
        if (!cd.isVisibleNull() && cp.getBarVisibility()) {
            Rectangle rect = new Rectangle(width, height);
            rect.grow(-2, -2 - rect.height/100);
            
            Range range = cf.getMainPanel().getChartPanel().getRange();
            
            int candleWidth = (int) Math.round(cp.getBarWidth());
            if (candleWidth <= 1)
                drawSubpixelOptimizedChart(g, cd, cp, rect, range, candleWidth);
            else
                drawChart(g, cd, cp, rect, range, candleWidth);
        }
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasValue);
    }
    
    protected void drawChart(Graphics2D g, ChartData cd, ChartProperties cp, Rectangle rect, Range range, int candleWidth) {
        VisibleCandles dataset = cd.getVisible().getVisibleDataset(cd.getChartDataset());
        boolean isLog = cp.getAxisLogarithmicFlag();
        
        Line2D line2d = new Line2D.Double();
        Rectangle2D rect2d = new Rectangle2D.Double();
        for (int i = 0, j = dataset.getLength(); i < j; i++) {
            Candle q0 = dataset.getQuoteAt(i);
            
            int x = (int)(0.5 + cd.getX(i, rect));
            int yOpen = (int)(0.5 + cd.getY(q0.open(), rect, range, isLog));
            int yClose = (int)(0.5 + cd.getY(q0.close(), rect, range, isLog));
            int candleHeight =  Math.abs(yOpen - yClose);
            boolean bearish = q0.isBearish();
            
            if (candleWidth >= 2) {
                if (bearish) {
                    if (cp.getBarDownVisibility()) {
                        g.setPaint(cp.getBarDownColor());
                        g.fillRect(x - candleWidth/2, yOpen, candleWidth, candleHeight);
                    }
                } else if (cp.getBarUpVisibility()) {
                    g.setPaint(cp.getBarUpColor());
                    g.fillRect(x - candleWidth/2, yClose, candleWidth, candleHeight);
                }
            }
        }
        
        g.setPaint(cp.getBarColor());
        g.setStroke(cp.getBarStroke());
        for (int i = 0, j = dataset.getLength(); i < j; i++) {
            Candle q0 = dataset.getQuoteAt(i);
            int x = (int)(0.5 + cd.getX(i, rect));
            double yOpen = (cd.getY(q0.open(), rect, range, isLog));
            double yClose = (cd.getY(q0.close(), rect, range, isLog));
            double yHigh = (cd.getY(q0.high(), rect, range, isLog));
            double yLow = (cd.getY(q0.low(), rect, range, isLog));
            double candleHeight =  Math.abs(yOpen - yClose);
            boolean bearish = q0.isBearish();
            
            // draw the candle upper wick
            line2d.setLine(x, (bearish? yOpen : yClose), x, yHigh);
            g.draw(line2d);
            
            // draw the candle lower wick
            line2d.setLine(x, (bearish? yClose : yOpen), x, yLow);
            g.draw(line2d);
            
            // draw the candle body outline (edge)
            rect2d.setRect(x - candleWidth/2, (bearish? yOpen : yClose), candleWidth, candleHeight);
            g.draw(rect2d);
        }
    }
    
    private void drawSubpixelOptimizedChart(Graphics2D g, ChartData cd, ChartProperties cp, Rectangle rect, Range range, int candleWidth) {
        if (cp.getBarVisibility()) {
            Rectangle2D.Double rect2 = new Rectangle2D.Double(rect.x, rect.y, rect.width, rect.height);
            VisibleCandles dataset = cd.getVisible().getVisibleDataset(cd.getChartDataset());
            boolean isLog = cp.getAxisLogarithmicFlag();
            
            g.setPaint(cp.getBarColor());
            g.setStroke(cp.getBarStroke());
            
            int xPrev = -1;
            double qMax = Double.NEGATIVE_INFINITY, qMin = Double.MAX_VALUE;
            GeneralPath path = new GeneralPath(GeneralPath.WIND_NON_ZERO, rect.width);
            for (int i = 0, j = dataset.getLength(); i < j; i++) {
                Candle q0 = dataset.getQuoteAt(i);
                double qHigh = q0.high(), qLow = q0.low();
                
                int x = (int)(0.5 + cd.getX(i, rect));
                if (x != xPrev || qHigh < qMin || qLow > qMax) {
                    // project y-coordinates and draw a vertical tick
                    if (xPrev >= 0) {
                        int yMax = (int)(0.5 + cd.getY2(qMax, rect2, range, isLog));
                        int yMin = (int)(0.5 + cd.getY2(qMin, rect2, range, isLog));
                        path.moveTo(xPrev, yMin);
                        path.lineTo(xPrev, yMax);
                    }
                    
                    qMax = qHigh;
                    qMin = qLow;
                    xPrev = x;
                } else {
                    // coerce current bar data with the previous data
                    if (qHigh > qMax)
                        qMax = qHigh;
                    if (qLow < qMin)
                        qMin = qLow;
                }
            }
            
            // draw the remaining aggregated bar
            if (xPrev >= 0) {
                int yMax = (int)(0.5 + cd.getY(qMax, rect, range, isLog));
                int yMin = (int)(0.5 + cd.getY(qMin, rect, range, isLog));
                path.moveTo(xPrev, yMin);
                path.lineTo(xPrev, yMax);
            }
            g.draw(path);
        }
    }
    
    private void paintWithHint(Graphics2D g, ChartContext cf, DataRenderingHint hint) {
        ChartData cd = cf.getChartData();
        ChartProperties cp = cf.getChartProperties();
        ChartPanel panel = cf.getMainPanel().getChartPanel();
        boolean isLog = cp.getAxisLogarithmicFlag();
        Rectangle rect = panel.getBounds();
        Insets insets = panel.getInsets();
        Range range = panel.getRange();
        
        if (!cd.isVisibleNull()) {
            VisibleCandles dataset = cd.getVisible().getVisibleDataset(cd.getChartDataset());
            for (int i = 0; i < dataset.getLength(); i++) {
                Candle q0 = dataset.getQuoteAt(i);
                double open = q0.open();
                double close = q0.close();
                double high = q0.high();
                double low = q0.low();
                
                double x = cd.getX(i, rect);
                double yOpen = cd.getY(open, range, rect, insets, isLog);
                double yClose = cd.getY(close, range, rect, insets, isLog);
                double yHigh = cd.getY(high, range, rect, insets, isLog);
                double yLow = cd.getY(low, range, rect, insets, isLog);
                
                double candleWidth = cp.getBarWidth();
                double candleHeight = Math.abs(yOpen - yClose);
                
                PlotStyle style = hint.getStyle(q0.getTime(), null);
                if (style != null) {
                    style.draw(g, CoordCalc.line(x, (open > close ? yOpen
                            : yClose), x, yHigh));
                    style.draw(g, CoordCalc.line(x, (open > close ? yClose
                            : yOpen), x, yLow));
                    style.draw(g, CoordCalc.rectangle(x - candleWidth / 2,
                            (open > close ? yOpen : yClose), candleWidth,
                            candleHeight));
                    style.fill(g, CoordCalc.rectangle(x - candleWidth / 2,
                            (open > close ? yOpen : yClose), candleWidth,
                            candleHeight));
                }
            }
        }
    }
}
