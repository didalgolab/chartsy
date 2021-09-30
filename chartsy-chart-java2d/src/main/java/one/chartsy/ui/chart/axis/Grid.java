/* Copyright 2016 by Mariusz Bernacki. PROPRIETARY and CONFIDENTIAL content.
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 * See the file "LICENSE.txt" for the full license governing this code. */
package one.chartsy.ui.chart.axis;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.io.Serializable;

import javax.swing.JPanel;

import one.chartsy.commons.Range;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.ChartData;
import one.chartsy.ui.chart.ChartProperties;
import one.chartsy.ui.chart.components.IndicatorPanel;
import one.chartsy.ui.chart.data.VisualRange;
import one.chartsy.ui.chart.internal.CoordCalc;
import one.chartsy.ui.chart.internal.Graphics2DHelper;

/**
 * Represents the both horizontal and vertical grids visible on the {@code ChartFrame}.
 * 
 * @author Mariusz Bernacki
 */
public class Grid extends JPanel implements Serializable {
    /** The chart frame to which this grid is associated. */
    private final ChartContext chartFrame;
    
    
    public Grid(ChartContext frame) {
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
            Rectangle bounds = chartFrame.getMainPanel().getChartPanel().getBounds();
            Insets insets = chartFrame.getMainPanel().getChartPanel().getInsets();
            Range range = chartFrame.getMainPanel().getChartPanel().getRange();
            double x, y;
            
            // Vertical Grid
            if (cp.getGridVerticalVisibility()) {
                g2.setColor(cp.getGridVerticalColor());
                g2.setStroke(cp.getGridVerticalStroke());
                AxisScale dateScale = cd.getDateAxisScale();
                do {
                    for (int i = 0, markCount = dateScale.getMarkCount(); i < markCount; i++) {
                        double barNo = dateScale.mapMark(i);
                        if (barNo < 0)
                            continue;
                        
                        x = cd.getX(barNo, bounds);
                        g2.draw(CoordCalc.line(x, 0, x, getHeight()));
                    }
                } while ((dateScale = dateScale.getSubScale()) != null);
            }
            
            // Horizontal Grid
            if (cp.getGridHorizontalVisibility()) {
                // paint grid for chart
                g2.setColor(cp.getGridHorizontalColor());
                g2.setStroke(cp.getGridHorizontalStroke());
                FontMetrics fm = getFontMetrics(chartFrame.getChartProperties().getFont());
                double[] list = cd.getYValues(range, bounds, insets, fm.getHeight());
                for (int i = 0; i < list.length; i++) {
                    double value = list[i];
                    y = cd.getY(value, range, bounds, insets, isLog);
                    if (bounds.contains(2, y)) {
                        g2.draw(CoordCalc.line(0, y, getWidth(), y));
                    }
                }
                
                double hy = bounds.getHeight();
                
                // IndicatorChooserDialog Horizontal Grid
                if (chartFrame.getMainPanel().getStackPanel().getIndicatorsCount() > 0) {
                    for (IndicatorPanel panel : chartFrame.getMainPanel().getStackPanel().getIndicatorPanels()) {
                        g2.translate(0, hy);
                        g2.setColor(cp.getGridHorizontalColor());
                        g2.setStroke(cp.getGridHorizontalStroke());
                        
                        if (!panel.isMinimized()) {
                            Rectangle indicatorBounds = panel.getBounds();
                            indicatorBounds.setLocation(0, 0);
                            VisualRange indicatorRange = panel.getIndicator().getRange(chartFrame);
                            
                            if (panel.getIndicator().paintValues()) {
                                double[] stepValues = panel.getIndicator().getStepValues(chartFrame);
                                for (double stepValue : stepValues) {
                                    y = cd.getY(stepValue, indicatorRange.range(), indicatorBounds, panel.getInsets(), indicatorRange.isLogarithmic());
                                    if (indicatorBounds.contains(2, y)) {
                                        g2.draw(CoordCalc.line(0, y, getWidth(), y));
                                    }
                                }
                                stepValues = null;
                            }
                        }
                        hy = panel.getHeight();
                    }
                }
            }
        }
    }
}
