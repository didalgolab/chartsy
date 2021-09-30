/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.axis;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.font.LineMetrics;
import java.io.Serializable;
import java.text.DecimalFormat;

import javax.swing.JPanel;

import one.chartsy.Candle;
import one.chartsy.commons.Range;
import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.components.ChartPanel;
import one.chartsy.ui.chart.components.IndicatorPanel;
import one.chartsy.ui.chart.data.VisualRange;
import one.chartsy.ui.chart.internal.CoordCalc;
import one.chartsy.ui.chart.internal.Graphics2DHelper;

/**
 * Represents the value (or price) axis associated with a {@code ChartFrame}.
 * 
 * TODO: rename to ValueAxis/YAxis
 * @author Mariusz Bernacki
 */
public class PriceAxis extends JPanel implements Serializable {
    /** The chart frame to which this value axis is associated. */
    private final ChartContext chartFrame;
    
    
    public PriceAxis(ChartContext frame) {
        chartFrame = frame;
        setOpaque(false);
        setDoubleBuffered(true);
    }
    
    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = Graphics2DHelper.prepareGraphics2D(g);
        
        ChartData cd = chartFrame.getChartData();
        ChartProperties cp = chartFrame.getChartProperties();
        boolean isLog = cp.getAxisLogarithmicFlag();
        
        if (!cd.isVisibleNull() && cd.getVisible().getLength() > 0) {
            // paint values for chart
            ChartPanel chartPanel = chartFrame.getMainPanel().getChartPanel();
            int indicatorsHeight = chartFrame.getMainPanel().getStackPanel().getHeight();
            Range chartRange = chartPanel.getRange();
            FontMetrics fm = getFontMetrics(chartFrame.getChartProperties().getFont());
            
            g2.setFont(cp.getFont());
            g2.translate(0, 0);
            g2.setPaint(cp.getAxisColor());
            g2.setStroke(cp.getAxisStroke());
            g2.drawLine(0, 0, 0, chartPanel.getHeight() + indicatorsHeight);
            
            g2.translate(0, chartPanel.getY());
            Rectangle bounds = chartPanel.getBounds();
            Insets insets = chartPanel.getInsets();
            
            double[] values = cd.getYValues(chartRange, bounds, insets, fm.getHeight());
            double axisTick = cp.getAxisTick();
            double axisStick = cp.getAxisPriceStick();
            double y;
            
            g.setFont(cp.getFont());
            LineMetrics lm = cp.getFont().getLineMetrics("0123456789", g2.getFontRenderContext());
            DecimalFormat df = new DecimalFormat("#,###.####");
            
            for (int i = 0; i < values.length; i++) {
                double value = values[i];
                y = cd.getY(value, chartRange, bounds, insets, isLog);
                if (bounds.contains(bounds.getCenterX(), y)) {
                    g2.setPaint(cp.getAxisColor());
                    g2.draw(CoordCalc.line(0, y, axisTick, y));
                    g2.setPaint(cp.getFontColor());
                    g2.drawString(df.format(value), (float) (axisTick + axisStick), (float) (y + lm.getDescent()));
                }
            }
            
            // paint chart marker
            Candle q0 = cd.getVisible().getLastQuote();
            double open = q0.open();
            double close = q0.close();
            y = cd.getY(close, chartRange, bounds, insets, isLog);
            Color markerColor = open > close ? cp.getBarDownColor() : cp.getBarUpColor();
            if (Color.WHITE.equals(markerColor))
                markerColor = Color.yellow;
            PriceAxisMarker.getInstance().paint(g2, chartFrame, close, markerColor, y);
            
            // paint overlays marker
            if (chartFrame.getMainPanel().getStackPanel().getChartPanel().getOverlaysCount() > 0) {
                for (Overlay overlay : chartFrame.getMainPanel().getStackPanel().getChartPanel().getOverlays()) {
                    if (overlay.getMarkerVisibility()) {
                        double[] ds = overlay.getValues(chartFrame);
                        if (ds.length > 0) {
                            Color[] cs = overlay.getColors();
                            for (int i = 0; i < ds.length; i++) {
                                y = cd.getY(ds[i], chartRange, bounds, insets, isLog);
                                PriceAxisMarker.getInstance().paint(g2, chartFrame, ds[i], cs[i], y);
                            }
                        }
                    }
                }
            }
            
            g2.translate(0, -chartFrame.getMainPanel().getChartPanel().getY());
            
            // paint values for indicators
            if (chartFrame.getMainPanel().getStackPanel().getIndicatorsCount() > 0) {
                for (IndicatorPanel panel : chartFrame.getMainPanel().getStackPanel().getIndicatorPanels()) {
                    if (!panel.isMinimized()) {
                        g2.translate(0, panel.getY());
                        
                        Rectangle indicatorBounds = panel.getBounds();
                        indicatorBounds.setLocation(0, 0);
                        VisualRange indicatorRange = panel.getIndicator().getRange(chartFrame);
                        
                        if (panel.getIndicator().paintValues()) {
                            double[] stepValues = panel.getIndicator().getStepValues(chartFrame);
                            for (double stepValue : stepValues) {
                                y = cd.getY(stepValue, indicatorBounds, indicatorRange.range(), indicatorRange.isLogarithmic());
                                if (indicatorBounds.contains(indicatorBounds.getCenterX(), y)) {
                                    g2.setPaint(cp.getAxisColor());
                                    g2.draw(CoordCalc.line(0, y, axisTick, y));
                                    g2.setPaint(cp.getFontColor());
                                    g2.drawString(df.format(stepValue), (float) (axisTick + axisStick), (float) (y + lm.getDescent()));
                                }
                            }
                            stepValues = null;
                            
                            // paint zero axis tick
                            if (indicatorRange.getMin() < 0 && indicatorRange.getMax() > 0) {
                                y = cd.getY(0, indicatorBounds, indicatorRange.range(), indicatorRange.isLogarithmic());
                                g2.setPaint(cp.getAxisColor());
                                g2.draw(CoordCalc.line(0, y, axisTick, y));
                                g2.setPaint(cp.getFontColor());
                                g2.drawString(df.format(0.0), (float) (axisTick + axisStick), (float) (y + lm.getDescent()));
                            }
                            
                            // paint indicators marker
                            if (panel.getIndicator().getMarkerVisibility()) {
                                double[] ds = panel.getIndicator().getValues(chartFrame);
                                if (ds.length > 0) {
                                    Color[] cs = panel.getIndicator().getColors();
                                    for (int i = 0; i < ds.length; i++) {
                                        y = cd.getY(ds[i], indicatorBounds, indicatorRange.range(), indicatorRange.isLogarithmic());
                                        if (cs[i] != null)
                                            PriceAxisMarker.getInstance().paint(g2, chartFrame, ds[i], cs[i], y);
                                    }
                                }
                            }
                        }
                        indicatorBounds.grow(2, 2);
                        g2.translate(0, -panel.getY());
                    }
                }
            }
        }
    }
}
