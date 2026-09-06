/* Copyright 2022 Mariusz Bernacki <consulting@didalgo.com>
 * SPDX-License-Identifier: Apache-2.0 */
package one.chartsy.data.provider.file;

import lombok.Builder;
import lombok.Getter;
import one.chartsy.TimeFrame;
import one.chartsy.data.provider.FlatFileDataProvider;
import one.chartsy.time.Chronological;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.function.Function;

@Getter
@Builder(builderClassName = "Builder")
public class FlatFileFormat {
    private final String fileNamePattern;
    private final Chronological.ChronoOrder dataOrder;
    private final String encoding;
    private final int skipFirstLines;
    private final boolean ignoreEmptyLines;
    private final boolean stripLines;
    private final boolean caseSensitiveSymbols;
    private final LineMapperType<?> lineMapper;
    /** Extracts the native candle period from a data row, when the format declares it. */
    private final Function<String, TimeFrame> timeFrameExtractor;
    /**
     * Declares that the last data record independently describes the latest candle. Requires
     * chronological, single-byte text and a native-period extractor. Intermediate records are
     * validated only when loading the history, not when reading endpoint metadata.
     */
    private final boolean latestCandleFromLastRecord;

    public static class Builder {
        Chronological.ChronoOrder dataOrder = Chronological.ChronoOrder.CHRONOLOGICAL;
        String encoding = "ISO-8859-2";
    }

    public static final FlatFileFormat FOREXTESTER = builder()
            .fileNamePattern("*\\.zip")
            .skipFirstLines(1)
            .lineMapper(
                    new SimpleCandleLineMapper.Type(
                            ',', Arrays.asList("SKIP","DATE","TIME","OPEN","HIGH","LOW","CLOSE","SKIP"), DateTimeFormatter.ofPattern("yyyyMMdd"), DateTimeFormatter.ofPattern("HHmmss")))
            .build();

    public static final FlatFileFormat HISTDATA_ASCII = builder()
            .fileNamePattern("*\\.zip")
            .lineMapper(
                    new SimpleCandleLineMapper.Type(
                            ';', Arrays.asList("OPEN_DATE_TIME","OPEN","HIGH","LOW","CLOSE","VOLUME"), DateTimeFormatter.ofPattern("yyyyMMdd HHmmss")))
            .build();

    public static final FlatFileFormat STOOQ = builder()
            .fileNamePattern("*\\.zip")
            .latestCandleFromLastRecord(true)
            .timeFrameExtractor(FlatFileFormat::stooqTimeFrame)
            .lineMapper(
                    new SimpleCandleLineMapper.Type(
                            ',', Arrays.asList("SKIP","SKIP","DATE","TIME","OPEN","HIGH","LOW","CLOSE","VOLUME?"), DateTimeFormatter.ofPattern("yyyyMMdd"), DateTimeFormatter.ofPattern("HHmmss")))
            .skipFirstLines(1)
            .build();

    private static TimeFrame stooqTimeFrame(String line) {
        int start = line.indexOf(',') + 1;
        int end = line.indexOf(',', start);
        if (start == 0 || end < 0)
            throw new FlatFileFormatException("Missing Stooq PER column");

        String period = line.substring(start, end).strip();
        return switch (period) {
            case "D" -> TimeFrame.Period.DAILY;
            case "W" -> TimeFrame.Period.WEEKLY;
            case "M" -> TimeFrame.Period.MONTHLY;
            default -> {
                int minutes;
                try {
                    minutes = Integer.parseInt(period);
                } catch (NumberFormatException ex) {
                    throw new FlatFileFormatException("Unsupported Stooq PER value: " + period);
                }
                if (minutes <= 0 || minutes >= 1440)
                    throw new FlatFileFormatException("Unsupported Stooq intraday period: " + period);
                Duration duration = Duration.ofMinutes(minutes);
                yield Arrays.stream(TimeFrame.Period.values())
                        .filter(frame -> duration.equals(frame.getRegularity()))
                        .map(TimeFrame.class::cast)
                        .findFirst()
                        .orElseGet(() -> TimeFrame.CustomPeriod.of(duration, "M" + period));
            }
        };
    }

    public FlatFileDataProvider newDataProvider(String file) throws IOException {
        return newDataProvider(Path.of(file));
    }

    public FlatFileDataProvider newDataProvider(Path file) throws IOException {
        return new FlatFileDataProvider(this, file);
    }

}
