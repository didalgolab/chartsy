/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart.components;

import one.chartsy.charting.Scale;
import one.chartsy.charting.DataInterval;
import one.chartsy.charting.financial.AdaptiveCategoryTimeSteps;
import one.chartsy.ui.chart.ChartContext;
import one.chartsy.ui.chart.ChartFonts;
import one.chartsy.ui.chart.ChartProperties;
import one.chartsy.ui.chart.internal.Graphics2DHelper;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

public final class SharedDateAxisFooter extends JComponent {
    private static final Color CROSSHAIR_LABEL_BACKGROUND = new Color(0x2D8CFF);
    private static final Color CROSSHAIR_LABEL_BORDER = new Color(0x1666C5);
    private static final Color CROSSHAIR_LABEL_FOREGROUND = Color.WHITE;
    private static final int LOWER_BASELINE_NUDGE = -1;
    private static final int ANNOTATION_Y_NUDGE = -2;

    private final ChartContext chartFrame;
    private volatile int hoverSlot = -1;
    private volatile FooterSnapshot cachedSnapshot = FooterSnapshot.empty();
    private volatile FooterCacheKey cachedKey = FooterCacheKey.empty();

    public SharedDateAxisFooter(ChartContext chartFrame) {
        this.chartFrame = chartFrame;
        setOpaque(true);
    }

    public void setHoverSlot(int hoverSlot) {
        if (this.hoverSlot != hoverSlot) {
            this.hoverSlot = hoverSlot;
            repaint();
        }
    }

    public void clearHover() {
        setHoverSlot(-1);
    }

    public FooterSnapshot snapshot() {
        Scale scale = resolveTimeScale();
        Rectangle plotBounds = resolvePlotBounds(scale);
        if (plotBounds.isEmpty() && !cachedSnapshot.plotBounds().isEmpty())
            plotBounds = new Rectangle(cachedSnapshot.plotBounds());
        var chartData = chartFrame.getChartData();
        if (chartData == null || !chartData.hasDataset())
            return FooterSnapshot.empty();

        DataInterval visibleRange = DateAxisFooterModel.resolveVisibleRange(chartFrame, chartData, scale);
        AdaptiveCategoryTimeSteps steps = DateAxisFooterModel.resolveAdaptiveSteps(scale);
        FooterCacheKey key = FooterCacheKey.of(plotBounds, visibleRange, steps);
        FooterSnapshot baseSnapshot = cachedSnapshot;
        if (!key.equals(cachedKey)) {
            FooterSnapshot fresh = DateAxisFooterModel.buildStatic(chartFrame, plotBounds, scale);
            if (!plotBounds.isEmpty() && (!fresh.upperTicks().isEmpty() || !fresh.lowerTicks().isEmpty() || fresh.lowerContextLabel() != null)) {
                cachedSnapshot = fresh;
                cachedKey = key;
                baseSnapshot = fresh;
            }
        }

        var hoverLabel = DateAxisFooterModel.buildHoverLabel(chartData, plotBounds, visibleRange, hoverSlot);
        return baseSnapshot.withHover(hoverLabel);
    }

    @Override
    public java.awt.Dimension getPreferredSize() {
        ChartProperties properties = chartFrame.getChartProperties();
        float upperSize = ChartFonts.footerUpperFont(properties).getSize2D();
        float lowerSize = ChartFonts.footerLowerFont(properties).getSize2D();
        float annotationSize = ChartFonts.scaleAnnotationFont(properties).getSize2D();
        int height = Math.max(38, Math.round(upperSize + lowerSize + annotationSize + 6.0f));
        return new java.awt.Dimension(0, height);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = Graphics2DHelper.prepareGraphics2D(g);
        ChartProperties properties = chartFrame.getChartProperties();

        g2.setColor(properties.getBackgroundColor());
        g2.fillRect(0, 0, getWidth(), getHeight());

        FooterSnapshot snapshot = snapshot();
        Rectangle plotBounds = snapshot.plotBounds();
        g2.setColor(properties.getAxisColor());
        g2.setStroke(properties.getAxisStroke());
        g2.drawLine(0, 0, getWidth(), 0);

        if (plotBounds.isEmpty())
            return;

        Graphics2D plotGraphics = (Graphics2D) g2.create();
        try {
            plotGraphics.clipRect(plotBounds.x, 0, plotBounds.width, getHeight());
            paintTicks(plotGraphics, snapshot, properties);
            paintHoverLabel(plotGraphics, snapshot, properties);
        } finally {
            plotGraphics.dispose();
        }
    }

    private void paintTicks(Graphics2D g2, FooterSnapshot snapshot, ChartProperties properties) {
        Font upperFont = ChartFonts.footerUpperFont(properties);
        Font lowerFont = ChartFonts.footerLowerFont(properties);
        FooterLayout layout = FooterLayout.measure(g2, upperFont, lowerFont, ChartFonts.scaleAnnotationFont(properties), getHeight());

        paintLane(g2, snapshot.plotBounds(), snapshot.upperTicks(), layout.upperFont(), layout.upperMetrics(),
                layout.upperTickTop(), layout.upperTickBottom(), layout.upperBaseline(), properties);
        paintLowerLane(g2, snapshot, layout.lowerFont(), layout.lowerMetrics(),
                layout.lowerTickTop(), layout.lowerTickBottom(), layout.lowerBaseline(), properties);
    }

    private void paintLane(Graphics2D g2,
                           Rectangle plotBounds,
                           List<TickMark> ticks,
                           Font font,
                           FontMetrics metrics,
                           int tickTop,
                           int tickBottom,
                           int textBaseline,
                           ChartProperties properties) {
        g2.setFont(font);
        int lastLabelRight = Integer.MIN_VALUE;
        for (TickMark tick : ticks) {
            g2.setColor(properties.getAxisColor());
            g2.drawLine(tick.x(), tickTop, tick.x(), tickBottom);

            int labelWidth = metrics.stringWidth(tick.label());
            int labelX = Math.max(plotBounds.x + 2, tick.labelX());
            int labelRight = labelX + labelWidth;
            if (labelX > lastLabelRight + 4 && labelRight <= plotBounds.x + plotBounds.width - 2) {
                g2.setColor(properties.getFontColor());
                g2.drawString(tick.label(), labelX, textBaseline);
                lastLabelRight = labelRight;
            }
        }
    }

    private void paintLowerLane(Graphics2D g2,
                                FooterSnapshot snapshot,
                                Font font,
                                FontMetrics metrics,
                                int tickTop,
                                int tickBottom,
                                int textBaseline,
                                ChartProperties properties) {
        Rectangle plotBounds = snapshot.plotBounds();
        g2.setFont(font);
        int lastLabelRight = Integer.MIN_VALUE;

        ContextLabel contextLabel = snapshot.lowerContextLabel();
        if (contextLabel != null) {
            int labelX = Math.max(plotBounds.x + 2, contextLabel.labelX());
            int labelWidth = metrics.stringWidth(contextLabel.label());
            int labelRight = labelX + labelWidth;
            if (labelRight <= plotBounds.x + plotBounds.width - 2) {
                g2.setColor(properties.getFontColor());
                g2.drawString(contextLabel.label(), labelX, textBaseline);
                lastLabelRight = labelRight;
            }
        }

        for (TickMark tick : snapshot.lowerTicks()) {
            g2.setColor(properties.getAxisColor());
            g2.drawLine(tick.x(), tickTop, tick.x(), tickBottom);

            int labelWidth = metrics.stringWidth(tick.label());
            int labelX = Math.max(plotBounds.x + 2, tick.labelX());
            int labelRight = labelX + labelWidth;
            if (labelX > lastLabelRight + 4 && labelRight <= plotBounds.x + plotBounds.width - 2) {
                g2.setColor(properties.getFontColor());
                g2.drawString(tick.label(), labelX, textBaseline);
                lastLabelRight = labelRight;
            }
        }
    }

    private void paintHoverLabel(Graphics2D g2, FooterSnapshot snapshot, ChartProperties properties) {
        HoverLabel hover = snapshot.hoverLabel();
        if (hover == null)
            return;

        FooterLayout layout = FooterLayout.measure(
                g2,
                ChartFonts.footerUpperFont(properties),
                ChartFonts.footerLowerFont(properties),
                ChartFonts.scaleAnnotationFont(properties),
                getHeight()
        );
        Font font = layout.annotationFont();
        FontMetrics metrics = layout.annotationMetrics();
        int labelWidth = metrics.stringWidth(hover.label()) + 10;
        int labelHeight = metrics.getHeight() + 4;
        Rectangle plotBounds = snapshot.plotBounds();
        int x = Math.clamp(hover.x() - labelWidth / 2, plotBounds.x, plotBounds.x + plotBounds.width - labelWidth);
        int y = Math.clamp(layout.annotationY(), 1, Math.max(1, getHeight() - labelHeight - 1));

        g2.setColor(CROSSHAIR_LABEL_BACKGROUND);
        g2.fillRect(x, y, labelWidth, labelHeight);
        g2.setColor(CROSSHAIR_LABEL_BORDER);
        g2.drawRect(x, y, labelWidth, labelHeight);
        g2.setColor(CROSSHAIR_LABEL_FOREGROUND);
        g2.setFont(font);
        g2.drawString(hover.label(), x + 5, y + 2 + metrics.getAscent());
    }

    private Scale resolveTimeScale() {
        if (chartFrame.getMainPanel() == null)
            return null;
        ChartStackPanel stackPanel = chartFrame.getMainPanel().getStackPanel();
        if (stackPanel == null)
            return null;
        return stackPanel.getSharedTimeScale();
    }

    private Rectangle resolvePlotBounds(Scale scale) {
        if (scale == null || scale.getChart() == null || scale.getChart().getChartArea() == null)
            return new Rectangle();
        var chart = scale.getChart();
        var chartArea = scale.getChart().getChartArea();
        Rectangle plotRect = chartArea.getPlotRect();
        if (plotRect == null || plotRect.isEmpty()) {
            Rectangle drawRect = chartArea.getDrawRect();
            if (drawRect == null || drawRect.isEmpty()) {
                int width = chartArea.getWidth();
                int height = chartArea.getHeight();
                if (width > 0 && height > 0)
                    drawRect = new Rectangle(0, 0, width, height);
                else if (chart.getWidth() > 0 && chart.getHeight() > 0)
                    drawRect = new Rectangle(0, 0, chart.getWidth(), chart.getHeight());
            }
            Insets margins = chartArea.getMargins();
            if (drawRect != null && !drawRect.isEmpty()) {
                int left = (margins != null) ? margins.left : 0;
                int right = (margins != null) ? margins.right : 0;
                plotRect = new Rectangle(
                        drawRect.x + left,
                        drawRect.y,
                        Math.max(1, drawRect.width - left - right),
                        drawRect.height
                );
            }
        }
        if (plotRect == null || plotRect.isEmpty())
            return new Rectangle();
        Component source = (chartArea.getWidth() > 0 || chartArea.getHeight() > 0) ? chartArea : chart;
        Rectangle converted = SwingUtilities.convertRectangle(source, plotRect, this);
        return new Rectangle(converted.x, 0, converted.width, getHeight());
    }

    public record TickMark(double value, int x, int labelX, String label, boolean forced) {
    }

    public record ContextLabel(int labelX, String label) {
    }

    public record HoverLabel(int slot, long epochMicros, int x, String label) {
    }

    public record FooterSnapshot(Rectangle plotBounds,
                                 List<TickMark> upperTicks,
                                 List<TickMark> lowerTicks,
                                 ContextLabel lowerContextLabel,
                                 HoverLabel hoverLabel) {

        static FooterSnapshot empty() {
            return new FooterSnapshot(new Rectangle(), List.of(), List.of(), null, null);
        }

        FooterSnapshot withHover(HoverLabel replacementHover) {
            return new FooterSnapshot(new Rectangle(plotBounds), upperTicks, lowerTicks, lowerContextLabel, replacementHover);
        }

        public String toDebugString() {
            StringBuilder dump = new StringBuilder(1024);
            dump.append("plotBounds=").append(plotBounds).append(System.lineSeparator());
            dump.append("upperTicks=").append(upperTicks.size()).append(System.lineSeparator());
            for (TickMark tick : upperTicks) {
                dump.append("  upper x=").append(tick.x())
                        .append(" labelX=").append(tick.labelX())
                        .append(" value=").append(tick.value())
                        .append(" label=").append(tick.label())
                        .append(System.lineSeparator());
            }
            dump.append("lowerTicks=").append(lowerTicks.size()).append(System.lineSeparator());
            for (TickMark tick : lowerTicks) {
                dump.append("  lower x=").append(tick.x())
                        .append(" labelX=").append(tick.labelX())
                        .append(" value=").append(tick.value())
                        .append(" forced=").append(tick.forced())
                        .append(" label=").append(tick.label())
                        .append(System.lineSeparator());
            }
            dump.append("lowerContext=").append(lowerContextLabel).append(System.lineSeparator());
            dump.append("hover=").append(hoverLabel).append(System.lineSeparator());
            return dump.toString();
        }
    }

    private record FooterLayout(Font upperFont,
                                FontMetrics upperMetrics,
                                int upperTickTop,
                                int upperTickBottom,
                                int upperBaseline,
                                Font lowerFont,
                                FontMetrics lowerMetrics,
                                int lowerTickTop,
                                int lowerTickBottom,
                                int lowerBaseline,
                                Font annotationFont,
                                FontMetrics annotationMetrics,
                                int annotationY) {

        private static FooterLayout measure(Graphics2D g2, Font upperFont, Font lowerFont, Font annotationFont, int height) {
            FontMetrics upperMetrics = g2.getFontMetrics(upperFont);
            FontMetrics lowerMetrics = g2.getFontMetrics(lowerFont);
            FontMetrics annotationMetrics = g2.getFontMetrics(annotationFont);

            int upperBandTop = 1;
            int upperBandBottom = Math.max(upperBandTop + upperMetrics.getHeight() + 2, height / 2 - 1);
            int lowerBandTop = upperBandBottom + 1;
            int lowerBandBottom = Math.max(lowerBandTop + lowerMetrics.getHeight(), height - 2);

            int upperBaseline = baselineWithin(upperBandTop, upperBandBottom, upperMetrics);
            int lowerBaseline = Math.max(lowerBandTop + lowerMetrics.getAscent(), baselineWithin(lowerBandTop, lowerBandBottom, lowerMetrics) + LOWER_BASELINE_NUDGE);
            int upperTickBottom = Math.min(upperBandBottom - 1, upperBaseline + Math.max(1, upperMetrics.getDescent() / 2));
            int lowerTickBottom = Math.min(lowerBandBottom - 1, lowerBaseline + Math.max(1, lowerMetrics.getDescent() / 2));
            int annotationHeight = annotationMetrics.getHeight() + 4;
            int annotationY = Math.clamp(
                    lowerBandTop + (lowerBandBottom - lowerBandTop - annotationHeight) / 2 + ANNOTATION_Y_NUDGE,
                    lowerBandTop,
                    Math.max(lowerBandTop, lowerBandBottom - annotationHeight)
            );

            return new FooterLayout(
                    upperFont,
                    upperMetrics,
                    0,
                    upperTickBottom,
                    upperBaseline,
                    lowerFont,
                    lowerMetrics,
                    lowerBandTop,
                    lowerTickBottom,
                    lowerBaseline,
                    annotationFont,
                    annotationMetrics,
                    annotationY
            );
        }

        private static int baselineWithin(int top, int bottom, FontMetrics metrics) {
            int bandHeight = Math.max(metrics.getHeight(), bottom - top + 1);
            return top + Math.max(metrics.getAscent(), (bandHeight - metrics.getHeight()) / 2 + metrics.getAscent());
        }
    }

    private record FooterCacheKey(int x, int width, double visibleMin, double visibleMax, int stepsIdentity) {
        private static FooterCacheKey empty() {
            return new FooterCacheKey(0, 0, Double.NaN, Double.NaN, 0);
        }

        private static FooterCacheKey of(Rectangle plotBounds, DataInterval visibleRange, AdaptiveCategoryTimeSteps steps) {
            return new FooterCacheKey(
                    (plotBounds != null) ? plotBounds.x : 0,
                    (plotBounds != null) ? plotBounds.width : 0,
                    (visibleRange != null) ? visibleRange.getMin() : Double.NaN,
                    (visibleRange != null) ? visibleRange.getMax() : Double.NaN,
                    System.identityHashCode(steps)
            );
        }
    }
}
