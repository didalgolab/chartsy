package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.ui.chart.components.AnnotationPanel;
import one.chartsy.ui.chart.components.IndicatorPanel;
import one.chartsy.ui.chart.components.SharedDateAxisFooter;
import one.chartsy.ui.chart.type.CandlestickChart;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StandardCrosshairRendererLayerAlignmentTest {
    private static final double HIDPI_SCALE = 1.25d;
    private static final int UNIFORM_CANDLE_COUNT = 180;
    private static final double UNIFORM_OPEN = 100.0;
    private static final double UNIFORM_HIGH = 110.0;
    private static final double UNIFORM_LOW = 90.0;
    private static final double UNIFORM_CLOSE = 105.0;
    private static final double UNIFORM_VOLUME = 1_000.0;

    @Test
    void vertical_crosshair_matches_rendered_candle_center_at_scaled_and_scrolled_viewport() {
        Dimension size = new Dimension(1009, 800);
        ChartFrame chartFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                uniformCandleDataset(),
                crosshairAlignmentTemplate(),
                size
        );
        renderScaledImage(chartFrame, size, HIDPI_SCALE);
        ChartData chartData = chartFrame.getChartData();
        int scrolledStart = Math.min(17, chartData.getMaxVisibleStart());
        assertThat(scrolledStart).isPositive();
        chartData.setVisibleStartSlot(scrolledStart);
        assertThat(chartData.getVisibleStartSlot()).isEqualTo(scrolledStart);
        chartFrame.refreshChartView();
        ChartExporter.layoutRecursively(chartFrame);

        BufferedImage baseline = renderScaledImage(chartFrame, size, HIDPI_SCALE);
        var crosshairLayer = findCrosshairLayer(chartFrame);
        var crosshair = (StandardCrosshairRendererLayer) crosshairLayer.getUI();
        AnnotationPanel pane = chartFrame.getMainStackPanel().getChartPanel().getAnnotationPanel();
        Rectangle plotBounds = pane.getRenderBounds();
        Rectangle plotInFrame = SwingUtilities.convertRectangle(pane, plotBounds, chartFrame);
        int wickY = upperWickDeviceY(chartFrame, pane, plotBounds, HIDPI_SCALE);
        SlotPixel target = findDoubleRoundingMismatch(
                chartFrame, pane, plotBounds, plotInFrame, HIDPI_SCALE);

        assertThat(target)
                .as("fixture must exercise the former logical-before-device rounding path")
                .isNotNull();
        assertThat(target.roundedLogicalX()).isNotEqualTo(target.candleX());
        assertThat(baseline.getRGB(target.candleX(), wickY))
                .as("rendered upper wick at slot %s", target.slot())
                .isNotEqualTo(Color.WHITE.getRGB());

        int x = (int) Math.round(target.logicalCenterX());
        int y = plotBounds.y + plotBounds.height / 2;
        moveCrosshair(crosshair, crosshairLayer, pane, x, y);

        BufferedImage withCrosshair = paintScaled(chartFrame, size, HIDPI_SCALE);
        ColumnDifference crosshairDifference = mostChangedColumn(
                baseline, withCrosshair, toDeviceBounds(plotInFrame, HIDPI_SCALE));

        assertThat(crosshairDifference.changedPixels())
                .as("vertical crosshair must be present in the rendered plot")
                .isGreaterThan(plotInFrame.height / 4);
        assertThat(crosshairDifference.x())
                .as("slot=%s logicalCenter=%s scale=%s visibleStart=%s roundedLogicalDeviceX=%s",
                        target.slot(), target.logicalCenterX(), HIDPI_SCALE,
                        chartData.getVisibleStartSlot(), target.roundedLogicalX())
                .isEqualTo(target.candleX());
    }

    @Test
    void dateAxisHoverLabel_snaps_to_candle_slot_center() {
        SlotFixture fixture = chartFrameWithFractionalSlotCenter();
        ChartFrame chartFrame = fixture.chartFrame();
        SharedDateAxisFooter footer = chartFrame.getDateAxisFooter();
        int slot = fixture.slot();

        footer.setHoverSlot(slot);
        SharedDateAxisFooter.FooterSnapshot snapshot = footer.snapshot();

        int expectedX = (int) Math.round(chartFrame.getChartData().getSlotCenterX(slot, snapshot.plotBounds()));
        assertThat(snapshot.hoverLabel()).isNotNull();
        assertThat(snapshot.hoverLabel().x()).isEqualTo(expectedX);
    }

    @Test
    void dateAxisHoverLabel_uses_daily_candle_display_date() {
        ChartFrame chartFrame = chartFrameWithIndicator();
        SharedDateAxisFooter footer = chartFrame.getDateAxisFooter();
        int slot = 0;

        footer.setHoverSlot(slot);
        SharedDateAxisFooter.HoverLabel hoverLabel = footer.snapshot().hoverLabel();

        assertThat(hoverLabel).isNotNull();
        assertThat(hoverLabel.label()).isEqualTo("2025-06-02");
        assertThat(hoverLabel.label()).isEqualTo(chartFrame.getChartData().getSlotDateLabel(slot));
    }

    @Test
    void dateAxisTicks_snap_to_candle_slot_centers() {
        ChartFrame chartFrame = chartFrameWithIndicator();
        SharedDateAxisFooter footer = chartFrame.getDateAxisFooter();
        SharedDateAxisFooter.FooterSnapshot snapshot = footer.snapshot();
        ChartData chartData = chartFrame.getChartData();

        assertThat(snapshot.upperTicks()).isNotEmpty();
        for (SharedDateAxisFooter.TickMark tick : snapshot.upperTicks()) {
            int expectedX = (int) Math.round(chartData.getSlotCenterX(tick.value(), snapshot.plotBounds()));
            assertThat(tick.x()).isEqualTo(expectedX);
        }
        for (SharedDateAxisFooter.TickMark tick : snapshot.lowerTicks()) {
            if (!tick.forced()) {
                int expectedX = (int) Math.round(chartData.getSlotCenterX(tick.value(), snapshot.plotBounds()));
                assertThat(tick.x()).isEqualTo(expectedX);
            }
        }
    }

    @Test
    void mainPanelHoverValueLabelStartsOnePixelInsideTheScaleStrip() throws Exception {
        ChartFrame chartFrame = chartFrameWithIndicator();
        var crosshairLayer = findCrosshairLayer(chartFrame);
        var crosshair = (StandardCrosshairRendererLayer) crosshairLayer.getUI();
        AnnotationPanel pane = chartFrame.getMainStackPanel().getChartPanel().getAnnotationPanel();
        Rectangle plotBounds = chartFrame.getMainStackPanel().getChartPanel().getRenderBounds();
        Rectangle labelBounds = hoverValueLabelBounds(crosshair, crosshairLayer, pane);
        Rectangle scaleBounds = crosshair.resolveScaleBounds(
                chartFrame.getMainStackPanel().getChartPanel().getEngineChart().getYScale(0),
                crosshairLayer
        );

        assertThat(labelBounds.x).isEqualTo(scaleBounds.x + 1);
        assertThat(labelBounds.x).isGreaterThanOrEqualTo(plotBounds.x + plotBounds.width + 1);
    }

    @Test
    void indicatorPanelHoverValueLabelStaysEntirelyInsideTheScaleStrip() throws Exception {
        ChartFrame chartFrame = chartFrameWithIndicator();
        var crosshairLayer = findCrosshairLayer(chartFrame);
        var crosshair = (StandardCrosshairRendererLayer) crosshairLayer.getUI();
        IndicatorPanel indicatorPanel = chartFrame.getMainStackPanel().getIndicatorPanels().getFirst();
        AnnotationPanel pane = indicatorPanel.getAnnotationPanel();
        Rectangle plotBounds = pane.getRenderBounds();
        Rectangle labelBounds = hoverValueLabelBounds(crosshair, crosshairLayer, pane);
        Rectangle scaleBounds = crosshair.resolveScaleBounds(indicatorPanel.getEngineChart().getYScale(0), crosshairLayer);

        assertThat(labelBounds.x).isEqualTo(scaleBounds.x + 1);
        assertThat(labelBounds.x).isGreaterThanOrEqualTo(scaleBounds.x + 1);
        assertThat(labelBounds.x).isGreaterThanOrEqualTo(plotBounds.x + plotBounds.width + 1);
    }

    private static ChartFrame chartFrameWithIndicator() {
        return chartFrameWithIndicator(new Dimension(1280, 800));
    }

    private static ChartFrame chartFrameWithIndicator(Dimension size) {
        return ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                fixtureDataset(),
                ChartTemplateDefaults.basicChartTemplate(),
                size
        );
    }

    private static SlotFixture chartFrameWithFractionalSlotCenter() {
        for (int width : List.of(997, 1009, 1021, 1033, 1049, 1280)) {
            ChartFrame chartFrame = chartFrameWithIndicator(new Dimension(width, 800));
            AnnotationPanel pane = chartFrame.getMainStackPanel().getChartPanel().getAnnotationPanel();
            Rectangle plotBounds = pane.getRenderBounds();
            int slot = findFractionalSlotCenter(chartFrame.getChartData(), plotBounds);
            if (slot >= 0)
                return new SlotFixture(chartFrame, slot);
        }
        throw new AssertionError("No fractional slot center found for test fixture sizes");
    }

    private static int findFractionalSlotCenter(ChartData chartData, Rectangle plotBounds) {
        int first = chartData.getVisibleStartSlot();
        int last = Math.min(chartData.getVisibleEndSlot(), chartData.getHistoricalSlotCount());
        for (int slot = first; slot < last; slot++) {
            double x = chartData.getSlotCenterX(slot, plotBounds);
            if (Math.abs(x - Math.rint(x)) > 0.05d)
                return slot;
        }
        return -1;
    }

    private static ChartTemplate crosshairAlignmentTemplate() {
        ChartProperties properties = new ChartProperties();
        properties.setBackgroundColor(Color.WHITE);
        properties.setAxisColor(Color.WHITE);
        properties.setBarColor(Color.BLACK);
        properties.setBarUpColor(Color.BLACK);
        properties.setBarDownColor(Color.BLACK);
        properties.setAxisLogarithmicFlag(false);
        properties.setGridHorizontalVisibility(false);
        properties.setGridVerticalVisibility(false);

        ChartTemplate template = new ChartTemplate("Crosshair Alignment");
        template.setChartProperties(properties);
        template.setChart(new CandlestickChart());
        return template;
    }

    private static CandleSeries uniformCandleDataset() {
        SymbolResource<Candle> resource = SymbolResource.of(
                SymbolIdentity.of("CROSSHAIR-HIDPI"), TimeFrame.Period.DAILY);
        List<Candle> candles = new ArrayList<>(UNIFORM_CANDLE_COUNT);
        LocalDate date = LocalDate.of(2025, 1, 2);
        for (int i = 0; i < UNIFORM_CANDLE_COUNT; i++) {
            candles.add(Candle.of(date.plusDays(i).atStartOfDay(),
                    UNIFORM_OPEN, UNIFORM_HIGH, UNIFORM_LOW, UNIFORM_CLOSE, UNIFORM_VOLUME));
        }
        return CandleSeries.of(resource, candles);
    }

    private static SlotPixel findDoubleRoundingMismatch(ChartFrame chartFrame,
                                                        AnnotationPanel pane,
                                                        Rectangle plotBounds,
                                                        Rectangle plotInFrame,
                                                        double scale) {
        ChartData chartData = chartFrame.getChartData();
        int plotStartDevice = toDevice(plotInFrame.x, scale);
        int plotEndDevice = toDevice(plotInFrame.x + plotInFrame.width - 1, scale);
        int paneX = SwingUtilities.convertPoint(pane, 0, 0, chartFrame).x;
        double plotSpan = plotBounds.width - 1.0;
        int first = chartData.getVisibleStartSlot();
        int last = Math.min(chartData.getVisibleEndSlot(), chartData.getHistoricalSlotCount());

        for (int slot = first; slot < last; slot++) {
            double logicalCenter = chartData.getSlotCenterX(slot, plotBounds);
            double fraction = (logicalCenter - plotBounds.x) / plotSpan;
            int candleX = plotStartDevice
                    + (int) Math.round(fraction * (plotEndDevice - plotStartDevice));
            int roundedLogicalX = toDevice(paneX + Math.round(logicalCenter), scale);
            if (candleX != roundedLogicalX)
                return new SlotPixel(slot, logicalCenter, candleX, roundedLogicalX);
        }
        return null;
    }

    private static int upperWickDeviceY(ChartFrame chartFrame,
                                        AnnotationPanel pane,
                                        Rectangle plotBounds,
                                        double scale) {
        ChartData chartData = chartFrame.getChartData();
        int paneY = SwingUtilities.convertPoint(pane, 0, 0, chartFrame).y;
        double highY = chartData.getY(UNIFORM_HIGH, plotBounds, chartData.getVisibleRange(), false);
        double closeY = chartData.getY(UNIFORM_CLOSE, plotBounds, chartData.getVisibleRange(), false);
        int highDeviceY = toDevice(paneY + highY, scale);
        int closeDeviceY = toDevice(paneY + closeY, scale);
        return (highDeviceY + closeDeviceY) / 2;
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
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, size.width, size.height);
            chartFrame.paint(graphics);
            return image;
        } finally {
            graphics.dispose();
        }
    }

    private static ColumnDifference mostChangedColumn(BufferedImage baseline,
                                                      BufferedImage changed,
                                                      Rectangle bounds) {
        int bestX = -1;
        int bestCount = -1;
        int firstX = Math.max(0, bounds.x);
        int lastX = Math.min(baseline.getWidth(), bounds.x + bounds.width);
        int firstY = Math.max(0, bounds.y);
        int lastY = Math.min(baseline.getHeight(), bounds.y + bounds.height);
        for (int x = firstX; x < lastX; x++) {
            int count = 0;
            for (int y = firstY; y < lastY; y++) {
                if (baseline.getRGB(x, y) != changed.getRGB(x, y))
                    count++;
            }
            if (count > bestCount) {
                bestX = x;
                bestCount = count;
            }
        }
        return new ColumnDifference(bestX, bestCount);
    }

    private static Rectangle toDeviceBounds(Rectangle logicalBounds, double scale) {
        int x = (int) Math.floor(logicalBounds.x * scale);
        int y = (int) Math.floor(logicalBounds.y * scale);
        int lastX = toDevice(logicalBounds.x + logicalBounds.width - 1, scale);
        int lastY = toDevice(logicalBounds.y + logicalBounds.height - 1, scale);
        return new Rectangle(x, y, Math.max(1, lastX - x + 1), Math.max(1, lastY - y + 1));
    }

    private static int toDevice(double logicalCoordinate, double scale) {
        return (int) Math.round(logicalCoordinate * scale);
    }

    private static void flushEdt() {
        try {
            if (!SwingUtilities.isEventDispatchThread())
                SwingUtilities.invokeAndWait(() -> { });
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for EDT", ex);
        } catch (InvocationTargetException ex) {
            throw new AssertionError("EDT flush failed", ex);
        }
    }

    private static CandleSeries fixtureDataset() {
        SymbolResource<Candle> resource = SymbolResource.of(SymbolIdentity.of("CROSSHAIR-ALIGNMENT"), TimeFrame.Period.DAILY);
        List<Candle> candles = new ArrayList<>(180);
        double close = 118.0;
        LocalDate date = LocalDate.of(2025, 6, 2);
        for (int i = 0; i < 180; i++) {
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

    @SuppressWarnings("unchecked")
    private static JLayer<JComponent> findCrosshairLayer(Component component) {
        if (component instanceof JLayer<?> layer && layer.getUI() instanceof StandardCrosshairRendererLayer)
            return (JLayer<JComponent>) layer;
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JLayer<JComponent> found = findCrosshairLayer(child);
                if (found != null)
                    return found;
            }
        }
        return null;
    }

    private static Rectangle hoverValueLabelBounds(StandardCrosshairRendererLayer crosshair,
                                                   JLayer<JComponent> layer,
                                                   AnnotationPanel pane) throws Exception {
        Rectangle plotBounds = pane.getRenderBounds();
        int x = plotBounds.x + (int) Math.round(plotBounds.width * 0.72d);
        int y = plotBounds.y + (int) Math.round(plotBounds.height * 0.42d);
        moveCrosshair(crosshair, layer, pane, x, y);

        Field overlayField = StandardCrosshairRendererLayer.class.getDeclaredField("valueLabelOverlay");
        overlayField.setAccessible(true);
        Object overlay = overlayField.get(crosshair);
        assertThat(overlay).isNotNull();

        Method boundsMethod = overlay.getClass().getDeclaredMethod("bounds");
        boundsMethod.setAccessible(true);
        return new Rectangle((Rectangle) boundsMethod.invoke(overlay));
    }

    private static void moveCrosshair(StandardCrosshairRendererLayer crosshair,
                                      JLayer<? extends JComponent> layer,
                                      AnnotationPanel pane,
                                      int x,
                                      int y) {
        long when = System.currentTimeMillis();
        crosshair.eventDispatched(new MouseEvent(pane, MouseEvent.MOUSE_ENTERED,
                when, 0, x, y, 0, false, MouseEvent.NOBUTTON), layer);
        crosshair.eventDispatched(new MouseEvent(pane, MouseEvent.MOUSE_MOVED,
                when + 1, 0, x, y, 0, false, MouseEvent.NOBUTTON), layer);
    }

    private record SlotFixture(ChartFrame chartFrame, int slot) {
    }

    private record SlotPixel(int slot, double logicalCenterX, int candleX, int roundedLogicalX) {
    }

    private record ColumnDifference(int x, int changedPixels) {
    }
}
