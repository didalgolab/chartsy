package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.FrontEndSupport;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.charting.Scale;
import one.chartsy.charting.ScaleAnnotation;
import one.chartsy.core.Range;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.ui.chart.type.CandlestickChart;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChartFrameRightEdgeSpacingTest {
    private static final int PLOT_EDGE_GAP_PX = 1;
    private static final int DIFFERENCE_TOLERANCE = 0;
    private static final int PLOT_SCAN_EDGE_INSET_PX = 2;
    private static final List<Double> REPRESENTATIVE_SCALES = List.of(1.0d, 1.25d, 1.5d, 1.75d, 2.0d);
    private static final double SCALED_RENDER_FACTOR = 1.25d;

    @Test
    void newestCandleLeavesOneBlankPixelBeforePlotRightEdge() {
        Dimension size = new Dimension(1280, 720);
        ChartFrame chartFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                fixtureDataset(),
                simpleTemplate(),
                size
        );
        BufferedImage image = ChartExporter.renderPngImage(chartFrame, ExportOptions.builder().dimensions(size).build());
        ChartData chartData = chartFrame.getChartData();
        Rectangle plotBounds = chartFrame.getMainPanel().getChartPanel().getRenderBounds();
        Candle lastCandle = chartData.getLastDisplayedCandle();
        int rightBodyEdge = rightmostRenderedPixel(image, chartFrame, plotBounds, lastCandle);
        int plotRight = plotRightPixel(plotBounds);
        int scaleLeft = valueScaleLeftPixel(chartFrame);
        int blankGap = plotRight - rightBodyEdge - 1;

        assertThat(plotBounds.width).isPositive();
        assertThat(blankGap)
                .as("plotRight=%s scaleLeft=%s rightBody=%s plot=%s", plotRight, scaleLeft, rightBodyEdge, plotBounds)
                .isEqualTo(PLOT_EDGE_GAP_PX);
    }

    @Test
    void newestCandleStillLeavesGapWhenViewportEndsAtTheLastSlot() {
        Dimension size = new Dimension(1280, 720);
        CandleSeries dataset = fixtureDataset();
        ChartFrame chartFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                dataset,
                simpleTemplate(),
                size
        );
        alignViewportEndToLastSlot(chartFrame);
        removeValueScaleAnnotations(chartFrame);

        var visibleRange = chartFrame.getMainPanel().getChartPanel().getEngineChart().getXAxis().getVisibleRange();
        ChartFrame truncatedFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                truncateLast(dataset),
                simpleTemplate(),
                size
        );
        applyVisibleRange(truncatedFrame, visibleRange.getMin(), visibleRange.getMax());
        removeValueScaleAnnotations(truncatedFrame);

        BufferedImage image = paintScaled(chartFrame, size, 1.0d);
        BufferedImage truncatedImage = paintScaled(truncatedFrame, size, 1.0d);
        Rectangle plotBounds = chartFrame.getMainPanel().getChartPanel().getRenderBounds();
        int scaleLeft = valueScaleLeftPixel(chartFrame);
        Rectangle scanBounds = extendedScanBounds(plotBounds, scaleLeft, image.getWidth(), image.getHeight());
        int rightmostLatestDataPixel = rightmostDifferencePixel(image, truncatedImage, scanBounds);
        int blankGap = scaleLeft - rightmostLatestDataPixel - 1;

        assertThat(blankGap)
                .as("scaleLeft=%s rightmost=%s plot=%s scan=%s", scaleLeft, rightmostLatestDataPixel, plotBounds, scanBounds)
                .isEqualTo(PLOT_EDGE_GAP_PX);
    }

    @Test
    void defaultPricePanelLatestDataLeavesReservedGapBeforePlotRightEdge() {
        Dimension size = new Dimension(1280, 720);
        CandleSeries dataset = fixtureDataset();
        ChartFrame chartFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                dataset,
                overlayTemplate(),
                size
        );
        BufferedImage image = ChartExporter.renderPngImage(chartFrame, ExportOptions.builder().dimensions(size).build());
        Rectangle plotBounds = chartFrame.getMainPanel().getChartPanel().getRenderBounds();
        CandleSeries truncatedDataset = truncateLast(dataset);
        ChartFrame truncatedFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                truncatedDataset,
                overlayTemplate(),
                size
        );
        BufferedImage truncatedImage = ChartExporter.renderPngImage(truncatedFrame, ExportOptions.builder().dimensions(size).build());
        int rightmostContentPixel = rightmostDifferencePixel(image, truncatedImage, plotBounds);
        int plotRight = plotRightPixel(plotBounds);
        int scaleLeft = valueScaleLeftPixel(chartFrame);

        assertThat(plotBounds.width).isPositive();
        assertThat(rightmostContentPixel).isGreaterThanOrEqualTo(plotBounds.x);
        assertThat(plotRight - rightmostContentPixel)
                .as("plotRight=%s scaleLeft=%s rightmost=%s plot=%s", plotRight, scaleLeft, rightmostContentPixel, plotBounds)
                .isGreaterThanOrEqualTo(PLOT_EDGE_GAP_PX);
    }

    @Test
    void defaultPricePanelAnyPlotContentLeavesReservedGapBeforePlotRightEdge() {
        Dimension size = new Dimension(1280, 720);
        ChartFrame chartFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                fixtureDataset(),
                overlayTemplate(),
                size
        );
        BufferedImage image = ChartExporter.renderPngImage(chartFrame, ExportOptions.builder().dimensions(size).build());
        Rectangle plotBounds = chartFrame.getMainPanel().getChartPanel().getRenderBounds();
        int background = chartFrame.getChartProperties().getBackgroundColor().getRGB();
        int rightmostContentPixel = rightmostRenderedPixel(
                image,
                inset(plotBounds, 0, PLOT_SCAN_EDGE_INSET_PX, 0, PLOT_SCAN_EDGE_INSET_PX),
                background
        );
        int plotRight = plotRightPixel(plotBounds);
        int scaleLeft = valueScaleLeftPixel(chartFrame);

        assertThat(rightmostContentPixel).isGreaterThanOrEqualTo(plotBounds.x);
        assertThat(plotRight - rightmostContentPixel)
                .as("plotRight=%s scaleLeft=%s rightmost=%s plot=%s", plotRight, scaleLeft, rightmostContentPixel, plotBounds)
                .isGreaterThanOrEqualTo(PLOT_EDGE_GAP_PX);
    }

    @Test
    void newestCandleGapHoldsAcrossRepresentativeWidths() {
        for (int width : List.of(480, 640, 800, 960, 1120, 1280)) {
            for (double scale : REPRESENTATIVE_SCALES)
                assertNewestCandleGap(new Dimension(width, 720), scale);
        }
    }

    @Test
    void newestCandleGapHoldsAcrossRepresentativeBarWidths() {
        for (double barWidth : List.of(1.0d, 3.0d, 5.0d, 7.0d, 9.0d, 11.0d)) {
            for (double scale : REPRESENTATIVE_SCALES)
                assertNewestCandleGap(new Dimension(480, 720), scale, barWidth);
        }
    }

    @Test
    void livePriceChartLeavesBlankColumnBeforeValueScaleForBearishLastCandle() {
        assertLiveBlankColumnEmpty(new Dimension(480, 720), PixelPerfectCandleGeometry.DEFAULT_BODY_WIDTH);
    }

    @Test
    void livePriceChartLeavesBlankColumnAcrossRepresentativeWidthsAndBarWidths() {
        for (double barWidth : List.of(3.0d, 5.0d, 7.0d, 9.0d, 11.0d)) {
            for (int width : List.of(220, 240, 260, 280, 320, 360, 420, 480, 640, 1280))
                assertLiveBlankColumnEmpty(new Dimension(width, 720), barWidth);
        }
    }

    @Test
    void livePriceChartKeepsBlankDeviceColumnBeforeValueScaleAtScaledPaint() {
        Dimension size = new Dimension(480, 720);
        ChartFrame chartFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                forceLastDown(fixtureDataset()),
                liveTemplate(PixelPerfectCandleGeometry.DEFAULT_BODY_WIDTH),
                size
        );
        BufferedImage image = renderScaledImage(chartFrame, size, SCALED_RENDER_FACTOR);
        Rectangle plotBounds = chartFrame.getMainPanel().getChartPanel().getRenderBounds();
        int scaleLeft = valueScaleLeftPixel(chartFrame);
        int blankColumnX = toDeviceX(scaleLeft, SCALED_RENDER_FACTOR) - 1;
        int occupied = occupiedPixelsInScaledColumn(chartFrame, image, plotBounds, blankColumnX, SCALED_RENDER_FACTOR);

        assertThat(occupied)
                .as("plot=%s scaleLeft=%s blankX=%s scale=%.2f", plotBounds, scaleLeft, blankColumnX, SCALED_RENDER_FACTOR)
                .isZero();
    }

    @Test
    void newestCandleLeavesOneBlankDevicePixelBeforePlotRightEdgeAtScaledPaint() {
        Dimension size = new Dimension(1280, 720);
        CandleSeries dataset = fixtureDataset();
        ChartFrame chartFrame = ChartExporter.createChartFrame(DataProvider.EMPTY, dataset, simpleTemplate(), size);
        BufferedImage image = renderScaledImage(chartFrame, size, SCALED_RENDER_FACTOR);
        Rectangle plotBounds = chartFrame.getMainPanel().getChartPanel().getRenderBounds();
        ChartFrame truncatedFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                truncateLast(dataset),
                simpleTemplate(),
                size
        );
        BufferedImage truncatedImage = renderScaledImage(truncatedFrame, size, SCALED_RENDER_FACTOR);
        Rectangle devicePlotBounds = toDeviceBounds(plotBounds, SCALED_RENDER_FACTOR);
        int rightBodyEdge = rightmostDifferencePixel(image, truncatedImage, devicePlotBounds);
        int plotRight = plotRightPixel(devicePlotBounds);

        assertThat(rightBodyEdge).isGreaterThanOrEqualTo(0);
        assertThat(plotRight - rightBodyEdge).isGreaterThanOrEqualTo(PLOT_EDGE_GAP_PX);
    }

    @Test
    void defaultPricePanelLatestDataLeavesReservedGapAtFailingScaledWidth() {
        Dimension size = new Dimension(480, 720);
        CandleSeries dataset = fixtureDataset();
        ChartFrame chartFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                dataset,
                overlayTemplate(),
                size
        );
        BufferedImage image = renderScaledImage(chartFrame, size, SCALED_RENDER_FACTOR);
        Rectangle plotBounds = chartFrame.getMainPanel().getChartPanel().getRenderBounds();

        ChartFrame truncatedFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                truncateLast(dataset),
                overlayTemplate(),
                size
        );
        BufferedImage truncatedImage = renderScaledImage(truncatedFrame, size, SCALED_RENDER_FACTOR);
        int rightmostContentPixel = rightmostDifferencePixel(
                image,
                truncatedImage,
                toDeviceBounds(plotBounds, SCALED_RENDER_FACTOR)
        );
        int plotRight = plotRightPixel(toDeviceBounds(plotBounds, SCALED_RENDER_FACTOR));

        assertThat(plotRight - rightmostContentPixel)
                .as("plotRight=%s rightmost=%s plot=%s", plotRight, rightmostContentPixel, plotBounds)
                .isGreaterThanOrEqualTo(PLOT_EDGE_GAP_PX);
    }

    private static ChartTemplate simpleTemplate() {
        ChartProperties properties = new ChartProperties();
        properties.setBackgroundColor(Color.WHITE);
        properties.setAxisColor(Color.WHITE);
        properties.setAxisLogarithmicFlag(false);
        properties.setGridHorizontalVisibility(false);
        properties.setGridVerticalVisibility(false);

        ChartTemplate template = new ChartTemplate("Right Edge");
        template.setChartProperties(properties);
        template.setChart(new CandlestickChart());
        return template;
    }

    private static ChartTemplate overlayTemplate() {
        ChartTemplate template = ChartTemplateDefaults.basicChartTemplate();
        ChartProperties properties = template.getChartProperties();
        properties.setBackgroundColor(Color.WHITE);
        properties.setAxisColor(Color.WHITE);
        properties.setAxisLogarithmicFlag(false);
        properties.setGridHorizontalVisibility(false);
        properties.setGridVerticalVisibility(false);
        return template;
    }

    private static int rightmostRenderedPixel(BufferedImage image, ChartFrame chartFrame, Rectangle plotBounds, Candle candle) {
        ChartData chartData = chartFrame.getChartData();
        boolean logarithmic = chartFrame.getChartProperties().getAxisLogarithmicFlag();
        double topY = chartData.getY(candle.high(), plotBounds, chartData.getVisibleRange(), logarithmic);
        double bottomY = chartData.getY(candle.low(), plotBounds, chartData.getVisibleRange(), logarithmic);
        int top = Math.max(plotBounds.y, (int) Math.floor(Math.min(topY, bottomY)) - 1);
        int bottom = Math.min(plotBounds.y + plotBounds.height - 1, (int) Math.ceil(Math.max(topY, bottomY)) + 1);
        int background = chartFrame.getChartProperties().getBackgroundColor().getRGB();
        int rightmost = -1;

        for (int y = top; y <= bottom; y++) {
            for (int x = plotBounds.x; x < plotBounds.x + plotBounds.width; x++) {
                if (image.getRGB(x, y) != background)
                    rightmost = Math.max(rightmost, x);
            }
        }
        return rightmost;
    }

    private static int rightmostRenderedPixel(BufferedImage image, Rectangle bounds, int background) {
        int rightmost = -1;
        for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
            for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
                if (image.getRGB(x, y) != background)
                    rightmost = Math.max(rightmost, x);
            }
        }
        return rightmost;
    }

    private static int rightmostDifferencePixel(BufferedImage image,
                                                BufferedImage other,
                                                Rectangle plotBounds) {
        int rightmost = -1;
        for (int y = plotBounds.y; y < plotBounds.y + plotBounds.height; y++) {
            for (int x = plotBounds.x; x < plotBounds.x + plotBounds.width; x++) {
                if (different(image.getRGB(x, y), other.getRGB(x, y)))
                    rightmost = Math.max(rightmost, x);
            }
        }
        return rightmost;
    }

    private static BufferedImage renderScaledImage(ChartFrame chartFrame, Dimension size, double scale) {
        chartFrame.setPreferredSize(size);
        chartFrame.setSize(size);
        ChartExporter.layoutRecursively(chartFrame);

        paintScaled(chartFrame, size, scale);
        flushEdt();
        ChartExporter.layoutRecursively(chartFrame);
        return paintScaled(chartFrame, size, scale);
    }

    private static BufferedImage paintScaled(ChartFrame chartFrame, Dimension size, double scale) {
        int deviceWidth = Math.max(1, (int) Math.ceil(size.width * scale));
        int deviceHeight = Math.max(1, (int) Math.ceil(size.height * scale));
        BufferedImage image = new BufferedImage(deviceWidth, deviceHeight, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.scale(scale, scale);
            graphics.setColor(chartFrame.getBackground() != null ? chartFrame.getBackground() : Color.WHITE);
            graphics.fillRect(0, 0, size.width, size.height);
            chartFrame.paint(graphics);
            return image;
        } finally {
            graphics.dispose();
        }
    }

    private static Rectangle toDeviceBounds(Rectangle logicalBounds, double scale) {
        int x = (int) Math.floor(logicalBounds.x * scale);
        int y = (int) Math.floor(logicalBounds.y * scale);
        int lastX = toDeviceX(logicalBounds.x + logicalBounds.width - 1, scale);
        int lastY = (int) Math.round((logicalBounds.y + logicalBounds.height - 1) * scale);
        return new Rectangle(x, y, Math.max(1, lastX - x + 1), Math.max(1, lastY - y + 1));
    }

    private static Rectangle inset(Rectangle bounds, int left, int top, int right, int bottom) {
        int x = bounds.x + left;
        int y = bounds.y + top;
        int width = Math.max(1, bounds.width - left - right);
        int height = Math.max(1, bounds.height - top - bottom);
        return new Rectangle(x, y, width, height);
    }

    private static void assertNewestCandleGap(Dimension size, double scale) {
        assertNewestCandleGap(size, scale, PixelPerfectCandleGeometry.DEFAULT_BODY_WIDTH);
    }

    private static void assertNewestCandleGap(Dimension size, double scale, double barWidth) {
        ChartTemplate template = simpleTemplate();
        template.getChartProperties().setBarWidth(barWidth);
        CandleSeries dataset = fixtureDataset();
        ChartFrame chartFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                dataset,
                template,
                size
        );
        BufferedImage image = (scale == 1.0d)
                ? ChartExporter.renderPngImage(chartFrame, ExportOptions.builder().dimensions(size).build())
                : renderScaledImage(chartFrame, size, scale);

        Rectangle plotBounds = chartFrame.getMainPanel().getChartPanel().getRenderBounds();
        Rectangle scanBounds = (scale == 1.0d) ? plotBounds : toDeviceBounds(plotBounds, scale);
        ChartFrame truncatedFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                truncateLast(dataset),
                template,
                size
        );
        BufferedImage truncatedImage = (scale == 1.0d)
                ? ChartExporter.renderPngImage(truncatedFrame, ExportOptions.builder().dimensions(size).build())
                : renderScaledImage(truncatedFrame, size, scale);
        int rightmostContentPixel = rightmostDifferencePixel(image, truncatedImage, scanBounds);
        int plotRight = plotRightPixel(scanBounds);
        int scaleLeft = valueScaleLeftPixel(chartFrame);
        String occupiedRange = occupiedRange(image, scanBounds, rightmostContentPixel, chartFrame.getChartProperties().getBackgroundColor().getRGB());

        assertThat(plotRight - rightmostContentPixel)
                .as("width=%s scale=%s barWidth=%s plotRight=%s scaleLeft=%s rightmost=%s plot=%s occupied=%s",
                        size.width, scale, barWidth, plotRight, scaleLeft, rightmostContentPixel, plotBounds, occupiedRange)
                .isGreaterThanOrEqualTo(PLOT_EDGE_GAP_PX);
    }

    private static void assertLiveBlankColumnEmpty(Dimension size, double barWidth) {
        ChartFrame chartFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                forceLastDown(fixtureDataset()),
                liveTemplate(barWidth),
                size,
                false
        );
        stabilizeLivePaint(chartFrame, size);

        Rectangle plotBounds = chartFrame.getMainPanel().getChartPanel().getRenderBounds();
        int scaleLeft = valueScaleLeftPixel(chartFrame);
        int blankColumnX = scaleLeft - 1;
        int occupied = occupiedPixelsInColumn(chartFrame, plotBounds, blankColumnX);

        assertThat(occupied)
                .as("width=%s barWidth=%s plot=%s scaleLeft=%s blankX=%s", size.width, barWidth, plotBounds, scaleLeft, blankColumnX)
                .isZero();
    }

    private static void alignViewportEndToLastSlot(ChartFrame chartFrame) {
        var engineChart = chartFrame.getMainPanel().getChartPanel().getEngineChart();
        double visibleLength = engineChart.getXAxis().getVisibleRange().getLength();
        double viewportMax = chartFrame.getChartData().getTotalSlotCount() - 0.5d;
        applyVisibleRange(chartFrame, viewportMax - visibleLength, viewportMax);
    }

    private static void applyVisibleRange(ChartFrame chartFrame, double min, double max) {
        var engineChart = chartFrame.getMainPanel().getChartPanel().getEngineChart();
        engineChart.getXAxis().setVisibleRange(min, max);
        ChartExporter.layoutRecursively(chartFrame);
    }

    private static void removeValueScaleAnnotations(ChartFrame chartFrame) {
        Scale valueScale = chartFrame.getMainPanel().getChartPanel().getEngineChart().getYScale(0);
        ScaleAnnotation[] annotations = valueScale.getAnnotations();
        if (annotations == null)
            return;
        for (ScaleAnnotation annotation : annotations)
            valueScale.removeAnnotation(annotation);
        ChartExporter.layoutRecursively(chartFrame);
    }

    private static Rectangle extendedScanBounds(Rectangle plotBounds, int scaleLeft, int imageWidth, int imageHeight) {
        int x = Math.max(0, plotBounds.x);
        int y = Math.max(0, plotBounds.y);
        int right = Math.min(imageWidth - 1, Math.max(scaleLeft + 2, plotBounds.x + plotBounds.width - 1));
        int bottom = Math.min(imageHeight - 1, plotBounds.y + plotBounds.height - 1);
        return new Rectangle(x, y, Math.max(1, right - x + 1), Math.max(1, bottom - y + 1));
    }

    private static int occupiedPixelsInColumn(ChartFrame chartFrame, Rectangle plotBounds, int x) {
        BufferedImage image = FrontEndSupport.getDefault().paintComponent(chartFrame);
        ChartData chartData = chartFrame.getChartData();
        Candle candle = chartData.getLastDisplayedCandle();
        Range range = chartData.getVisibleRange();
        boolean logarithmic = chartFrame.getChartProperties().getAxisLogarithmicFlag();
        double topY = chartData.getY(candle.high(), plotBounds, range, logarithmic);
        double bottomY = chartData.getY(candle.low(), plotBounds, range, logarithmic);
        int top = Math.max(plotBounds.y, (int) Math.floor(Math.min(topY, bottomY)) - 1);
        int bottom = Math.min(plotBounds.y + plotBounds.height - 1, (int) Math.ceil(Math.max(topY, bottomY)) + 1);
        int background = chartFrame.getChartProperties().getBackgroundColor().getRGB();
        int occupied = 0;
        for (int y = top; y <= bottom; y++) {
            if (image.getRGB(x, y) != background)
                occupied++;
        }
        return occupied;
    }

    private static int occupiedPixelsInScaledColumn(ChartFrame chartFrame,
                                                    BufferedImage image,
                                                    Rectangle plotBounds,
                                                    int x,
                                                    double scale) {
        ChartData chartData = chartFrame.getChartData();
        Candle candle = chartData.getLastDisplayedCandle();
        Range range = chartData.getVisibleRange();
        boolean logarithmic = chartFrame.getChartProperties().getAxisLogarithmicFlag();
        double topY = chartData.getY(candle.high(), plotBounds, range, logarithmic);
        double bottomY = chartData.getY(candle.low(), plotBounds, range, logarithmic);
        int top = Math.max(0, (int) Math.floor(Math.min(topY, bottomY) * scale) - 1);
        int bottom = Math.min(image.getHeight() - 1, (int) Math.ceil(Math.max(topY, bottomY) * scale) + 1);
        int background = chartFrame.getChartProperties().getBackgroundColor().getRGB();
        int occupied = 0;
        for (int y = top; y <= bottom; y++) {
            if (image.getRGB(x, y) != background)
                occupied++;
        }
        return occupied;
    }

    private static String occupiedRange(BufferedImage image, Rectangle bounds, int x, int background) {
        if (x < bounds.x || x >= bounds.x + bounds.width)
            return "n/a";
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int count = 0;
        for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
            if (image.getRGB(x, y) == background)
                continue;
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            count++;
        }
        if (count == 0)
            return "empty";
        return minY + "-" + maxY + " (" + count + ")";
    }

    private static int toDeviceX(int logicalX, double scale) {
        return (int) Math.round(logicalX * scale);
    }

    private static int plotRightPixel(Rectangle bounds) {
        return bounds.x + bounds.width - 1;
    }

    private static int valueScaleLeftPixel(ChartFrame chartFrame) {
        Scale valueScale = chartFrame.getMainPanel().getChartPanel().getEngineChart().getYScale(0);
        valueScale.getChart().updateScalesIfNeeded();
        return valueScale.getBoundsUsingCache(null).getBounds().x;
    }

    private static void flushEdt() {
        try {
            if (SwingUtilities.isEventDispatchThread())
                return;
            SwingUtilities.invokeAndWait(() -> { });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for EDT", ex);
        } catch (InvocationTargetException ex) {
            throw new AssertionError("EDT flush failed", ex);
        }
    }

    private static boolean different(int rgb, int otherRgb) {
        return Math.abs(channel(rgb, 16) - channel(otherRgb, 16)) > DIFFERENCE_TOLERANCE
                || Math.abs(channel(rgb, 8) - channel(otherRgb, 8)) > DIFFERENCE_TOLERANCE
                || Math.abs(channel(rgb, 0) - channel(otherRgb, 0)) > DIFFERENCE_TOLERANCE
                || Math.abs(channel(rgb, 24) - channel(otherRgb, 24)) > DIFFERENCE_TOLERANCE;
    }

    private static int channel(int rgb, int shift) {
        return (rgb >>> shift) & 0xFF;
    }

    private static CandleSeries fixtureDataset() {
        SymbolResource<Candle> resource = SymbolResource.of(SymbolIdentity.of("RIGHT-EDGE-GAP"), TimeFrame.Period.DAILY);
        var candles = new ArrayList<Candle>(220);
        double close = 118.0;
        LocalDate date = LocalDate.of(2024, 1, 2);
        for (int i = 0; i < 220; i++) {
            double drift = Math.sin(i / 7.5d) * 1.4d + Math.cos(i / 15.0d) * 0.6d;
            double open = close + Math.sin(i / 5.0d) * 0.55d;
            close = Math.max(45.0d, open + drift);
            double high = Math.max(open, close) + 0.7d + Math.abs(Math.sin(i / 3.0d));
            double low = Math.min(open, close) - 0.7d - Math.abs(Math.cos(i / 4.0d));
            double volume = 1_500_000d + (i % 17) * 85_000d + Math.abs(drift) * 120_000d;
            candles.add(Candle.of(date.plusDays(i).atStartOfDay(), open, high, low, close, volume));
        }
        return CandleSeries.of(resource, candles);
    }

    private static CandleSeries truncateLast(CandleSeries dataset) {
        int length = dataset.length();
        var candles = new ArrayList<Candle>(Math.max(0, length - 1));
        for (int index = length - 1; index >= 1; index--)
            candles.add(dataset.get(index));
        return CandleSeries.of(dataset.getResource(), candles);
    }

    private static ChartTemplate liveTemplate(double barWidth) {
        ChartTemplate template = new ChartTemplate("Live Right Edge");
        ChartProperties properties = new ChartProperties();
        properties.setBackgroundColor(Color.WHITE);
        properties.setAxisLogarithmicFlag(false);
        properties.setGridHorizontalVisibility(false);
        properties.setGridVerticalVisibility(false);
        properties.setBarWidth(barWidth);
        template.setChartProperties(properties);
        template.setChart(new CandlestickChart());
        return template;
    }

    private static CandleSeries forceLastDown(CandleSeries dataset) {
        var candles = new ArrayList<Candle>(dataset.length());
        for (int index = dataset.length() - 1; index >= 0; index--)
            candles.add(dataset.get(index));
        int lastIndex = candles.size() - 1;
        Candle last = candles.get(lastIndex);
        double open = Math.max(last.open(), last.close()) + 2.0d;
        double close = Math.min(last.open(), last.close()) - 2.5d;
        double high = Math.max(last.high(), open + 0.8d);
        double low = Math.min(last.low(), close - 0.8d);
        candles.set(lastIndex, Candle.of(last.time(), open, high, low, close, last.volume()));
        return CandleSeries.of(dataset.getResource(), candles);
    }

    private static void stabilizeLivePaint(ChartFrame chartFrame, Dimension size) {
        chartFrame.setBackground(Color.WHITE);
        chartFrame.setPreferredSize(size);
        chartFrame.setSize(size);
        ChartExporter.layoutRecursively(chartFrame);
        FrontEndSupport.getDefault().paintComponent(chartFrame);
        flushEdt();
        ChartExporter.layoutRecursively(chartFrame);
        FrontEndSupport.getDefault().paintComponent(chartFrame);
        flushEdt();
        ChartExporter.layoutRecursively(chartFrame);
    }
}
