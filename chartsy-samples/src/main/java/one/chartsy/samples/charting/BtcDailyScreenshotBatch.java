/* Copyright 2025 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.samples.charting;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.TimeFrame;
import one.chartsy.core.io.ResourcePaths;
import one.chartsy.data.CandleSeries;
import one.chartsy.data.DataQuery;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.data.provider.file.SimpleCandleLineMapper;
import one.chartsy.time.Chronological;
import one.chartsy.ui.chart.ChartTool;
import one.chartsy.SymbolResource;

import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class BtcDailyScreenshotBatch {
    private static final int VISIBLE_BARS = 200;
    private static final Dimension IMAGE_SIZE = new Dimension(1536, 793);

    public static void main(String[] args) throws Exception {
        Path repoRoot = Path.of("").toAbsolutePath();
        Path outputRoot = repoRoot.resolve("private");
        Files.createDirectories(outputRoot);

        FlatFileFormat format = FlatFileFormat.builder()
                .skipFirstLines(1)
                .lineMapper(new SimpleCandleLineMapper.Type(
                        ',', List.of("DATE", "OPEN", "HIGH", "LOW", "CLOSE", "VOLUME"),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd")))
                .build();

        Path archive = ResourcePaths.pathToResource("BTC_DAILY.zip");
        FlatFileDataProvider provider = new FlatFileDataProvider(format, archive);

        SymbolIdentity symbol = SymbolIdentity.of("BTC_DAILY");
        TimeFrame timeFrame = TimeFrame.Period.DAILY;

        CandleSeries all = loadAllCandles(provider, symbol, timeFrame);
        List<Candle> candles = new java.util.ArrayList<>(all.getData().toImmutableList());
        if (candles.isEmpty())
            throw new IllegalStateException("No candle data loaded from BTC_DAILY.zip");

        candles.sort(Comparator.comparingLong(Candle::time));
        LocalDate lastDate = Chronological.toDateTime(candles.get(candles.size() - 1).time()).toLocalDate();
        LocalDate startDate = LocalDate.of(lastDate.getYear() - 1, 1, 1);

        Path outputDir = outputRoot.resolve(
                "btc_daily_screenshots_" + startDate.getYear() + "_" + lastDate.getYear());
        Files.createDirectories(outputDir);

        ChartTool.DataRange baseRange = ChartTool.DataRange.last(VISIBLE_BARS);
        int count = 0;

        for (Candle candle : candles) {
            LocalDateTime endTime = Chronological.toDateTime(candle.time());
            LocalDate date = endTime.toLocalDate();
            if (date.isBefore(startDate))
                continue;
            if (date.isAfter(lastDate))
                break;

            ChartTool.DataRange range = new ChartTool.DataRange(
                    null, endTime, baseRange.limit(), baseRange.warmupBars());
            Path output = outputDir.resolve(date + ".png");
            ChartTool.renderChartToPng(output, provider, symbol, timeFrame, range, IMAGE_SIZE);

            count++;
            if (count % 50 == 0)
                System.out.printf("Rendered %d screenshots...%n", count);
        }

        System.out.printf("Rendered %d screenshots to %s (range %s to %s).%n",
                count, outputDir.toAbsolutePath(), startDate, lastDate);
    }

    private static CandleSeries loadAllCandles(FlatFileDataProvider provider,
                                               SymbolIdentity symbol,
                                               TimeFrame timeFrame) throws IOException {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(timeFrame, "timeFrame");

        var resource = SymbolResource.of(symbol, timeFrame).withDataType(Candle.class);
        DataQuery<Candle> query = DataQuery.<Candle>resource(resource).build();
        List<Candle> candles = provider.queryForCandles(query).collectSortedList().block();
        if (candles == null || candles.isEmpty())
            throw new IllegalStateException("No candle data for symbol `" + symbol.name() + "`");
        return CandleSeries.of((SymbolResource<Candle>) resource, candles);
    }
}
