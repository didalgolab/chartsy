/* Copyright 2026 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider;

import one.chartsy.Candle;
import one.chartsy.SymbolResource;
import one.chartsy.TimeFrame;
import one.chartsy.core.ResourceHandle;
import one.chartsy.data.DataQuery;
import one.chartsy.data.provider.file.FlatFileFormat;
import one.chartsy.data.provider.file.FlatFileFormatException;
import one.chartsy.data.provider.file.FlatFileParseException;
import one.chartsy.data.provider.file.LineMapperType;
import one.chartsy.time.Chronological;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class FlatFileDataProviderLatestCandleTimeTest {
    private static final String HEADER = "<TICKER>,<PER>,<DATE>,<TIME>,<OPEN>,<HIGH>,<LOW>,<CLOSE>,<VOL>\n";
    private static final String FIRST = "TEST,D,20260129,000000,10,14,8,12,100";
    private static final String LAST = "TEST,D,20260203,000000,24,28,22,26,400";
    @TempDir Path directory;

    @Test
    void getLatestCandleTime_daily_and_coarser_frames_match_last_output_candle() throws IOException {
        write(FIRST + "\n" + LAST + "\n");
        try (var provider = local(FlatFileFormat.STOOQ)) {
            assertSame(provider, provider.getLookup().lookup(LatestCandleTimeProvider.class));
            for (var frame : List.of(TimeFrame.Period.DAILY, TimeFrame.Period.WEEKLY,
                    TimeFrame.Period.MONTHLY, TimeFrame.Period.QUARTERLY, TimeFrame.Period.YEARLY)) {
                assertEndpointMatches(provider, frame, "2026-02-04T00:00");
            }
        }
    }

    @Test
    void getLatestCandleTime_intraday_native_and_aggregates_use_actual_last_timestamp() throws IOException {
        write("TEST,60,20260129,100000,10,14,8,12,100\nTEST,60,20260203,110000,24,28,22,26,400\n");
        try (var provider = local(FlatFileFormat.STOOQ)) {
            for (var frame : List.of(TimeFrame.Period.H1, TimeFrame.Period.H2,
                    TimeFrame.Period.DAILY, TimeFrame.Period.WEEKLY, TimeFrame.Period.MONTHLY)) {
                assertEndpointMatches(provider, frame, "2026-02-03T11:00");
            }
        }
    }

    @Test
    void getLatestCandleTime_equivalent_custom_native_period_is_supported() throws IOException {
        write("TEST,7,20260203,110700,24,28,22,26,400\n");
        try (var provider = local(FlatFileFormat.STOOQ)) {
            assertEndpointMatches(provider, TimeFrame.CustomPeriod.of(Duration.ofMinutes(7), "7 minutes"), "2026-02-03T11:07");
        }
    }

    @Test
    void getLatestCandleTime_header_only_and_zero_byte_files_are_known_empty() throws IOException {
        for (String contents : List.of("", HEADER, HEADER.stripTrailing())) {
            Files.writeString(data(), contents);
            try (var provider = local(FlatFileFormat.STOOQ)) {
                var resource = resource(TimeFrame.Period.DAILY);
                assertTrue(provider.supports(resource));
                assertEquals(OptionalLong.empty(), provider.getLatestCandleTime(resource));
                assertTrue(provider.query(Candle.class, DataQuery.of(resource)).collectList().block().isEmpty());
            }
        }
    }

    @Test
    void getLatestCandleTime_handles_crlf_cr_lf_and_absent_final_newline() throws IOException {
        for (String separator : List.of("\r\n", "\r", "\n")) {
            for (String suffix : List.of("", separator)) {
                // More than one retained tail exercises the streaming path.
                String padding = "X".repeat(3000);
                Files.writeString(data(), HEADER.stripTrailing() + separator
                        + FIRST.replace("TEST", padding) + separator + LAST + suffix);
                try (var provider = local(FlatFileFormat.STOOQ)) {
                    assertEndpointMatches(provider, TimeFrame.Period.DAILY, "2026-02-04T00:00");
                }
            }
        }
    }

    @Test
    void getLatestCandleTime_does_not_parse_an_oversized_record_from_a_truncated_suffix() throws IOException {
        write(FIRST + "\n" + LAST.replace("TEST", "X".repeat(100000)) + "\n");
        try (var provider = local(FlatFileFormat.STOOQ)) {
            assertEndpointMatches(provider, TimeFrame.Period.DAILY, "2026-02-04T00:00");
        }
    }

    @Test
    void getLatestCandleTime_empty_lines_follow_the_declared_format_policy() throws IOException {
        write(FIRST.replace("TEST", "X".repeat(3000)) + "\n" + LAST + "\n\n");
        try (var provider = local(FlatFileFormat.STOOQ)) {
            assertThrows(FlatFileParseException.class, () -> provider.getLatestCandleTime(resource(TimeFrame.Period.DAILY)));
            assertThrows(FlatFileParseException.class, () -> candles(provider, TimeFrame.Period.DAILY));
        }
        var lenient = format(FlatFileFormat.STOOQ.getLineMapper()).ignoreEmptyLines(true).stripLines(true).build();
        Files.writeString(data(), Files.readString(data()) + " \r\n".repeat(3000));
        try (var provider = local(lenient)) {
            assertEndpointMatches(provider, TimeFrame.Period.DAILY, "2026-02-04T00:00");
        }
    }

    @Test
    void getLatestCandleTime_reads_only_endpoint_candle_and_reuses_unchanged_cache() throws IOException {
        write(FIRST + "\n" + LAST + "\n");
        AtomicInteger parsed = new AtomicInteger();
        try (var provider = local(countingFormat(parsed))) {
            assertTrue(provider.supports(resource(TimeFrame.Period.DAILY)));
            assertEquals(0, parsed.get());
            var first = provider.getLatestCandleTime(resource(TimeFrame.Period.DAILY));
            assertEquals(1, parsed.get());
            assertEquals(first, provider.getLatestCandleTime(resource(TimeFrame.Period.MONTHLY)));
            assertEquals(1, parsed.get());
        }
    }

    @Test
    void query_unbounded_load_populates_endpoint_cache_without_another_candle_parse() throws IOException {
        write(FIRST + "\n" + LAST + "\n");
        AtomicInteger parsed = new AtomicInteger();
        try (var provider = local(countingFormat(parsed))) {
            long expected = candles(provider, TimeFrame.Period.WEEKLY).getLast().time();
            assertEquals(2, parsed.get());
            assertEquals(OptionalLong.of(expected), provider.getLatestCandleTime(resource(TimeFrame.Period.DAILY)));
            assertEquals(2, parsed.get());
        }
    }

    @Test
    void getLatestCandleTime_refreshes_same_size_local_history_and_empty_to_populated_history() throws IOException {
        write("");
        try (var provider = local(FlatFileFormat.STOOQ)) {
            assertEquals(OptionalLong.empty(), provider.getLatestCandleTime(resource(TimeFrame.Period.DAILY)));
            write(LAST + "\n");
            assertEndpointMatches(provider, TimeFrame.Period.DAILY, "2026-02-04T00:00");
            FileTime oldTime = Files.getLastModifiedTime(data());
            write(LAST.replace("20260203", "20260204") + "\n");
            Files.setLastModifiedTime(data(), FileTime.fromMillis(oldTime.toMillis() + 2000));
            assertEndpointMatches(provider, TimeFrame.Period.DAILY, "2026-02-05T00:00");
        }
    }

    @Test
    void getLatestCandleSnapshot_same_date_price_edit_changes_revision() throws IOException {
        write(FIRST + "\n" + LAST + "\n");
        try (var provider = local(FlatFileFormat.STOOQ)) {
            var resource = resource(TimeFrame.Period.DAILY);
            var before = provider.getLatestCandleSnapshot(resource);
            assertEquals(before, provider.getLatestCandleSnapshot(resource));
            FileTime oldTime = Files.getLastModifiedTime(data());
            write(FIRST + "\n" + LAST.replace(",26,400", ",27,400") + "\n");
            Files.setLastModifiedTime(data(), FileTime.fromMillis(oldTime.toMillis() + 2000));
            assertEquals(27, candles(provider, TimeFrame.Period.DAILY).getLast().close());
            var after = provider.getLatestCandleSnapshot(resource);
            assertEquals(before.time(), after.time());
            assertNotEquals(before.revision(), after.revision());
            assertNotEquals(before, after);
            assertEquals(after, provider.getLatestCandleSnapshot(resource));
        }
    }

    @Test
    void getLatestCandleTime_refreshes_writable_zip_endpoint_and_native_period() throws IOException {
        Path zip = directory.resolve("quotes.zip");
        try (var output = new ZipOutputStream(Files.newOutputStream(zip))) {
            output.putNextEntry(new ZipEntry("test.txt"));
            output.write((HEADER + LAST + "\n").getBytes(StandardCharsets.ISO_8859_1));
            output.closeEntry();
        }
        try (var provider = FlatFileFormat.STOOQ.newDataProvider(zip)) {
            assertEndpointMatches(provider, TimeFrame.Period.DAILY, "2026-02-04T00:00");
            Files.writeString(provider.getFileSystem().getPath("/test.txt"), HEADER + "TEST,60,20260204,130000,24,28,22,26,400\n");
            assertEndpointMatches(provider, TimeFrame.Period.DAILY, "2026-02-04T13:00");
            assertEndpointMatches(provider, TimeFrame.Period.H1, "2026-02-04T13:00");
        }
    }

    @Test
    void supports_undeclared_reversed_or_multibyte_formats_are_not_advertised() throws IOException {
        write(LAST + "\n");
        for (var format : List.of(FlatFileFormat.FOREXTESTER,
                format(FlatFileFormat.STOOQ.getLineMapper()).latestCandleFromLastRecord(false).build(),
                format(FlatFileFormat.STOOQ.getLineMapper()).encoding("UTF-16").build(),
                format(FlatFileFormat.STOOQ.getLineMapper()).encoding("IBM037").build(),
                format(FlatFileFormat.STOOQ.getLineMapper()).dataOrder(Chronological.ChronoOrder.REVERSE_CHRONOLOGICAL).build())) {
            try (var provider = local(format)) {
                assertFalse(provider.supports(resource(TimeFrame.Period.DAILY)));
                assertThrows(UnsupportedOperationException.class, () -> provider.getLatestCandleTime(resource(TimeFrame.Period.DAILY)));
            }
        }
    }

    @Test
    void supports_incompatible_timeframes_are_distinct_from_empty_history() throws IOException {
        write(LAST + "\n");
        try (var provider = local(FlatFileFormat.STOOQ)) {
            assertFalse(provider.supports(resource(TimeFrame.Period.H1)));
            assertThrows(UnsupportedOperationException.class, () -> provider.getLatestCandleTime(resource(TimeFrame.Period.H1)));
        }
    }

    @Test
    void getLatestCandleTime_missing_deleted_or_malformed_histories_are_errors() throws IOException {
        write(FIRST + "\nTEST,D,INVALID,000000,24,28,22,26,400\n");
        try (var provider = local(FlatFileFormat.STOOQ)) {
            assertThrows(DataProviderException.class, () -> provider.getLatestCandleTime(SymbolResource.of("MISSING", TimeFrame.Period.DAILY)));
            assertThrows(FlatFileParseException.class, () -> provider.getLatestCandleTime(resource(TimeFrame.Period.DAILY)));
            write("TEST,UNKNOWN,20260203,000000,24,28,22,26,400\n");
            assertThrows(FlatFileFormatException.class, () -> provider.getLatestCandleTime(resource(TimeFrame.Period.DAILY)));
            Files.delete(data());
            assertThrows(UncheckedIOException.class, () -> provider.getLatestCandleTime(resource(TimeFrame.Period.DAILY)));
        }
    }

    @Test
    void getLatestCandleTime_reports_file_change_during_endpoint_read() throws IOException {
        write(LAST + "\n");
        LineMapperType<Candle> changingMapper = context -> {
            var delegate = FlatFileFormat.STOOQ.getLineMapper().createLineMapper(context);
            return (line, number) -> {
                Candle candle = (Candle) delegate.mapLine(line, number);
                Files.writeString(data(), HEADER + FIRST + "\n" + LAST + "\n");
                return candle;
            };
        };
        try (var provider = local(format(changingMapper).build())) {
            assertThrows(DataProviderException.class, () -> provider.getLatestCandleTime(resource(TimeFrame.Period.DAILY)));
        }
    }

    @Test
    void query_reports_file_change_instead_of_populating_a_mixed_version_cache() throws IOException {
        write(LAST + "\n");
        LineMapperType<Candle> changingMapper = context -> {
            var delegate = FlatFileFormat.STOOQ.getLineMapper().createLineMapper(context);
            return (line, number) -> {
                Candle candle = (Candle) delegate.mapLine(line, number);
                Files.setLastModifiedTime(data(), FileTime.fromMillis(Files.getLastModifiedTime(data()).toMillis() + 2000));
                return candle;
            };
        };
        try (var provider = local(format(changingMapper).build())) {
            assertThrows(DataProviderException.class, () -> candles(provider, TimeFrame.Period.DAILY));
        }
    }

    @Test
    void getLatestCandleTime_concurrent_resources_have_independent_mappers_and_tails() throws Exception {
        for (int index = 1; index <= 20; index++)
            Files.writeString(directory.resolve("stock" + index + ".txt"), HEADER + LAST.replace("20260203", "202602%02d".formatted(index)) + "\n");
        try (var provider = local(FlatFileFormat.STOOQ); var workers = Executors.newFixedThreadPool(4)) {
            List<Callable<OptionalLong>> tasks = new ArrayList<>();
            for (int index = 1; index <= 20; index++) {
                var resource = SymbolResource.<Candle>of("STOCK" + index, TimeFrame.Period.DAILY);
                tasks.add(() -> provider.getLatestCandleTime(resource));
            }
            var results = workers.invokeAll(tasks);
            for (int index = 0; index < results.size(); index++)
                assertEquals(OptionalLong.of(time("2026-02-%02dT00:00".formatted(index + 2))), results.get(index).get());
        }
    }

    private Path data() { return directory.resolve("test.txt"); }

    private void write(String rows) throws IOException { Files.writeString(data(), HEADER + rows); }

    private FlatFileDataProvider local(FlatFileFormat format) throws IOException {
        return new FlatFileDataProvider(format, ResourceHandle.of(FileSystems.getDefault()), "local fixture", List.of(directory));
    }

    private static FlatFileFormat.Builder format(LineMapperType<?> mapper) {
        return FlatFileFormat.builder().skipFirstLines(1).lineMapper(mapper)
                .timeFrameExtractor(FlatFileFormat.STOOQ.getTimeFrameExtractor()).latestCandleFromLastRecord(true);
    }

    private static FlatFileFormat countingFormat(AtomicInteger parsed) {
        LineMapperType<Candle> mapper = context -> {
            var delegate = FlatFileFormat.STOOQ.getLineMapper().createLineMapper(context);
            return (line, number) -> { parsed.incrementAndGet(); return (Candle) delegate.mapLine(line, number); };
        };
        return format(mapper).build();
    }

    private static SymbolResource<Candle> resource(TimeFrame frame) { return SymbolResource.of("TEST", frame); }

    private static List<Candle> candles(FlatFileDataProvider provider, TimeFrame frame) {
        return provider.query(Candle.class, DataQuery.of(resource(frame))).collectList().block();
    }

    private static long time(String date) { return Chronological.toEpochNanos(LocalDateTime.parse(date)); }

    private static void assertEndpointMatches(FlatFileDataProvider provider, TimeFrame frame, String expected) {
        assertTrue(provider.supports(resource(frame)));
        assertEquals(OptionalLong.of(time(expected)), provider.getLatestCandleTime(resource(frame)));
        assertEquals(time(expected), candles(provider, frame).getLast().time());
    }
}
