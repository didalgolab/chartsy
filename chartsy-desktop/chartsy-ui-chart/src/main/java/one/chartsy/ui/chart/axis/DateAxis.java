/*
 * Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.axis;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.Calendar;

import javax.swing.JPanel;

import lombok.Getter;
import lombok.Setter;
import one.chartsy.ui.chart.*;
import one.chartsy.ui.chart.components.ChartPanel;
import one.chartsy.ui.chart.internal.CoordCalc;
import one.chartsy.ui.chart.internal.Graphics2DHelper;
import org.openide.util.NbBundle;

/**
 * Represents the date axis associated with the {@code ChartFrame}.
 * 
 * @author Mariusz Bernacki
 */
@Getter
@Setter
public class DateAxis extends JPanel implements Serializable {
    /** The manual axis adjustment cursor. */
    private static final Cursor adjustmentCursor = Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
    /** The chart frame to which this axis is associated. */
    private final ChartContext chartFrame;
    
    private boolean adjusting;
    
    
    public DateAxis(ChartContext frame) {
        chartFrame = frame;
        setOpaque(false);
        setDoubleBuffered(true);
        setUpInteractionManagers();
    }
    
    public void setAdjusting(boolean adjusting) {
        boolean wasAdjusting = isAdjusting();
        if (wasAdjusting != adjusting) {
            this.adjusting = adjusting;
            firePropertyChange("adjusting", wasAdjusting, adjusting);
        }
    }
    
    public boolean isAdjusting() {
        return adjusting;
    }
    
    protected void setUpInteractionManagers() {
        class InteractionManager extends MouseAdapter implements PropertyChangeListener {
            
            @Override
            public void propertyChange(PropertyChangeEvent e) {
                if ("adjusting".equals(e.getPropertyName()))
                    setCursor(Boolean.TRUE.equals(e.getNewValue())? adjustmentCursor: null);
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1)
                    setAdjusting(true);
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                setAdjusting(false);
            }
            
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // reverse back to the predefined bar width and the recent position
                    ChartTemplate chartTemplate = chartFrame.getChartTemplate();
                    if (chartTemplate != null) {
                        ChartProperties properties = chartFrame.getChartProperties();
                        properties.setBarWidth(chartTemplate.getChartProperties().getBarWidth());
                        chartFrame.getChartData().setLast(0);
                        chartFrame.updateHorizontalScrollBar();
                    }
                }
            }
        }
        InteractionManager im = new InteractionManager();
        addMouseListener(im);
        addPropertyChangeListener("adjusting", im);
    }
    
    private AxisScale paintLabels(Graphics2D g2, LineMetrics lm, Rectangle bounds, AxisScale dateScale, Rectangle2D nextLabelBounds, int[] scaleIndexes, int depth) {
        ChartData cd = chartFrame.getChartData();
        ChartProperties cp = chartFrame.getChartProperties();
        FontRenderContext frc = g2.getFontRenderContext();
        AxisScale subScale = dateScale.getSubScale();
        // the finest scale labels shouldn't be pained with bold font
        Font oldFont = g2.getFont();
        if (subScale == null)
            g2.setFont(cp.getFont());
        
        String label = null;
        Rectangle2D previousLabelBounds = null;
        block:
        {
            for (int i = scaleIndexes[depth], j = dateScale.getMarkCount(); i < j; scaleIndexes[depth] = ++i) {
                double barNo = dateScale.mapMark(i);
                boolean tickVisible = (barNo >= 0);
                double x = cd.getX(barNo, bounds);
                
                if (previousLabelBounds != null) {
                    if (previousLabelBounds.getMaxX() + 2 < x) {
                        g2.setColor(cp.getFontColor());
                        g2.drawString(label, (float) (previousLabelBounds.getX() + 5), lm.getAscent());
                    }
                    previousLabelBounds = null;
                }
                
                label = dateScale.getLabelAt(i);
                Rectangle2D labelBounds = cp.getFont().getStringBounds(label, frc);
                double h = labelBounds.getHeight();
                double w = labelBounds.getWidth() + 5;
                labelBounds.setRect(x, labelBounds.getY(), w, h);
                
                if (x > nextLabelBounds.getMaxX() + 2)
                    break block;
                
                if (x < nextLabelBounds.getX() - 2) {
                    if (tickVisible) {
                        g2.setColor(cp.getAxisColor());
                        g2.draw(CoordCalc.line(x, 0, x, h));
                    }
                    previousLabelBounds = labelBounds;
                }
                if (subScale != null)
                    subScale = paintLabels(g2, lm, bounds, subScale, labelBounds, scaleIndexes, depth + 1);
            }
            if (previousLabelBounds != null && previousLabelBounds.getMaxX() + 2 < nextLabelBounds.getX()) {
                g2.setColor(cp.getFontColor());
                g2.drawString(label, (float) (previousLabelBounds.getX() + 5), lm.getAscent());
            }
            if (subScale != null)
                paintLabels(g2, lm, bounds, subScale, nextLabelBounds, scaleIndexes, depth + 1);
        }
        g2.setFont(oldFont);
        return dateScale;
    }
    
    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = Graphics2DHelper.prepareGraphics2D(g);
        ChartData cd = chartFrame.getChartData();
        ChartProperties cp = chartFrame.getChartProperties();
        
        String year = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
        String copy = NbBundle.getMessage(ChartFrame.class, "copyright.notice", year);
        g2.setColor(cp.getFontColor());
        g2.setFont(cp.getFont().deriveFont(Font.BOLD));
        g2.drawString(copy, 0, getHeight() - 5);
        
        if (!cd.isVisibleNull() && cd.getVisible().getLength() > 0) {
            ChartPanel chartPanel = chartFrame.getMainPanel().getChartPanel();
            Rectangle bounds = chartPanel.getBounds(chartPanel.getInsets());
            
            g2.setColor(cp.getAxisColor());
            g2.setStroke(cp.getAxisStroke());
            g2.drawLine(0, 0, chartPanel.getWidth(), 0);
            
            LineMetrics lm = cp.getFont().getLineMetrics("0123456789/", g2.getFontRenderContext());
            
            AxisScale dateScale = cd.getDateAxisScale();
            if (dateScale != null) {
                int k = 1;
                AxisScale s = dateScale;
                while ((s = s.getSubScale()) != null)
                    k++;
                int[] scaleIndexes = new int[k];
                paintLabels(g2, lm, bounds, dateScale, new Rectangle2D.Double(bounds.getMaxX(), 0, 1, 1), scaleIndexes, 0);
            }
        }
    }

    private float heightFactor = 3.3f;

    @Override
    public Dimension getPreferredSize() {
        Dimension preferredSize = super.getPreferredSize();
        preferredSize.height = Math.round(getFont().getSize2D() * getHeightFactor());
        return preferredSize;
    }
}
