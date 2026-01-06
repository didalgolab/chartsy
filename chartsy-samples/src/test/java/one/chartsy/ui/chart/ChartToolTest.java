/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.FrontEndSupport;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.SystemFiles;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.data.provider.file.SimpleCandleLineMapper;
import one.chartsy.core.io.ResourcePaths;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Manual only")
class ChartToolTest {

    @Test
    void renders_basic_chart_with_expected_overlays_and_indicator(@TempDir Path tempDir) throws Exception {
        // Arrange: use included historical BTC data for a natural-looking chart.
        FlatFileFormat format = btcDailyFormat();

        Path archive = ResourcePaths.pathToResource("BTC_DAILY.zip");
        try (FlatFileDataProvider provider = new FlatFileDataProvider(format, archive)) {
            var symbol = SymbolIdentity.of("BTC_DAILY");
            var timeFrame = TimeFrame.Period.DAILY;
            var range = ChartTool.DataRange.all();
            var size = new Dimension(1536, 793);

            // Assert template composition (services must be discoverable without launching the app).
            ChartTemplate template = ChartExporter.basicChartTemplate();
            assertThat(template.getOverlays()).extracting(Overlay::getName)
                    .contains("FRAMA, Leading", "FRAMA, Trailing", "Sfora", "Volume", "Sentiment Bands");
            assertThat(template.getIndicators()).extracting(Indicator::getName)
                    .contains("Fractal Dimension");

            // Assert the resulting ChartFrame contains expected plugins (ensures proper wiring with ChartFrame listeners).
            DataQuery<Candle> query = DataQuery.<Candle>resource(
                            SymbolResource.of(symbol, timeFrame).withDataType(Candle.class))
                    .endTime(range.endTime())
                    .build();
            CandleSeries dataset = ChartExporter.loadCandles(provider, query);
            ChartFrame frame = ChartExporter.createChartFrame(provider, dataset, template, size);
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
            ChartFrame exportFrame = ChartExporter.createExportChartFrame(provider, query, ExportOptions.builder()
                    .dimensions(size)
                    .build());
            BufferedImage image = FrontEndSupport.getDefault().paintComponent(exportFrame);

            // Assert: basic image sanity.
            assertThat(image.getWidth()).isEqualTo(size.width);
            assertThat(image.getHeight()).isEqualTo(size.height);
            assertThat(hasNonWhitePixel(image)).isTrue();

            // Assert: image can be written to PNG.
            Path output = tempDir.resolve("chart.png");
            ImageIO.write(image, "png", output.toFile());
            assertThat(Files.size(output)).isGreaterThan(0L);
        }
    }

    @Test
    void renders_chart_to_svg(@TempDir Path tempDir) throws Exception {
        FlatFileFormat format = FlatFileFormat.builder()
                .skipFirstLines(1)
                .lineMapper(new SimpleCandleLineMapper.Type(
                        ',', List.of("DATE", "OPEN", "HIGH", "LOW", "CLOSE", "VOLUME"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .build();

        Path archive = ResourcePaths.pathToResource("BTC_DAILY.zip");
        try (FlatFileDataProvider provider = new FlatFileDataProvider(format, archive)) {
            var symbol = SymbolIdentity.of("BTC_DAILY");
            var timeFrame = TimeFrame.Period.DAILY;
            var range = ChartTool.DataRange.all();
            var size = new Dimension(1536, 793);

            Path output = tempDir.resolve("chart.svg");
            DataQuery<Candle> query = DataQuery.<Candle>resource(
                            SymbolResource.of(symbol, timeFrame).withDataType(Candle.class))
                    .endTime(range.endTime())
                    .build();
            ExportOptions options = ExportOptions.builder()
                    .format(ChartExporter.Format.SVG)
                    .dimensions(size)
                    .build();
            ChartExporter.export(output, provider, query, options);

            assertThat(Files.size(output)).isGreaterThan(0L);
            assertThat(Files.readString(output)).contains("<svg");
        }
    }

    @Test
    void creates_prompt_with_expected_bar_counts() throws Exception {
        FlatFileFormat format = btcDailyFormat();

        Path archive = ResourcePaths.pathToResource("BTC_DAILY.zip");
        try (FlatFileDataProvider provider = new FlatFileDataProvider(format, archive)) {
            var symbol = SymbolIdentity.of("BTC_DAILY");
            var timeFrame = TimeFrame.Period.DAILY;
            var range = ChartTool.DataRange.all();

            String prompt = ChartTool.createAnalysisPrompt(provider, symbol, timeFrame, range).prompt();
            Map<String, Object> parsed = new Yaml().load(prompt);

            assertThat(parsed).containsKeys("daily_bars", "weekly_bars", "monthly_bars");
            assertThat(((Map<?, ?>) parsed.get("daily_bars")).size()).isEqualTo(24);
            assertThat(((Map<?, ?>) parsed.get("weekly_bars")).size()).isEqualTo(24);
            assertThat(((Map<?, ?>) parsed.get("monthly_bars")).size()).isEqualTo(24);
        }
    }

    @Test
    void writes_prompt_to_temp_file_for_series_ending_on_2025_06_01(@TempDir Path tempDir) throws Exception {
        FlatFileFormat format = btcDailyFormat();

        Path archive = ResourcePaths.pathToResource("BTC_DAILY.zip");
        try (FlatFileDataProvider provider = new FlatFileDataProvider(format, archive)) {
            var symbol = SymbolIdentity.of("BTC_DAILY");
            var timeFrame = TimeFrame.Period.DAILY;
            var endTime = LocalDate.of(2025, 6, 1).atTime(23, 59, 59);
            var range = ChartTool.DataRange.until(endTime);

            var output = tempDir.resolve("btc_prompt_2025-06-01.yaml");
            var prompt = ChartTool.createAnalysisPrompt(provider, symbol, timeFrame, range);
            prompt.writeTo(output);
            System.out.println("Prompt written to: " + output.toAbsolutePath());

            assertThat(Files.size(output)).isGreaterThan(0L);
            assertThat(prompt.prompt()).contains("2025-05-31");
        }
    }

    @Test
    void renders_stooq_chart_for_user_supplied_zip_symbol_and_end_date() throws Exception {
        Path zipPath = resolveStooqZipPath(System.getProperty("stooq.zip",
                "C:/Users/Mariusz/Downloads/d_pl_txt (1).zip"));
        String symbol = System.getProperty("stooq.symbol", "ATR");
        LocalDate endDate = LocalDate.parse(System.getProperty("stooq.endDate", "2025-05-12"));
        SymbolIdentity symbolId = SymbolIdentity.of(symbol);

        try (FlatFileDataProvider provider = FlatFileFormat.STOOQ.newDataProvider(zipPath)) {
            var timeFrame = TimeFrame.Period.DAILY;
            var endTime = endDate.atStartOfDay().plusDays(1);
            var range = ChartTool.DataRange.until(endTime);

            DataQuery<Candle> datasetQuery = DataQuery.<Candle>resource(
                            SymbolResource.of(symbolId, timeFrame).withDataType(Candle.class))
                    .endTime(range.endTime())
                    .build();
            CandleSeries dataset = ChartExporter.loadCandles(provider, datasetQuery);
            assertThat(dataset.isEmpty()).isFalse();

            Path outputDir = SystemFiles.PRIVATE_DIR.resolve("ChartTool");
            Files.createDirectories(outputDir);
            Path output = outputDir.resolve(symbol + "__" + endDate + ".png");

            DataQuery<Candle> query = DataQuery.<Candle>resource(
                            SymbolResource.of(symbolId, timeFrame).withDataType(Candle.class))
                    .endTime(range.endTime())
                    .build();
            ExportOptions options = ExportOptions.builder()
                    .format(ChartExporter.Format.PNG)
                    .dimensions(new Dimension(1536, 793))
                    .build();
            ChartExporter.export(output, provider, query, options);
            System.out.println("Stooq screenshot saved to: " + output.toAbsolutePath());

            assertThat(Files.size(output)).isGreaterThan(0L);

            Path promptPath = outputDir.resolve(symbol + "__" + endDate + ".prompt.md");
            ChartTool.createAnalysisPrompt(provider, symbolId, timeFrame, range).writeTo(promptPath);
            System.out.println("Stooq prompt written to: " + promptPath.toAbsolutePath());

            assertThat(Files.size(promptPath)).isGreaterThan(0L);
        }
    }

    private static Path resolveStooqZipPath(String rawPath) {
        Objects.requireNonNull(rawPath, "rawPath");
        Path path = Path.of(rawPath);
        if (Files.exists(path)) {
            return path;
        }
        String normalized = rawPath.replace('\\', '/');
        if (normalized.length() >= 3 && Character.isLetter(normalized.charAt(0)) && normalized.charAt(1) == ':') {
            String drive = String.valueOf(Character.toLowerCase(normalized.charAt(0)));
            String rest = normalized.substring(2);
            if (rest.startsWith("/")) {
                rest = rest.substring(1);
            }
            Path wslPath = Path.of("/mnt", drive, rest);
            if (Files.exists(wslPath)) {
                return wslPath;
            }
        }
        throw new IllegalArgumentException("Stooq ZIP file not found: " + rawPath);
    }

    private static FlatFileFormat btcDailyFormat() {
        return FlatFileFormat.builder()
                .skipFirstLines(1)
                .lineMapper(new SimpleCandleLineMapper.Type(
                        ',', List.of("DATE", "OPEN", "HIGH", "LOW", "CLOSE", "VOLUME"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .build();
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
