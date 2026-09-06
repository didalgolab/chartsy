/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider;

import one.chartsy.Candle;
import one.chartsy.SymbolIdentity;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.core.ResourceHandle;
import one.chartsy.data.DataQuery;
import one.chartsy.data.UnsupportedDataQueryException;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.data.provider.file.FlatFileFormatException;
import one.chartsy.time.Chronological;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class FlatFileDataProviderTimeFrameTest {
    private static final String HEADER = "<TICKER>,<PER>,<DATE>,<TIME>,<OPEN>,<HIGH>,<LOW>,<CLOSE>,<VOL>\n";
    @TempDir Path directory;

    @Test
    void query_weekly_from_daily_zip_aggregates_candles_and_preserves_daily_reload() throws IOException {
        try (var provider = dailyProvider()) {
            var daily = candles(provider, TimeFrame.Period.DAILY);
            var weekly = candles(provider, TimeFrame.Period.WEEKLY);

            assertEquals(4, daily.size());
            assertEquals(2, weekly.size());
            assertCandle(weekly.getFirst(), "2026-01-31T00:00", 10, 18, 8, 16, 300);
            assertCandle(weekly.getLast(), "2026-02-04T00:00", 20, 28, 18, 26, 700);
            assertEquals(daily, candles(provider, TimeFrame.Period.DAILY));
        }
    }

    @Test
    void query_monthly_quarterly_and_yearly_group_daily_rows_by_calendar_period() throws IOException {
        try (var provider = dailyProvider()) {
            var monthly = candles(provider, TimeFrame.Period.MONTHLY);
            assertEquals(2, monthly.size());
            assertCandle(monthly.getFirst(), "2026-01-31T00:00", 10, 18, 8, 16, 300);
            assertCandle(monthly.getLast(), "2026-02-04T00:00", 20, 28, 18, 26, 700);
            for (var frame : List.of(TimeFrame.Period.QUARTERLY, TimeFrame.Period.YEARLY)) {
                var result = candles(provider, frame);
                assertEquals(1, result.size());
                assertCandle(result.getFirst(), "2026-02-04T00:00", 10, 28, 8, 26, 1000);
            }
        }
    }

    @Test
    void query_limit_applies_to_aggregated_bars_and_end_time_excludes_future_inputs() throws IOException {
        try (var provider = dailyProvider()) {
            var query = DataQuery.resource(resource(provider, TimeFrame.Period.WEEKLY))
                    .endTime(LocalDateTime.parse("2026-02-03T00:00")).limit(1).build();
            var result = provider.query(Candle.class, query).collectList().block();
            assertEquals(1, result.size());
            assertCandle(result.getFirst(), "2026-02-03T00:00", 20, 24, 18, 22, 300);
        }
    }

    @Test
    void getAvailableTimeFrames_daily_archive_offers_supported_frames_and_rejects_intraday() throws IOException {
        try (var provider = dailyProvider()) {
            assertEquals(List.of(TimeFrame.Period.DAILY, TimeFrame.Period.WEEKLY,
                            TimeFrame.Period.MONTHLY, TimeFrame.Period.QUARTERLY, TimeFrame.Period.YEARLY),
                    provider.getAvailableTimeFrames(provider.listSymbols().getFirst()));
            assertThrows(UnsupportedDataQueryException.class, () -> candles(provider, TimeFrame.Period.H1));
        }
    }

    @Test
    void query_hourly_archive_uses_native_timestamps_and_aggregates_without_daily_shift() throws IOException {
        try (var provider = provider("""
                TEST,60,20260130,100000,10,14,8,12,100
                TEST,60,20260130,110000,14,18,12,16,200
                TEST,60,20260202,100000,20,24,18,22,300
                """)) {
            var frames = provider.getAvailableTimeFrames(provider.listSymbols().getFirst());
            assertTrue(frames.containsAll(List.of(TimeFrame.Period.H1, TimeFrame.Period.H2,
                    TimeFrame.Period.DAILY, TimeFrame.Period.WEEKLY, TimeFrame.Period.MONTHLY)));
            assertFalse(frames.contains(TimeFrame.Period.M30));
            assertThrows(UnsupportedDataQueryException.class, () -> candles(provider, TimeFrame.Period.M30));
            var daily = candles(provider, TimeFrame.Period.DAILY);
            assertEquals(2, daily.size());
            assertCandle(daily.getFirst(), "2026-01-30T11:00", 10, 18, 8, 16, 300);
            assertCandle(daily.getLast(), "2026-02-02T10:00", 20, 24, 18, 22, 300);
            assertEquals(3, candles(provider, TimeFrame.Period.H1).size());
        }
    }

    @Test
    void query_period_boundaries_keep_last_day_in_its_week_and_month() throws IOException {
        try (var provider = provider("""
                TEST,D,20260531,000000,10,14,8,12,100
                TEST,D,20260601,000000,14,18,12,16,200
                """)) {
            for (var frame : List.of(TimeFrame.Period.WEEKLY, TimeFrame.Period.MONTHLY)) {
                var result = candles(provider, frame);
                assertEquals(2, result.size());
                assertCandle(result.getFirst(), "2026-06-01T00:00", 10, 14, 8, 12, 100);
                assertCandle(result.getLast(), "2026-06-02T00:00", 14, 18, 12, 16, 200);
            }
        }
    }

    @Test
    void getAvailableTimeFrames_weekly_archive_does_not_split_weeks_into_months() throws IOException {
        try (var provider = provider("TEST,W,20260130,000000,10,14,8,12,100\n")) {
            assertEquals(List.of(TimeFrame.Period.WEEKLY),
                    provider.getAvailableTimeFrames(provider.listSymbols().getFirst()));
            assertThrows(UnsupportedDataQueryException.class, () -> candles(provider, TimeFrame.Period.MONTHLY));
            assertThrows(UnsupportedDataQueryException.class, () -> candles(provider, TimeFrame.Period.DAILY));
            assertEquals(1, candles(provider, TimeFrame.Period.WEEKLY).size());
        }
    }

    @Test
    void getAvailableTimeFrames_monthly_archive_allows_quarters_and_years() throws IOException {
        try (var provider = provider("""
                TEST,M,20260130,000000,10,14,8,12,100
                TEST,M,20260227,000000,14,18,12,16,200
                """)) {
            assertEquals(List.of(TimeFrame.Period.MONTHLY, TimeFrame.Period.QUARTERLY, TimeFrame.Period.YEARLY),
                    provider.getAvailableTimeFrames(provider.listSymbols().getFirst()));
            var result = candles(provider, TimeFrame.Period.QUARTERLY);
            assertEquals(1, result.size());
            assertCandle(result.getFirst(), "2026-02-28T00:00", 10, 18, 8, 16, 300);
        }
    }

    @Test
    void query_unknown_native_period_fails_instead_of_relabelling_candles() throws IOException {
        try (var provider = provider("TEST,UNKNOWN,20260130,000000,10,14,8,12,100\n")) {
            assertThrows(FlatFileFormatException.class, () -> candles(provider, TimeFrame.Period.DAILY));
        }
    }

    @Test
    void query_empty_archive_series_returns_no_candles() throws IOException {
        try (var provider = provider("")) {
            assertTrue(candles(provider, TimeFrame.Period.WEEKLY).isEmpty());
        }
    }

    @Test
    void query_refreshes_native_period_after_an_empty_local_file_is_populated() throws IOException {
        Path data = directory.resolve("test.txt");
        Files.writeString(data, HEADER);
        try (var provider = localProvider()) {
            assertTrue(candles(provider, TimeFrame.Period.WEEKLY).isEmpty());
            Files.writeString(data, HEADER + """
                    TEST,D,20260130,000000,10,14,8,12,100
                    TEST,D,20260202,000000,20,24,18,22,300
                    TEST,D,20260203,000000,24,28,22,26,400
                    """);
            assertEquals(2, candles(provider, TimeFrame.Period.WEEKLY).size());
        }
    }

    @Test
    void query_refreshes_changed_native_period_on_a_writable_zip_filesystem() throws IOException {
        try (var provider = dailyProvider()) {
            assertEquals(4, candles(provider, TimeFrame.Period.DAILY).size());
            Path data = provider.getFileSystem().getPath("/data/quotes/test.txt");
            FileTime previous = Files.getLastModifiedTime(data);
            Files.writeString(data, HEADER + """
                    TEST,60,20260130,100000,10,14,8,12,100
                    TEST,60,20260130,110000,14,18,12,16,200
                    """);
            Files.setLastModifiedTime(data, FileTime.fromMillis(previous.toMillis() + 2000));
            var daily = candles(provider, TimeFrame.Period.DAILY);
            assertEquals(1, daily.size());
            assertCandle(daily.getFirst(), "2026-01-30T11:00", 10, 18, 8, 16, 300);
            assertTrue(provider.getAvailableTimeFrames(provider.listSymbols().getFirst()).contains(TimeFrame.Period.H1));
        }
    }

    @Test
    void query_rejects_custom_boundaries_that_cannot_be_reconstructed_from_source_bars() throws IOException {
        try (var provider = dailyProvider()) {
            assertThrows(UnsupportedDataQueryException.class,
                    () -> candles(provider, TimeFrame.Period.DAILY.withDailyAlignment(LocalTime.NOON)));
            assertThrows(UnsupportedDataQueryException.class,
                    () -> candles(provider, TimeFrame.Period.DAILY.withTimeZone(ZoneId.of("America/New_York"))));
        }
        try (var provider = provider("TEST,W,20260130,000000,10,14,8,12,100\n")) {
            assertThrows(UnsupportedDataQueryException.class,
                    () -> candles(provider, TimeFrame.Period.WEEKLY.withCandleAlignment(DayOfWeek.TUESDAY)));
        }
    }

    @Test
    void query_accepts_equivalent_native_custom_period_after_metadata_refresh() throws IOException {
        Files.writeString(directory.resolve("test.txt"), HEADER + "TEST,7,20260130,100000,10,14,8,12,100\n");
        try (var provider = localProvider()) {
            var nativePeriod = TimeFrame.CustomPeriod.of(Duration.ofMinutes(7), "Seven minutes");
            assertEquals(1, candles(provider, nativePeriod).size());
            assertThrows(UnsupportedDataQueryException.class, () -> candles(provider, TimeFrame.Period.WEEKLY));
            Files.writeString(directory.resolve("test.txt"), HEADER + """
                    TEST,7,20260130,100000,10,14,8,12,100
                    TEST,7,20260130,100700,14,18,12,16,200
                    """);
            assertEquals(2, candles(provider, nativePeriod).size());
        }
    }

    @Test
    void getAvailableTimeFrames_refreshes_same_size_file_when_native_period_changes() throws IOException {
        Path data = directory.resolve("test.txt");
        String daily = HEADER + "TEST,D,20260130,000000,10,14,8,12,100\n";
        Files.writeString(data, daily);
        try (var provider = localProvider()) {
            var symbol = provider.listSymbols().getFirst();
            assertTrue(provider.getAvailableTimeFrames(symbol).contains(TimeFrame.Period.MONTHLY));
            FileTime previous = Files.getLastModifiedTime(data);
            Files.writeString(data, daily.replace("TEST,D,", "TEST,W,"));
            Files.setLastModifiedTime(data, FileTime.fromMillis(previous.toMillis() + 2000));
            assertEquals(List.of(TimeFrame.Period.WEEKLY), provider.getAvailableTimeFrames(symbol));
        }
    }

    @Test
    void query_rejects_coarsening_from_nonstandard_source_boundaries() throws IOException {
        Files.writeString(directory.resolve("test.txt"), HEADER + "TEST,D,20260130,000000,10,14,8,12,100\n");
        var format = FlatFileFormat.builder().skipFirstLines(1)
                .lineMapper(FlatFileFormat.STOOQ.getLineMapper())
                .timeFrameExtractor(line -> TimeFrame.Period.DAILY.withDailyAlignment(LocalTime.NOON)).build();
        try (var provider = new FlatFileDataProvider(format, ResourceHandle.of(FileSystems.getDefault()),
                "shifted source", List.of(directory))) {
            assertThrows(UnsupportedDataQueryException.class, () -> candles(provider, TimeFrame.Period.WEEKLY));
        }
    }

    private FlatFileDataProvider localProvider() throws IOException {
        return new FlatFileDataProvider(FlatFileFormat.STOOQ, ResourceHandle.of(FileSystems.getDefault()),
                "local fixture", List.of(directory));
    }

    private FlatFileDataProvider dailyProvider() throws IOException {
        return provider("""
                TEST,D,20260129,000000,10,14,8,12,100
                TEST,D,20260130,000000,14,18,12,16,200
                TEST,D,20260202,000000,20,24,18,22,300
                TEST,D,20260203,000000,24,28,22,26,400
                """);
    }

    private FlatFileDataProvider provider(String rows) throws IOException {
        Path zip = directory.resolve("quotes.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("data/quotes/test.txt"));
            output.write(("<TICKER>,<PER>,<DATE>,<TIME>,<OPEN>,<HIGH>,<LOW>,<CLOSE>,<VOL>\n" + rows)
                    .getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return FlatFileFormat.STOOQ.newDataProvider(zip);
    }

    private static List<Candle> candles(FlatFileDataProvider provider, TimeFrame timeFrame) {
        return provider.query(Candle.class, DataQuery.of(resource(provider, timeFrame))).collectList().block();
    }

    private static SymbolResource<Candle> resource(FlatFileDataProvider provider, TimeFrame timeFrame) {
        SymbolIdentity symbol = provider.listSymbols().getFirst();
        return SymbolResource.of(symbol, timeFrame);
    }

    private static void assertCandle(Candle candle, String closeTime, double open, double high,
                                     double low, double close, double volume) {
        assertAll(
                () -> assertEquals(Chronological.toEpochNanos(LocalDateTime.parse(closeTime)), candle.time()),
                () -> assertEquals(open, candle.open()),
                () -> assertEquals(high, candle.high()),
                () -> assertEquals(low, candle.low()),
                () -> assertEquals(close, candle.close()),
                () -> assertEquals(volume, candle.volume()),
                () -> assertEquals(close * volume, candle.turnover()));
    }
}
