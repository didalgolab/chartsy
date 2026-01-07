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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

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
        return createAnalysisPrompt(dataset);
    }

    public static AnalysisPrompt createAnalysisPrompt(CandleSeries dataset) {
        ChartDescription context = ChartExporter.createChartDescription(dataset);
        return new AnalysisPrompt(GOAL_ANALYZE, context.toYamlString());
    }

    private static CandleSeries loadCandles(DataProvider provider,
                                            SymbolIdentity symbol,
                                            TimeFrame timeFrame,
                                            DataRange range) {
        var resource = SymbolResource.of(symbol, timeFrame).withDataType(Candle.class);

        DataQuery<Candle> query = DataQuery.<Candle>resource(resource)
                .endTime(range.endTime())
                .build();
        return ChartExporter.loadCandles(provider, query);
    }

}
