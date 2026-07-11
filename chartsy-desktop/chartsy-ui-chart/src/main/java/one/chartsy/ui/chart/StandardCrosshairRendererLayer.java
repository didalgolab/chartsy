/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.charting.LabelRenderer;
import one.chartsy.charting.Scale;
import one.chartsy.charting.util.DevicePixelSnapper;
import one.chartsy.ui.chart.components.AnnotationPanel;
import one.chartsy.ui.chart.components.ChartPanel;
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
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
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
    private static final double CROSSHAIR_DASH_LENGTH = 4.0;

    private final DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
    private final DecimalFormat preciseDecimalFormat = new DecimalFormat("#,##0.0000");
    private final LabelRenderer valueAnnotationRenderer = createAnnotationRenderer();
    private CrosshairPosition hoverPosition;
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

        CrosshairPosition position = hoverPosition;
        if (position == null && valueLabelOverlay == null)
            return;

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            if (position != null) {
                configureCrosshairGraphics(g2);
                paintCrosshair(g2, c, position);
            }
        } finally {
            g2.dispose();
        }
        if (valueLabelOverlay != null)
            paintValueLabel((Graphics2D) g, c);
    }

    private void updateCrosshair(CrosshairPosition newPosition, JLayer<?> layer) {
        CrosshairPosition oldPosition = hoverPosition;
        if (oldPosition == null && newPosition == null)
            return;

        Graphics2D g2 = (Graphics2D) layer.getGraphics();
        if (g2 == null) {
            hoverPosition = newPosition;
            layer.repaint();
            return;
        }
        try {
            configureCrosshairGraphics(g2);
            if (oldPosition != null)
                paintCrosshair(g2, layer, oldPosition);
            hoverPosition = newPosition;
            if (newPosition != null)
                paintCrosshair(g2, layer, newPosition);
        } finally {
            g2.dispose();
        }
        layer.getToolkit().sync();
    }

    private void paintCrosshair(Graphics2D g2, JComponent c, CrosshairPosition position) {
        DevicePixelSnapper devicePixels = new DevicePixelSnapper(g2);
        int top = devicePixels.snapY(0.0);
        int bottom = devicePixels.snapY(c.getHeight());
        int left = devicePixels.snapX(0.0);
        int right = devicePixels.snapX(c.getWidth());
        int dashHeight = devicePixels.deviceHeightRounded(CROSSHAIR_DASH_LENGTH);
        int dashWidth = devicePixels.deviceWidthRounded(CROSSHAIR_DASH_LENGTH);

        Graphics2D deviceGraphics = createDeviceSpaceGraphics(g2);
        try {
            paintVerticalDashes(deviceGraphics, position.xAnchor().deviceX(devicePixels), top, bottom, dashHeight);
            paintHorizontalDashes(deviceGraphics, devicePixels.snapY(position.y()), left, right, dashWidth);
        } finally {
            deviceGraphics.dispose();
        }
    }

    private void paintVerticalDashes(Graphics2D g2,
                                     int x,
                                     int firstY,
                                     int lastY,
                                     int dashSize) {
        int top = Math.min(firstY, lastY);
        int bottom = Math.max(firstY, lastY);
        for (int y = top; y < bottom; y += dashSize * 2) {
            int height = Math.min(dashSize, bottom - y);
            g2.fillRect(x, y, 1, height);
        }
    }

    private void paintHorizontalDashes(Graphics2D g2,
                                       int y,
                                       int firstX,
                                       int lastX,
                                       int dashSize) {
        int left = Math.min(firstX, lastX);
        int right = Math.max(firstX, lastX);
        for (int x = left; x < right; x += dashSize * 2) {
            int width = Math.min(dashSize, right - x);
            g2.fillRect(x, y, width, 1);
        }
    }

    /**
     * Creates an integer device-coordinate view of {@code userGraphics} while preserving its clip.
     *
     * <p>On accelerated Windows surfaces, XOR-filling a one-device-pixel rectangle after mapping
     * it back to a fractional user-space width can rasterize to no pixels at some 125% scaling
     * phases. Painting the dashes directly on the device grid avoids that driver-dependent gap.</p>
     */
    static Graphics2D createDeviceSpaceGraphics(Graphics2D userGraphics) {
        Shape userClip = userGraphics.getClip();
        AffineTransform userToDevice = userGraphics.getTransform();
        Shape deviceClip = (userClip != null) ? userToDevice.createTransformedShape(userClip) : null;

        Graphics2D deviceGraphics = (Graphics2D) userGraphics.create();
        deviceGraphics.setClip(null);
        deviceGraphics.setTransform(new AffineTransform());
        if (deviceClip != null)
            deviceGraphics.clip(deviceClip);
        return deviceGraphics;
    }

    private void configureCrosshairGraphics(Graphics2D g2) {
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
        SlotXAnchor xAnchor = resolveSlotXAnchor(chartData, slot, plotBounds, pane, layer);
        int layerY = mouseLayerPoint.y;
        updateFooterHover(pane.getChartFrame(), slot);
        int alignedLayerY = updateValueAnnotation(pane, e.getY(), layer);
        if (alignedLayerY >= 0)
            layerY = alignedLayerY;
        updateCrosshair(new CrosshairPosition(xAnchor, layerY), layer);
    }

    private void clearHover(JLayer<? extends JComponent> layer, ChartContext chartFrame) {
        if (hoverCandle != null && chartFrame != null)
            QuoteHoverListener.Broadcaster.mouseExited(new HoverEvent(chartFrame, null));
        hoverCandle = null;
        replaceValueLabel(null, layer);
        clearFooterHover(chartFrame);
        updateCrosshair(null, layer);
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

    private SlotXAnchor resolveSlotXAnchor(ChartData chartData,
                                          int slot,
                                          Rectangle plotBounds,
                                          AnnotationPanel pane,
                                          JLayer<? extends JComponent> layer) {
        Point plotOrigin = SwingUtilities.convertPoint(pane, plotBounds.x, plotBounds.y, layer);
        double plotSpan = Math.max(0.0, plotBounds.width - 1.0);
        double fraction = (plotSpan > 0.0)
                ? (chartData.getSlotCenterX(slot, plotBounds) - plotBounds.x) / plotSpan
                : 0.0;
        return new SlotXAnchor(plotOrigin.x, plotOrigin.x + plotSpan, fraction);
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

    private record SlotXAnchor(double startX, double endX, double fraction) {
        private int deviceX(DevicePixelSnapper devicePixels) {
            return devicePixels.interpolateX(startX, endX, fraction);
        }
    }

    private record CrosshairPosition(SlotXAnchor xAnchor, int y) {
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
