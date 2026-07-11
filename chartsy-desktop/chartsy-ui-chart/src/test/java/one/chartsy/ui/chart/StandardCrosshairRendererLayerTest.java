package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.charting.Scale;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.provider.DataProvider;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.JLayer;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class StandardCrosshairRendererLayerTest {

    @Test
    void device_space_graphics_paints_every_one_pixel_xor_phase() {
        BufferedImage image = new BufferedImage(80, 60, BufferedImage.TYPE_INT_ARGB);
        Graphics2D userGraphics = image.createGraphics();
        try {
            userGraphics.setColor(Color.WHITE);
            userGraphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            userGraphics.scale(1.25, 1.25);
            userGraphics.clip(new Rectangle(8, 8, 40, 30));
            userGraphics.setColor(Color.BLACK);
            userGraphics.setXORMode(Color.WHITE);

            Graphics2D deviceGraphics = StandardCrosshairRendererLayer.createDeviceSpaceGraphics(userGraphics);
            try {
                assertThat(deviceGraphics.getTransform()).isEqualTo(new AffineTransform());
                for (int x = 20; x < 25; x++)
                    deviceGraphics.fillRect(x, 13, 1, 10);
                for (int y = 30; y < 35; y++)
                    deviceGraphics.fillRect(30, y, 10, 1);
                deviceGraphics.fillRect(5, 13, 1, 10);
                deviceGraphics.fillRect(65, 13, 1, 10);
                deviceGraphics.fillRect(30, 5, 10, 1);
                deviceGraphics.fillRect(30, 50, 10, 1);
            } finally {
                deviceGraphics.dispose();
            }
        } finally {
            userGraphics.dispose();
        }

        for (int x = 20; x < 25; x++) {
            assertThat(nonWhitePixelsInColumn(image, x, 13, 23))
                    .as("device x phase %s", x % 5)
                    .isEqualTo(10);
        }
        for (int y = 30; y < 35; y++) {
            assertThat(nonWhitePixelsInRow(image, y, 30, 40))
                    .as("device y phase %s", y % 5)
                    .isEqualTo(10);
        }
        assertThat(image.getRGB(5, 13)).isEqualTo(Color.WHITE.getRGB());
        assertThat(image.getRGB(65, 13)).isEqualTo(Color.WHITE.getRGB());
        assertThat(image.getRGB(30, 5)).isEqualTo(Color.WHITE.getRGB());
        assertThat(image.getRGB(30, 50)).isEqualTo(Color.WHITE.getRGB());
    }

    @Test
    void resolveScaleBoundsRecoversAfterScaleInvalidation() {
        ChartFrame chartFrame = ChartExporter.createChartFrame(
                DataProvider.EMPTY,
                fixtureDataset(),
                ChartTemplateDefaults.basicChartTemplate(),
                new Dimension(1280, 800)
        );
        Scale valueScale = chartFrame.getMainPanel().getChartPanel().getEngineChart().getYScale(0);
        @SuppressWarnings("unchecked")
        JLayer<? extends JComponent> layer = (JLayer<? extends JComponent>) SwingUtilities.getAncestorOfClass(
                JLayer.class,
                chartFrame.getMainPanel()
        );

        valueScale.getChart().invalidateScales();

        assertThat(layer).isNotNull();
        assertThatNoException()
                .isThrownBy(() -> new StandardCrosshairRendererLayer().resolveScaleBounds(valueScale, layer));
    }

    private static int nonWhitePixelsInColumn(BufferedImage image, int x, int firstY, int lastY) {
        int count = 0;
        for (int y = firstY; y < lastY; y++) {
            if (image.getRGB(x, y) != Color.WHITE.getRGB())
                count++;
        }
        return count;
    }

    private static int nonWhitePixelsInRow(BufferedImage image, int y, int firstX, int lastX) {
        int count = 0;
        for (int x = firstX; x < lastX; x++) {
            if (image.getRGB(x, y) != Color.WHITE.getRGB())
                count++;
        }
        return count;
    }

    private static CandleSeries fixtureDataset() {
        return CandleSeries.of(
                SymbolResource.of(SymbolIdentity.of("CROSSHAIR-FIXTURE"), TimeFrame.Period.DAILY)
                        .withDataType(Candle.class),
                List.of(
                        Candle.of(LocalDate.of(2026, 1, 1).atStartOfDay(), 100, 104, 98, 103, 1_000),
                        Candle.of(LocalDate.of(2026, 1, 2).atStartOfDay(), 103, 106, 101, 105, 6_000),
                        Candle.of(LocalDate.of(2026, 1, 3).atStartOfDay(), 105, 107, 102, 104, 3_500),
                        Candle.of(LocalDate.of(2026, 1, 4).atStartOfDay(), 104, 108, 103, 107, 8_000),
                        Candle.of(LocalDate.of(2026, 1, 5).atStartOfDay(), 107, 111, 106, 110, 7_400)
                )
        );
    }
}
