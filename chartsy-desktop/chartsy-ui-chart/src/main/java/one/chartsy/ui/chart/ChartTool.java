/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.ui.chart;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.provider.DataProvider;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a prompt-ready summary without launching the full application.
 * <p>
 * This utility intentionally mirrors the "Basic Chart" template used by the GUI front-end
 * (candlesticks + common overlays/indicators).
 */
public final class ChartTool {

    private static final String GOAL_ANALYZE = """
            Analyze the attached financial chart for trade opportunities and their qualities.
            """.strip();
    private static final int PROMPT_BAR_COUNT = 60;

    public record AnalysisPrompt(String prompt, String context) {

        public Path writeTo(Path destination) throws IOException {
            return Files.writeString(destination, this.toString());
        }

        @Override
        public String toString() {
            return prompt + "\n\n" + context;
        }
    }

    /**
     * Specifies the inclusive end time for candle loading.
     *
     * <p>All available candles up to {@code endTime} are loaded.
     *
     * @param endTime inclusive visible end, or {@code null} for unbounded
     */
    public record DataRange(LocalDateTime endTime) {
        public static DataRange all() {
            return new DataRange(null);
        }

        public static DataRange until(LocalDateTime endTime) {
            return new DataRange(endTime);
        }
    }

    private ChartTool() { }

    public static AnalysisPrompt createAnalysisPrompt(DataProvider provider,
                                                      SymbolIdentity symbol,
                                                      TimeFrame timeFrame) {
        return createAnalysisPrompt(provider, symbol, timeFrame, DataRange.all());
    }

    public static AnalysisPrompt createAnalysisPrompt(DataProvider provider,
                                              SymbolIdentity symbol,
                                              TimeFrame timeFrame,
                                              DataRange range) {
        var dataset = loadCandles(provider, symbol, timeFrame, range);
        var daily = aggregateCandles(dataset, TimeFrame.Period.DAILY);
        var weekly = aggregateCandles(dataset, TimeFrame.Period.WEEKLY);
        var monthly = aggregateCandles(dataset, TimeFrame.Period.MONTHLY);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("daily_bars", toBarsMap(daily, PROMPT_BAR_COUNT, "daily"));
        root.put("weekly_bars", toBarsMap(weekly, PROMPT_BAR_COUNT, "weekly"));
        root.put("monthly_bars", toBarsMap(monthly, PROMPT_BAR_COUNT, "monthly"));

        return new AnalysisPrompt(GOAL_ANALYZE, toYamlString(root));
    }

    private static CandleSeries loadCandles(DataProvider provider,
                                            SymbolIdentity symbol,
                                            TimeFrame timeFrame,
                                            DataRange range) {
        var resource = SymbolResource.of(symbol, timeFrame).withDataType(Candle.class);

        DataQuery<Candle> query = DataQuery.<Candle>resource(resource)
                .endTime(range.endTime())
                .build();
        return provider.query(Candle.class, query)
                .collectSortedList()
                .as(CandleSeries.of(resource));
    }

    private static List<Candle> aggregateCandles(CandleSeries series, TimeFrame timeFrame) {
        var aggregator = timeFrame.getAggregator();
        var chronological = new ArrayList<Candle>(series.length());
        for (Candle candle : series) {
            chronological.add(candle);
        }
        return aggregator.aggregate(chronological, true);
    }

    private static Map<String, Map<String, BigDecimal>> toBarsMap(List<Candle> candles, int count, String label) {
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
