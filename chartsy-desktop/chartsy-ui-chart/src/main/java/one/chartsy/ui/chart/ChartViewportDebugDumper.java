/*
 * Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0
 */
package one.chartsy.ui.chart;

import one.chartsy.ui.chart.components.ChartPanel;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

public final class ChartViewportDebugDumper {
    private static final AtomicReference<String> LAST_DUMP = new AtomicReference<>("");
    private ChartViewportDebugDumper() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean("chartsy.debug.viewportDump");
    }

    public static void dump(ChartContext chartFrame, ChartPanel chartPanel, double paintScaleX) {
        if (!isEnabled() || chartFrame == null || chartPanel == null)
            return;

        ChartData chartData = chartFrame.getChartData();
        if (chartData == null || !chartData.hasDataset())
            return;

        Rectangle plotBounds = chartPanel.getRenderBounds();
        if (plotBounds.width <= 0 || plotBounds.height <= 0)
            return;

        GraphicsConfiguration configuration = chartPanel.getGraphicsConfiguration();
        double graphicsScaleX = 1.0;
        if (configuration != null) {
            graphicsScaleX = configuration.getDefaultTransform().getScaleX();
            if (!Double.isFinite(graphicsScaleX) || graphicsScaleX <= 0.0)
                graphicsScaleX = 1.0;
        }

        double bodyWidth = chartFrame.getChartProperties().getBarWidth();
        int snappedBodyWidth = PixelPerfectCandleGeometry.snapBodyWidth(bodyWidth);
        int slotStep = PixelPerfectCandleGeometry.slotStep(snappedBodyWidth);
        int visibleStart = chartData.getVisibleStartSlot();
        int visibleCount = chartData.getVisibleSlotCount();
        int sampleCount = Math.min(12, visibleCount);

        StringBuilder samples = new StringBuilder();
        for (int i = 0; i < sampleCount; i++) {
            int slot = visibleStart + i;
            double logicalCenter = chartData.getSlotCenterX(slot, plotBounds);
            long deviceCenter = Math.round((logicalCenter - plotBounds.x) * paintScaleX);
            if (i > 0)
                samples.append(", ");
            samples.append(slot).append(':').append(deviceCenter);
        }

        StringBuilder gaps = new StringBuilder();
        for (int i = 1; i < sampleCount; i++) {
            int previousSlot = visibleStart + i - 1;
            int slot = visibleStart + i;
            double previousCenter = chartData.getSlotCenterX(previousSlot, plotBounds);
            double center = chartData.getSlotCenterX(slot, plotBounds);
            long previousDeviceCenter = Math.round((previousCenter - plotBounds.x) * paintScaleX);
            long deviceCenter = Math.round((center - plotBounds.x) * paintScaleX);
            if (i > 1)
                gaps.append(", ");
            gaps.append(deviceCenter - previousDeviceCenter);
        }

        var panelTransform = chartPanel.getGraphicsConfiguration() != null
                ? chartPanel.getGraphicsConfiguration().getDefaultTransform()
                : null;
        String dump = """
                timestamp=%s
                paintScaleX=%s
                graphicsScaleX=%s
                graphicsTranslateX=%s
                graphicsTranslateY=%s
                panelWidth=%d
                plotBounds=%s
                barWidth=%s
                snappedBodyWidth=%d
                slotStep=%d
                visibleStart=%d
                visibleCount=%d
                leadingSlotPadding=%s
                centers=%s
                centerGaps=%s
                """.formatted(
                Instant.now(),
                format(paintScaleX),
                format(graphicsScaleX),
                format(panelTransform != null ? panelTransform.getTranslateX() : 0.0),
                format(panelTransform != null ? panelTransform.getTranslateY() : 0.0),
                chartPanel.getWidth(),
                plotBounds,
                format(bodyWidth),
                snappedBodyWidth,
                slotStep,
                visibleStart,
                visibleCount,
                format(chartData.getLeadingSlotPadding()),
                samples,
                gaps);

        if (dump.equals(LAST_DUMP.get()))
            return;
        LAST_DUMP.set(dump);
        try {
            Path dumpPath = dumpPath();
            Files.createDirectories(dumpPath.getParent());
            Files.writeString(dumpPath, dump, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static void capture(ChartPanel chartPanel, Rectangle plotBounds, String signature) {
        if (!isEnabled() || chartPanel == null || plotBounds.width <= 0 || plotBounds.height <= 0)
            return;
        SwingUtilities.invokeLater(() -> {
            if (!chartPanel.isDisplayable() || chartPanel.getWidth() <= 0 || chartPanel.getHeight() <= 0)
                return;
            try {
                BufferedImage source = new BufferedImage(chartPanel.getWidth(), chartPanel.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = source.createGraphics();
                try {
                    chartPanel.printAll(g2);
                } finally {
                    g2.dispose();
                }

                Rectangle cropBounds = cropBounds(plotBounds, source.getWidth(), source.getHeight());
                BufferedImage crop = source.getSubimage(cropBounds.x, cropBounds.y, cropBounds.width, cropBounds.height);
                BufferedImage zoom = new BufferedImage(crop.getWidth() * 8, crop.getHeight() * 8, BufferedImage.TYPE_INT_ARGB);
                Graphics2D zoomGraphics = zoom.createGraphics();
                try {
                    zoomGraphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                    zoomGraphics.drawImage(crop, 0, 0, zoom.getWidth(), zoom.getHeight(), null);
                } finally {
                    zoomGraphics.dispose();
                }

                Path dumpPath = dumpPath();
                Path dumpDirectory = dumpPath.getParent();
                Files.createDirectories(dumpDirectory);
                ImageIO.write(crop, "png", dumpDirectory.resolve("live-chart-crop.png").toFile());
                ImageIO.write(zoom, "png", dumpDirectory.resolve("live-chart-crop-8x.png").toFile());
            } catch (IOException ignored) {
            }
        });
    }

    private static Path dumpPath() {
        String configuredPath = System.getProperty("chartsy.debug.viewportDump.path", "").trim();
        if (!configuredPath.isEmpty())
            return Path.of(configuredPath).toAbsolutePath();
        return Path.of("tmp", "live-chart-viewport.txt").toAbsolutePath();
    }

    private static Rectangle cropBounds(Rectangle plotBounds, int imageWidth, int imageHeight) {
        int width = Math.min(220, plotBounds.width);
        int height = Math.min(180, plotBounds.height);
        int x = Math.clamp(plotBounds.x + plotBounds.width - width, 0, Math.max(0, imageWidth - width));
        int y = Math.clamp(plotBounds.y, 0, Math.max(0, imageHeight - height));
        return new Rectangle(x, y, width, height);
    }

    private static String format(double value) {
        if (!Double.isFinite(value))
            return "NaN";
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
