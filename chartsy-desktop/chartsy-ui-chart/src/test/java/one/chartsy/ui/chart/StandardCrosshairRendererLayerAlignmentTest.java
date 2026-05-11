package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.provider.DataProvider;
import one.chartsy.ui.chart.components.AnnotationPanel;
import one.chartsy.ui.chart.components.IndicatorPanel;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLayer;
import java.awt.Component;
import java.awt.Container;
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
        return ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                fixtureDataset(),
                ChartTemplateDefaults.basicChartTemplate(),
                new java.awt.Dimension(1280, 800)
        );
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
}
