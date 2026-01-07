/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.core.io.ResourcePaths;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.data.provider.file.SimpleCandleLineMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ChartExporterTest {

    private static final int PROMPT_BAR_COUNT = 60;

    @Test
    void can_export_as_png(@TempDir Path tempDir) throws Exception {
        FlatFileFormat format = btcDailyFormat();
        Path archive = ResourcePaths.pathToResource("BTC_DAILY.zip");

        try (FlatFileDataProvider provider = new FlatFileDataProvider(format, archive)) {
            var symbol = SymbolIdentity.of("BTC_DAILY");
            var resource = SymbolResource.of(symbol, TimeFrame.Period.DAILY);

            Path output = tempDir.resolve("chart.png");
            ChartExporter.export(output, provider, resource);

            assertThat(Files.size(output)).isGreaterThan(0L);
        }
    }

    @Test
    void can_export_as_svg(@TempDir Path tempDir) throws Exception {
        FlatFileFormat format = btcDailyFormat();
        Path archive = ResourcePaths.pathToResource("BTC_DAILY.zip");

        try (FlatFileDataProvider provider = new FlatFileDataProvider(format, archive)) {
            var symbol = SymbolIdentity.of("BTC_DAILY");
            var resource = SymbolResource.of(symbol, TimeFrame.Period.DAILY);

            Path output = tempDir.resolve("chart.svg");
            ChartExporter.export(output, provider, resource);

            assertThat(Files.size(output)).isGreaterThan(0L);
            assertThat(Files.readString(output)).contains("<svg");
        }
    }

    @Test
    void can_export_as_svgz(@TempDir Path tempDir) throws Exception {
        FlatFileFormat format = btcDailyFormat();
        Path archive = ResourcePaths.pathToResource("BTC_DAILY.zip");

        try (FlatFileDataProvider provider = new FlatFileDataProvider(format, archive)) {
            var symbol = SymbolIdentity.of("BTC_DAILY");
            var resource = SymbolResource.of(symbol, TimeFrame.Period.DAILY);

            Path output = tempDir.resolve("chart.svgz");
            ChartExporter.export(output, provider, resource);

            assertThat(Files.size(output)).isGreaterThan(0L);
            try (var in = new GZIPInputStream(Files.newInputStream(output))) {
                String svg = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertThat(svg).contains("<svg");
            }
        }
    }

    @Test
    void can_export_as_yaml(@TempDir Path tempDir) throws Exception {
        FlatFileFormat format = btcDailyFormat();
        Path archive = ResourcePaths.pathToResource("BTC_DAILY.zip");

        try (FlatFileDataProvider provider = new FlatFileDataProvider(format, archive)) {
            var symbol = SymbolIdentity.of("BTC_DAILY");
            var resource = SymbolResource.of(symbol, TimeFrame.Period.DAILY);

            Path output = tempDir.resolve("chart.yaml");
            ChartExporter.export(output, provider, resource);

            assertThat(Files.size(output)).isGreaterThan(0L);
            assertThat(Files.readString(output)).contains("daily_bars:");
        }
    }

    @Test
    void yaml_matches_legacy_format() throws Exception {
        FlatFileFormat format = btcDailyFormat();
        Path archive = ResourcePaths.pathToResource("BTC_DAILY.zip");

        try (FlatFileDataProvider provider = new FlatFileDataProvider(format, archive)) {
            var symbol = SymbolIdentity.of("BTC_DAILY");
            var resource = SymbolResource.of(symbol, TimeFrame.Period.DAILY).withDataType(Candle.class);
            DataQuery<Candle> query = DataQuery.<Candle>resource(resource).build();
            CandleSeries dataset = ChartExporter.loadCandles(provider, query);

            String actual = ChartDescription.from(dataset).toYamlString();
            String expected = legacyYaml(dataset);

            assertThat(actual).isEqualTo(expected);
        }
    }

    private static FlatFileFormat btcDailyFormat() {
        return FlatFileFormat.builder()
                .skipFirstLines(1)
                .lineMapper(new SimpleCandleLineMapper.Type(
                        ',', List.of("DATE", "OPEN", "HIGH", "LOW", "CLOSE", "VOLUME"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .build();
    }

    private static String legacyYaml(CandleSeries dataset) {
        var daily = aggregateCandles(dataset, TimeFrame.Period.DAILY);
        var weekly = aggregateCandles(dataset, TimeFrame.Period.WEEKLY);
        var monthly = aggregateCandles(dataset, TimeFrame.Period.MONTHLY);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("daily_bars", toBarsMap(daily, PROMPT_BAR_COUNT));
        root.put("weekly_bars", toBarsMap(weekly, PROMPT_BAR_COUNT));
        root.put("monthly_bars", toBarsMap(monthly, PROMPT_BAR_COUNT));

        return toYamlString(root);
    }

    private static List<Candle> aggregateCandles(CandleSeries series, TimeFrame timeFrame) {
        var aggregator = timeFrame.getAggregator();
        var chronological = new ArrayList<Candle>(series.length());
        for (Candle candle : series) {
            chronological.add(candle);
        }
        return aggregator.aggregate(chronological, true);
    }

    private static Map<String, Map<String, BigDecimal>> toBarsMap(List<Candle> candles, int count) {
        int startIndex = Math.max(0, candles.size() - count);
        var bars = new LinkedHashMap<String, Map<String, BigDecimal>>(count);
        for (int i = candles.size() - 1; i >= startIndex; i--) {
            Candle candle = candles.get(i);
            LocalDate date = candle.getDate();
            bars.put(date.toString(), toOhlcvMap(candle));
        }
        return bars;
    }

    private static Map<String, BigDecimal> toOhlcvMap(Candle candle) {
        var values = new LinkedHashMap<String, BigDecimal>(5);
        values.put("open", toDecimal(candle.open()));
        values.put("high", toDecimal(candle.high()));
        values.put("low", toDecimal(candle.low()));
        values.put("close", toDecimal(candle.close()));
        values.put("volume", toDecimal(candle.volume()));
        return values;
    }

    private static BigDecimal toDecimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String toYamlString(Map<String, Object> data) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(1);
        options.setLineBreak(DumperOptions.LineBreak.UNIX);

        Yaml yaml = new Yaml(options);
        return yaml.dump(data).stripTrailing();
    }
}
