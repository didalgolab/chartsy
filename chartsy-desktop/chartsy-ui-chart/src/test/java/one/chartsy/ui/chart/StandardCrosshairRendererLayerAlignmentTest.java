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
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StandardCrosshairRendererLayerAlignmentTest {

    @Test
    void verticalCrosshair_snaps_to_candle_slot_center() throws Exception {
        SlotFixture fixture = chartFrameWithFractionalSlotCenter();
        ChartFrame chartFrame = fixture.chartFrame();
        var crosshairLayer = findCrosshairLayer(chartFrame);
        var crosshair = (StandardCrosshairRendererLayer) crosshairLayer.getUI();
        AnnotationPanel pane = chartFrame.getMainStackPanel().getChartPanel().getAnnotationPanel();
        Rectangle plotBounds = pane.getRenderBounds();
        int slot = fixture.slot();
        int x = (int) Math.round(chartFrame.getChartData().getSlotCenterX(slot, plotBounds));
        int y = plotBounds.y + plotBounds.height / 2;
        long when = System.currentTimeMillis();

        crosshair.eventDispatched(new MouseEvent(pane, MouseEvent.MOUSE_ENTERED, when, 0, x, y, 0, false, MouseEvent.NOBUTTON), crosshairLayer);
        crosshair.eventDispatched(new MouseEvent(pane, MouseEvent.MOUSE_MOVED, when + 1, 0, x, y, 0, false, MouseEvent.NOBUTTON), crosshairLayer);

        Point hoverPoint = hoverPoint(crosshair);
        Point expectedLayerPoint = SwingUtilities.convertPoint(pane, x, y, crosshairLayer);
        assertThat(hoverPoint.x).isEqualTo(expectedLayerPoint.x);
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
        long when = System.currentTimeMillis();

        crosshair.eventDispatched(new MouseEvent(pane, MouseEvent.MOUSE_ENTERED, when, 0, x, y, 0, false, MouseEvent.NOBUTTON), layer);
        crosshair.eventDispatched(new MouseEvent(pane, MouseEvent.MOUSE_MOVED, when + 1, 0, x, y, 0, false, MouseEvent.NOBUTTON), layer);

        Field overlayField = StandardCrosshairRendererLayer.class.getDeclaredField("valueLabelOverlay");
        overlayField.setAccessible(true);
        Object overlay = overlayField.get(crosshair);
        assertThat(overlay).isNotNull();

        Method boundsMethod = overlay.getClass().getDeclaredMethod("bounds");
        boundsMethod.setAccessible(true);
        return new Rectangle((Rectangle) boundsMethod.invoke(overlay));
    }

    private static Point hoverPoint(StandardCrosshairRendererLayer crosshair) throws Exception {
        Field hoverPointField = StandardCrosshairRendererLayer.class.getDeclaredField("hoverPoint");
        hoverPointField.setAccessible(true);
        return new Point((Point) hoverPointField.get(crosshair));
    }

    private record SlotFixture(ChartFrame chartFrame, int slot) {
    }
}
