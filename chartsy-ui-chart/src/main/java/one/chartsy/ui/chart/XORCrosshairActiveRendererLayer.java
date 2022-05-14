/* Copyright 2022 Mariusz Bernacki <info@softignition.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import java.awt.AWTEvent;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.plaf.LayerUI;

import one.chartsy.Candle;
import one.chartsy.ui.chart.components.AnnotationPanel;
import one.chartsy.ui.chart.components.MainPanel;
import one.chartsy.ui.chart.hover.HoverEvent;
import one.chartsy.ui.chart.hover.QuoteHoverListener;
import one.chartsy.util.Pair;

/**
 * Provides the crosshair cursor functionality for a component being decorated.
 * <p>
 * The crosshair is actively rendered onscreen using the {@code Graphics} XOR mode.
 * 
 * @author Mariusz Bernacki
 *
 */
public class XORCrosshairActiveRendererLayer extends LayerUI<JComponent> {

    @Override
    public void eventDispatched(AWTEvent e, JLayer<? extends JComponent> layer) {
        super.eventDispatched(e, layer);
        
        switch (e.getID()) {
        case MouseEvent.MOUSE_ENTERED:
            if (((MouseEvent) e).getButton() != MouseEvent.NOBUTTON)
                break;
        case MouseEvent.MOUSE_MOVED:
            MouseEvent event = (MouseEvent) e;
            if (event.getComponent() instanceof AnnotationPanel) {
                Pair<AnnotationPanel, Rectangle> hover = getHoverComponent((AnnotationPanel) event.getComponent());
                if (hover != null)
                    handleCrosshairMove(event, hover.getLeft(), layer, hover.getRight());
            }
            break;
        case MouseEvent.MOUSE_EXITED:
        case MouseEvent.MOUSE_PRESSED:
            hoverCandle = null;
            drawCrossLines(-1, -1, layer);
            break;
        }
    }
    
    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);
        
        if (hoverPoint.x >= 0) {
            Graphics2D g2 = (Graphics2D) g;
            
            g2.setColor(Color.BLACK);
            g2.setXORMode(Color.DARK_GRAY);
            drawCrossLines(g2);
            g2.setPaintMode();
        }
    }
    
    int drawnLineX = -1, drawnLineY = -1;
    
    public void drawCrossLines(int x, int y, JLayer<?> layer) {
        int x0 = hoverPoint.x, y0 = hoverPoint.y;
        if (x0 < 0 && x < 0) {
            return;
        }
        
        int width = layer.getWidth(), height = layer.getHeight();
        Graphics2D g2 = (Graphics2D) layer.getGraphics();
        g2.setColor(Color.BLACK);
        g2.setXORMode(Color.DARK_GRAY);
        hoverPoint.setLocation(x, y);
        if (x0 != x) {
            if (x0 > -1)
                g2.drawLine(x0, 0, x0, height-1);
            if (x > -1)
                g2.drawLine(x, 0, x, height-1);
        }
        if (y0 != y) {
            if (y0 > -1)
                g2.drawLine(0, y0, width-1, y0);
            if (y > -1)
                g2.drawLine(0, y, width-1, y);
        }
        g2.dispose();
        layer.getToolkit().sync();
    }
    
    protected void drawCrossLines(Graphics g) {
        Rectangle bounds = g.getClipBounds();
        if (bounds == null)
            return;
        
        Point hoverPoint = this.hoverPoint;
        if (hoverPoint.x >= 0) {
            g.drawLine(hoverPoint.x, bounds.y, hoverPoint.x, bounds.y + bounds.height);
            g.drawLine(bounds.x, hoverPoint.y, bounds.x + bounds.width, hoverPoint.y);
        }
    }
    
    /** The currently hovered annotation panel and its relative bounds. */
    private Pair<AnnotationPanel, Rectangle> hoverComponent;
    
    private Pair<AnnotationPanel, Rectangle> getHoverComponent(AnnotationPanel p) {
        // check match with previously found component
        if (hoverComponent != null && hoverComponent.getLeft() == p)
            return hoverComponent;
        
        // traverse up through component hierarchy looking for ChartSplitPanel's bounds and relative location
        Rectangle rect = new Rectangle(p.getX(), p.getY(), p.getWidth(), p.getHeight());
        Component pane = p;
        while ((pane = pane.getParent()) != null) {
            rect.setSize(pane.getWidth(), pane.getHeight()); // TODO move after IF?
            if (pane instanceof MainPanel)
                break;
            rect.translate(pane.getX(), pane.getY());
        }
        
        // rollback to this bounds when MainPanel was not found
        if (pane == null)
            return null;
        return (hoverComponent = Pair.of(p, rect));
    }
    
    private Candle hoverCandle;
    private final Point hoverPoint = new Point(-1, -1);
    
    private void handleCrosshairMove(MouseEvent e, AnnotationPanel pane, JLayer<? extends JComponent> layer, Rectangle bounds) {
        // tooltipHandler(e);
        
        Rectangle rect = pane.getBounds();
        rect.translate(2, 0);
        
        ChartContext chartFrame = pane.getChartFrame();
        int index = pane.getChartFrame().getChartData().getIndex(e.getX(), e.getY(), rect);
        
        if (index < 0) {
            hoverCandle = null;
            QuoteHoverListener.Broadcaster.mouseExited(new HoverEvent(chartFrame, null));
            
            drawCrossLines(-1, -1, layer);
        } else {
            Candle q0 = chartFrame.getChartData().getVisible().getQuoteAt(index);
            Point hoverPoint = this.hoverPoint;
            if (q0 != null && q0 != hoverCandle) {
                hoverCandle = q0;
                QuoteHoverListener.Broadcaster.mouseEntered(new HoverEvent(chartFrame, q0));
                
                int x = (int)(0.5 + chartFrame.getChartData().getX(index, rect));
                drawCrossLines(x + bounds.x, e.getY() + bounds.y, layer);
            } else if (hoverPoint.x >= 0) {
                drawCrossLines(hoverPoint.x, e.getY() + bounds.y, layer);
            }
        }
    }
    
    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        
        @SuppressWarnings("unchecked")
        JLayer<JComponent> layer = (JLayer<JComponent>) c;
        layer.setLayerEventMask(AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    }
    
    @Override
    public void uninstallUI(JComponent c) {
        super.uninstallUI(c);
        
        @SuppressWarnings("unchecked")
        JLayer<JComponent> layer = (JLayer<JComponent>) c;
        layer.setLayerEventMask(0);
    }
}
