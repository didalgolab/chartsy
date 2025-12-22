/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.SymbolIdentity;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.data.provider.file.SimpleCandleLineMapper;
import one.chartsy.core.io.ResourcePaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChartScreenshotToolTest {

    @Test
    void renders_basic_chart_with_expected_overlays_and_indicator(@TempDir Path tempDir) throws Exception {
        // Arrange: use included historical BTC data for a natural-looking chart.
        FlatFileFormat format = FlatFileFormat.builder()
                .skipFirstLines(1)
                .lineMapper(new SimpleCandleLineMapper.Type(
                        ',', List.of("DATE", "OPEN", "HIGH", "LOW", "CLOSE", "VOLUME"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .build();

        Path archive = ResourcePaths.pathToResource("BTC_DAILY.zip");
        FlatFileDataProvider provider = new FlatFileDataProvider(format, archive);

        var symbol = SymbolIdentity.of("BTC_DAILY");
        var timeFrame = TimeFrame.Period.DAILY;
        var range = ChartScreenshotTool.DataRange.last(200);
        var size = new Dimension(1200, 800);

        // Assert template composition (services must be discoverable without launching the app).
        ChartTemplate template = ChartScreenshotTool.basicChartTemplate();
        assertThat(template.getOverlays()).extracting(Overlay::getName)
                .contains("FRAMA, Leading", "FRAMA, Trailing", "Sfora", "Volume", "Sentiment Bands");
        assertThat(template.getIndicators()).extracting(Indicator::getName)
                .contains("Fractal Dimension");

        // Assert the resulting ChartFrame contains expected plugins (ensures proper wiring with ChartFrame listeners).
        CandleSeries dataset = ChartScreenshotTool.loadCandles(provider, symbol, timeFrame, range);
        ChartFrame frame = ChartScreenshotTool.createChartFrame(provider, dataset, template, size);
        assertThat(frame.getWidth()).isEqualTo(size.width);
        assertThat(frame.getHeight()).isEqualTo(size.height);
        assertThat(frame.getMainPanel().getChartPanel().getWidth()).isGreaterThan(0);
        assertThat(frame.getMainPanel().getChartPanel().getHeight()).isGreaterThan(0);
        assertThat(frame.getMainStackPanel().getChartPanel().getOverlays())
                .extracting(Overlay::getName)
                .contains("FRAMA, Leading", "FRAMA, Trailing", "Sfora", "Volume", "Sentiment Bands");
        assertThat(frame.getMainStackPanel().getIndicatorsList())
                .extracting(Indicator::getName)
                .contains("Fractal Dimension");

        // Assert: warmup bars are kept so long-warmup overlays don't "start late" at the left edge.
        Overlay sfora = frame.getMainStackPanel().getChartPanel().getOverlays().stream()
                .filter(o -> o.getName().equals("Sfora"))
                .findFirst()
                .orElseThrow();
        assertThat(sfora.visibleDataset(frame, "8")).isNotNull();
        assertThat(sfora.visibleDataset(frame, "8").getValueAt(0)).isNotNaN();

        // Act: render a screenshot using the tool.
        BufferedImage image = ChartScreenshotTool.renderChart(provider, symbol, timeFrame, range, size);

        // Assert: basic image sanity.
        assertThat(image.getWidth()).isEqualTo(size.width);
        assertThat(image.getHeight()).isEqualTo(size.height);
        assertThat(hasNonWhitePixel(image)).isTrue();

        // Assert: image can be written to PNG.
        Path output = tempDir.resolve("chart.png");
        ImageIO.write(image, "png", output.toFile());
        assertThat(Files.size(output)).isGreaterThan(0L);
    }

    private static boolean hasNonWhitePixel(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int white = 0x00FFFFFF;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((image.getRGB(x, y) & 0x00FFFFFF) != white)
                    return true;
            }
        }
        return false;
    }
}
