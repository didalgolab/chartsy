/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.TimeFrameHelper;
import one.chartsy.charting.LabelRenderer;
import one.chartsy.charting.Scale;
import one.chartsy.ui.chart.components.AnnotationPanel;
import one.chartsy.ui.chart.components.ChartPanel;
import one.chartsy.ui.chart.components.ChartStackPanel;
import one.chartsy.ui.chart.components.IndicatorPanel;
import one.chartsy.ui.chart.components.SharedDateAxisFooter;
import one.chartsy.ui.chart.hover.HoverEvent;
import one.chartsy.ui.chart.hover.QuoteHoverListener;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.SwingUtilities;
import javax.swing.plaf.LayerUI;
import java.awt.AWTEvent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.geom.Dimension2D;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.ConcurrentModificationException;

/**
 * Provides the crosshair cursor functionality for a component being decorated.
 * <p>
 * The crosshair is rendered on the top of the underlying component render.
 *
 * @author Mariusz Bernacki
 */
public class StandardCrosshairRendererLayer extends LayerUI<JComponent> {
    private static final Color CROSSHAIR_LINE_COLOR = new Color(0x2E3436);
    private static final Color CROSSHAIR_LABEL_BACKGROUND = new Color(0x2D8CFF);
    private static final Color CROSSHAIR_LABEL_BORDER = new Color(0x1666C5);
    private static final Color CROSSHAIR_LABEL_FOREGROUND = Color.WHITE;

    private final Stroke lineStroke = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            0, new float[] {4}, 0);
    private final DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
    private final DecimalFormat preciseDecimalFormat = new DecimalFormat("#,##0.0000");
    private final LabelRenderer valueAnnotationRenderer = createAnnotationRenderer();
    private final Point hoverPoint = new Point(-1, -1);
    private Candle hoverCandle;
    private ValueLabelOverlay valueLabelOverlay;

    @Override
    public void eventDispatched(AWTEvent e, JLayer<? extends JComponent> layer) {
        super.eventDispatched(e, layer);

        if (!(e instanceof MouseEvent event))
            return;

        switch (event.getID()) {
        case MouseEvent.MOUSE_ENTERED:
        case MouseEvent.MOUSE_MOVED:
        case MouseEvent.MOUSE_DRAGGED:
            if (event.getComponent() instanceof AnnotationPanel pane)
                handleCrosshairMove(event, pane, layer);
            break;
        case MouseEvent.MOUSE_EXITED:
        case MouseEvent.MOUSE_PRESSED:
            clearHover(layer, event.getComponent() instanceof AnnotationPanel pane ? pane.getChartFrame() : null);
            break;
        }
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        super.paint(g, c);

        if (hoverPoint.x < 0 && valueLabelOverlay == null)
            return;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (hoverPoint.x >= 0) {
                configureCrosshairGraphics(g2);
                drawCrossLines(g2, c);
            }
        } finally {
            g2.dispose();
        }
        if (valueLabelOverlay != null)
            paintValueLabel((Graphics2D) g, c);
    }

    public void drawCrossLines(int x, int y, JLayer<?> layer) {
        int x0 = hoverPoint.x;
        int y0 = hoverPoint.y;
        if (x0 < 0 && x < 0)
            return;

        Graphics2D g2 = (Graphics2D) layer.getGraphics();
        if (g2 == null) {
            hoverPoint.setLocation(x, y);
            layer.repaint();
            return;
        }
        try {
            configureCrosshairGraphics(g2);
            if (x0 >= 0)
                drawCrossLines(g2, layer, x0, y0);
            hoverPoint.setLocation(x, y);
            if (x >= 0)
                drawCrossLines(g2, layer, x, y);
        } finally {
            g2.dispose();
        }
        layer.getToolkit().sync();
    }

    protected void drawCrossLines(Graphics g, JComponent c) {
        drawCrossLines(g, c, hoverPoint.x, hoverPoint.y);
    }

    private void drawCrossLines(Graphics g, JComponent c, int x, int y) {
        if (x < 0 || y < 0)
            return;
        g.drawLine(x, 0, x, c.getHeight());
        g.drawLine(0, y, c.getWidth(), y);
    }

    private void configureCrosshairGraphics(Graphics2D g2) {
        g2.setStroke(lineStroke);
        g2.setColor(CROSSHAIR_LINE_COLOR);
        g2.setXORMode(Color.WHITE);
    }

    private void handleCrosshairMove(MouseEvent e, AnnotationPanel pane, JLayer<? extends JComponent> layer) {
        Rectangle plotBounds = pane.getRenderBounds();
        if (plotBounds.isEmpty() || !plotBounds.contains(e.getPoint())) {
            clearHover(layer, pane.getChartFrame());
            return;
        }

        ChartContext chartFrame = pane.getChartFrame();
        ChartData chartData = chartFrame.getChartData();
        int slot = chartData.getSlotAtX(e.getX(), plotBounds);
        if (slot < 0) {
            clearHover(layer, chartFrame);
            return;
        }

        Candle candle = chartData.getCandleAtSlot(slot);
        if (candle != null) {
            if (candle != hoverCandle) {
                hoverCandle = candle;
                QuoteHoverListener.Broadcaster.mouseEntered(new HoverEvent(chartFrame, candle));
            }
        } else if (hoverCandle != null) {
            hoverCandle = null;
            QuoteHoverListener.Broadcaster.mouseExited(new HoverEvent(chartFrame, null));
        }

        Point mouseLayerPoint = SwingUtilities.convertPoint(pane, e.getPoint(), layer);
        Point layerPoint = new Point(resolveSlotLayerX(chartFrame, chartData, slot, plotBounds, pane, layer), mouseLayerPoint.y);
        int alignedLayerY = updateScaleAnnotations(pane, chartData, slot, e.getY(), layer);
        if (alignedLayerY >= 0)
            layerPoint.y = alignedLayerY;
        drawCrossLines(layerPoint.x, layerPoint.y, layer);
    }

    private void clearHover(JLayer<? extends JComponent> layer, ChartContext chartFrame) {
        if (hoverCandle != null && chartFrame != null)
            QuoteHoverListener.Broadcaster.mouseExited(new HoverEvent(chartFrame, null));
        hoverCandle = null;
        replaceValueLabel(null, layer);
        clearFooterHover(chartFrame);
        drawCrossLines(-1, -1, layer);
    }

    private int updateScaleAnnotations(AnnotationPanel pane,
                                       ChartData chartData,
                                       int slot,
                                       int mouseY,
                                       JLayer<? extends JComponent> layer) {
        updateFooterHover(pane.getChartFrame(), slot);
        return updateValueAnnotation(pane, mouseY, layer);
    }

    private int updateValueAnnotation(AnnotationPanel pane, int mouseY, JLayer<? extends JComponent> layer) {
        ValueScaleHit hit = resolveValueScaleHit(pane, mouseY, layer);
        if (hit == null) {
            replaceValueLabel(null, layer);
            return -1;
        }
        ValueLabelOverlay newOverlay = createValueLabelOverlay(pane, hit, layer);
        replaceValueLabel(newOverlay, layer);
        return hit.layerY();
    }

    private Scale resolveValueScale(AnnotationPanel pane) {
        if (pane.getParent() instanceof ChartPanel chartPanel)
            return chartPanel.getEngineChart().getYScale(0);
        if (pane.getParent() instanceof IndicatorPanel indicatorPanel)
            return indicatorPanel.getEngineChart().getYScale(0);
        return null;
    }

    private ValueScaleHit resolveValueScaleHit(AnnotationPanel pane, int mouseY, JLayer<? extends JComponent> layer) {
        Scale valueScale = resolveValueScale(pane);
        if (valueScale == null || valueScale.getChart() == null || !valueScale.isVisible())
            return null;

        var chartArea = valueScale.getChart().getChartArea();
        if (chartArea == null)
            return null;

        Point chartAreaPoint = SwingUtilities.convertPoint(pane, 0, mouseY, chartArea);
        double value = valueScale.toValue(chartAreaPoint.x, chartAreaPoint.y);
        Point snappedAreaPoint = valueScale.toPoint(value);
        Point snappedLayerPoint = SwingUtilities.convertPoint(chartArea, snappedAreaPoint.x, snappedAreaPoint.y, layer);
        return new ValueScaleHit(valueScale, value, snappedLayerPoint.y);
    }

    private void updateFooterHover(ChartContext chartFrame, int slot) {
        SharedDateAxisFooter footer = chartFrame.getDateAxisFooter();
        if (footer != null)
            footer.setHoverSlot(slot);
    }

    private void clearFooterHover(ChartContext chartFrame) {
        if (chartFrame == null)
            return;
        SharedDateAxisFooter footer = chartFrame.getDateAxisFooter();
        if (footer != null)
            footer.clearHover();
    }

    private int resolveSlotLayerX(ChartContext chartFrame,
                                  ChartData chartData,
                                  int slot,
                                  Rectangle plotBounds,
                                  AnnotationPanel pane,
                                  JLayer<? extends JComponent> layer) {
        Scale timeScale = resolveSharedTimeScale(chartFrame);
        if (timeScale != null && timeScale.getChart() != null && timeScale.getChart().getChartArea() != null) {
            Point chartAreaPoint = timeScale.toPoint(slot);
            Point layerPoint = SwingUtilities.convertPoint(timeScale.getChart().getChartArea(), chartAreaPoint.x, chartAreaPoint.y, layer);
            return layerPoint.x;
        }
        int x = (int) Math.round(chartData.getSlotCenterX(slot, plotBounds));
        return SwingUtilities.convertPoint(pane, x, 0, layer).x;
    }

    private Scale resolveSharedTimeScale(ChartContext chartFrame) {
        if (chartFrame == null || chartFrame.getMainPanel() == null)
            return null;
        ChartStackPanel stackPanel = chartFrame.getMainPanel().getStackPanel();
        return (stackPanel != null) ? stackPanel.getSharedTimeScale() : null;
    }

    private String formatValue(double value) {
        return (value > 9.9999 || value < -9.9999)
                ? decimalFormat.format(value)
                : preciseDecimalFormat.format(value);
    }

    private ValueLabelOverlay createValueLabelOverlay(AnnotationPanel pane,
                                                      ValueScaleHit hit,
                                                      JLayer<? extends JComponent> layer) {
        String label = formatValue(hit.value());
        valueAnnotationRenderer.setFont(ChartFonts.scaleAnnotationFont(pane.getChartFrame().getChartProperties()));
        Rectangle scaleBounds = resolveScaleBounds(hit.scale(), layer);
        if (scaleBounds.isEmpty())
            return null;

        Dimension2D labelSize = valueAnnotationRenderer.getSize2D(layer, label, true, true);
        if (labelSize.getWidth() <= 0.0 || labelSize.getHeight() <= 0.0)
            return null;

        double centerX = resolveLabelCenterX(hit.scale(), scaleBounds, labelSize.getWidth());
        double centerY = hit.layerY();
        Rectangle2D bounds2D = valueAnnotationRenderer.getBounds(layer, centerX, centerY, label, null);
        Rectangle bounds = bounds2D.getBounds();
        return new ValueLabelOverlay(label, centerX, centerY, bounds);
    }

    Rectangle resolveScaleBounds(Scale valueScale, JLayer<? extends JComponent> layer) {
        if (valueScale == null || valueScale.getChart() == null || valueScale.getChart().getChartArea() == null)
            return new Rectangle();

        try {
            valueScale.getChart().updateScalesIfNeeded();
            Rectangle stripBounds = resolveScaleStripBounds(valueScale, layer);
            if (!stripBounds.isEmpty())
                return stripBounds;

            Rectangle2D bounds2D = valueScale.getBoundsUsingCache(null);
            if (bounds2D == null || bounds2D.isEmpty())
                return new Rectangle();

            Rectangle bounds = bounds2D.getBounds();
            return SwingUtilities.convertRectangle(valueScale.getChart().getChartArea(), bounds, layer);
        } catch (ConcurrentModificationException | NullPointerException ex) {
            valueScale.getChart().invalidateScales();
            return new Rectangle();
        }
    }

    Rectangle resolveScaleStripBounds(Scale valueScale, JLayer<? extends JComponent> layer) {
        var chartArea = valueScale.getChart().getChartArea();
        Rectangle plotRect = chartArea.getPlotRect();
        if (plotRect == null || plotRect.isEmpty())
            return new Rectangle();

        Rectangle chartAreaBounds = SwingUtilities.convertRectangle(
                chartArea,
                new Rectangle(0, 0, chartArea.getWidth(), chartArea.getHeight()),
                layer
        );
        Rectangle plotBounds = SwingUtilities.convertRectangle(chartArea, plotRect, layer);
        if (chartAreaBounds.isEmpty() || plotBounds.isEmpty())
            return new Rectangle();

        if (valueScale.getAxis() != null && valueScale.getAxis().isYAxis()) {
            if (valueScale.getSide() >= 0) {
                int x = plotBounds.x + plotBounds.width;
                int width = Math.max(0, chartAreaBounds.x + chartAreaBounds.width - x);
                return new Rectangle(x, chartAreaBounds.y, width, chartAreaBounds.height);
            }

            int width = Math.max(0, plotBounds.x - chartAreaBounds.x);
            return new Rectangle(chartAreaBounds.x, chartAreaBounds.y, width, chartAreaBounds.height);
        }
        return new Rectangle();
    }

    private double resolveLabelCenterX(Scale valueScale, Rectangle scaleBounds, double labelWidth) {
        double boundedWidth = Math.min(labelWidth, Math.max(0.0d, scaleBounds.getWidth() - 2.0d));
        double leftX = (valueScale.getSide() >= 0)
                ? scaleBounds.getX() + 1.0d
                : Math.max(scaleBounds.getX() + 1.0d, scaleBounds.getMaxX() - boundedWidth - 1.0d);
        return leftX + boundedWidth / 2.0d;
    }

    private void replaceValueLabel(ValueLabelOverlay newOverlay, JLayer<? extends JComponent> layer) {
        ValueLabelOverlay oldOverlay = valueLabelOverlay;
        if (equalsOverlay(oldOverlay, newOverlay))
            return;

        valueLabelOverlay = newOverlay;
        Rectangle dirty = null;
        if (oldOverlay != null)
            dirty = new Rectangle(oldOverlay.bounds());
        if (newOverlay != null)
            dirty = (dirty == null) ? new Rectangle(newOverlay.bounds()) : dirty.union(newOverlay.bounds());
        if (dirty != null) {
            dirty.grow(2, 2);
            layer.repaint(dirty.x, dirty.y, dirty.width, dirty.height);
        }
    }

    private boolean equalsOverlay(ValueLabelOverlay left, ValueLabelOverlay right) {
        if (left == right)
            return true;
        if (left == null || right == null)
            return false;
        return left.label().equals(right.label())
                && left.bounds().equals(right.bounds())
                && left.centerX() == right.centerX()
                && left.centerY() == right.centerY();
    }

    private void paintValueLabel(Graphics2D g2, JComponent c) {
        ValueLabelOverlay overlay = valueLabelOverlay;
        if (overlay == null)
            return;
        valueAnnotationRenderer.paintLabel(c, g2, overlay.label(), overlay.centerX(), overlay.centerY());
    }

    private static LabelRenderer createAnnotationRenderer() {
        LabelRenderer renderer = new LabelRenderer(CROSSHAIR_LABEL_BACKGROUND, CROSSHAIR_LABEL_BORDER);
        renderer.setOpaque(true);
        renderer.setColor(CROSSHAIR_LABEL_FOREGROUND);
        renderer.setScalingFont(false);
        renderer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CROSSHAIR_LABEL_BORDER),
                BorderFactory.createEmptyBorder(1, 5, 1, 5)
        ));
        return renderer;
    }

    private record ValueScaleHit(Scale scale, double value, int layerY) {
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

    private record ValueLabelOverlay(String label, double centerX, double centerY, Rectangle bounds) {
    }
}
